package pyrlyn.taskboard.files

import fs.joinPath
import fs.listDir
import kotlinx.io.files.Path
import pyrlyn.taskboard.Board
import pyrlyn.taskboard.BoardOptions
import pyrlyn.taskboard.Boards
import pyrlyn.taskboard.NewTask
import pyrlyn.taskboard.Op
import pyrlyn.taskboard.Patch
import pyrlyn.taskboard.Ref
import pyrlyn.taskboard.Status
import pyrlyn.taskboard.TaskId
import pyrlyn.taskboard.testing.FakeClock
import pyrlyn.taskboard.testing.Invariants
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Every file under [dir] by relative path, but the lock: what two boards must share to be the same board. */
private fun tree(
    dir: String,
    rel: String = "",
): Map<String, String> =
    buildMap {
        for (name in listDir(if (rel.isEmpty()) Path(dir) else joinPath(dir, rel)).sorted()) {
            val path = if (rel.isEmpty()) name else "$rel/$name"
            when {
                name == ".lock" -> {}

                fs.isDir(joinPath(dir, path)) -> {
                    putAll(tree(dir, path))
                }

                else -> {
                    put(path, read(dir, path))
                }
            }
        }
    }

private val batch =
    listOf(
        Op.Move(TaskId.parse("T1"), Status.Done, report = "shipped", force = true),
        Op.Update(TaskId.parse("T2"), Patch(title = "A renamed task")),
        Op.Create(NewTask("brand new"), ref = "n"),
        Op.Update(Ref("n"), Patch(description = "filled in")),
    )

private fun boardOn(
    dir: String,
    crashAt: () -> Int?,
    applied: (Int) -> Unit = {},
): Board =
    Boards.open(
        FileStore(dir) { n ->
            applied(n)
            n == crashAt()
        },
        BoardOptions(clock = FakeClock()),
    )

private fun setup(board: Board) {
    board.create(NewTask("first", "body one"))
    board.create(NewTask("second"))
}

class CrashRedoTest {
    @Test
    fun aCrashAfterAnyEffectIsFinishedByTheNextWriterAndLeavesTheSameBoard() {
        // A run without a crash is the reference, and tells how many effects the batch has.
        var most = 0
        val reference =
            withBoard { dir ->
                var armed = false
                val board = boardOn(dir, { null }) { if (armed) most = maxOf(most, it) }
                setup(board)
                armed = true
                board.batch(batch)
                armed = false
                board.create(NewTask("later"))
                tree(dir)
            }
        assertTrue(most >= 8, "the batch should have many effects, had $most")
        for (k in 0..most) {
            withBoard { dir ->
                var crashAt: Int? = null
                val board = boardOn(dir, { crashAt })
                setup(board)
                crashAt = k
                val crash = assertFailsWith<SimulatedCrash>("crash after $k") { board.batch(batch) }
                assertEquals(k, crash.applied)
                assertTrue(exists(dir, ".state/txn.json"), "the plan stays behind after a crash at $k")
                crashAt = null
                // A lock-free read in between may see the commit half applied, but never fails.
                boardOn(dir, { null }).snapshot()
                boardOn(dir, { null }).create(NewTask("later"))
                val after = tree(dir)
                assertEquals(reference.keys, after.keys, "files after a crash at $k")
                for ((path, text) in reference) assertEquals(text, after.getValue(path), "$path after a crash at $k")
                val journal = after.getValue(".state/journal.jsonl").lines().filter { it.isNotEmpty() }
                assertEquals(journal.size, journal.toSet().size, "the journal repeats a line after a crash at $k")
                Invariants.check(FileStore(dir))
            }
        }
    }

    @Test
    fun aCrashedCommitIsNotVisibleAsFinishedUntilRedone() =
        withBoard { dir ->
            var crashAt: Int? = null
            val board = boardOn(dir, { crashAt })
            setup(board)
            crashAt = 1
            assertFailsWith<SimulatedCrash> { board.batch(batch) }
            // One file replaced, the rest not: the reader sees a board in the middle of a commit, no torn file.
            val seen = FileStore(dir).snapshot()
            assertTrue(seen.warnings().all { "using the newer one" in it }, seen.warnings().toString())
            assertEquals(
                Status.Done,
                seen
                    .tasks()
                    .first { it.value.id == TaskId.parse("T1") }
                    .value.status,
            )
            assertEquals(
                "second",
                seen
                    .tasks()
                    .first { it.value.id == TaskId.parse("T2") }
                    .value.title,
            )
        }

    @Test
    fun aPlanThatCannotBeTrustedIsNotReplayed() {
        for (plan in listOf("not json", "{}", "{\"effects\":[{\"op\":\"write\",\"path\":\"../evil.md\",\"text\":\"x\"}]}", "{\"effects\":[{\"op\":\"unlink\",\"path\":\"/etc/passwd\"}]}", "{\"effects\":[{\"op\":\"nope\",\"path\":\"todo/T1-x.md\"}]}")) {
            withBoard { dir ->
                val board = boardOn(dir, { null })
                setup(board)
                put(dir, ".state/txn.json", plan)
                assertCode("invalid") { board.create(NewTask("x")) }
                assertTrue(exists(dir, ".state/txn.json"), "kept for the user to look at: $plan")
                assertTrue(!exists(dir, "../evil.md"))
            }
        }
    }
}
