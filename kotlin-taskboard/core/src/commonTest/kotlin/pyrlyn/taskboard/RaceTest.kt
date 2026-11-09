package pyrlyn.taskboard

import kotlinx.serialization.json.JsonPrimitive
import pyrlyn.taskboard.testing.InjectedFault
import pyrlyn.taskboard.testing.Invariants
import pyrlyn.taskboard.testing.MemoryStore
import pyrlyn.taskboard.testing.Race
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

private const val CLIENTS = 8
private const val ROUNDS = 120

class RaceTest {
    @Test
    fun twoClientsNeverClaimTheSameTask() {
        val f = Fixture()
        repeat(ROUNDS) { round ->
            val id = f.create("round $round").value.id
            val results = Race.run(CLIENTS) { c -> runCatching { f.board.claim(id, "agent-$c") } }
            val winners = results.map { it.getOrThrow() }.filter { it.isSuccess }
            assertEquals(1, winners.size, "round $round")
            val losers = results.map { it.getOrThrow() }.filter { it.isFailure }
            assertTrue(losers.all { (it.exceptionOrNull() as BoardException).code == "claimed" })
            f.board.move(id, Status.Done, force = true)
        }
        Invariants.check(f.store)
    }

    @Test
    fun claimNextHandsEveryTaskToExactlyOneAgent() {
        val f = Fixture()
        repeat(ROUNDS) { f.create("t$it") }
        val results = Race.run(CLIENTS) { c -> generateSequence { f.board.claim(null, "agent-$c") }.map { it.task.value.id }.toList() }
        val all = results.flatMap { it.getOrThrow() }
        assertEquals(ROUNDS, all.size)
        assertEquals(ROUNDS, all.toSet().size)
        Invariants.check(f.store)
    }

    @Test
    fun concurrentCreatesGetDistinctIds() {
        val f = Fixture()
        val results =
            Race.run(CLIENTS) { c ->
                (0 until 25).map {
                    f.board
                        .create(NewTask("c$c-$it"))
                        .task.value.id
                }
            }
        val ids = results.flatMap { it.getOrThrow() }
        assertEquals(CLIENTS * 25, ids.toSet().size)
        assertEquals((1..CLIENTS * 25).map { id("T$it") }.toSet(), ids.toSet())
        Invariants.check(f.store)
    }

    @Test
    fun concurrentSubtaskCreatesGetDistinctChildIds() {
        val f = Fixture()
        val parent = f.create("p").value.id
        val ids =
            Race
                .run(CLIENTS) { c ->
                    (0 until 10).map {
                        f.board
                            .create(NewTask("s$c-$it", parent = parent))
                            .task.value.id
                    }
                }.flatMap { it.getOrThrow() }
        assertEquals(CLIENTS * 10, ids.toSet().size)
        assertTrue(ids.all { it.parent() == parent })
    }

    @Test
    fun editsOfDifferentFieldsBothSurvive() {
        val f = Fixture()
        repeat(ROUNDS) {
            val a = f.create("a")
            val results =
                Race.run(2) { c ->
                    val patch = if (c == 0) Patch(title = "title") else Patch(description = "description")
                    f.board.update(a.value.id, patch, Write(baseRev = a.rev))
                }
            results.forEach { it.getOrThrow() }
            val t = f.get(a.value.id).value
            assertEquals("title" to "description", t.title to t.description)
        }
        Invariants.check(f.store)
    }

    @Test
    fun editsOfTheSameFieldWithABaseGiveExactlyOneConflict() {
        val f = Fixture()
        repeat(ROUNDS) { round ->
            val a = f.create("a")
            val results = Race.run(2) { c -> f.board.update(a.value.id, Patch(title = "from-$c"), Write(baseRev = a.rev)) }
            val failures = results.mapNotNull { it.exceptionOrNull() }
            assertEquals(1, failures.size, "round $round")
            val e = failures.single() as BoardException
            assertEquals("conflict", e.code)
            val clash = (e.error as BoardError.Conflict).fields.single()
            assertEquals("title", clash.field)
            assertEquals(JsonPrimitive("a"), clash.base)
            assertEquals(clash.current, JsonPrimitive(f.get(a.value.id).value.title))
        }
    }

    @Test
    fun concurrentIfRevWritersGiveExactlyOneWinner() {
        val f = Fixture()
        repeat(ROUNDS) {
            val a = f.create("a")
            val results = Race.run(CLIENTS) { c -> f.board.update(a.value.id, Patch(description = "c$c"), Write(ifRev = a.rev)) }
            assertEquals(1, results.count { it.isSuccess })
            assertTrue(results.mapNotNull { it.exceptionOrNull() }.all { (it as BoardException).code == "conflict" })
        }
    }

    @Test
    fun aRetriedKeyNeverDuplicates() {
        val f = Fixture()
        repeat(ROUNDS) { round ->
            val results = Race.run(CLIENTS) { f.board.create(NewTask("once $round"), Write(key = "req-$round")) }
            val written = results.map { it.getOrThrow() }
            assertEquals(1, written.count { !it.replayed })
            assertEquals(1, written.map { it.task.value.id }.toSet().size)
        }
        assertEquals(
            ROUNDS,
            f.board
                .snapshot()
                .columns[0]
                .cards.size,
        )
        Invariants.check(f.store)
    }

    @Test
    fun aBatchThatFailsWritesNothingEvenWhenRacing() {
        val f = Fixture()
        val results =
            Race.run(CLIENTS) { c ->
                runCatching { f.board.batch(listOf(Op.Create(NewTask("x$c")), Op.Create(NewTask("y$c")), Op.Update(id("T99"), Patch(title = "boom")))) }
            }
        assertTrue(results.all { it.getOrThrow().isFailure })
        assertTrue(
            f.board
                .snapshot(Filter(all = true))
                .columns
                .all { it.cards.isEmpty() },
        )
        assertEquals(0, f.store.commitCount)
    }

    @Test
    fun batchesAreAtomicToOtherClients() {
        val f = Fixture()
        Race.run(CLIENTS) { c -> repeat(10) { f.board.batch(listOf(Op.Create(NewTask("a$c-$it")), Op.Create(NewTask("b$c-$it")))) } }.forEach { it.getOrThrow() }
        val titles =
            f.board
                .snapshot()
                .columns[0]
                .cards
                .map { it.title }
        assertEquals(CLIENTS * 20, titles.size)
        for (c in 0 until CLIENTS) for (i in 0 until 10) assertEquals(titles.contains("a$c-$i"), titles.contains("b$c-$i"))
        Invariants.check(f.store)
    }

    @Test
    fun aFailedCommitLeavesNoPartialState() {
        val store = MemoryStore(failCommit = { it % 3 == 0 })
        val f = Fixture(store = store)
        var created = 0
        repeat(30) {
            try {
                f.board.batch(listOf(Op.Create(NewTask("p$it")), Op.Create(NewTask("q$it")), Op.Claim(null, "a1")))
                created += 2
            } catch (_: InjectedFault) {
                // The write that was cut off must not show anywhere.
            }
            Invariants.check(store)
        }
        assertEquals(
            created,
            f.board
                .snapshot(Filter(all = true))
                .columns
                .sumOf { it.cards.size },
        )
        assertEquals(
            created + created / 2,
            f.board
                .changes(0)
                .changes.size,
        )
    }

    @Test
    fun aFailedCommitDoesNotBurnIdsOrKeys() {
        val store = MemoryStore(failCommit = { it == 2 })
        val f = Fixture(store = store)
        f.create("a")
        assertFailsWith<InjectedFault> { f.board.create(NewTask("b"), Write(key = "k")) }
        val again = f.board.create(NewTask("b"), Write(key = "k"))
        assertEquals(id("T2"), again.task.value.id)
        assertEquals(false, again.replayed)
    }

    @Test
    fun theJournalStaysContiguousUnderLoad() {
        val f = Fixture(journalKeep = 50)
        val a = f.create("a").value.id
        Race.run(CLIENTS) { c -> repeat(40) { f.board.update(a, Patch(description = "c$c-$it")) } }.forEach { it.getOrThrow() }
        Invariants.check(f.store)
        val page = f.board.changes(f.board.snapshot().seq - 10)
        assertEquals(10, page.changes.size)
        Invariants.assertSequential(page.changes)
    }

    @Test
    fun watchersSeeEveryChangeWhileClientsWrite() {
        val f = Fixture()
        val total = CLIENTS * 20
        val seen = mutableListOf<Change>()
        val writers =
            Race.run(CLIENTS + 1) { c ->
                if (c == 0) {
                    var since = 0L
                    while (seen.size < total) {
                        val page = f.board.changes(since)
                        assertTrue(!page.reset)
                        page.changes.forEach {
                            seen += it
                            since = it.seq
                        }
                    }
                } else {
                    repeat(20) { f.board.create(NewTask("w$c-$it")) }
                }
            }
        writers.forEach { it.getOrThrow() }
        assertEquals((1..total).map { it.toLong() }, seen.map { it.seq })
        assertEquals(total, seen.map { it.id }.toSet().size)
    }

    @Test
    fun lockedOutClientsGetBusy() {
        val store = MemoryStore()
        val board = Boards.open(store, BoardOptions(lockTimeout = 50.milliseconds))
        val e = store.holdingLock { assertFailsWith<BoardException> { board.create(NewTask("a")) } }
        assertEquals("busy", e.code)
        assertTrue(
            board
                .create(NewTask("a"))
                .task.value.id == id("T1"),
        )
    }
}
