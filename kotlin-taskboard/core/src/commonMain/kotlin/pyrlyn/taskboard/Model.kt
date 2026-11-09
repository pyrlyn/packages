package pyrlyn.taskboard

import kotlinx.serialization.json.JsonElement
import kotlin.time.Duration
import kotlin.time.Instant

/** Priority when a task names none. `0` is the highest, `4` the lowest. */
const val DEFAULT_PRIORITY = 2

/** Highest priority number accepted. */
const val PRIORITY_MAX = 4

/** Where a task stands. `Done` and `Closed` both leave the plan; `Closed` is "won't do". */
enum class Status(
    val word: String,
) {
    Todo("todo"),
    InProgress("in-progress"),
    Done("done"),
    Closed("closed"),
    ;

    /** Still in the plan: listed by default and blocking a parent from finishing. */
    val isActive get() = this == Todo || this == InProgress

    override fun toString() = word

    companion object {
        private val aliases =
            mapOf(
                "open" to Todo,
                "pending" to Todo,
                "doing" to InProgress,
                "completed" to Done,
                "wontdo" to Closed,
                "won't-do" to Closed,
                "not-planned" to Closed,
                "dropped" to Closed,
            )

        /** Takes the canonical words and the aliases of the checklist tools; `in_progress` and `in progress` too. */
        fun parseOrNull(raw: String): Status? {
            val w =
                raw
                    .trim()
                    .lowercase()
                    .replace('_', '-')
                    .replace(' ', '-')
            return entries.firstOrNull { it.word == w } ?: aliases[w]
        }

        fun parse(raw: String): Status = parseOrNull(raw) ?: invalid("unknown task status \"$raw\": use todo, in-progress, done or closed")
    }
}

/** 16 lowercase hex characters: the first 8 bytes of the SHA-256 of what the store holds for one task. */
data class Rev(
    val value: String,
) {
    init {
        if (value.length != REV_LENGTH || !value.all { it in '0'..'9' || it in 'a'..'f' }) invalid("rev \"$value\" is not $REV_LENGTH hex characters")
    }

    override fun toString() = value

    companion object {
        const val REV_LENGTH = 16

        /** The rev of [bytes]; stores call it on exactly the bytes they keep, so a hand edit changes the rev. */
        fun of(bytes: ByteArray): Rev = Rev(hex(sha256(bytes).asList().take(REV_LENGTH / 2)))
    }
}

/** A value with the rev it had when it was read. */
data class Versioned<out T>(
    val value: T,
    val rev: Rev,
)

/** One card on the board. Times keep the clock's own precision; the wire carries whole seconds. */
data class Task(
    val id: TaskId,
    val title: String,
    val description: String = "",
    val status: Status = Status.Todo,
    val priority: Int = DEFAULT_PRIORITY,
    val parent: TaskId? = null,
    val assignee: String? = null,
    val blockedBy: List<TaskId> = emptyList(),
    val conflictsWith: List<TaskId> = emptyList(),
    val parallel: Boolean = false,
    /** The closing report of a done or closed task. */
    val report: String? = null,
    /** The URL of the linked issue on the partner tracker. */
    val remote: String? = null,
    val created: Instant,
    val updated: Instant,
    /** Front matter keys this library does not know; a store keeps them on rewrite. */
    val extra: Map<String, JsonElement> = emptyMap(),
)

/** A claim: [agent] holds the task from [acquired] until [expires], and again after each renewal. */
data class Lease(
    val agent: String,
    val acquired: Instant,
    val expires: Instant,
)

/** The compact view of a task that boards, the MCP tools and the change feed share. */
data class Card(
    val id: TaskId,
    val title: String,
    val status: Status,
    val priority: Int,
    val parent: TaskId?,
    val assignee: String?,
    val blockedBy: List<TaskId>,
    val conflictsWith: List<TaskId>,
    val parallel: Boolean,
    /** Could be claimed right now. */
    val ready: Boolean,
    /** The holder's lease ran out and nothing says the holder is alive: another agent may take the task. */
    val stale: Boolean,
    val lease: Lease?,
    val rev: Rev,
    val remote: String?,
)

data class Column(
    val status: Status,
    val cards: List<Card>,
)

/** What [Board.snapshot] returns. [seq] is the board's change counter at the moment of the read. */
data class BoardView(
    val seq: Long,
    val columns: List<Column>,
    val warnings: List<String>,
)

/** Which tasks [Board.snapshot] shows. The default is the plan: every active task. */
data class Filter(
    val statuses: Set<Status> = emptySet(),
    val all: Boolean = false,
    val parent: TaskId? = null,
    /** Only tasks held by this agent. */
    val mine: String? = null,
    val ready: Boolean = false,
)

/** [old] and [new] of one field; JSON null stands for "not set". */
data class FieldChange(
    val old: JsonElement,
    val new: JsonElement,
)

/** One journal entry: what [actor] did to task [id] and which fields moved from where to where. */
data class Change(
    val seq: Long,
    val at: Instant,
    val actor: String?,
    val op: String,
    val id: TaskId,
    /** The task's rev after the change; null when the task is gone. */
    val rev: Rev?,
    val fields: Map<String, FieldChange>,
    /** Somebody edited the store directly and the engine noticed it afterwards. */
    val external: Boolean = false,
)

/** [seq] is the board's head. [reset]: the journal no longer reaches back to `since`; read the board again. */
data class ChangePage(
    val seq: Long,
    val changes: List<Change>,
    val reset: Boolean,
)

/** [Board.show]: a task with its lease and its subtasks. */
data class Shown(
    val task: Versioned<Task>,
    val lease: Lease?,
    val stale: Boolean,
    val subtasks: List<TaskId>,
)

/** What one operation did. [changed] is false when it was a no-op (or only renewed a lease). */
data class OpResult(
    val task: Versioned<Task>,
    val changed: Boolean,
    val lease: Lease? = null,
    val tookOverFrom: String? = null,
    /** Conflict partners in progress right now: a warning to the claimer, not a refusal. */
    val conflictsInProgress: List<TaskId> = emptyList(),
)

/** What a write returned. [replayed]: an idempotency key matched, and this is the first call's result. */
data class Written(
    val results: List<OpResult>,
    val seq: Long,
    val replayed: Boolean = false,
) {
    val result get() = results.single()
    val task get() = result.task
}

/** `claim`'s answer. */
data class ClaimResult(
    val task: Versioned<Task>,
    val lease: Lease,
    val changed: Boolean,
    val tookOverFrom: String?,
    val conflictsInProgress: List<TaskId>,
    val seq: Long,
    val replayed: Boolean,
)

internal val Duration.isPositiveFinite get() = this > Duration.ZERO && this.isFinite()

/** What [Board.watch] emits. */
sealed interface WatchEvent {
    data class Changed(
        val change: Change,
    ) : WatchEvent

    /** The journal no longer reaches back; re-read the board, which is at [seq] or later, then go on from there. */
    data class Reset(
        val seq: Long,
    ) : WatchEvent
}
