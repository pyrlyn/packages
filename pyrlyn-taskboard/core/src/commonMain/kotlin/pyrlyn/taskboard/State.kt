package pyrlyn.taskboard

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlin.time.Instant

/** The last rev and fields the engine saw of one task; hand edits show up as a difference from it. */
internal class IndexEntry(
    val rev: Rev,
    val fields: Map<String, JsonElement>,
)

/** `index`: the journal's bounds and what each task looked like when the engine last wrote or noticed it. */
internal class IndexState(
    /** The newest journal entry's seq; the board's change counter. */
    var seq: Long = 0,
    /** The oldest journal entry still kept; `seq + 1` while the journal is empty. */
    var first: Long = 1,
    val tasks: MutableMap<TaskId, IndexEntry> = mutableMapOf(),
) {
    fun encode(): String =
        buildJsonObject {
            put("seq", seq)
            put("first", first)
            put(
                "tasks",
                JsonObject(
                    tasks.entries.sortedBy { it.key }.associate { (id, e) ->
                        id.toString() to
                            buildJsonObject {
                                put("rev", e.rev.value)
                                put("fields", JsonObject(e.fields))
                            }
                    },
                ),
            )
        }.toString()

    companion object {
        /** The index of a board whose own was unreadable: the counter carries on from the journal that is left, or starts again. */
        fun recovered(journal: String?): IndexState {
            val seqs = Work.parseJournal(journal).map { it.seq }
            val seq = seqs.maxOrNull() ?: 0L
            return IndexState(seq, seqs.minOrNull() ?: (seq + 1))
        }

        fun decodeOrNull(text: String?): IndexState? =
            try {
                decode(text)
            } catch (_: BoardException) {
                null
            }

        fun decode(text: String?): IndexState? {
            if (text == null) return null
            val o = stateObject(StateNames.INDEX, text)
            val tasks = mutableMapOf<TaskId, IndexEntry>()
            for ((k, v) in (o["tasks"] as? JsonObject).orEmpty()) {
                val e = v as? JsonObject ?: corrupt(StateNames.INDEX)
                tasks[TaskId.parse(k)] = IndexEntry(Rev(e.str("rev")), e["fields"] as? JsonObject ?: corrupt(StateNames.INDEX))
            }
            return IndexState(o.num("seq"), o.num("first"), tasks)
        }
    }
}

/** An idempotency key: the request it first came with, when, and what that request returned. */
internal class KeyEntry(
    val hash: String,
    val at: Instant,
    val result: JsonObject,
)

internal object State {
    fun decodeLeases(text: String?): MutableMap<TaskId, Lease> = decodeMap(StateNames.LEASES, text) { k, v -> TaskId.parse(k) to Codec.leaseFromJson(v.obj(StateNames.LEASES)) }

    fun encodeLeases(leases: Map<TaskId, Lease>): String = JsonObject(leases.entries.sortedBy { it.key }.associate { (id, l) -> id.toString() to Codec.leaseToJson(l) }).toString()

    /** Last number handed out per parent (`""` for top level). */
    fun decodeCounters(text: String?): MutableMap<String, Long> = decodeMap(StateNames.IDS, text) { k, v -> k to ((v as? JsonPrimitive)?.longOrNull ?: corrupt(StateNames.IDS)) }

    fun encodeCounters(counters: Map<String, Long>): String = JsonObject(counters.entries.sortedBy { it.key }.associate { it.key to JsonPrimitive(it.value) }).toString()

    fun decodeKeys(text: String?): MutableMap<String, KeyEntry> =
        decodeMap(StateNames.KEYS, text) { k, v ->
            val o = v.obj(StateNames.KEYS)
            k to KeyEntry(o.str("hash"), Instant.fromEpochSeconds(o.num("at")), o["result"] as? JsonObject ?: corrupt(StateNames.KEYS))
        }

    fun encodeKeys(keys: Map<String, KeyEntry>): String =
        JsonObject(
            keys.entries.sortedBy { it.key }.associate { (k, e) ->
                k to
                    buildJsonObject {
                        put("hash", e.hash)
                        put("at", e.at.epochSeconds)
                        put("result", e.result)
                    }
            },
        ).toString()

    private fun <K, V> decodeMap(
        name: String,
        text: String?,
        entry: (String, JsonElement) -> Pair<K, V>,
    ): MutableMap<K, V> {
        if (text == null) return mutableMapOf()
        val out = mutableMapOf<K, V>()
        for ((k, v) in stateObject(name, text)) {
            val (key, value) = entry(k, v)
            out[key] = value
        }
        return out
    }
}

private fun JsonElement.obj(name: String) = this as? JsonObject ?: corrupt(name)

private fun JsonObject?.orEmpty() = this ?: JsonObject(emptyMap())

internal fun corrupt(name: String): Nothing = invalid("board state \"$name\" is unreadable")

private fun stateObject(
    name: String,
    text: String,
): JsonObject =
    try {
        Codec.parseObject(text)
    } catch (_: BoardException) {
        corrupt(name)
    }

private fun JsonObject.str(name: String): String = (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: invalid("board state field \"$name\" is missing")

private fun JsonObject.num(name: String): Long = (this[name] as? JsonPrimitive)?.longOrNull ?: invalid("board state field \"$name\" is missing")
