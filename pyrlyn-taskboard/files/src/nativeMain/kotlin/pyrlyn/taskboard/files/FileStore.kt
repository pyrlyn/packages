package pyrlyn.taskboard.files

import fs.FileLock
import fs.WriteOptions
import fs.bytesIfExists
import fs.createDirAll
import kotlinx.io.files.Path
import pyrlyn.taskboard.BoardError
import pyrlyn.taskboard.BoardException
import pyrlyn.taskboard.Commit
import pyrlyn.taskboard.Reader
import pyrlyn.taskboard.Rev
import pyrlyn.taskboard.Session
import pyrlyn.taskboard.Store
import pyrlyn.taskboard.Task
import pyrlyn.taskboard.TaskId
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

private const val GITIGNORE = ".lock\n.state/\n.sync/\n"

/** What rtok's first board layout wrote; it gets the current content, any other file is the user's. */
private const val OLD_GITIGNORE = ".lock\n.sync/\n"

private val LOCK_POLL = 5.milliseconds

/**
 * A [Store] on a folder: one subfolder per column (`todo`, `in-progress`, `done`, `closed`), one Markdown file per task
 * (`<ID>-<slug>.md`), and the engine's state under `.state/`. The folder is the status, so moving a file is a status
 * change.
 *
 * Reads take no lock and create nothing, so a missing folder is an empty board. A commit holds `<dir>/.lock` (a
 * `flock`, so threads and processes of any number of [FileStore]s on one folder exclude each other), writes its plan to
 * `.state/txn.json`, applies it file by file and deletes the plan. A crash leaves the plan behind; the next lock
 * holder replays it first, so a commit is all or nothing. An unlocked read can still see a commit half applied. Not for
 * network file systems.
 */
class FileStore internal constructor(
    dir: String,
    /** Test hook: gets the number of effects applied so far, and returning true stands for the process dying there. */
    private val crashAfter: (applied: Int) -> Boolean,
) : Store {
    constructor(dir: String) : this(dir, { false })

    private val board = BoardDir(Path(dir))
    private val log = TxnLog(board)

    override fun snapshot(): Reader = Snapshot(board)

    override fun <T> locked(
        timeout: Duration,
        body: (Session) -> T,
    ): T {
        prepare()
        val lock = FileLock.exclusive(board.lockPath, timeout, LOCK_POLL) ?: throw BoardException(BoardError.Busy())
        return lock.use {
            log.redo()
            body(Locked())
        }
    }

    /** The folder and its `.gitignore`, made the first time a write needs them. */
    private fun prepare() {
        createDirAll(board.root)
        val existing = bytesIfExists(board.gitignorePath)?.decodeToString()
        if (existing == null || existing == OLD_GITIGNORE) WriteOptions().write(board.gitignorePath, GITIGNORE.encodeToByteArray())
    }

    /** One scan, taken when first asked for, so the tasks and the warnings of a read belong together. */
    private class Snapshot(
        private val board: BoardDir,
    ) : Reader {
        private val scan by lazy { board.scan() }

        override fun tasks() = scan.tasks

        override fun warnings() = scan.warnings

        override fun state(name: String) = board.state(name)
    }

    private inner class Locked : Session {
        private var cached: Scan? = null

        private val scan get() = cached ?: board.scan().also { cached = it }

        override fun tasks() = scan.tasks

        override fun warnings() = scan.warnings

        override fun state(name: String) = board.state(name)

        override fun revOf(task: Task): Rev = BoardDir.revOf(task.status, TaskFile.render(task).encodeToByteArray())

        override fun commit(commit: Commit): Map<TaskId, Rev> {
            if (commit.isEmpty) return emptyMap()
            val planned = planCommit(board, scan, commit)
            cached = null
            log.save(planned.txn)
            log.apply(planned.txn) { if (crashAfter(it)) throw SimulatedCrash(it) }
            log.finish()
            return planned.revs
        }
    }
}
