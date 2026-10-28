// Single-entry operations Rust's std::fs has and kotlinx-io does not: exclusive create, links, plain removal, modes
// and modification times, which a lock file and an archive extractor are built from.
@file:OptIn(ExperimentalForeignApi::class)

package fs

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.LongVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import kotlinx.io.files.Path
import platform.posix.O_CLOEXEC
import platform.posix.O_CREAT
import platform.posix.O_EXCL
import platform.posix.O_WRONLY
import platform.posix.chmod
import platform.posix.close
import platform.posix.fsync
import platform.posix.link
import platform.posix.mkdir
import platform.posix.open
import platform.posix.rename
import platform.posix.rmdir
import platform.posix.symlink
import platform.posix.unlink
import kotlin.time.Duration
import kotlin.time.Duration.Companion.nanoseconds

/** Rust's default mode for a new file, before the umask. */
private const val NEW_FILE_MODE = 0b110_110_110

/**
 * Create [path] holding [bytes], failing with [FsException.Kind.AlreadyExists] rather than replacing it (Rust
 * `create_new`). [durable] fsyncs the bytes before the file is closed (Rust `sync_all`).
 */
fun createNew(
    path: Path,
    bytes: ByteArray,
    durable: Boolean = false,
) {
    val fd = retry { open(path.toString(), O_WRONLY or O_CREAT or O_EXCL or O_CLOEXEC, NEW_FILE_MODE) }
    ensure(fd >= 0, path)
    try {
        writeAll(fd, bytes, path)
        if (durable) ensure(fsync(fd) == 0, path)
    } finally {
        close(fd)
    }
}

/** Give [original] a second name, [link] (Rust `hard_link`); an existing [link] is [FsException.Kind.AlreadyExists]. */
fun hardLink(
    original: Path,
    link: Path,
) = ensure(link(original.toString(), link.toString()) == 0, link)

/** Create [link] as a symbolic link holding [target], as written (Rust `unix::fs::symlink`); an existing [link] is [FsException.Kind.AlreadyExists]. */
fun symlink(
    target: Path,
    link: Path,
) = ensure(symlink(target.toString(), link.toString()) == 0, link)

/** Create one directory, not its parents (Rust `create_dir`); an existing entry is [FsException.Kind.AlreadyExists]. */
fun createDir(path: Path) = ensure(mkdir(path.toString(), DIR_MODE.convert()) == 0, path)

/** Set [path]'s permission bits to [bits], following a link (Rust `set_permissions` with `from_mode`). */
fun setMode(
    path: Path,
    bits: Int,
) = ensure(chmod(path.toString(), bits.convert()) == 0, path)

/** Remove one file or link, never a directory (Rust `remove_file`); a missing one is [FsException.Kind.NotFound]. */
fun removeFile(path: Path) = ensure(unlink(path.toString()) == 0, path)

/** Remove [path] if it is an empty directory (Rust `remove_dir`); one with anything in it stays and is an error. */
fun removeEmptyDir(path: Path) = ensure(rmdir(path.toString()) == 0, path)

/**
 * Give [from] the name [to] in one `rename(2)`, replacing a file there (Rust `fs::rename`). Unlike kotlinx-io's
 * `atomicMove`, the error keeps its [FsException.kind] and errno, so a caller can tell a passing lock from a refusal.
 */
fun rename(
    from: Path,
    to: Path,
) = ensure(rename(from.toString(), to.toString()) == 0, from)

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

/** Set [path]'s access and modification times to [epochSeconds], following a link (what tar-rs does on unpack). */
fun setTimes(
    path: Path,
    epochSeconds: Long,
) {
    val rc = fs.mtime.kit_set_times(path.toString(), epochSeconds)
    if (rc != 0) throw FsException.ofErrno(path, rc)
}
