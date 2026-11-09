package fs

import kotlinx.io.files.Path

/** An open file that holds an exclusive lock until [close]. */
internal interface LockHandle {
    fun close()
}

/**
 * [path] opened, created when missing, with an exclusive lock taken without waiting; null when the open failed or
 * another holder has the lock, told apart by [isLockTaken] on [lastOsError].
 */
internal expect fun tryLockFile(path: Path): LockHandle?

/** Whether [code] says the lock is held elsewhere, rather than that the call failed. */
internal expect fun isLockTaken(code: Int): Boolean

/** Sleep the calling thread for [nanos]. */
internal expect fun pauseNanos(nanos: Long)
