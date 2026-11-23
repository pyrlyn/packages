package pyrlyn.sync.testing

import kotlinx.io.bytestring.decodeToString
import kotlinx.io.bytestring.encodeToByteString
import pyrlyn.sync.client.LocalStore
import pyrlyn.sync.client.MemorySecretStore
import pyrlyn.sync.client.Outcome
import pyrlyn.sync.client.Pushed
import pyrlyn.sync.client.RecordKey
import pyrlyn.sync.client.Secret
import pyrlyn.sync.client.StoredToken
import pyrlyn.sync.client.key
import pyrlyn.sync.client.newMutation
import pyrlyn.sync.v1.Mutation
import pyrlyn.sync.v1.Record
import pyrlyn.sync.v1.SyncLimits
import pyrlyn.sync.v1.copy

/** An app's local sync state in memory, with board's merge for `reject` conflicts. */
class Mem : LocalStore {
    /** The opt-in switch: the limits of the last `EnableSync`. */
    var limits: SyncLimits? = null

    /** Where the next pull starts. */
    var cursor: String? = null

    /** Records as the server last confirmed them. */
    val synced = mutableMapOf<RecordKey, Record>()

    /** Unpushed local changes. */
    val queue = mutableListOf<Mutation>()

    /** Changes the server refused for good. */
    val rejected = mutableListOf<Mutation>()

    /** A local edit at writer time [at]; a null [payload] deletes. */
    fun edit(
        kind: String,
        id: String,
        payload: String?,
        at: Long,
    ) {
        val key = RecordKey(kind, id)
        // A second edit before a push replaces the first but keeps its base, so the server still
        // judges it against the version it was made on.
        val queued = queue.indexOfFirst { it.key == key }
        val base = if (queued >= 0) queue.removeAt(queued).baseVersion else synced[key]?.version ?: 0uL
        queue += newMutation(kind, id, base, payload?.encodeToByteString()).copy { modifiedAtUnixMs = at }
    }

    /** What the user sees: live synced records with unpushed edits on top, as `kind/id` to payload text, in key order. */
    fun view(): Map<String, String> {
        val view = mutableMapOf<String, String>()
        for (r in synced.values) if (!r.deleted) view["${r.kind}/${r.id}"] = r.payload.decodeToString()
        for (m in queue) {
            val key = "${m.kind}/${m.id}"
            if (m.deleted) view.remove(key) else view[key] = m.payload.decodeToString()
        }
        return view.entries.sortedBy { it.key }.associate { it.key to it.value }
    }

    /** Ids of the unpushed changes, oldest first. */
    fun queued(): List<String> = queue.map { it.id }

    override suspend fun limits(): SyncLimits? = limits

    override suspend fun setLimits(limits: SyncLimits?) {
        this.limits = limits
    }

    override suspend fun cursor(): String? = cursor

    override suspend fun savePage(
        records: List<Record>,
        cursor: String?,
    ) {
        for (r in records) if (queue.none { it.key == r.key }) synced[r.key] = r
        if (cursor != null) this.cursor = cursor
    }

    override suspend fun finishResync(
        seen: Set<RecordKey>,
        cursor: String,
    ) {
        synced.keys.retainAll(seen)
        for (i in queue.indices) if (queue[i].key !in seen) queue[i] = queue[i].copy { baseVersion = 0uL }
        this.cursor = cursor
    }

    override suspend fun pending(): List<Mutation> = queue.toList()

    override suspend fun savePush(results: List<Pushed>) {
        for (p in results) {
            queue.removeAll { it.mutationId == p.mutation.mutationId }
            val record = p.record
            synced[record.key] = record
            if (p.outcome == Outcome.CONFLICT) {
                // Both texts in a fixed order, so every device merges alike.
                val merged = listOf(record.payload.decodeToString(), p.mutation.payload.decodeToString()).sorted().joinToString("+")
                queue += newMutation(record.kind, record.id, record.version, merged.encodeToByteString())
            }
        }
    }

    override suspend fun reject(mutation: Mutation) {
        queue.removeAll { it.mutationId == mutation.mutationId }
        rejected += mutation
    }
}

/** A [StoredToken] in memory holding [jwt]. */
suspend fun stored(jwt: String): StoredToken = StoredToken(MemorySecretStore(), "sync").also { it.save(Secret(jwt)) }
