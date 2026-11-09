package pyrlyn.taskboard

import pyrlyn.taskboard.testing.FakeClock
import pyrlyn.taskboard.testing.MemoryStore
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/** One board over a [MemoryStore] on a [FakeClock], the way every engine test starts. */
class Fixture(
    val store: MemoryStore = MemoryStore(),
    val clock: FakeClock = FakeClock(),
    lease: Duration = 30.minutes,
    liveness: ((String) -> Boolean?)? = null,
    journalKeep: Int = 10_000,
    keyTtl: Duration = 24.hours,
) {
    val board: Board = Boards.open(store, BoardOptions(clock = clock, idPrefix = "T", lease = lease, liveness = liveness, journalKeep = journalKeep, keyTtl = keyTtl))

    fun create(
        title: String = "task",
        write: Write = Write(),
        build: (NewTask) -> NewTask = { it },
    ): Versioned<Task> = board.create(build(NewTask(title)), write).task

    fun get(id: TaskId) = board.get(id)
}

fun id(raw: String) = TaskId.parse(raw)

fun code(block: () -> Unit): String = assertFailsWith<BoardException>(block = block).code

fun fail(block: () -> Unit): BoardException = assertFailsWith<BoardException>(block = block)

fun assertCode(
    expected: String,
    block: () -> Unit,
) = assertEquals(expected, code(block))
