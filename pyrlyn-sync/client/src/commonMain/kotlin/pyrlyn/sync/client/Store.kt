@file:OptIn(ExperimentalUuidApi::class)

package pyrlyn.sync.client

import kotlinx.io.bytestring.ByteString
import pyrlyn.sync.v1.Mutation
import pyrlyn.sync.v1.Record
import pyrlyn.sync.v1.SyncLimits
import pyrlyn.sync.v1.invoke
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** A record's address inside its log. */
data class RecordKey(
    val kind: String,
    val id: String,
)

val Record.key: RecordKey get() = RecordKey(kind, id)

val Mutation.key: RecordKey get() = RecordKey(kind, id)

/** What the server did with one pushed mutation. */
enum class Outcome {
    /** Stored; `record.version` is the new version. */
    APPLIED,

    /** A newer write won under last-writer-wins. */
    SUPERSEDED,

    /** The kind rejects writes on a stale base. */
    CONFLICT,
}

/** One pushed mutation, its outcome and the record as the server now holds it. */
data class Pushed(
    /** The mutation as it was sent. */
    val mutation: Mutation,
    val outcome: Outcome,
    /** The new state when applied, the winning state otherwise. */
    val record: Record,
)

/**
 * Local sync state of one product and scope. Each method is one local transaction, so a crash
 * leaves either the old state or the new one. A failure is any exception; the client reports it
 * as [SyncException.Local].
 */
interface LocalStore {
    /**
     * The limits from the last `EnableSync`, or null while the user has not opted this device in.
     * This is the opt-in switch: the client sends nothing while it is null.
     */
    suspend fun limits(): SyncLimits?

    /** Turns sync on (with the server's limits) or off (null). */
    suspend fun setLimits(limits: SyncLimits?)

    /** The cursor of the last saved page; null before the first pull. */
    suspend fun cursor(): String?

    /**
     * Stores one pulled page and, when given, its cursor. A record with an unpushed local change
     * keeps that change and its original base version, so the server decides on the next push;
     * rebasing it here would turn every conflict into a local win. A record whose version is
     * already held (this device's own write) is skipped.
     *
     * [cursor] is null for the pages of a full resync, so an interrupted resync leaves the old
     * cursor and starts over next time.
     */
    suspend fun savePage(
        records: List<Record>,
        cursor: String?,
    )

    /**
     * Ends a full resync: deletes synced records whose key is not in [seen] unless they have an
     * unpushed change, sets the base version of those changes to 0 so they are pushed as creates,
     * and stores [cursor].
     */
    suspend fun finishResync(
        seen: Set<RecordKey>,
        cursor: String,
    )

    /**
     * Unpushed changes, oldest first, at most one per record, each with the mutation id it was
     * queued with. Every retry resends that id, so a push whose reply was lost applies once.
     */
    suspend fun pending(): List<Mutation>

    /**
     * Stores push outcomes. [Outcome.APPLIED]: keep the record's new version and drop the change.
     * [Outcome.SUPERSEDED]: replace the local copy with the record and drop the change.
     * [Outcome.CONFLICT]: run the product's merge and queue the result as a new mutation (new id)
     * on `record.version`.
     */
    suspend fun savePush(results: List<Pushed>)

    /**
     * The server will never take this change (its payload is over the kind's limit): drop it from
     * the queue, keep the local data and tell the user.
     */
    suspend fun reject(mutation: Mutation)
}

/** A change to queue: a fresh UUIDv7 mutation id and the current wall-clock time. A null [payload] deletes the record. */
fun newMutation(
    kind: String,
    id: String,
    baseVersion: ULong,
    payload: ByteString?,
    clock: Clock = Clock.System,
): Mutation =
    Mutation {
        mutationId = Uuid.generateV7().toString()
        this.kind = kind
        this.id = id
        this.baseVersion = baseVersion
        deleted = payload == null
        this.payload = payload ?: ByteString()
        modifiedAtUnixMs = clock.now().toEpochMilliseconds()
    }
