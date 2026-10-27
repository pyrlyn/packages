@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package fs

import kotlinx.cinterop.toKString
import kotlinx.io.files.Path
import platform.posix.free
import platform.posix.realpath

/** Rust `fs::canonicalize`: the existing path with every link and `..` resolved; an error for a missing one. */
fun canonicalize(path: Path): Path = realOrNull(path) ?: throw FsException.ofErrno(null)

/** The canonical path when it exists, otherwise [path] as given. */
fun canon(path: Path): Path = realOrNull(path) ?: path

/** `realpath(3)`; null with `errno` set when it fails. */
internal fun realOrNull(path: Path): Path? {
    val real = realpath(path.toString(), null) ?: return null
    try {
        return Path(real.toKString())
    } finally {
        free(real)
    }
}
