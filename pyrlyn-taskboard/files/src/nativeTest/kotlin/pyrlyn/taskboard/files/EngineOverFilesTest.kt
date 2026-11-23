package pyrlyn.taskboard.files

import fs.FileLock
import fs.createDirAll
import fs.entryKind
import fs.joinPath
import fs.rename
import kotlinx.serialization.json.JsonPrimitive
import pyrlyn.taskboard.BoardOptions
import pyrlyn.taskboard.Boards
import pyrlyn.taskboard.NewTask
import pyrlyn.taskboard.Op
import pyrlyn.taskboard.Patch
import pyrlyn.taskboard.Ref
import pyrlyn.taskboard.Status
import pyrlyn.taskboard.TaskId
import pyrlyn.taskboard.testing.FakeClock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

private fun id(raw: String) = TaskId.parse(raw)

private class Rig(
    val dir: String,
    val clock: FakeClock = FakeClock(),
    store: FileStore = FileStore(dir),
) {
    val board = Boards.open(store, BoardOptions(clock = clock, idPrefix = "T", lockTimeout = 200.milliseconds))
}

class EngineOverFilesTest {
    @Test
    fun createUpdateMoveClaimReleaseAndBatchWorkEndToEnd() =
        withBoard { dir ->
            val r = Rig(dir)
            val a = r.board.create(NewTask("Port the parser", "Make it fast.")).task
            assertEquals(listOf("T1-port-the-parser.md"), files(dir, "todo"))
            assertEquals(a.value, r.board.get(id("T1")).value)

            r.clock.advance(1.minutes)
            r.board.update(id("T1"), Patch(title = "Port the lexer", priority = 1, parallel = true))
            assertEquals(listOf("T1-port-the-lexer.md"), files(dir, "todo"))

            val claim = assertNotNull(r.board.claim(null, "agent-1"))
            assertEquals(id("T1"), claim.task.value.id)
            assertEquals(listOf("T1-port-the-lexer.md"), files(dir, "in-progress"))
            assertEquals(emptyList(), files(dir, "todo"))

            r.board.release(id("T1"), "agent-1")
            assertEquals(listOf("T1-port-the-lexer.md"), files(dir, "todo"))

            val written =
                r.board.batch(
                    listOf(
                        Op.Create(NewTask("child"), ref = "c"),
                        Op.Update(Ref("c"), Patch(description = "child body")),
                        Op.Move(id("T1"), Status.Done, report = "shipped", force = true),
                    ),
                )
            assertEquals(3, written.results.size)
            assertEquals(listOf("T1-port-the-lexer.md"), files(dir, "done"))
            assertEquals(listOf("T2-child.md"), files(dir, "todo"))
            val done = r.board.get(id("T1")).value
            assertEquals("shipped", done.report)
            assertEquals("Make it fast.", done.description)
            assertTrue("## Report" in read(dir, "done/T1-port-the-lexer.md"))

            val journal = r.board.changes(0).changes
            assertEquals(listOf("create", "update", "claim", "release", "create", "update", "move"), journal.map { it.op })
            assertTrue(journal.none { it.external })
        }

    @Test
    fun aBoardThatIsOnlyReadIsNeverCreated() =
        withBoard { dir ->
            val r = Rig(dir)
            assertEquals(
                emptyList(),
                r.board
                    .snapshot()
                    .columns
                    .flatMap { it.cards },
            )
            assertEquals(
                0,
                r.board
                    .changes(0)
                    .changes.size,
            )
            assertNull(entryKind(joinPath(dir)))
        }

    @Test
    fun anotherBoardOnTheSameFolderSeesWhatTheFirstWrote() =
        withBoard { dir ->
            val a = Rig(dir)
            val b = Rig(dir)
            a.board.create(NewTask("x"))
            assertEquals(
                id("T1"),
                b.board
                    .get(id("T1"))
                    .value.id,
            )
            assertEquals(
                id("T2"),
                b.board
                    .create(NewTask("y"))
                    .task.value.id,
            )
            assertEquals(
                2,
                a.board
                    .snapshot()
                    .columns
                    .sumOf { it.cards.size },
            )
        }

    @Test
    fun aHandEditIsReportedAsAnExternalChange() =
        withBoard { dir ->
            val r = Rig(dir)
            val t =
                r.board
                    .create(NewTask("a"))
                    .task.value
            val seq = r.board.changes(0).seq
            put(dir, "todo/T1-a.md", TaskFile.render(t.copy(description = "by hand")))
            val change =
                r.board
                    .changes(seq)
                    .changes
                    .single()
            assertTrue(change.external)
            assertEquals("update", change.op)
            assertEquals(JsonPrimitive("by hand"), change.fields.getValue("description").new)
            assertEquals(
                "by hand",
                r.board
                    .get(t.id)
                    .value.description,
            )
        }

    @Test
    fun aHandMoveIsReportedAsAnExternalChange() =
        withBoard { dir ->
            val r = Rig(dir)
            r.board.create(NewTask("a"))
            val seq = r.board.changes(0).seq
            createDirAll(joinPath(dir, "done"))
            rename(joinPath(dir, "todo/T1-a.md"), joinPath(dir, "done/T1-a.md"))
            val change =
                r.board
                    .changes(seq)
                    .changes
                    .single()
            assertTrue(change.external)
            assertEquals(JsonPrimitive("done"), change.fields.getValue("status").new)
            assertEquals(
                Status.Done,
                r.board
                    .get(id("T1"))
                    .value.status,
            )
        }

    @Test
    fun aHandDeleteIsReportedAsAnExternalChange() =
        withBoard { dir ->
            val r = Rig(dir)
            r.board.create(NewTask("a"))
            val seq = r.board.changes(0).seq
            fs.removeFile(joinPath(dir, "todo/T1-a.md"))
            val change =
                r.board
                    .changes(seq)
                    .changes
                    .single()
            assertTrue(change.external)
            assertEquals("delete", change.op)
        }

    @Test
    fun aHandCopyIsReportedAsAnExternalChange() =
        withBoard { dir ->
            val r = Rig(dir)
            r.board.create(NewTask("a"))
            val seq = r.board.changes(0).seq
            put(dir, "todo/T7-copy.md", read(dir, "todo/T1-a.md").replace("id: T1", "id: T7"))
            val created =
                r.board
                    .changes(seq)
                    .changes
                    .single()
            assertTrue(created.external)
            assertEquals("create", created.op)
            assertEquals(id("T7"), created.id)

            // The same file copied into a later column is one task that moved: the later column wins a tie.
            val seq2 = r.board.changes(0).seq
            put(dir, "done/T1-a.md", read(dir, "todo/T1-a.md"))
            val moved =
                r.board
                    .changes(seq2)
                    .changes
                    .single()
            assertTrue(moved.external)
            assertEquals(
                Status.Done,
                r.board
                    .get(id("T1"))
                    .value.status,
            )
            // The next write by the engine tidies the duplicate away.
            r.board.update(id("T1"), Patch(title = "b"))
            assertEquals(emptyList(), files(dir, "todo").filter { it.startsWith("T1") })
        }

    @Test
    fun aWriteOnTopOfAHandCopyKeepsOneFile() =
        withBoard { dir ->
            val r = Rig(dir)
            r.board.create(NewTask("a"))
            put(dir, "in-progress/T1-a.md", read(dir, "todo/T1-a.md"))
            r.board.update(id("T1"), Patch(description = "x"))
            assertEquals(1, listOf("todo", "in-progress").sumOf { files(dir, it).size })
        }

    @Test
    fun aBusyBoardAnswersBusyAndWorksAgainOnceTheLockIsFree() =
        withBoard { dir ->
            val r = Rig(dir)
            r.board.create(NewTask("a"))
            val held = assertNotNull(FileLock.tryExclusive(joinPath(dir, ".lock").toString()))
            assertCode("busy") { r.board.create(NewTask("b")) }
            held.close()
            assertEquals(
                id("T2"),
                r.board
                    .create(NewTask("b"))
                    .task.value.id,
            )
        }
}
