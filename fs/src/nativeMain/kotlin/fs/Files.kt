package fs

import kotlinx.io.files.Path

/** Rust `symlink_metadata().file_type()`; null when nothing, not even a dangling link, is there. */
fun entryKind(path: Path): EntryKind? = stat(path, follow = false)?.kind

/** Rust `Path::is_dir`: a directory, following links. */
fun isDir(path: Path): Boolean = stat(path, follow = true)?.kind == EntryKind.Dir

/** The permission bits (`0o777` and above it the set-id and sticky bits), following links; null when missing. */
fun mode(path: Path): Int? = stat(path, follow = true)?.mode

/** The permission bits of [path] itself, a link not followed (Rust `symlink_metadata().permissions()`); null when missing. */
fun linkMode(path: Path): Int? = stat(path, follow = false)?.mode

/** The size of [path] itself, a link not followed (Rust `DirEntry::metadata().len()`); null when missing. */
fun linkSize(path: Path): Long? = stat(path, follow = false)?.size

/** Rust `fs::read_link`: the link's target as written; null when [path] is not a link. */
fun readLink(path: Path): Path? = readLinkText(path)?.let(::Path)

/** Rust `fs::create_dir_all`: every missing directory down to [dir]; an existing directory is fine. */
fun createDirAll(dir: Path) {
    if (isDir(dir)) return
    dir.exactParent?.let { createDirAll(it) }
    if (!makeDir(dir)) {
        val code = lastOsError()
        if (!(osKind(code) == FsException.Kind.AlreadyExists && isDir(dir))) throw FsException.ofErrno(dir, code)
    }
}

/** Entry names of [dir] without `.` and `..`, in the order the system gives them, like `fs::read_dir`. */
fun listDir(dir: Path): List<String> = dirNames(dir) ?: throw FsException.ofErrno(dir)

/** Every byte of [path]. */
internal fun readAll(path: Path): ByteArray = readWhole(path) ?: throw FsException.ofErrno(path)

/** Everything [read] yields into a buffer until it returns 0; negative is a failure, as from read(2). */
internal inline fun readChunks(read: (ByteArray) -> Int): ByteArray? {
    val out = ArrayList<ByteArray>()
    val buf = ByteArray(CHUNK)
    while (true) {
        val n = read(buf)
        if (n < 0) return null
        if (n == 0) break
        out += buf.copyOf(n)
    }
    val all = ByteArray(out.sumOf { it.size })
    var at = 0
    for (chunk in out) {
        chunk.copyInto(all, at)
        at += chunk.size
    }
    return all
}

/** Write the first [size] bytes of [bytes] to [file]; a short write continues where it stopped. */
internal fun writeAll(
    file: WriteHandle,
    bytes: ByteArray,
    path: Path,
    size: Int = bytes.size,
) {
    var at = 0
    while (at < size) {
        val n = file.write(bytes, at, size - at)
        if (n < 0) throw FsException.ofErrno(path)
        at += n
    }
}

/** Throw the last OS error as an error about [path] unless [ok]. */
internal fun ensure(
    ok: Boolean,
    path: Path?,
) {
    if (!ok) throw FsException.ofErrno(path)
}

/** Rust's default mode for a new file, before the umask. */
internal const val NEW_FILE_MODE = 0b110_110_110
internal const val DIR_MODE = 0b111_111_111
private const val CHUNK = 64 * 1024
