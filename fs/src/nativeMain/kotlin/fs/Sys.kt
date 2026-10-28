// The calls fs is built from, one set per system: POSIX in unixMain, Win32 in mingwMain. Each one that fails returns
// null, false or a negative count and leaves its code for lastOsError, as a C call leaves errno.
package fs

import kotlinx.io.files.Path

/** One entry's metadata. [mode] is the permission bits; Windows has none, so it reports a read-only file as 0o444. */
internal class Stat(
    val kind: EntryKind,
    val mode: Int,
    val size: Long,
)

/** A file open for writing. */
internal interface WriteHandle {
    /** Bytes written from [bytes] at [at], at most [len]; negative on failure. */
    fun write(
        bytes: ByteArray,
        at: Int,
        len: Int,
    ): Int

    fun sync(): Boolean

    fun setMode(bits: Int): Boolean

    fun close()
}

/** How [openWrite] creates its file. */
internal enum class Create { New, Truncate }

/** The code the last failed call left: `errno` on Unix, `GetLastError` on Windows (Rust `raw_os_error`). */
internal expect fun lastOsError(): Int

/** The system's own words for [code], as Rust prints them before ` (os error N)`. */
internal expect fun osMessage(code: Int): String

internal expect fun osKind(code: Int): FsException.Kind

/** [path]'s metadata, following a link when [follow]. */
internal expect fun stat(
    path: Path,
    follow: Boolean,
): Stat?

internal expect fun readLinkText(path: Path): String?

internal expect fun makeDir(path: Path): Boolean

/** Entry names without `.` and `..`. */
internal expect fun dirNames(dir: Path): List<String>?

internal expect fun realPath(path: Path): String?

internal expect fun readWhole(path: Path): ByteArray?

internal expect fun openWrite(
    path: Path,
    create: Create,
    mode: Int,
): WriteHandle?

/** Rename, replacing a file at [to]. */
internal expect fun renameEntry(
    from: Path,
    to: Path,
): Boolean

/** Remove a file or a link, never a directory. */
internal expect fun unlinkEntry(path: Path): Boolean

internal expect fun removeDirEntry(path: Path): Boolean

/** Set [path]'s permission bits, following a link; Windows keeps only whether the owner may write. */
internal expect fun chmodEntry(
    path: Path,
    bits: Int,
): Boolean

internal expect fun hardLinkEntry(
    original: Path,
    link: Path,
): Boolean

/** [dir] says whether the link points at a directory, which Windows needs to know and Unix ignores. */
internal expect fun symlinkEntry(
    target: String,
    link: Path,
    dir: Boolean,
): Boolean

/** Fsync a directory, best effort. */
internal expect fun syncDirEntry(dir: Path)

/** Nanoseconds since [path] was last modified, by the wall clock; 0 for a time in the future. */
internal expect fun modifiedAgoNanos(path: Path): Long?

/** Set the modification time to [nanos] before now, the access time unchanged. */
internal expect fun setModifiedAgoNanos(
    path: Path,
    nanos: Long,
): Boolean

/** Set both times to [seconds] since the epoch, following a link. */
internal expect fun setTimesSeconds(
    path: Path,
    seconds: Long,
): Boolean

/** Rust `env::temp_dir`. */
internal expect fun tempRoot(): Path

internal expect fun processId(): Int

/** dunce `simplified`: [path] without a `\\?\` prefix where dropping it keeps the meaning; unchanged off Windows. */
internal expect fun simplified(path: String): String
