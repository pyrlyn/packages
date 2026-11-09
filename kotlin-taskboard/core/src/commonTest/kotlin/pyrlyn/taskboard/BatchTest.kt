package pyrlyn.taskboard

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BatchTest {
    private val f = Fixture()
    private val board get() = f.board

    @Test
    fun refsLetLaterStepsPointAtEarlierCreates() {
        val w =
            board.batch(
                listOf(
                    Op.Create(NewTask("parent"), ref = "p"),
                    Op.Create(NewTask("child", parent = Ref("p"))),
                    Op.Update(Ref("p"), Patch(priority = 0)),
                    Op.Claim(Ref("p"), "a1"),
                ),
            )
        assertEquals(4, w.results.size)
        assertEquals(
            id("T1.1"),
            w.results[1]
                .task.value.id,
        )
        assertEquals(
            id("T1"),
            w.results[1]
                .task.value.parent,
        )
        assertEquals(0, board.get(id("T1")).value.priority)
        assertEquals("a1", board.get(id("T1")).value.assignee)
        assertEquals(w.seq, board.snapshot().seq)
    }

    @Test
    fun aFailingStepWritesNothingAndNamesTheStep() {
        f.create("keep")
        val before = board.changes(0)
        val e =
            fail {
                board.batch(listOf(Op.Create(NewTask("one")), Op.Create(NewTask("two")), Op.Update(id("T9"), Patch(title = "x"))))
            }
        assertEquals("not_found", e.code)
        assertEquals(2, e.opIndex)
        assertEquals(before, board.changes(0))
        assertEquals(
            listOf(id("T1")),
            board
                .snapshot(Filter(all = true))
                .columns
                .flatMap { it.cards }
                .map { it.id },
        )
        assertEquals(id("T2"), f.create("next").value.id)
    }

    @Test
    fun anUnknownRefIsInvalid() {
        val e = fail { board.batch(listOf(Op.Update(Ref("nope"), Patch(title = "x")))) }
        assertEquals("invalid", e.code)
        assertEquals(0, e.opIndex)
        assertCode("invalid") { board.batch(listOf(Op.Create(NewTask("a"), ref = "r"), Op.Create(NewTask("b"), ref = "r"))) }
    }

    @Test
    fun emptyBatchAndPerBatchRevsAreInvalid() {
        assertCode("invalid") { board.batch(emptyList()) }
        assertCode("invalid") { board.batch(listOf(Op.Create(NewTask("a"))), Write(ifRev = Rev("0123456789abcdef"))) }
        assertCode("invalid") { board.create(NewTask("a"), Write(ifRev = Rev("0123456789abcdef"))) }
    }

    @Test
    fun aBatchIsOneJournalRunInOrder() {
        board.batch(listOf(Op.Create(NewTask("a")), Op.Create(NewTask("b")), Op.Move(id("T1"), Status.Done)), Write(actor = "bot"))
        val changes = board.changes(0).changes
        assertEquals(listOf("create", "create", "move"), changes.map { it.op })
        assertEquals(listOf(1L, 2L, 3L), changes.map { it.seq })
        assertTrue(changes.all { it.actor == "bot" })
        assertEquals(1, f.store.commitCount)
    }

    @Test
    fun batchedMoveAndClaimWork() {
        val a = f.create("a").value.id
        board.batch(listOf(Op.Claim(a, "a1"), Op.Move(a, Status.Done, report = "ok")), Write(actor = "a1"))
        val t = board.get(a).value
        assertEquals(Status.Done, t.status)
        assertEquals("ok", t.report)
        assertNull(board.show(a).lease)
    }
}
