@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package fs

import kotlinx.cinterop.toKString
import kotlinx.io.IOException
import kotlinx.io.files.Path
import platform.posix.EACCES
import platform.posix.EEXIST
import platform.posix.EINVAL
import platform.posix.EISDIR
import platform.posix.ENOENT
import platform.posix.ENOTDIR
import platform.posix.EPERM
import platform.posix.errno
import platform.posix.strerror

/**
 * A failed file operation (fs-kit's `io::Error` with a `PathError` inside). [path] is where it happened, so a
 * caller with its own path-carrying error prints the path once; [kind] and [errno] are the original error's.
 */
class FsException(
    val path: Path?,
    val kind: Kind,
    val errno: Int?,
    val detail: String,
) : IOException(if (path == null) detail else "$path: $detail") {
    enum class Kind { NotFound, AlreadyExists, PermissionDenied, IsADirectory, NotADirectory, InvalidInput, InvalidData, Other }

    /** The same error at [at]. */
    fun at(at: Path) = FsException(at, kind, errno, detail)

    /** The original error without its path (fs-kit `split`). */
    fun withoutPath() = FsException(null, kind, errno, detail)

    companion object {
        /** The error `errno` names, worded as Rust prints an OS error: `No such file or directory (os error 2)`. */
        fun ofErrno(
            path: Path?,
            code: Int = errno,
        ) = FsException(path, kindOf(code), code, "${strerror(code)?.toKString()} (os error $code)")

        fun other(
            path: Path?,
            detail: String,
        ) = FsException(path, Kind.Other, null, detail)

        private fun kindOf(code: Int) =
            when (code) {
                ENOENT -> Kind.NotFound
                EEXIST -> Kind.AlreadyExists
                EACCES, EPERM -> Kind.PermissionDenied
                EISDIR -> Kind.IsADirectory
                ENOTDIR -> Kind.NotADirectory
                EINVAL -> Kind.InvalidInput
                else -> Kind.Other
            }
    }
}
