package pyrlyn.taskboard

import pyrlyn.taskboard.testing.Invariants
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A damaged state blob is warned about and rebuilt, never fatal. */
class CorruptStateTest {
    private val f = Fixture()
    private val board get() = f.board

    private fun damage(name: String) = f.store.corruptState(name, "{not json")

    @Test
    fun unreadableLeasesReadAsEmptyAndTheNextWriteRepairsThem() {
        val a = f.create("a").value.id
        board.claim(a, "a1")
        damage(StateNames.LEASES)
        val view = board.snapshot()
        assertTrue(view.warnings.any { "leases" in it })
        assertEquals(1, f.warnings.size)
        assertNull(board.show(a).lease)
        board.update(a, Patch(description = "d"))
        val warned = f.warnings.size
        assertEquals("a1", board.claim(a, "a2")!!.tookOverFrom, "with the lease gone the hold is stale")
        assertEquals(warned, f.warnings.size, "the write repaired the blob, so later calls stay quiet")
        Invariants.check(f.store)
    }

    @Test
    fun unreadableKeysReadAsEmpty() {
        f.create("a", Write(key = "k"))
        damage(StateNames.KEYS)
        val again = board.create(NewTask("a"), Write(key = "k"))
        assertFalse(again.replayed)
        assertTrue(f.warnings.any { "keys" in it })
        assertTrue(board.create(NewTask("a"), Write(key = "k")).replayed, "the repaired blob works again")
    }

    @Test
    fun unreadableIdsAreRebuiltFromTheBoard() {
        f.create("a")
        val b = f.create("b").value.id
        damage(StateNames.IDS)
        val c = f.create("c").value.id
        assertEquals(id("T3"), c)
        assertEquals(id("T2"), b)
        assertTrue(f.warnings.any { "ids" in it })
        assertEquals(id("T4"), f.create("d").value.id)
        assertEquals(1, f.warnings.size, "the repaired blob stays readable")
    }

    @Test
    fun unreadableIndexIsReadoptedWithoutAFloodOfExternalChanges() {
        val a = f.create("a").value.id
        f.create("b")
        board.update(a, Patch(description = "d"))
        val before = board.changes(0)
        damage(StateNames.INDEX)
        assertTrue(board.snapshot().warnings.any { "index" in it })
        val page = board.changes(before.seq)
        assertFalse(page.reset)
        assertTrue(page.changes.isEmpty(), "nothing is reported as a hand edit")
        assertEquals(before.seq, page.seq, "seq carries on from the journal")
        val next = board.update(a, Patch(description = "e"))
        assertEquals(before.seq + 1, next.seq)
        assertTrue(board.changes(0).changes.none { it.external })
        assertEquals(before.seq + 1, board.snapshot().seq)
        Invariants.check(f.store)
    }

    @Test
    fun anUnreadableIndexWithNoJournalStartsFreshAndWatchersReset() {
        val a = f.create("a").value.id
        damage(StateNames.INDEX)
        f.store.corruptState(StateNames.JOURNAL, "")
        val page = board.changes(1)
        assertTrue(page.reset)
        assertEquals(0, page.seq)
        board.update(a, Patch(description = "d"))
        assertEquals(1, board.changes(0).seq)
    }
}
