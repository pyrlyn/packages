@file:OptIn(ExperimentalAtomicApi::class)

package pyrlyn.sync.client

import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlinx.rpc.grpc.GrpcStatusCode
import kotlinx.rpc.grpc.GrpcStatusException
import kotlinx.rpc.grpc.append
import kotlinx.rpc.grpc.client.GrpcClient
import kotlinx.rpc.grpc.client.GrpcClientCallScope
import kotlinx.rpc.grpc.client.GrpcClientConfiguration
import kotlinx.rpc.grpc.client.GrpcClientInterceptor
import kotlinx.rpc.grpc.status
import kotlinx.rpc.grpc.statusCode
import kotlinx.rpc.withService
import pyrlyn.common.v1.ClientInfo
import pyrlyn.sync.v1.DisableSyncRequest
import pyrlyn.sync.v1.EnableSyncRequest
import pyrlyn.sync.v1.Mutation
import pyrlyn.sync.v1.MutationOutcome
import pyrlyn.sync.v1.MutationResult
import pyrlyn.sync.v1.PullRequest
import pyrlyn.sync.v1.PushRequest
import pyrlyn.sync.v1.Scope
import pyrlyn.sync.v1.SyncLimits
import pyrlyn.sync.v1.SyncService
import pyrlyn.sync.v1.invoke
import pyrlyn.sync.v1.presence
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.coroutines.cancellation.CancellationException
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** Estimated bytes of mutations per push. The service receives at most 4 MiB per message; the rest is headroom for the per-field framing the estimate leaves out. */
internal const val MAX_BATCH_BYTES = 3 * 1024 * 1024

/** Push passes per round. A conflict on a `reject` kind queues a merged change the next pass sends, but two devices fighting over one record must not keep a round going. */
private const val MAX_PASSES = 3

/** Per-mutation framing the batch estimate adds to its fields. */
private const val MUTATION_OVERHEAD = 32

/** The doubling stops here, long before any sane cap. */
private const val MAX_DOUBLINGS = 16

/** Backoff for `UNAVAILABLE`, `DEADLINE_EXCEEDED`, `INTERNAL` and `RATE_LIMITED`. */
data class Retry(
    /** Retries after the first attempt. */
    val attempts: Int = 5,
    /** Wait before the first retry; doubles on each one. */
    val base: Duration = 250.milliseconds,
    /** Longest wait, also of a wait the server asks for. */
    val max: Duration = 30.seconds,
) {
    /**
     * The wait before retry [attempt] (from 0): half the capped exponential step plus a random part
     * of the other half, so devices that lost the server together do not all come back at once.
     */
    internal fun delay(
        attempt: Int,
        random: Random = Random,
    ): Duration {
        val step = minOf(base * (1L shl attempt.coerceIn(0, MAX_DOUBLINGS)).toDouble(), max) / 2
        return step + step * random.nextDouble()
    }
}

/** Who syncs what: the device, the scope and how patiently. */
data class SyncConfig(
    /** Product, app version and the device's stable random id. */
    val client: ClientInfo,
    /** Whose records to sync. */
    val scope: Scope,
    /** Device name shown in the dashboard; sent by every `EnableSync`. */
    val deviceName: String,
    /** Deadline of one call. */
    val timeout: Duration = 30.seconds,
    /** Backoff for transient failures. */
    val retry: Retry = Retry(),
)

/** What one call did. */
data class Report(
    /** Records received from pulls. */
    val pulled: Int = 0,
    /** Mutations the server answered. */
    val pushed: Int = 0,
    /** Mutations of kinds this server does not know yet; they stay queued. */
    val held: Int = 0,
    /** Mutations dropped through [LocalStore.reject]. */
    val rejected: Int = 0,
    /** Whether the cursor was refused and the log was read again from the start. */
    val resynced: Boolean = false,
)

private class Tally {
    var pulled = 0
    var pushed = 0
    var held = 0
    var rejected = 0
    var resynced = false

    fun report() = Report(pulled, pushed, held, rejected, resynced)
}

/** What one push round keeps across its passes. */
private class PushRound(
    var max: Int,
) {
    val held = mutableSetOf<RecordKey>()
    var quota: GrpcStatusException? = null
}

/** Sends the bearer token and the deadline of the call in flight. */
private class Bearer(
    private val timeout: Duration,
) : GrpcClientInterceptor {
    val token = AtomicReference("")

    override fun <Request, Response> GrpcClientCallScope<Request, Response>.intercept(request: Flow<Request>): Flow<Response> {
        callOptions.timeout = timeout
        requestHeaders.append("authorization", "Bearer ${token.load()}")
        return proceed(request)
    }
}

/**
 * Syncs one product and scope of this device with the sync service at [target] (`host:port`).
 * [configure] sets the channel's credentials: TLS, or plaintext for a local server. Calls run one
 * at a time, as each one reads and writes the same [LocalStore].
 */
class SyncClient<T : TokenSource>(
    target: String,
    /** The token source, for example to save a token after a login. */
    val tokens: T,
    private val config: SyncConfig,
    configure: GrpcClientConfiguration.() -> Unit,
) : AutoCloseable {
    private val bearer = Bearer(config.timeout)
    private val grpc =
        GrpcClient(target) {
            configure()
            intercept(bearer)
        }
    private val service = grpc.withService<SyncService>()
    private val running = Mutex()

    override fun close() {
        grpc.shutdownNow()
    }

    /** Opts this device in and turns sync on locally. Calling it again renames the device. Throws [SyncException]. */
    suspend fun enable(store: LocalStore): SyncLimits = run { enableDevice(store) }

    /** Turns sync off locally, then opts this device out on the server. Throws [SyncException]; sync is off locally even then. */
    suspend fun disable(store: LocalStore) {
        run {
            // The local switch is the primary guard, so it goes off even when the server cannot be reached.
            local { store.setLimits(null) }
            val request =
                DisableSyncRequest {
                    client = config.client
                    scope = config.scope
                }
            call { disableSync(request) }
        }
    }

    /** One sync round: pull every page, then push every pending change. Throws [SyncException]; pages already saved and pushes already answered stay stored. */
    suspend fun sync(store: LocalStore): Report =
        run {
            val tally = Tally()
            step(store, tally, push = false)
            step(store, tally, push = true)
            tally.report()
        }

    /** Pulls every page after the stored cursor, or the whole log when the cursor is refused. Throws [SyncException]. */
    suspend fun pull(store: LocalStore): Report = run { Tally().also { step(store, it, push = false) }.report() }

    /** Pushes every pending change in batches within the server's limits. Throws [SyncException]. */
    suspend fun push(store: LocalStore): Report = run { Tally().also { step(store, it, push = true) }.report() }

    private suspend fun <R> run(block: suspend () -> R): R =
        running.withLock {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (
                // Every failure leaves as what the app should do about it.
                @Suppress("TooGenericExceptionCaught") e: Exception,
            ) {
                throw mapFailure(e)
            }
        }

    private suspend fun step(
        store: LocalStore,
        tally: Tally,
        push: Boolean,
    ) {
        var limits = local { store.limits() } ?: throw SyncException.NotOptedIn()
        var reenabled = false
        while (true) {
            try {
                if (push) pushAll(store, limits, tally) else pullAll(store, tally)
                return
            } catch (e: GrpcStatusException) {
                // Sync is still on locally, so the server lost the opt-in or the device was disabled
                // elsewhere: opt in again, once.
                if (reenabled || e.reason != "DEVICE_NOT_ENABLED") throw e
                reenabled = true
                limits = enableDevice(store)
            }
        }
    }

    private suspend fun enableDevice(store: LocalStore): SyncLimits {
        val request =
            EnableSyncRequest {
                client = config.client
                scope = config.scope
                deviceName = config.deviceName
            }
        val limits = call { enableSync(request) }.limits
        local { store.setLimits(limits) }
        return limits
    }

    private suspend fun pullAll(
        store: LocalStore,
        tally: Tally,
    ) {
        val cursor = local { store.cursor() }.orEmpty()
        try {
            pullPages(store, cursor, null, tally)
        } catch (e: GrpcStatusException) {
            if (e.reason != "CURSOR_EXPIRED" && e.reason != "CURSOR_INVALID") throw e
            tally.resynced = true
            pullPages(store, "", mutableSetOf(), tally)
        }
    }

    /** Pages from [from] until `has_more` is false. With [seen] it is a full resync: the cursor is stored only by [LocalStore.finishResync] after the last page. */
    private suspend fun pullPages(
        store: LocalStore,
        from: String,
        seen: MutableSet<RecordKey>?,
        tally: Tally,
    ) {
        var cursor = from
        while (true) {
            val request =
                PullRequest {
                    client = config.client
                    scope = config.scope
                    this.cursor = cursor
                    limit = 0u
                }
            val page = call { pull(request) }
            tally.pulled += page.records.size
            local {
                if (seen == null) {
                    store.savePage(page.records, page.cursor)
                } else {
                    page.records.mapTo(seen) { it.key }
                    store.savePage(page.records, null)
                    if (!page.hasMore) store.finishResync(seen, page.cursor)
                }
            }
            if (!page.hasMore) return
            cursor = page.cursor
        }
    }

    private suspend fun pushAll(
        store: LocalStore,
        limits: SyncLimits,
        tally: Tally,
    ) {
        val round = PushRound(minOf(limits.maxPushMutations, Int.MAX_VALUE.toUInt()).toInt().coerceAtLeast(1))
        for (pass in 1..MAX_PASSES) if (!pushPass(store, round, tally)) break
        round.quota?.let { throw it }
    }

    /** One pass over the queue; whether a conflict queued a merged change for another pass. */
    private suspend fun pushPass(
        store: LocalStore,
        round: PushRound,
        tally: Tally,
    ): Boolean {
        val queue = local { store.pending() }.filterTo(mutableListOf()) { it.key !in round.held && (round.quota == null || it.deleted) }
        var conflicts = false
        while (queue.isNotEmpty()) {
            val n = batchLen(queue, round.max)
            val request =
                PushRequest {
                    client = config.client
                    scope = config.scope
                    mutations = queue.subList(0, n).toList()
                }
            val response =
                try {
                    call { push(request) }
                } catch (e: GrpcStatusException) {
                    refused(e, store, queue, n, round, tally)
                    continue
                }
            val sent = queue.subList(0, n)
            val results = pushed(sent.toList(), response.results)
            sent.clear()
            conflicts = conflicts || results.any { it.outcome == Outcome.CONFLICT }
            local { store.savePush(results) }
            tally.pushed += n
        }
        return conflicts
    }

    /** Acts on a push of the first [n] of [queue] that [status] refused, or throws it when nothing helps. */
    private suspend fun refused(
        status: GrpcStatusException,
        store: LocalStore,
        queue: MutableList<Mutation>,
        n: Int,
        round: PushRound,
        tally: Tally,
    ) {
        val reason = status.reason
        val bad = status.badMutation?.takeIf { it < n }
        when {
            (reason == "BATCH_TOO_LARGE" || (status.status.statusCode == GrpcStatusCode.OUT_OF_RANGE && reason.isEmpty())) && n > 1 -> {
                round.max = status.metaNumber("limit")?.takeIf { it in 1 until n } ?: (n / 2)
            }

            // This server predates the kind: keep those records for a later round.
            reason == "UNKNOWN_KIND" && bad != null -> {
                round.held += queue.removeAt(bad).key
                tally.held++
            }

            reason == "PAYLOAD_TOO_LARGE" && bad != null -> {
                val mutation = queue.removeAt(bad)
                local { store.reject(mutation) }
                tally.rejected++
            }

            // Deletions always pass the quota check; additions wait for the user.
            reason == "QUOTA_EXCEEDED" && queue.any { !it.deleted } -> {
                queue.retainAll { it.deleted }
                round.quota = status
            }

            else -> {
                throw status
            }
        }
    }

    /** One RPC with the bearer token, one token refresh and backoff on transient errors. */
    private suspend fun <R> call(rpc: suspend SyncService.() -> R): R {
        var token = token { tokens.token() }
        var refreshed = false
        var attempt = 0
        while (true) {
            val status =
                try {
                    return send(token, rpc)
                } catch (e: GrpcStatusException) {
                    e
                }
            // An expired token, or one for another organization than the scope: ask for a new one
            // once. A source that cannot give one leaves the server's answer.
            if (!refreshed && (status.status.statusCode == GrpcStatusCode.UNAUTHENTICATED || status.reason == "ORGANIZATION_MISMATCH")) {
                refreshed = true
                token = refresh(status)
            } else if (attempt < config.retry.attempts && status.transient) {
                delay(status.retryDelay?.coerceAtMost(config.retry.max) ?: config.retry.delay(attempt))
                attempt++
            } else {
                throw status
            }
        }
    }

    private suspend fun <R> send(
        token: Secret,
        rpc: suspend SyncService.() -> R,
    ): R {
        val value = token.reveal()
        // A header carries visible ASCII only; anything else is not a token the server could take.
        if (value.isEmpty() || value.any { it !in ' '..'~' }) throw AuthException.LoginRequired()
        bearer.token.store(value)
        return try {
            // The deadline is enforced here too, so a server that never answers still ends in a retry.
            withTimeout(config.timeout) { service.rpc() }
        } catch (_: TimeoutCancellationException) {
            throw statusException(GrpcStatusCode.DEADLINE_EXCEEDED, "no answer before the deadline")
        }
    }

    /** A new token after [refused]; a source that cannot give one leaves the server's answer. */
    private suspend fun refresh(refused: GrpcStatusException): Secret =
        try {
            token { tokens.refresh() }
        } catch (_: AuthException.LoginRequired) {
            throw refused
        }
}

/** A [TokenSource] call; whatever else than [AuthException] it throws is the source failing. */
private suspend fun token(call: suspend () -> Secret): Secret =
    try {
        call()
    } catch (e: CancellationException) {
        throw e
    } catch (e: AuthException) {
        throw e
    } catch (
        @Suppress("TooGenericExceptionCaught") e: Exception,
    ) {
        throw AuthException.Server(e.message.orEmpty(), e)
    }

/** How many mutations from the front of [queue] fit one push: at most [max], about [MAX_BATCH_BYTES], one per record, and never none. */
internal fun batchLen(
    queue: List<Mutation>,
    max: Int,
): Int {
    val keys = mutableSetOf<RecordKey>()
    var bytes = 0L
    var n = 0
    for (m in queue.take(max)) {
        bytes += m.payload.size + m.id.encodeToByteArray().size + m.kind.encodeToByteArray().size + m.mutationId.encodeToByteArray().size + MUTATION_OVERHEAD
        if (!keys.add(m.key) || bytes > MAX_BATCH_BYTES) break
        n++
    }
    return n.coerceAtLeast(1)
}

/** Pairs each sent mutation with its result, refusing a reply that does not match. */
internal fun pushed(
    sent: List<Mutation>,
    results: List<MutationResult>,
): List<Pushed> {
    fun bad() = statusException(GrpcStatusCode.INTERNAL, "push reply does not match the batch")
    if (sent.size != results.size) throw bad()
    return sent.zip(results) { mutation, result ->
        val outcome =
            when (result.outcome) {
                MutationOutcome.APPLIED -> Outcome.APPLIED
                MutationOutcome.SUPERSEDED -> Outcome.SUPERSEDED
                MutationOutcome.CONFLICT -> Outcome.CONFLICT
                else -> null
            }
        if (outcome == null || !result.presence.hasRecord || result.mutationId != mutation.mutationId) throw bad()
        Pushed(mutation, outcome, result.record)
    }
}
