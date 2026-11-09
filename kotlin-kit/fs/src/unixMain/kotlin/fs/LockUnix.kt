@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.UnsafeNumber::class)

package fs

import kotlinx.cinterop.convert
import kotlinx.io.files.Path
import platform.posix.EWOULDBLOCK
import platform.posix.O_CLOEXEC
import platform.posix.O_CREAT
import platform.posix.O_RDWR
import platform.posix.close
import platform.posix.open
import platform.posix.set_posix_errno
import platform.posix.usleep

internal actual fun tryLockFile(path: Path): LockHandle? {
    val fd = retry { open(path.toString(), O_RDWR or O_CREAT or O_CLOEXEC, LOCK_FILE_MODE) }
    return if (fd < 0) null else lockedOrClosed(fd)
}

/** flock belongs to the open file description, so a second open in this process is excluded like another process. */
private fun lockedOrClosed(fd: Int): LockHandle? {
    val code = fs.mtime.kit_flock_try(fd)
    if (code == 0) return FdLock(fd)
    close(fd)
    set_posix_errno(code)
    return null
}

private class FdLock(
    private val fd: Int,
) : LockHandle {
    override fun close() {
        close(fd)
    }
}

internal actual fun isLockTaken(code: Int) = code == EWOULDBLOCK

internal actual fun pauseNanos(nanos: Long) {
    usleep((nanos / NANOS_PER_MICRO).convert())
}

private const val LOCK_FILE_MODE = 0b110_100_100
private const val NANOS_PER_MICRO = 1000L
