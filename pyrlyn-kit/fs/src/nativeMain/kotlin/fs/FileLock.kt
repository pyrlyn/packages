package fs

import kotlinx.io.files.Path
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

/**
 * An exclusive lock on a file, held until [close]. The lock belongs to the open file, not to the process, so two
 * opens of one path exclude each other, whether they are in two threads or two processes. It does not work on
 * network file systems.
 */
class FileLock private constructor(
    private var handle: LockHandle?,
) : AutoCloseable {
    /** Releases the lock; closing again does nothing. */
    override fun close() {
        handle?.close()
        handle = null
    }

    companion object {
        /** Opens, creating it, [path] and takes the lock without waiting; null when another holder has it. */
        fun tryExclusive(path: String): FileLock? {
            val p = Path(path)
            tryLockFile(p)?.let { return FileLock(it) }
            val code = lastOsError()
            if (isLockTaken(code)) return null
            throw FsException.ofErrno(p, code)
        }

        /** Polls [tryExclusive] every [poll] until [timeout]; null when the lock stays taken. */
        fun exclusive(
            path: String,
            timeout: Duration,
            poll: Duration = 10.milliseconds,
        ): FileLock? {
            val deadline = TimeSource.Monotonic.markNow() + timeout
            while (true) {
                tryExclusive(path)?.let { return it }
                if (deadline.hasPassedNow()) return null
                pauseNanos(minOf(poll, -deadline.elapsedNow()).coerceAtLeast(Duration.ZERO).inWholeNanoseconds)
            }
        }
    }
}
