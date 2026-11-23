package fs

import kotlinx.io.IOException
import kotlinx.io.files.Path

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
        /**
         * The OS error [code] (`errno`, on Windows `GetLastError`), worded as Rust prints one: `No such file or
         * directory (os error 2)`.
         */
        fun ofErrno(
            path: Path?,
            code: Int = lastOsError(),
        ) = FsException(path, osKind(code), code, "${osMessage(code)} (os error $code)")

        fun other(
            path: Path?,
            detail: String,
        ) = FsException(path, Kind.Other, null, detail)
    }
}
