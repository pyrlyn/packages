@file:OptIn(ExperimentalForeignApi::class)

package archive

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.toKString
import kotlinx.cinterop.usePinned
import kotlinx.io.IOException
import kotlinx.io.files.Path
import platform.posix.EINTR
import platform.posix.O_CLOEXEC
import platform.posix.O_RDONLY
import platform.posix.errno
import platform.posix.fstat
import platform.posix.open
import platform.posix.pread
import platform.posix.stat
import platform.posix.strerror

internal actual fun openFile(path: Path): OpenFile = UnixFile(path)

private class UnixFile(
    private val path: Path,
) : OpenFile {
    private val fd: Int = open(path.toString(), O_RDONLY or O_CLOEXEC).also { if (it < 0) fail("open", path) }

    override val length: Long =
        memScoped {
            val st = alloc<stat>()
            if (fstat(fd, st.ptr) != 0) {
                platform.posix.close(fd)
                fail("stat", path)
            }
            st.st_size
        }

    override fun readAt(
        into: ByteArray,
        offset: Int,
        len: Int,
        at: Long,
    ): Int {
        while (true) {
            val n = into.usePinned { pread(fd, it.addressOf(offset), len.convert(), at.convert()) }
            if (n >= 0) return n.toInt()
            if (errno != EINTR) fail("read", path)
        }
    }

    override fun close() {
        platform.posix.close(fd)
    }
}

private fun fail(
    what: String,
    path: Path,
): Nothing = throw IOException("cannot $what $path: ${strerror(errno)?.toKString()}")
