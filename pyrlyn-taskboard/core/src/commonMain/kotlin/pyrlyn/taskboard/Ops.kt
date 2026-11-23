package pyrlyn.taskboard

import kotlin.time.Duration

/** What a caller supplies to create a task; the board adds the id. [parent] may be a [Ref] to an earlier `Create`. */
data class NewTask(
    val title: String,
    val description: String = "",
    val parent: Target? = null,
    val priority: Int = DEFAULT_PRIORITY,
    val blockedBy: List<TaskId> = emptyList(),
    val conflictsWith: List<TaskId> = emptyList(),
    val parallel: Boolean = false,
    val remote: String? = null,
)

/**
 * The fields an update sets; everything left null stays as it is, so two clients that edit different fields never
 * overwrite each other. Set fields take add and remove, which never clash. Status changes go through `move`, the
 * assignee through `claim` and `release`.
 */
data class Patch(
    val title: String? = null,
    val description: String? = null,
    val priority: Int? = null,
    val parent: TaskId? = null,
    /** Detach the task from its parent. Wins over [parent]. */
    val clearParent: Boolean = false,
    val parallel: Boolean? = null,
    /** The linked issue URL; an empty string unlinks. */
    val remote: String? = null,
    val addBlockedBy: Set<TaskId> = emptySet(),
    val removeBlockedBy: Set<TaskId> = emptySet(),
    val addConflictsWith: Set<TaskId> = emptySet(),
    val removeConflictsWith: Set<TaskId> = emptySet(),
)

/** The concurrency options every write carries. */
data class Write(
    /** Strict compare-and-set: fail with `conflict` unless the task still has this rev. */
    val ifRev: Rev? = null,
    /** Field merge: apply unless a field this write sets changed since this rev. */
    val baseRev: Rev? = null,
    /** Idempotency key: a repeat returns the first result. */
    val key: String? = null,
    /** Who writes, for the journal and for the holder checks of move and release. */
    val actor: String? = null,
)

/** One step of a batch. [ifRev] and [baseRev] apply to the task the step targets. */
sealed interface Op {
    val ifRev: Rev?
    val baseRev: Rev?

    /** [ref] names the new task for later steps of the batch. */
    data class Create(
        val task: NewTask,
        val ref: String? = null,
        override val ifRev: Rev? = null,
        override val baseRev: Rev? = null,
    ) : Op

    data class Update(
        val target: Target,
        val patch: Patch,
        override val ifRev: Rev? = null,
        override val baseRev: Rev? = null,
    ) : Op

    /** Finishing a parent with active subtasks needs [force]; a [report] goes with done or closed. */
    data class Move(
        val target: Target,
        val status: Status,
        val report: String? = null,
        val force: Boolean = false,
        override val ifRev: Rev? = null,
        override val baseRev: Rev? = null,
    ) : Op

    /** A null [target] claims the next ready task. */
    data class Claim(
        val target: Target?,
        val agent: String,
        val lease: Duration? = null,
        override val ifRev: Rev? = null,
        override val baseRev: Rev? = null,
    ) : Op

    data class Release(
        val target: Target,
        val agent: String,
        val force: Boolean = false,
        override val ifRev: Rev? = null,
        override val baseRev: Rev? = null,
    ) : Op
}
