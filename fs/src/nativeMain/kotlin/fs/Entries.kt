// Single-entry operations Rust's std::fs has and kotlinx-io does not: exclusive create, links, plain removal, modes
// and modification times, which a lock file and an archive extractor are built from.
package fs

import kotlinx.io.files.Path
import kotlin.time.Duration
import kotlin.time.Duration.Companion.nanoseconds

/**
 * Create [path] holding [bytes], failing with [FsException.Kind.AlreadyExists] rather than replacing it (Rust
 * `create_new`). [durable] fsyncs the bytes before the file is closed (Rust `sync_all`).
 */
fun createNew(
    path: Path,
    bytes: ByteArray,
    durable: Boolean = false,
) {
    val file = openWrite(path, Create.New, NEW_FILE_MODE) ?: throw FsException.ofErrno(path)
    try {
        writeAll(file, bytes, path)
        if (durable) ensure(file.sync(), path)
    } finally {
        file.close()
    }
}

/** Give [original] a second name, [link] (Rust `hard_link`); an existing [link] is [FsException.Kind.AlreadyExists]. */
fun hardLink(
    original: Path,
    link: Path,
) = ensure(hardLinkEntry(original, link), link)

/**
 * Create [link] as a symbolic link holding [target], as written (Rust `unix::fs::symlink`); an existing [link] is
 * [FsException.Kind.AlreadyExists]. Windows makes a directory link when [target], read from [link]'s directory, is
 * a directory (Rust `symlink_dir`, otherwise `symlink_file`).
 */
fun symlink(
    target: Path,
    link: Path,
) {
    val resolved = if (target.isAbsolute) target else joinPath(parentOf(link), target.toString())
    ensure(symlinkEntry(target.toString(), link, isDir(resolved)), link)
}

/** Create one directory, not its parents (Rust `create_dir`); an existing entry is [FsException.Kind.AlreadyExists]. */
fun createDir(path: Path) = ensure(makeDir(path), path)

/** Set [path]'s permission bits to [bits], following a link (Rust `set_permissions` with `from_mode`). */
fun setMode(
    path: Path,
    bits: Int,
) = ensure(chmodEntry(path, bits), path)

/** Remove one file or link, never a directory (Rust `remove_file`); a missing one is [FsException.Kind.NotFound]. */
fun removeFile(path: Path) = ensure(unlinkEntry(path), path)

/** Remove [path] if it is an empty directory (Rust `remove_dir`); one with anything in it stays and is an error. */
fun removeEmptyDir(path: Path) = ensure(removeDirEntry(path), path)

/**
 * Give [from] the name [to] in one `rename(2)`, replacing a file there (Rust `fs::rename`). Unlike kotlinx-io's
 * `atomicMove`, the error keeps its [FsException.kind] and errno, so a caller can tell a passing lock from a refusal.
 */
fun rename(
    from: Path,
    to: Path,
) = ensure(renameEntry(from, to), from)

/** How long ago [path] was last modified, by the wall clock; zero for a time in the future (Rust `modified()?.elapsed()`). */
fun modifiedAgo(path: Path): Duration = (modifiedAgoNanos(path) ?: throw FsException.ofErrno(path)).nanoseconds

/** Set [path]'s modification time to [ago] before now, its access time unchanged (Rust `File::set_modified`). */
fun setModifiedAgo(
    path: Path,
    ago: Duration,
) {
    ensure(setModifiedAgoNanos(path, ago.inWholeNanoseconds), path)
}

/** Set [path]'s access and modification times to [epochSeconds], following a link (what tar-rs does on unpack). */
fun setTimes(
    path: Path,
    epochSeconds: Long,
) {
    ensure(setTimesSeconds(path, epochSeconds), path)
}
