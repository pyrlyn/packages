package pyrlyn.taskboard.files

import pyrlyn.taskboard.BoardOptions
import pyrlyn.taskboard.Boards
import pyrlyn.taskboard.NewTask
import pyrlyn.taskboard.Patch
import pyrlyn.taskboard.Status
import pyrlyn.taskboard.testing.FakeClock
import pyrlyn.taskboard.testing.Invariants
import pyrlyn.taskboard.testing.Race
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

private const val CLIENTS = 6

/** Each client has a `FileStore` and a `Board` of its own, as separate processes would. */
private fun boardOn(dir: String) = Boards.open(FileStore(dir), BoardOptions(clock = FakeClock(), lockTimeout = 60.seconds))

class RaceOverFilesTest {
    @Test
    fun everyReadyTaskIsClaimedByExactlyOneClient() =
        withBoard { dir ->
            val tasks = 24
            repeat(tasks) { boardOn(dir).create(NewTask("t$it")) }
            val results = Race.run(CLIENTS) { c -> generateSequence { boardOn(dir).claim(null, "agent-$c") }.map { it.task.value.id }.toList() }
            val claimed = results.flatMap { it.getOrThrow() }
            assertEquals(tasks, claimed.size)
            assertEquals(tasks, claimed.toSet().size)
            val store = FileStore(dir)
            Invariants.check(store)
            assertEquals(emptyList(), store.snapshot().warnings())
            assertEquals(tasks, store.snapshot().tasks().count { it.value.status == Status.InProgress })
        }

    @Test
    fun concurrentCreatesAndUpdatesKeepEveryTaskAndTheJournalWhole() =
        withBoard { dir ->
            val results =
                Race.run(CLIENTS) { c ->
                    val board = boardOn(dir)
                    (0 until 6).map { n ->
                        val id =
                            board
                                .create(NewTask("c$c-$n"))
                                .task.value.id
                        board.update(id, Patch(description = "by client $c"))
                        id
                    }
                }
            val ids = results.flatMap { it.getOrThrow() }
            assertEquals(CLIENTS * 6, ids.toSet().size)
            val store = FileStore(dir)
            Invariants.check(store)
            assertEquals(emptyList(), store.snapshot().warnings())
            assertEquals(
                ids.toSet(),
                store
                    .snapshot()
                    .tasks()
                    .map { it.value.id }
                    .toSet(),
            )
            assertEquals(CLIENTS * 6, store.snapshot().tasks().count { it.value.description.startsWith("by client") })
        }
}
