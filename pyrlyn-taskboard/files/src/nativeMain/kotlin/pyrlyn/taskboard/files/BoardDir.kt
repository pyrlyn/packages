package pyrlyn.taskboard.files

import fs.EntryKind
import fs.FsException
import fs.bytesIfExists
import fs.entryKind
import fs.joinPath
import fs.listDir
import fs.modifiedAgo
import kotlinx.io.files.Path
import pyrlyn.taskboard.BoardError
import pyrlyn.taskboard.BoardException
import pyrlyn.taskboard.Rev
import pyrlyn.taskboard.StateNames
import pyrlyn.taskboard.Status
import pyrlyn.taskboard.Task
import pyrlyn.taskboard.TaskId
import pyrlyn.taskboard.Versioned
import kotlin.time.Clock

/** What a pass over the board's folders found. */
internal class Scan(
    /** One entry per id, sorted by id. */
    val tasks: List<Versioned<Task>>,
    /** Every file that parses as an id, the winner and any duplicate of it, relative to the board folder. */
    val copies: Map<TaskId, List<String>>,
    /** Files whose name carries an id but that cannot be read as that task, relative to the board folder. */
    val unreadable: Map<TaskId, List<String>>,
    val warnings: List<String>,
)

/** Where things live under a board folder, and how to read them. Nothing here writes. */
internal class BoardDir(
    val root: Path,
) {
    val lockPath: String get() = joinPath(root, LOCK_NAME).toString()
    val gitignorePath: Path get() = joinPath(root, GITIGNORE_NAME)
    val txnPath: Path get() = pathOf(TXN_FILE)

    /** [relative] (always with `/`) under the board folder. */
    fun pathOf(relative: String): Path = joinPath(root, relative)

    fun taskPath(task: Task) = "${task.status.word}/${TaskFile.fileName(task)}"

    /** The file a state blob lives in. Refuses a name that could point anywhere else. */
    fun statePath(name: String): String {
        if (!STATE_NAME.matches(name) || name == TXN_NAME) throw BoardException(BoardError.Invalid("state name \"$name\" must be a lowercase word of letters, digits, _ and -"))
        return if (name == StateNames.JOURNAL) "$STATE_DIR/journal.jsonl" else "$STATE_DIR/$name.json"
    }

    fun state(name: String): String? = bytesIfExists(pathOf(statePath(name)))?.decodeToString()

    fun names(dir: Path): List<String> =
        try {
            listDir(dir).sorted()
        } catch (e: FsException) {
            if (e.kind == FsException.Kind.NotFound) emptyList() else throw e
        }

    /** Reads every column folder. Files that cannot be used are skipped and reported, never fatal. */
    fun scan(): Scan {
        val warnings = mutableListOf<String>()
        noteStrays(warnings)
        val found = mutableListOf<Found>()
        val unreadable = mutableMapOf<TaskId, MutableList<String>>()
        for (column in Status.entries) {
            val folder = joinPath(root, column.word)
            for ((name, nameId) in names(folder).mapNotNull { n -> TaskFile.fileId(n)?.let { n to it } }) {
                val rel = "${column.word}/$name"
                val path = joinPath(folder, name)
                if (entryKind(path) != EntryKind.File) continue
                when (val got = load(path, column, nameId, warnings)) {
                    null -> {}

                    is Loaded -> {
                        found += Found(rel, column, got.task)
                    }

                    is Skipped -> {
                        unreadable.getOrPut(nameId) { mutableListOf() } += rel
                    }
                }
            }
        }
        return settle(found, unreadable, warnings)
    }

    private sealed interface Load

    private class Loaded(
        val task: Versioned<Task>,
    ) : Load

    private data object Skipped : Load

    private class Found(
        val path: String,
        val column: Status,
        val task: Versioned<Task>,
    )

    /** null: the file vanished while it was read. */
    private fun load(
        path: Path,
        column: Status,
        nameId: TaskId,
        warnings: MutableList<String>,
    ): Load? {
        val bytes = bytesIfExists(path) ?: return null
        val parsed =
            try {
                TaskFile.parse(decode(bytes), column) { Clock.System.now().epochSeconds - modifiedAgo(path).inWholeSeconds }
            } catch (e: FormatException) {
                warnings += "skipping $path: ${e.message}"
                return Skipped
            }
        if (parsed.task.id != nameId) {
            warnings += "skipping $path: the file name says $nameId but the front matter says ${parsed.task.id}"
            return Skipped
        }
        parsed.warnings.forEach { warnings += "$path: $it" }
        return Loaded(Versioned(parsed.task, revOf(column, bytes)))
    }

    private fun decode(bytes: ByteArray): String =
        try {
            bytes.decodeToString(throwOnInvalidSequence = true)
        } catch (_: CharacterCodingException) {
            throw FormatException("the file is not valid UTF-8")
        }

    /** Two files of one id: the newer `updated` wins, a tie goes to the later column. */
    private fun settle(
        found: List<Found>,
        unreadable: Map<TaskId, List<String>>,
        warnings: MutableList<String>,
    ): Scan {
        val byId = found.groupBy { it.task.value.id }
        val tasks =
            byId.entries.sortedBy { it.key }.map { (id, copies) ->
                val best = copies.withIndex().maxWith(compareBy({ it.value.task.value.updated }, { it.value.column.ordinal }, { it.index })).value
                if (copies.size > 1) warnings += "$id is in ${copies.size} files, ${copies.joinToString { it.path }}; using the newer one, ${best.path}"
                best.task
            }
        return Scan(tasks, byId.mapValues { (_, v) -> v.map { it.path } }, unreadable, warnings)
    }

    /** Folders and loose task files that are not part of the board. */
    private fun noteStrays(warnings: MutableList<String>) {
        for (name in names(root)) {
            val path = joinPath(root, name)
            when {
                name.startsWith('.') || Status.entries.any { it.word == name } -> {}

                entryKind(path) == EntryKind.Dir -> {
                    warnings += "skipping the folder $path: the board's columns are todo, in-progress, done and closed"
                }

                TaskFile.fileId(name) != null -> {
                    warnings += "skipping $path: a task file belongs in a column folder"
                }
            }
        }
    }

    companion object {
        const val LOCK_NAME = ".lock"
        const val GITIGNORE_NAME = ".gitignore"
        const val STATE_DIR = ".state"
        private const val TXN_NAME = "txn"
        const val TXN_FILE = "$STATE_DIR/$TXN_NAME.json"
        private val STATE_NAME = Regex("[a-z][a-z0-9_-]*")

        /** The column is part of the rev, so a move that keeps the second still changes it. */
        fun revOf(
            column: Status,
            bytes: ByteArray,
        ): Rev = Rev.of(column.word.encodeToByteArray() + '\n'.code.toByte() + bytes)
    }
}
