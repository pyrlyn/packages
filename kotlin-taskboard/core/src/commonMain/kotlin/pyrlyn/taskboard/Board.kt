package pyrlyn.taskboard

import kotlinx.coroutines.flow.Flow
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * The board every front end calls. Reads take no lock; every write is one locked session, so clients on one machine
 * never lose each other's writes. Every failure is a [BoardException].
 */
interface Board {
    fun snapshot(filter: Filter = Filter()): BoardView

    /** Throws `not_found`. */
    fun get(id: TaskId): Versioned<Task>

    /** The task with its lease and subtasks. Throws `not_found`. */
    fun show(id: TaskId): Shown

    fun create(
        task: NewTask,
        write: Write = Write(),
    ): Written

    fun update(
        id: TaskId,
        patch: Patch,
        write: Write = Write(),
    ): Written

    fun move(
        id: TaskId,
        status: Status,
        report: String? = null,
        force: Boolean = false,
        write: Write = Write(),
    ): Written

    /** Claims [id], or the next ready task when it is null (the answer is then null if none is ready). Renews your own lease. */
    fun claim(
        id: TaskId?,
        agent: String,
        lease: Duration? = null,
        write: Write = Write(),
    ): ClaimResult?

    fun release(
        id: TaskId,
        agent: String,
        force: Boolean = false,
        write: Write = Write(),
    ): Written

    /** All of [ops] or none; a failure names the op. [write] contributes `key` and `actor` only. */
    fun batch(
        ops: List<Op>,
        write: Write = Write(),
    ): Written

    /** The journal after [since] (a seq from an earlier answer; 0 for all), at most [limit] entries. */
    fun changes(
        since: Long,
        limit: Int = DEFAULT_CHANGE_LIMIT,
    ): ChangePage

    /**
     * Every change after [since], in order, polling the journal every [pollInterval]. When the journal no longer
     * reaches back to the cursor the flow emits [WatchEvent.Reset] and carries on from the board's head.
     */
    fun watch(
        since: Long,
        pollInterval: Duration = 1.seconds,
    ): Flow<WatchEvent>

    companion object {
        const val DEFAULT_CHANGE_LIMIT = 1000
    }
}

data class BoardOptions(
    val clock: Clock = Clock.System,
    /** Prefix of new top-level ids; subtasks inherit their parent's. */
    val idPrefix: String = "T",
    /** How long a claim holds unless renewed. */
    val lease: Duration = 30.minutes,
    /** How long a write waits for the board lock before `busy`. */
    val lockTimeout: Duration = 10.seconds,
    /**
     * Whether the holder of a lapsed lease is still running: true keeps the lease, false or null lets another agent
     * take it. Called under the board lock, so it must be quick.
     */
    val liveness: ((agent: String) -> Boolean?)? = null,
    /** Journal entries kept; older ones answer `changes` with `reset`. */
    val journalKeep: Int = 10_000,
    /** How long an idempotency key remembers its result. */
    val keyTtl: Duration = 24.hours,
) {
    init {
        TaskId.top(idPrefix, 1u)
        if (!lease.isPositiveFinite || !lockTimeout.isPositiveFinite || !keyTtl.isPositiveFinite) invalid("lease, lock timeout and key ttl must be positive")
        if (journalKeep < 1) invalid("journalKeep must be at least 1")
    }
}

object Boards {
    fun open(
        store: Store,
        options: BoardOptions = BoardOptions(),
    ): Board = Engine(store, options)
}
