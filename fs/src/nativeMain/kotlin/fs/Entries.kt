// Single-entry operations Rust's std::fs has and kotlinx-io does not: exclusive create, hard links, plain removal
// and modification times, which a lock file is built from.
@file:OptIn(ExperimentalForeignApi::class)

package fs

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.LongVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import kotlinx.io.files.Path
import platform.posix.O_CLOEXEC
import platform.posix.O_CREAT
import platform.posix.O_EXCL
import platform.posix.O_WRONLY
import platform.posix.close
import platform.posix.link
import platform.posix.open
import platform.posix.unlink
import kotlin.time.Duration
import kotlin.time.Duration.Companion.nanoseconds

/** Rust's default mode for a new file, before the umask. */
private const val NEW_FILE_MODE = 0b110_110_110

/** Create [path] holding [bytes], failing with [FsException.Kind.AlreadyExists] rather than replacing it (Rust `create_new`). */
fun createNew(
    path: Path,
    bytes: ByteArray,
) {
    val fd = retry { open(path.toString(), O_WRONLY or O_CREAT or O_EXCL or O_CLOEXEC, NEW_FILE_MODE) }
    ensure(fd >= 0, path)
    try {
        writeAll(fd, bytes, path)
    } finally {
        close(fd)
    }
}

/** Give [original] a second name, [link] (Rust `hard_link`); an existing [link] is [FsException.Kind.AlreadyExists]. */
fun hardLink(
    original: Path,
    link: Path,
) = ensure(link(original.toString(), link.toString()) == 0, link)

/** Remove one file or link, never a directory (Rust `remove_file`); a missing one is [FsException.Kind.NotFound]. */
fun removeFile(path: Path) = ensure(unlink(path.toString()) == 0, path)

/** How long ago [path] was last modified, by the wall clock; zero for a time in the future (Rust `modified()?.elapsed()`). */
fun modifiedAgo(path: Path): Duration =
    memScoped {
        val ago = alloc<LongVar>()
        val rc = fs.mtime.kit_modified_ago(path.toString(), ago.ptr)
        if (rc != 0) throw FsException.ofErrno(path, rc)
        ago.value.nanoseconds
    }

/** Set [path]'s modification time to [ago] before now, its access time unchanged (Rust `File::set_modified`). */
fun setModifiedAgo(
    path: Path,
    ago: Duration,
) {
    val rc = fs.mtime.kit_set_modified_ago(path.toString(), ago.inWholeNanoseconds)
    if (rc != 0) throw FsException.ofErrno(path, rc)
}
