package pyrlyn.taskboard.files

import fs.TempDir
import fs.atomic
import fs.entryKind
import fs.joinPath
import fs.listDir
import fs.readText
import kotlinx.io.files.Path
import pyrlyn.taskboard.BoardException
import pyrlyn.taskboard.Status
import pyrlyn.taskboard.Task
import pyrlyn.taskboard.TaskId
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Instant

/** A board folder that does not exist yet, under a temp dir that is removed afterwards. */
fun <T> withBoard(body: (dir: String) -> T): T = TempDir.create("files-test-").use { body(joinPath(it.path, "board").toString()) }

fun at(seconds: Long): Instant = Instant.fromEpochSeconds(seconds)

fun task(
    id: String,
    title: String = "task $id",
    status: Status = Status.Todo,
    updated: Long = 1_700_000_000,
) = Task(TaskId.parse(id), title, status = status, created = at(1_700_000_000), updated = at(updated))

/** Writes [text] to [rel] under [dir], as a person with an editor would. */
fun put(
    dir: String,
    rel: String,
    text: String,
) = atomic(Path(dir, *rel.split('/').toTypedArray()).also { fs.createDirAll(it.parent!!) }, text.encodeToByteArray())

fun read(
    dir: String,
    rel: String,
): String = readText(joinPath(dir, rel))

fun exists(
    dir: String,
    rel: String,
) = entryKind(joinPath(dir, rel)) != null

/** The files in a column folder, sorted; empty when the folder is missing. */
fun files(
    dir: String,
    column: String,
): List<String> = if (entryKind(joinPath(dir, column)) == null) emptyList() else listDir(joinPath(dir, column)).filter { !it.startsWith('.') }.sorted()

fun code(block: () -> Unit): String = assertFailsWith<BoardException>(block = block).code

fun assertCode(
    expected: String,
    block: () -> Unit,
) = assertEquals(expected, code(block))
