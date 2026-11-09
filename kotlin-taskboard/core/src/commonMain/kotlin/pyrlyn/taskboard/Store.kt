package pyrlyn.taskboard

import kotlin.time.Duration

/** Names of the state blobs the engine keeps next to the tasks. A store keeps any blob it is given under its name. */
object StateNames {
    const val IDS = "ids"
    const val LEASES = "leases"
    const val KEYS = "keys"
    const val INDEX = "index"
    const val JOURNAL = "journal"
}

/** What a store holds, readable without the lock. */
interface Reader {
    /** Every task with the rev of the bytes it was read from. */
    fun tasks(): List<Versioned<Task>>

    /** Files that were skipped and why; shown to the user, never fatal. */
    fun warnings(): List<String>

    /** The blob [name] as text, or null when it was never written. */
    fun state(name: String): String?
}

/**
 * Everything one write needs to be applied as a single step. A store applies all of it or none of it, also across a
 * crash: a file store keeps a redo log, and the next lock holder finishes what the crash interrupted. A file is never
 * seen half written, but a reader that takes no lock may see a commit in progress, some of its files replaced and
 * others not yet. A store that swaps the whole board in with one write, as an in-memory one does, may promise more.
 */
data class Commit(
    /** Tasks to create or replace. */
    val put: List<Task> = emptyList(),
    val remove: List<TaskId> = emptyList(),
    /** Blobs to replace; null deletes one. */
    val state: Map<String, String?> = emptyMap(),
    /** Lines to append to a blob (the journal); applied after [state]. */
    val append: Map<String, List<String>> = emptyMap(),
) {
    val isEmpty get() = put.isEmpty() && remove.isEmpty() && state.isEmpty() && append.isEmpty()
}

/** The exclusive view a [Store.locked] body gets. */
interface Session : Reader {
    /**
     * The rev [task] gets once stored. Pure: the same task always has the same rev, and it equals the rev [tasks]
     * reports for it after [commit]. A store that keeps files hashes its own encoding of the task.
     */
    fun revOf(task: Task): Rev

    /**
     * Applies [commit] all or nothing and returns the new rev of every task in `put`. A failure that happens before
     * anything is written leaves the store as it was; a file store keeps a redo log, so a crash, or an error halfway,
     * is finished by the next lock holder instead.
     */
    fun commit(commit: Commit): Map<TaskId, Rev>
}

/**
 * Where a board's tasks and state live. The engine adds every rule on top, so a store only reads, locks and writes.
 * Boards on network file systems are not supported: a file store locks with flock.
 */
interface Store {
    /**
     * A read of the tasks and state without taking the lock; it may lag a write in progress, or see one half applied
     * (see [Commit]).
     */
    fun snapshot(): Reader

    /**
     * Runs [body] with exclusive access, across threads and processes. Throws `BoardException(Busy)` when the lock
     * stays taken for [timeout]. [body] must not call back into the board.
     */
    fun <T> locked(
        timeout: Duration,
        body: (Session) -> T,
    ): T
}
