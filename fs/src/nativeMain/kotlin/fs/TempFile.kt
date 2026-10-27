@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package fs

import kotlinx.io.files.Path
import platform.posix.EEXIST
import platform.posix.O_CLOEXEC
import platform.posix.O_CREAT
import platform.posix.O_EXCL
import platform.posix.O_WRONLY
import platform.posix.close
import platform.posix.errno
import platform.posix.open
import platform.posix.rename
import platform.posix.unlink
import kotlin.random.Random

/**
 * Rust `tempfile::NamedTempFile`: a fresh file only its owner can read, written piece by piece and then renamed into
 * place with [persist]. Closed without that, it is removed, so a download that fails half way leaves nothing behind.
 */
class TempFile private constructor(
    val path: Path,
    private var fd: Int,
) : AutoCloseable {
    private var persisted = false

    /** Append the first [size] bytes of [bytes]. */
    fun write(
        bytes: ByteArray,
        size: Int = bytes.size,
    ) {
        check(fd >= 0) { "temp file is closed" }
        writeAll(fd, bytes, path, size)
    }

    /** Rename the file to [dest], replacing what is there; it is no longer removed on [close]. */
    fun persist(dest: Path) {
        closeFd()
        ensure(rename(path.toString(), dest.toString()) == 0, dest)
        persisted = true
    }

    // Best effort, as Rust's drop is: a failed cleanup must not hide the error that ended the work.
    override fun close() {
        closeFd()
        if (!persisted) unlink(path.toString())
    }

    private fun closeFd() {
        if (fd >= 0) close(fd)
        fd = -1
    }

    companion object {
        private const val ATTEMPTS = 8
        private const val OWNER_ONLY = 0b110_000_000
        private const val NAME_CHARS = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        private const val NAME_LENGTH = 6

        /** Create one in [dir] named `.tmp` and six random characters; fails as [FsException]. */
        fun createIn(dir: Path): TempFile {
            repeat(ATTEMPTS) {
                val name = ".tmp" + (1..NAME_LENGTH).map { NAME_CHARS[Random.nextInt(NAME_CHARS.length)] }.joinToString("")
                val path = Path(dir, name)
                val fd = retry { open(path.toString(), O_WRONLY or O_CREAT or O_EXCL or O_CLOEXEC, OWNER_ONLY) }
                if (fd >= 0) return TempFile(path, fd)
                if (errno != EEXIST) throw FsException.ofErrno(dir)
            }
            throw FsException.other(dir, "no free temp file name")
        }
    }
}
