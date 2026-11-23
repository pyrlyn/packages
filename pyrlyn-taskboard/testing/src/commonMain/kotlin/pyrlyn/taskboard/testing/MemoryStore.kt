package pyrlyn.taskboard.testing

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withTimeoutOrNull
import pyrlyn.taskboard.BoardError
import pyrlyn.taskboard.BoardException
import pyrlyn.taskboard.Codec
import pyrlyn.taskboard.Commit
import pyrlyn.taskboard.Reader
import pyrlyn.taskboard.Rev
import pyrlyn.taskboard.Session
import pyrlyn.taskboard.Store
import pyrlyn.taskboard.Task
import pyrlyn.taskboard.TaskId
import pyrlyn.taskboard.Versioned
import kotlin.concurrent.Volatile
import kotlin.time.Duration

/** What [MemoryStore.failCommit] throws; never a [BoardException], so a test can tell it from the engine's own errors. */
class InjectedFault(
    message: String,
) : RuntimeException(message)

/**
 * A [Store] in memory with a real lock, so threads race on it exactly as processes race on a file store's flock.
 * A commit builds the next version aside and swaps it in with one write, so a failed or injected-fault commit leaves
 * nothing behind and an unlocked [snapshot] never sees half of one.
 *
 * [failCommit] gets the 1-based number of each commit and returns true to make that one fail before it changes anything.
 */
class MemoryStore(
    private val failCommit: (commitNumber: Int) -> Boolean = { false },
) : Store {
    private class Data(
        val tasks: Map<TaskId, Versioned<Task>>,
        val state: Map<String, String>,
    )

    private val mutex = Mutex()

    @Volatile
    private var data = Data(emptyMap(), emptyMap())
    private var commits = 0

    /** Shown by `snapshot().warnings()`, as a file store shows skipped files. */
    @Volatile
    var warnings: List<String> = emptyList()

    /** Commits applied so far, for tests that count writes. */
    val commitCount get() = commits

    private fun encode(task: Task): ByteArray = Codec.taskToJson(task, withExtra = true).toString().encodeToByteArray()

    private fun revOf(task: Task) = Rev.of(encode(task))

    private fun reader(d: Data) =
        object : Reader {
            override fun tasks() = d.tasks.values.sortedBy { it.value.id }

            override fun warnings() = this@MemoryStore.warnings

            override fun state(name: String) = d.state[name]
        }

    override fun snapshot(): Reader = reader(data)

    override fun <T> locked(
        timeout: Duration,
        body: (Session) -> T,
    ): T {
        val got = runBlocking { withTimeoutOrNull(timeout) { mutex.lock() } }
        if (got == null) throw BoardException(BoardError.Busy())
        try {
            return body(session())
        } finally {
            mutex.unlock()
        }
    }

    private fun session() =
        object : Session, Reader by reader(data) {
            override fun revOf(task: Task) = this@MemoryStore.revOf(task)

            override fun commit(commit: Commit): Map<TaskId, Rev> {
                commits++
                if (failCommit(commits)) throw InjectedFault("commit $commits failed")
                val tasks = data.tasks.toMutableMap()
                val revs = mutableMapOf<TaskId, Rev>()
                commit.remove.forEach { tasks.remove(it) }
                for (task in commit.put) {
                    // Through the encoding, as a file store goes through its files: what is stored is what was written.
                    val bytes = encode(task)
                    val rev = Rev.of(bytes)
                    tasks[task.id] = Versioned(Codec.taskFromJson(Codec.parseObject(bytes.decodeToString())), rev)
                    revs[task.id] = rev
                }
                val state = data.state.toMutableMap()
                for ((name, text) in commit.state) if (text == null) state.remove(name) else state[name] = text
                for ((name, lines) in commit.append) state[name] = state[name].orEmpty() + lines.joinToString("") { it + "\n" }
                data = Data(tasks, state)
                return revs
            }
        }

    /** Writes [task] as a person editing the files would: no engine, no journal, no lock wait. */
    fun handEdit(task: Task) {
        runBlocking { mutex.lock() }
        try {
            val bytes = encode(task)
            data = Data(data.tasks + (task.id to Versioned(task, Rev.of(bytes))), data.state)
        } finally {
            mutex.unlock()
        }
    }

    /** Overwrites a state blob with [text], as a damaged file would be. */
    fun corruptState(
        name: String,
        text: String,
    ) {
        runBlocking { mutex.lock() }
        try {
            data = Data(data.tasks, data.state + (name to text))
        } finally {
            mutex.unlock()
        }
    }

    /** The raw text of a state blob. */
    fun stateText(name: String): String? = data.state[name]

    fun handRemove(id: TaskId) {
        runBlocking { mutex.lock() }
        try {
            data = Data(data.tasks - id, data.state)
        } finally {
            mutex.unlock()
        }
    }

    /** Holds the lock for the whole of [body], to make other clients meet `busy`. */
    fun <T> holdingLock(body: () -> T): T {
        runBlocking { mutex.lock() }
        try {
            return body()
        } finally {
            mutex.unlock()
        }
    }
}
