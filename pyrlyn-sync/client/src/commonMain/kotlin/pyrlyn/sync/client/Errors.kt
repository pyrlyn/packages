package pyrlyn.sync.client

import com.google.rpc.presence
import kotlinx.rpc.grpc.GrpcStatus
import kotlinx.rpc.grpc.GrpcStatusCode
import kotlinx.rpc.grpc.GrpcStatusException
import kotlinx.rpc.grpc.description
import kotlinx.rpc.grpc.status
import kotlinx.rpc.grpc.statusCode
import pyrlyn.rpc.statusDetails
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Duration.Companion.seconds

/** The `ErrorInfo.domain` of the sync service's errors; reasons of other domains are not its own. */
internal const val DOMAIN = "pyrlyn.sync"

/** Why a sync call stopped. Every subclass is a decision for the app; retries the protocol allows have already been made. */
sealed class SyncException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause) {
    /** The user has not opted this device in for the scope; nothing was sent. */
    class NotOptedIn : SyncException("sync is off on this device for this scope")

    /** No usable token: ask the user to log in. */
    class Auth(
        val error: AuthException,
    ) : SyncException(error.message.orEmpty(), error)

    /** The token belongs to another user than the scope: stop syncing it and ask the user. */
    class ScopeMismatch : SyncException("the signed-in account does not own this scope")

    /** Even a refreshed token is not for this organization: the user left it. */
    class OrganizationMismatch(
        val organizationId: String,
    ) : SyncException("the token is not for organization $organizationId")

    /** The product is not in the owner's plan: show the upgrade path. */
    class NotEntitled(
        val product: String,
    ) : SyncException("product $product is not in the owner's plan")

    /** The server needs a newer app: ask the user to update. */
    class ClientTooOld(
        /** The lowest version the server accepts. */
        val minVersion: String,
    ) : SyncException("this app is too old; the server needs $minVersion")

    /** A quota is full: additions stay queued, deletions went through. Tell the user to delete or upgrade. */
    class QuotaExceeded(
        /** `records`, `storage_bytes` or `devices`. */
        val quota: String,
        /** The plan's limit. */
        val limit: String,
        /** Current usage. */
        val used: String,
    ) : SyncException("quota $quota is full ($used of $limit); additions stay queued")

    /** The device was disabled on the server again right after re-enabling it. */
    class DeviceNotEnabled : SyncException("the server disabled this device for the scope")

    /**
     * The server stayed unreachable or failing through every retry. Pending changes stay queued
     * with their mutation ids; sync again later.
     */
    class Unavailable(
        val status: GrpcStatusException,
    ) : SyncException("the sync server is unavailable: ${status.status.description}", status)

    /** The server refused the request for a reason retrying cannot fix (a client bug or misconfiguration). */
    class Refused(
        val status: GrpcStatusException,
    ) : SyncException("the sync server refused the request: ${status.status.description}", status)

    /** The app's local store failed. */
    class Local(
        cause: Throwable,
    ) : SyncException("local sync store: ${cause.message}", cause)
}

/** A [LocalStore] failure on its way to [SyncException.Local], kept apart from the client's own errors. */
internal class LocalFailure(
    cause: Throwable,
) : Exception(cause)

internal suspend fun <T> local(call: suspend () -> T): T =
    try {
        call()
    } catch (e: CancellationException) {
        throw e
    } catch (
        // Whatever the app's store throws is a store failure.
        @Suppress("TooGenericExceptionCaught") e: Exception,
    ) {
        throw LocalFailure(e)
    }

internal fun statusException(
    code: GrpcStatusCode,
    message: String,
): GrpcStatusException = GrpcStatusException(GrpcStatus(code, message))

/** What the app should do about [failure], one of the client's internal failures. */
internal fun mapFailure(failure: Exception): Exception =
    when (failure) {
        is SyncException -> failure
        is LocalFailure -> SyncException.Local(failure.cause ?: failure)
        is AuthException -> SyncException.Auth(failure)
        is GrpcStatusException -> failure.toSyncException()
        else -> failure
    }

internal fun GrpcStatusException.toSyncException(): SyncException =
    when (reason) {
        "SCOPE_MISMATCH" -> {
            SyncException.ScopeMismatch()
        }

        "ORGANIZATION_MISMATCH" -> {
            SyncException.OrganizationMismatch(meta("organization_id"))
        }

        "PRODUCT_NOT_ENTITLED" -> {
            SyncException.NotEntitled(meta("product"))
        }

        "CLIENT_TOO_OLD" -> {
            SyncException.ClientTooOld(meta("min_version"))
        }

        "QUOTA_EXCEEDED" -> {
            SyncException.QuotaExceeded(meta("quota"), meta("limit"), meta("used"))
        }

        "DEVICE_NOT_ENABLED" -> {
            SyncException.DeviceNotEnabled()
        }

        else -> {
            when {
                status.statusCode == GrpcStatusCode.UNAUTHENTICATED -> SyncException.Auth(AuthException.LoginRequired())
                transient -> SyncException.Unavailable(this)
                else -> SyncException.Refused(this)
            }
        }
    }

/** `ErrorInfo.reason` of a sync error; empty for errors raised outside the sync service (auth layer, transport, decoder). */
internal val GrpcStatusException.reason: String
    get() =
        statusDetails()
            ?.errorInfo
            ?.takeIf { it.domain == DOMAIN }
            ?.reason
            .orEmpty()

/** Errors the protocol says to retry with the same batch. */
internal val GrpcStatusException.transient: Boolean
    get() =
        status.statusCode in setOf(GrpcStatusCode.UNAVAILABLE, GrpcStatusCode.DEADLINE_EXCEEDED, GrpcStatusCode.INTERNAL) ||
            reason == "RATE_LIMITED"

/** The wait `RetryInfo` asks for, if any; a negative one is none. */
internal val GrpcStatusException.retryDelay: Duration?
    get() {
        val info = statusDetails()?.retryInfo ?: return null
        if (!info.presence.hasRetryDelay) return null
        val delay = info.retryDelay
        return (delay.seconds.seconds + delay.nanos.nanoseconds).takeIf { !it.isNegative() }
    }

/** The index `i` of the `mutations[i]` a `BadRequest` names. */
internal val GrpcStatusException.badMutation: Int?
    get() {
        val field =
            statusDetails()
                ?.badRequest
                ?.fieldViolations
                ?.firstOrNull()
                ?.field ?: return null
        val index = field.removePrefix("mutations[").takeIf { it != field }?.substringBefore(']', "") ?: return null
        return index.takeIf { it.isNotEmpty() && it.all { c -> c in '0'..'9' } }?.toIntOrNull()
    }

/** An `ErrorInfo.metadata` value, empty when absent. */
internal fun GrpcStatusException.meta(key: String): String =
    statusDetails()
        ?.errorInfo
        ?.metadata
        ?.get(key)
        .orEmpty()

/** A numeric `ErrorInfo.metadata` value, such as `limit`. */
internal fun GrpcStatusException.metaNumber(key: String): Int? = meta(key).toIntOrNull()
