package pyrlyn.taskboard

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

class KeyTest {
    private val f = Fixture()
    private val board get() = f.board

    @Test
    fun aRepeatedKeyReturnsTheFirstResult() {
        val first = board.create(NewTask("a"), Write(key = "req-1"))
        assertFalse(first.replayed)
        val again = board.create(NewTask("a"), Write(key = "req-1"))
        assertTrue(again.replayed)
        assertEquals(first.task, again.task)
        assertEquals(first.seq, again.seq)
        assertEquals(
            1,
            board
                .snapshot()
                .columns[0]
                .cards.size,
        )
    }

    @Test
    fun theReplayIsTheFirstStateEvenAfterLaterEdits() {
        val first = board.create(NewTask("a"), Write(key = "k"))
        board.update(first.task.value.id, Patch(title = "changed"))
        assertEquals(
            "a",
            board
                .create(NewTask("a"), Write(key = "k"))
                .task.value.title,
        )
    }

    @Test
    fun theSameKeyWithAnotherRequestIsRefused() {
        board.create(NewTask("a"), Write(key = "k"))
        val e = fail { board.create(NewTask("b"), Write(key = "k")) }
        assertEquals("key_reused", e.code)
        assertEquals(
            1,
            board
                .snapshot()
                .columns[0]
                .cards.size,
        )
    }

    @Test
    fun keysExpire() {
        val ttl = Fixture(keyTtl = 1.hours)
        ttl.board.create(NewTask("a"), Write(key = "k"))
        ttl.clock.advance(61.minutes)
        val second = ttl.board.create(NewTask("a"), Write(key = "k"))
        assertFalse(second.replayed)
        assertEquals(
            2,
            ttl.board
                .snapshot()
                .columns[0]
                .cards.size,
        )
    }

    @Test
    fun aFailedRequestLeavesTheKeyFree() {
        assertCode("invalid") { board.create(NewTask(""), Write(key = "k")) }
        assertFalse(board.create(NewTask("ok"), Write(key = "k")).replayed)
    }

    @Test
    fun claimsAndBatchesTakeKeysToo() {
        f.create("a")
        val c1 = board.claim(null, "a1", write = Write(key = "c"))!!
        val c2 = board.claim(null, "a1", write = Write(key = "c"))!!
        assertTrue(c2.replayed)
        assertEquals(c1.task, c2.task)
        val ops = listOf(Op.Create(NewTask("x")), Op.Create(NewTask("y")))
        val b1 = board.batch(ops, Write(key = "b"))
        val b2 = board.batch(ops, Write(key = "b"))
        assertEquals(b1.results, b2.results)
        assertTrue(b2.replayed)
        assertEquals(3, board.snapshot().columns.sumOf { it.cards.size })
    }

    @Test
    fun badKeysAreInvalid() {
        assertCode("invalid") { board.create(NewTask("a"), Write(key = " ")) }
        assertCode("invalid") { board.create(NewTask("a"), Write(key = "k".repeat(500))) }
    }
}
