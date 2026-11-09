package pyrlyn.taskboard.testing

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import pyrlyn.taskboard.Change
import pyrlyn.taskboard.Status
import pyrlyn.taskboard.Store
import pyrlyn.taskboard.TaskId
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Runs clients at the same moment on real threads and reports what each of them did. */
object Race {
    /**
     * Starts [clients] workers behind one gate and opens it, so they all hit the board together. The blocking calls
     * of a [pyrlyn.taskboard.Board] run on `Dispatchers.Default` threads, one per client up to the machine's cores.
     */
    fun <T> run(
        clients: Int,
        body: (client: Int) -> T,
    ): List<Result<T>> =
        runBlocking {
            val gate = CompletableDeferred<Unit>()
            val jobs =
                (0 until clients).map { c ->
                    async(Dispatchers.Default) {
                        gate.await()
                        runCatching { body(c) }
                    }
                }
            gate.complete(Unit)
            jobs.awaitAll()
        }

    /** [run] [rounds] times, each round with a fresh gate; [body] gets the round too. */
    fun <T> rounds(
        rounds: Int,
        clients: Int,
        body: (round: Int, client: Int) -> T,
    ): List<List<Result<T>>> = (0 until rounds).map { r -> run(clients) { c -> body(r, c) } }
}

/** What must hold on any board at any moment a lock is not held, whatever raced before. */
object Invariants {
    fun check(store: Store) {
        val r = store.snapshot()
        val tasks = r.tasks().map { it.value }
        assertEquals(tasks.size, tasks.map { it.id }.toSet().size, "two tasks share an id")
        val journal =
            r
                .state("journal")
                .orEmpty()
                .lineSequence()
                .filter { it.isNotBlank() }
                .map { pyrlyn.taskboard.Codec.changeFromJson(pyrlyn.taskboard.Codec.parseObject(it)) }
                .toList()
        assertSequential(journal)
        val index = r.state("index")?.let { pyrlyn.taskboard.Codec.parseObject(it) }
        if (index != null) {
            val seq = (index["seq"] as kotlinx.serialization.json.JsonPrimitive).content.toLong()
            assertEquals(seq, journal.lastOrNull()?.seq ?: seq, "the index's seq is not the journal's last")
            val first = (index["first"] as kotlinx.serialization.json.JsonPrimitive).content.toLong()
            assertEquals(first, journal.firstOrNull()?.seq ?: first, "the index's first is not the journal's first")
            val known = (index["tasks"] as kotlinx.serialization.json.JsonObject).keys.map(TaskId::parse).toSet()
            assertEquals(tasks.map { it.id }.toSet(), known, "the index and the board hold different tasks")
        }
        val leases =
            r
                .state("leases")
                ?.let { pyrlyn.taskboard.Codec.parseObject(it) }
                ?.keys
                ?.map(TaskId::parse)
                .orEmpty()
                .toSet()
        for (t in tasks) if (t.status == Status.InProgress) assertTrue(t.assignee != null, "${t.id} is in progress with no assignee")
        for (id in leases) assertTrue(tasks.any { it.id == id && it.status == Status.InProgress }, "$id has a lease but is not in progress")
    }

    /** Journal seqs rise by one from entry to entry. */
    fun assertSequential(changes: List<Change>) {
        changes.zipWithNext().forEach { (a, b) -> assertEquals(a.seq + 1, b.seq, "journal seq jumps from ${a.seq} to ${b.seq}") }
    }
}
