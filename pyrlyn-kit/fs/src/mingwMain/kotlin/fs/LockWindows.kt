@file:OptIn(ExperimentalForeignApi::class)

package fs

import fs.win.kit_CreateFileW
import fs.win.kit_LockFileEx
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.convert
import kotlinx.io.files.Path
import platform.windows.CloseHandle
import platform.windows.FILE_ATTRIBUTE_NORMAL
import platform.windows.GENERIC_READ
import platform.windows.GENERIC_WRITE
import platform.windows.HANDLE
import platform.windows.LOCKFILE_EXCLUSIVE_LOCK
import platform.windows.LOCKFILE_FAIL_IMMEDIATELY
import platform.windows.OPEN_ALWAYS
import platform.windows.Sleep

internal actual fun tryLockFile(path: Path): LockHandle? {
    val access = GENERIC_READ.convert<UInt>() or GENERIC_WRITE.convert<UInt>()
    val h = opened(kit_CreateFileW(path.win, access, SHARE_ALL, OPEN_ALWAYS.convert(), FILE_ATTRIBUTE_NORMAL.convert()))
    return if (h == null) null else lockedOrClosed(h)
}

private fun lockedOrClosed(h: HANDLE): LockHandle? {
    if (kit_LockFileEx(h, (LOCKFILE_EXCLUSIVE_LOCK or LOCKFILE_FAIL_IMMEDIATELY).convert()) != 0) return HandleLock(h)
    // The shim already holds the code, so closing the handle cannot replace it.
    CloseHandle(h)
    return failed()
}

private class HandleLock(
    private val handle: HANDLE,
) : LockHandle {
    override fun close() {
        CloseHandle(handle)
    }
}

internal actual fun isLockTaken(code: Int) = code == ERROR_LOCK_VIOLATION

internal actual fun pauseNanos(nanos: Long) {
    Sleep((nanos / NANOS_PER_MILLI).coerceAtLeast(1).convert())
}

// winerror.h
private const val ERROR_LOCK_VIOLATION = 33
private const val NANOS_PER_MILLI = 1_000_000L
