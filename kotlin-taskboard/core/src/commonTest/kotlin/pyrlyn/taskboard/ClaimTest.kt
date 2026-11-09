package pyrlyn.taskboard

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class ClaimTest {
    private var alive: Boolean? = null
    private val f = Fixture(liveness = { alive })
    private val board get() = f.board

    @Test
    fun claimMovesTheCardAndTakesALease() {
        val a = f.create("a").value.id
        val r = board.claim(a, "a1")!!
        assertTrue(r.changed)
        assertEquals(Status.InProgress, r.task.value.status)
        assertEquals("a1", r.task.value.assignee)
        assertEquals(Lease("a1", f.clock.now(), f.clock.now() + 30.minutes), r.lease)
        assertNull(r.tookOverFrom)
    }

    @Test
    fun claimWithoutIdPicksTheNextReadyTask() {
        f.create("low") { it.copy(priority = 3) }
        val high = f.create("high") { it.copy(priority = 1) }.value.id
        assertEquals(
            high,
            board
                .claim(null, "a1")!!
                .task.value.id,
        )
        assertEquals(
            id("T1"),
            board
                .claim(null, "a2")!!
                .task.value.id,
        )
        assertNull(board.claim(null, "a3"))
    }

    @Test
    fun claimingYourOwnTaskRenewsTheLease() {
        val a = f.create("a").value.id
        val first = board.claim(a, "a1")!!
        f.clock.advance(10.minutes)
        val again = board.claim(a, "a1", lease = 5.minutes)!!
        assertFalse(again.changed)
        assertEquals(first.task, again.task)
        assertEquals(first.lease.acquired, again.lease.acquired)
        assertEquals(f.clock.now() + 5.minutes, again.lease.expires)
        assertEquals(again.lease, board.show(a).lease)
    }

    @Test
    fun aLiveLeaseKeepsOthersOut() {
        val a = f.create("a").value.id
        board.claim(a, "a1")
        val e = fail { board.claim(a, "a2") }
        assertEquals("claimed", e.code)
        val err = e.error as BoardError.Claimed
        assertEquals("a1", err.holder)
        assertEquals(f.clock.now() + 30.minutes, err.until)
        assertNull(board.claim(null, "a2"))
    }

    @Test
    fun claimRefusesBlockedAndFinishedTasks() {
        val a = f.create("a").value.id
        val b = f.create("b") { it.copy(blockedBy = listOf(a)) }.value.id
        assertCode("invalid") { board.claim(b, "x") }
        board.move(a, Status.Done)
        assertCode("invalid") { board.claim(a, "x") }
        assertCode("not_found") { board.claim(id("T9"), "x") }
        assertCode("invalid") { board.claim(b, " ") }
    }

    @Test
    fun anExpiredLeaseCanBeTakenOver() {
        val a = f.create("a").value.id
        board.claim(a, "a1")
        f.clock.advance(31.minutes)
        assertTrue(
            board
                .snapshot()
                .columns[1]
                .cards
                .single()
                .stale,
        )
        assertTrue(
            board
                .snapshot(Filter(ready = true))
                .columns[1]
                .cards
                .single()
                .ready,
        )
        val r = board.claim(a, "a2")!!
        assertEquals("a1", r.tookOverFrom)
        assertEquals("a2", r.task.value.assignee)
        assertEquals("a2", r.lease.agent)
        assertEquals(f.clock.now(), r.lease.acquired)
        assertFalse(
            board
                .snapshot()
                .columns[1]
                .cards
                .single()
                .stale,
        )
    }

    @Test
    fun claimNextTakesAStaleTaskToo() {
        f.create("a")
        board.claim(null, "a1")
        f.clock.advance(31.minutes)
        assertEquals("a1", board.claim(null, "a2")!!.tookOverFrom)
    }

    @Test
    fun aHolderTheHostVouchesForKeepsItsLease() {
        val a = f.create("a").value.id
        board.claim(a, "a1")
        f.clock.advance(31.minutes)
        alive = true
        assertFalse(
            board
                .snapshot()
                .columns[1]
                .cards
                .single()
                .stale,
        )
        assertCode("claimed") { board.claim(a, "a2") }
        alive = false
        assertEquals("a1", board.claim(a, "a2")!!.tookOverFrom)
    }

    @Test
    fun aLivenessHookThatThrowsKeepsTheLease() {
        val boom = Fixture(liveness = { error("registry down") })
        val a = boom.create("a").value.id
        boom.board.claim(a, "a1")
        boom.clock.advance(31.minutes)
        assertCode("claimed") { boom.board.claim(a, "a2") }
    }

    @Test
    fun releaseNeedsTheHolderOrForce() {
        val a = f.create("a").value.id
        board.claim(a, "a1")
        assertCode("claimed") { board.release(a, "a2") }
        val forced = board.release(a, "a2", force = true).task.value
        assertEquals(Status.Todo, forced.status)
        assertNull(forced.assignee)
        assertNull(board.show(a).lease)
    }

    @Test
    fun releaseByTheHolderReopensTheTask() {
        val a = f.create("a").value.id
        board.claim(a, "a1")
        val r = board.release(a, "a1")
        assertTrue(r.result.changed)
        assertFalse(board.release(a, "a1").result.changed)
        assertNotNull(board.claim(a, "a2"))
        board.move(a, Status.Done, report = "ok", write = Write(actor = "a2"))
        assertCode("invalid") { board.release(a, "a2") }
    }

    @Test
    fun moveByANonHolderNeedsForce() {
        val a = f.create("a").value.id
        board.claim(a, "a1")
        assertCode("claimed") { board.move(a, Status.Done, write = Write(actor = "a2")) }
        assertCode("claimed") { board.move(a, Status.Done) }
        board.move(a, Status.Done, write = Write(actor = "a1"), report = "ok")
        val b = f.create("b").value.id
        board.claim(b, "a1")
        board.move(b, Status.Done, force = true, write = Write(actor = "a2"))
        assertNull(board.show(b).lease)
        assertEquals("a1", board.get(b).value.assignee)
    }

    @Test
    fun aLapsedLeaseNoLongerProtectsFromMoveOrRelease() {
        val a = f.create("a").value.id
        board.claim(a, "a1")
        f.clock.advance(31.minutes)
        board.release(a, "a2")
        board.claim(a, "a1")
        f.clock.advance(31.minutes)
        board.move(a, Status.Done)
    }

    @Test
    fun movingBackToTodoDropsTheHold() {
        val a = f.create("a").value.id
        board.claim(a, "a1")
        val t = board.move(a, Status.Todo, write = Write(actor = "a1")).task.value
        assertNull(t.assignee)
        assertNull(board.show(a).lease)
    }

    @Test
    fun conflictPartnersInProgressAreWarnedAbout() {
        val a = f.create("a").value.id
        val b = f.create("b") { it.copy(conflictsWith = listOf(a)) }.value.id
        board.claim(a, "a1")
        assertEquals(listOf(a), board.claim(b, "a2")!!.conflictsInProgress)
    }

    @Test
    fun aBareAssigneeWithNoLeaseCanBeTaken() {
        val a = f.create("a").value.id
        f.store.handEdit(board.get(a).value.copy(assignee = "ghost", status = Status.InProgress))
        assertEquals("ghost", board.claim(a, "a1")!!.tookOverFrom)
    }

    @Test
    fun leaseCanLapseBetweenRenewals() {
        val a = f.create("a").value.id
        board.claim(a, "a1", lease = 10.seconds)
        f.clock.advance(11.seconds)
        assertTrue(
            board
                .snapshot()
                .columns[1]
                .cards
                .single()
                .stale,
        )
        board.claim(a, "a1")
        assertFalse(
            board
                .snapshot()
                .columns[1]
                .cards
                .single()
                .stale,
        )
    }
}
