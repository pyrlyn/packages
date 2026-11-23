package pyrlyn.taskboard.testing

import kotlin.concurrent.Volatile
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant

/** A clock that moves only when a test says so. */
class FakeClock(
    start: Instant = Instant.fromEpochSeconds(DEFAULT_START),
) : Clock {
    @Volatile
    private var current = start

    override fun now(): Instant = current

    fun advance(by: Duration) {
        current += by
    }
}

private const val DEFAULT_START = 1_700_000_000L
