package pyrlyn.taskboard

import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

class RevisionTest {
    private val f = Fixture()
    private val board get() = f.board

    @Test
    fun ifRevIsStrictCompareAndSet() {
        val a = f.create("a")
        val b = board.update(a.value.id, Patch(title = "b"), Write(ifRev = a.rev)).task
        val e = fail { board.update(a.value.id, Patch(description = "other field"), Write(ifRev = a.rev)) }
        assertEquals("conflict", e.code)
        val err = e.error as BoardError.Conflict
        assertEquals(b, err.current)
        assertTrue(err.fields.isEmpty())
        assertEquals(b, board.get(a.value.id))
    }

    @Test
    fun ifRevGuardsMoveClaimAndRelease() {
        val a = f.create("a")
        board.update(a.value.id, Patch(title = "b"))
        assertCode("conflict") { board.move(a.value.id, Status.Done, write = Write(ifRev = a.rev)) }
        assertCode("conflict") { board.claim(a.value.id, "x", write = Write(ifRev = a.rev)) }
        assertCode("conflict") { board.release(a.value.id, "x", write = Write(ifRev = a.rev)) }
    }

    @Test
    fun baseRevMergesFieldsThatDidNotClash() {
        val a = f.create("a")
        board.update(a.value.id, Patch(title = "A"))
        val r = board.update(a.value.id, Patch(description = "why"), Write(baseRev = a.rev)).task.value
        assertEquals("A", r.title)
        assertEquals("why", r.description)
    }

    @Test
    fun baseRevClashNamesBaseCurrentAndRequested() {
        val a = f.create("a")
        board.update(a.value.id, Patch(title = "mine"))
        val e = fail { board.update(a.value.id, Patch(title = "yours", description = "why"), Write(baseRev = a.rev)) }
        val err = e.error as BoardError.Conflict
        assertEquals(1, err.fields.size)
        val clash = err.fields.single()
        assertEquals("title", clash.field)
        assertEquals(JsonPrimitive("a"), clash.base)
        assertEquals(JsonPrimitive("mine"), clash.current)
        assertEquals(JsonPrimitive("yours"), clash.requested)
        assertEquals("mine", board.get(a.value.id).value.title)
        assertEquals("", board.get(a.value.id).value.description)
    }

    @Test
    fun baseRevAcceptsTheSameValueThatAlreadyLanded() {
        val a = f.create("a")
        board.update(a.value.id, Patch(title = "same"))
        val r = board.update(a.value.id, Patch(title = "same"), Write(baseRev = a.rev))
        assertEquals(false, r.result.changed)
    }

    @Test
    fun setFieldEditsNeverClash() {
        val a = f.create("a")
        val x = f.create("x").value.id
        val y = f.create("y").value.id
        board.update(a.value.id, Patch(addBlockedBy = setOf(x)))
        val r = board.update(a.value.id, Patch(addBlockedBy = setOf(y)), Write(baseRev = a.rev)).task.value
        assertEquals(listOf(x, y), r.blockedBy)
    }

    @Test
    fun baseRevAppliesToMoveToo() {
        val a = f.create("a")
        board.move(a.value.id, Status.Closed)
        val e = fail { board.move(a.value.id, Status.Done, write = Write(baseRev = a.rev)) }
        assertEquals("status", (e.error as BoardError.Conflict).fields.single().field)
    }

    @Test
    fun aBaseOlderThanTheJournalActsAsIfRev() {
        val small = Fixture(journalKeep = 3)
        val a = small.create("a")
        repeat(10) { small.board.update(a.value.id, Patch(description = "d$it")) }
        val e = fail { small.board.update(a.value.id, Patch(title = "x"), Write(baseRev = a.rev)) }
        assertTrue((e.error as BoardError.Conflict).fields.isEmpty())
        val fresh = small.get(a.value.id)
        small.board.update(a.value.id, Patch(title = "x"), Write(baseRev = fresh.rev))
    }

    @Test
    fun baseRevSeesAHandEditAsAChange() {
        val a = f.create("a")
        f.store.handEdit(a.value.copy(title = "by hand"))
        val e = fail { board.update(a.value.id, Patch(title = "mine"), Write(baseRev = a.rev)) }
        val clash = (e.error as BoardError.Conflict).fields.single()
        assertEquals(JsonPrimitive("by hand"), clash.current)
        f.clock.advance(1.minutes)
        board.update(a.value.id, Patch(description = "ok"), Write(baseRev = a.rev))
    }

    @Test
    fun revsInABatchSeeEarlierStepsOfTheBatch() {
        val a = f.create("a")
        val ops = listOf(Op.Update(a.value.id, Patch(title = "b")), Op.Update(a.value.id, Patch(description = "d"), ifRev = a.rev))
        val e = fail { board.batch(ops) }
        assertEquals("conflict", e.code)
        assertEquals(1, e.opIndex)
        assertEquals("a", board.get(a.value.id).value.title)
    }
}
