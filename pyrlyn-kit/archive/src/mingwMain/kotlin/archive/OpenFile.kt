@file:OptIn(ExperimentalForeignApi::class)

package archive

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import kotlinx.io.IOException
import kotlinx.io.files.Path
import platform.windows.CloseHandle
import platform.windows.CreateFileW
import platform.windows.DWORDVar
import platform.windows.ERROR_HANDLE_EOF
import platform.windows.FILE_ATTRIBUTE_NORMAL
import platform.windows.FILE_SHARE_DELETE
import platform.windows.FILE_SHARE_READ
import platform.windows.GENERIC_READ
import platform.windows.GetFileSizeEx
import platform.windows.GetLastError
import platform.windows.HANDLE
import platform.windows.INVALID_HANDLE_VALUE
import platform.windows.LARGE_INTEGER
import platform.windows.OPEN_EXISTING
import platform.windows.OVERLAPPED
import platform.windows.ReadFile

internal actual fun openFile(path: Path): OpenFile = WindowsFile(path)

private class WindowsFile(
    private val path: Path,
) : OpenFile {
    // Others may read it, and delete or rename it as Unix would let them, while it is open.
    private val handle: HANDLE =
        CreateFileW(
            path.toString(),
            GENERIC_READ,
            (FILE_SHARE_READ or FILE_SHARE_DELETE).convert(),
            null,
            OPEN_EXISTING.convert(),
            FILE_ATTRIBUTE_NORMAL.convert(),
            null,
        ).takeIf { it != INVALID_HANDLE_VALUE } ?: fail("open", path)

    override val length: Long =
        memScoped {
            val size = alloc<LARGE_INTEGER>()
            if (GetFileSizeEx(handle, size.ptr) == 0) {
                val code = GetLastError()
                CloseHandle(handle)
                fail("stat", path, code)
            }
            size.QuadPart
        }

    override fun readAt(
        into: ByteArray,
        offset: Int,
        len: Int,
        at: Long,
    ): Int =
        memScoped {
            // ReadFile on a synchronous handle reads at the OVERLAPPED's offset, as pread does.
            val where = alloc<OVERLAPPED>()
            where.Offset = (at and LOW_32).convert()
            where.OffsetHigh = (at ushr HIGH_SHIFT).convert()
            val read = alloc<DWORDVar>()
            val ok = into.usePinned { ReadFile(handle, it.addressOf(offset), len.convert(), read.ptr, where.ptr) }
            when {
                ok != 0 -> read.value.toInt()
                GetLastError() == ERROR_HANDLE_EOF.toUInt() -> 0
                else -> fail("read", path)
            }
        }

    override fun close() {
        CloseHandle(handle)
    }
}

private const val LOW_32 = 0xFFFF_FFFFL
private const val HIGH_SHIFT = 32

private fun fail(
    what: String,
    path: Path,
    code: UInt = GetLastError(),
): Nothing = throw IOException("cannot $what $path: Windows error $code")
