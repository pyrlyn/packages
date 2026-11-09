package pyrlyn.taskboard

import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import pyrlyn.taskboard.testing.Invariants
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class JournalTest {
    private val f = Fixture()
    private val board get() = f.board

    @Test
    fun everyChangeIsJournaledInOrderWithItsActor() {
        val a = f.create("a", Write(actor = "u1")).value.id
        board.update(a, Patch(title = "b"), Write(actor = "u2"))
        board.claim(a, "a1")
        board.move(a, Status.Done, report = "ok", write = Write(actor = "a1"))
        val page = board.changes(0)
        assertEquals(listOf("create", "update", "claim", "move"), page.changes.map { it.op })
        assertEquals(listOf("u1", "u2", "a1", "a1"), page.changes.map { it.actor })
        assertEquals(listOf(1L, 2L, 3L, 4L), page.changes.map { it.seq })
        assertEquals(4, page.seq)
        assertFalse(page.reset)
        assertEquals(FieldChange(JsonPrimitive("a"), JsonPrimitive("b")), page.changes[1].fields["title"])
        assertEquals(mapOf("status" to FieldChange(JsonPrimitive("todo"), JsonPrimitive("in-progress")), "assignee" to FieldChange(JsonNull, JsonPrimitive("a1"))), page.changes[2].fields)
        assertEquals(board.get(a).rev, page.changes.last().rev)
    }

    @Test
    fun changesReturnsOnlyWhatIsNewAndHonorsTheLimit() {
        repeat(5) { f.create("t$it") }
        assertEquals(listOf(4L, 5L), board.changes(3).changes.map { it.seq })
        assertEquals(listOf(1L, 2L), board.changes(0, limit = 2).changes.map { it.seq })
        assertTrue(board.changes(5).changes.isEmpty())
        assertCode("invalid") { board.changes(0, limit = 0) }
    }

    @Test
    fun aSinceFromTheFutureOrTooOldMeansReset() {
        assertFalse(board.changes(0).reset)
        assertTrue(board.changes(7).reset)
        val small = Fixture(journalKeep = 5)
        val a = small.create("a").value.id
        repeat(20) { small.board.update(a, Patch(description = "d$it")) }
        val page = small.board.changes(2)
        assertTrue(page.reset)
        assertTrue(page.changes.isEmpty())
        assertEquals(21, page.seq)
        val tail = small.board.changes(page.seq - 3)
        assertFalse(tail.reset)
        assertEquals(listOf(19L, 20L, 21L), tail.changes.map { it.seq })
        Invariants.check(small.store)
    }

    @Test
    fun theJournalIsCappedNearJournalKeep() {
        val small = Fixture(journalKeep = 10)
        val a = small.create("a").value.id
        repeat(100) { small.board.update(a, Patch(description = "d$it")) }
        val lines =
            small.store
                .snapshot()
                .state("journal")!!
                .lines()
                .count { it.isNotBlank() }
        assertTrue(lines in 10..12, "kept $lines")
        Invariants.check(small.store)
    }

    @Test
    fun handEditsAreRecordedAsExternalByTheNextWriter() {
        val a = f.create("a").value
        f.store.handEdit(a.copy(title = "edited by hand"))
        f.store.handEdit(Task(id("T9"), "dropped in", created = a.created, updated = a.created))
        f.create("b")
        val changes = board.changes(1).changes
        val external = changes.filter { it.external }
        assertEquals(listOf("update", "create"), external.map { it.op })
        assertEquals(listOf(id("T1"), id("T9")), external.map { it.id })
        assertEquals(FieldChange(JsonPrimitive("a"), JsonPrimitive("edited by hand")), external[0].fields["title"])
        assertNull(external[0].actor)
        assertEquals(board.get(id("T9")).rev, external[1].rev)
        assertFalse(changes.last().external)
        assertEquals(id("T10"), changes.last().id)
    }

    @Test
    fun aWatcherRecordsHandEditsOnAQuietBoard() {
        val a = f.create("a").value
        f.store.handEdit(a.copy(priority = 0))
        val page = board.changes(1)
        assertEquals(listOf("update"), page.changes.map { it.op })
        assertTrue(page.changes.single().external)
        assertEquals(2, page.seq)
        assertEquals(page, board.changes(1))
    }

    @Test
    fun handRemovalIsRecordedAndDropsTheLease() {
        val a = f.create("a").value.id
        board.claim(a, "a1")
        f.store.handRemove(a)
        val delete = board.changes(0).changes.last()
        assertEquals("delete", delete.op)
        assertNull(delete.rev)
        assertTrue(delete.external)
        assertNull(
            f.store
                .snapshot()
                .state("leases")
                ?.takeIf { it.contains("T1") },
        )
    }

    @Test
    fun aHandEditThatChangesNoFieldIsNotAChange() {
        val a = f.create("a")
        val seq = board.changes(0).seq
        f.store.handEdit(a.value.copy(updated = a.value.updated + 1.hours))
        assertEquals(
            seq,
            board
                .changes(0)
                .changes
                .last()
                .seq,
        )
    }

    @Test
    fun aBoardWithNoIndexIsAdoptedQuietly() {
        f.store.handEdit(Task(id("T3"), "old", created = f.clock.now(), updated = f.clock.now()))
        val page = board.changes(0)
        assertTrue(page.changes.isEmpty())
        assertEquals(0, page.seq)
        assertEquals(id("T4"), f.create("new").value.id)
        assertEquals(listOf("create"), board.changes(0).changes.map { it.op })
    }

    @Test
    fun watchStreamsEveryChangeInOrder() =
        runBlocking {
            val a = f.create("a").value.id
            val job =
                launch {
                    delay5()
                    board.update(a, Patch(title = "b"))
                    board.claim(a, "a1")
                }
            val seen = withTimeout(10.seconds) { board.watch(0, pollInterval = 10.milliseconds).take(3).toList() }.map { (it as WatchEvent.Changed).change }
            job.join()
            assertEquals(listOf("create", "update", "claim"), seen.map { it.op })
            assertEquals(listOf(1L, 2L, 3L), seen.map { it.seq })
        }

    private suspend fun delay5() = kotlinx.coroutines.delay(5)

    @Test
    fun watchEmitsResetWhenTheJournalNoLongerReachesBack() =
        runBlocking {
            val small = Fixture(journalKeep = 3)
            val a = small.create("a").value.id
            repeat(20) { small.board.update(a, Patch(description = "d$it")) }
            val writer =
                launch {
                    kotlinx.coroutines.delay(100)
                    small.board.update(a, Patch(title = "after"))
                }
            val events =
                withTimeout(10.seconds) {
                    small.board
                        .watch(1, pollInterval = 10.milliseconds)
                        .take(2)
                        .toList()
                }
            writer.join()
            assertEquals(WatchEvent.Reset(21), events[0])
            assertEquals(22L, (events[1] as WatchEvent.Changed).change.seq)
        }
}
