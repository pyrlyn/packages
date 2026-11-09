package pyrlyn.taskboard

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlin.time.Instant

/**
 * The working set of one locked session: the board as the session's operations have changed it so far. Nothing
 * reaches the store until [commit], so an operation that fails leaves no trace, whichever step of a batch it was.
 */
internal class Work(
    private val session: Session,
    val options: BoardOptions,
    val now: Instant,
) {
    private val stored = session.tasks().associateBy { it.value.id }
    val tasks: MutableMap<TaskId, Task> = stored.mapValuesTo(LinkedHashMap()) { it.value.value }
    private val put = LinkedHashMap<TaskId, Task>()

    private var indexDirty = false
    val index: IndexState = IndexState.decode(session.state(StateNames.INDEX)) ?: IndexState()
    val leases = State.decodeLeases(session.state(StateNames.LEASES))
    val counters = State.decodeCounters(session.state(StateNames.IDS))
    val keys = State.decodeKeys(session.state(StateNames.KEYS))
    val refs = mutableMapOf<String, TaskId>()

    var leasesDirty = false
    var countersDirty = false
    var keysDirty = false

    /** Entries this session appended, for base-rev lookups that must see the batch's own earlier steps. */
    private val pending = mutableListOf<Change>()
    private val lines = mutableListOf<String>()
    private var journal: List<Change>? = null

    init {
        // A board that predates the engine's index is adopted as it is, not reported as one big hand edit.
        if (session.state(StateNames.INDEX) == null) {
            tasks.values.forEach { index.tasks[it.id] = IndexEntry(stored.getValue(it.id).rev, it.fieldValues()) }
            indexDirty = true
        }
    }

    fun rev(id: TaskId): Rev = put[id]?.let(session::revOf) ?: stored.getValue(id).rev

    fun versioned(id: TaskId) = Versioned(tasks.getValue(id), rev(id))

    /** The agent a task is held by: the lease's holder, else a bare `assignee`. */
    fun holder(task: Task): String? = holderOf(task, leases[task.id])

    /** The hold can be taken: its lease ran out (or there never was one) and the host does not vouch for the holder. */
    fun stale(task: Task): Boolean = isStale(task, leases[task.id], now, options)

    /** Stores [after] for [before]'s id and journals it. A write that changes nothing is dropped by the caller. */
    fun save(
        before: Task?,
        after: Task,
        op: String,
        actor: String?,
    ) {
        tasks[after.id] = after
        put[after.id] = after
        record(op, after.id, before?.fieldValues(), after.fieldValues(), actor, session.revOf(after), external = false)
    }

    fun setLease(
        id: TaskId,
        lease: Lease?,
    ) {
        if (lease == null) {
            if (leases.remove(id) != null) leasesDirty = true
        } else {
            leases[id] = lease
            leasesDirty = true
        }
    }

    /**
     * Journals what changed in the store behind the engine's back: tasks that are new, edited or gone since the index
     * was written. The index is brought up to date either way.
     */
    fun reconcile() {
        for ((id, versioned) in stored.entries.sortedBy { it.key }) {
            val known = index.tasks[id]
            if (known?.rev == versioned.rev) continue
            val fields = versioned.value.fieldValues()
            val changes = diff(known?.fields ?: emptyMap(), fields)
            if (changes.isNotEmpty()) record(if (known == null) "create" else "update", id, known?.fields, fields, null, versioned.rev, external = true)
            index.tasks[id] = IndexEntry(versioned.rev, fields)
            indexDirty = true
        }
        for (id in index.tasks.keys
            .filter { it !in stored }
            .sorted()) {
            record("delete", id, index.tasks.getValue(id).fields, emptyMap(), null, null, external = true)
            index.tasks.remove(id)
            setLease(id, null)
        }
    }

    private fun record(
        op: String,
        id: TaskId,
        before: Map<String, JsonElement>?,
        after: Map<String, JsonElement>,
        actor: String?,
        rev: Rev?,
        external: Boolean,
    ) {
        val change = Change(++index.seq, now, actor, op, id, rev, diff(before ?: emptyMap(), after), external)
        pending += change
        lines += Codec.changeToJson(change).toString()
        if (rev != null && op != "delete") index.tasks[id] = IndexEntry(rev, after)
        indexDirty = true
    }

    /** The journal: what the store holds plus this session's own entries. Unreadable lines are skipped. */
    fun journalEntries(): List<Change> = (journal ?: parseJournal(session.state(StateNames.JOURNAL)).also { journal = it }) + pending

    /** `ifRev` / `baseRev` of one op: strict compare-and-set, then the field merge for [changing]. */
    fun check(
        current: Versioned<Task>,
        after: Task,
        ifRev: Rev?,
        baseRev: Rev?,
    ) {
        if (ifRev != null && ifRev != current.rev) throw BoardException(BoardError.Conflict(current, emptyList()))
        if (baseRev == null || baseRev == current.rev) return
        val since = changedSince(current.value.id, baseRev) ?: throw BoardException(BoardError.Conflict(current, emptyList()))
        val now = current.value.fieldValues()
        val requested = after.fieldValues()
        val clashes =
            diff(now, requested).keys.filter { it in since && it !in SET_FIELDS }.map {
                FieldClash(it, since.getValue(it), now[it] ?: JsonNull, requested[it] ?: JsonNull)
            }
        if (clashes.isNotEmpty()) throw BoardException(BoardError.Conflict(current, clashes))
    }

    /** Field name to its value at [baseRev], for every field changed after it; null when the journal no longer knows [baseRev]. */
    private fun changedSince(
        id: TaskId,
        baseRev: Rev,
    ): Map<String, JsonElement>? {
        val mine = journalEntries().filter { it.id == id }
        val at = mine.indexOfLast { it.rev == baseRev }
        if (at < 0) return null
        val out = LinkedHashMap<String, JsonElement>()
        for (entry in mine.drop(at + 1)) for ((name, change) in entry.fields) if (name !in out) out[name] = change.old
        return out
    }

    /** Applies everything the session did, or nothing when it did nothing. */
    fun commit() {
        val state = mutableMapOf<String, String?>()
        if (leasesDirty) state[StateNames.LEASES] = State.encodeLeases(leases)
        if (countersDirty) state[StateNames.IDS] = State.encodeCounters(counters)
        if (keysDirty) state[StateNames.KEYS] = State.encodeKeys(keys)
        var append = if (lines.isEmpty()) emptyMap() else mapOf(StateNames.JOURNAL to lines.toList())
        val kept = index.seq - index.first + 1
        if (kept > options.journalKeep + slack()) {
            // Trimming in steps keeps the journal from being rewritten on every write once it is full.
            val all = journalEntries()
            val keep = all.takeLast(options.journalKeep)
            index.first = keep.first().seq
            state[StateNames.JOURNAL] = keep.joinToString("") { Codec.changeToJson(it).toString() + "\n" }
            append = emptyMap()
            indexDirty = true
        }
        if (indexDirty) state[StateNames.INDEX] = index.encode()
        val commit = Commit(put.values.toList(), emptyList(), state, append)
        if (!commit.isEmpty) session.commit(commit)
    }

    private fun slack() = maxOf(options.journalKeep / TRIM_SLACK_DIVISOR, 1)

    companion object {
        private const val TRIM_SLACK_DIVISOR = 10

        /** Set fields take add and remove, which never clash. */
        private val SET_FIELDS = setOf("blocked_by", "conflicts_with")

        fun parseJournal(text: String?): List<Change> =
            text
                .orEmpty()
                .lineSequence()
                .filter { it.isNotBlank() }
                .mapNotNull { line ->
                    try {
                        Codec.changeFromJson(Codec.parseObject(line))
                    } catch (_: BoardException) {
                        null
                    }
                }.toList()
    }
}
