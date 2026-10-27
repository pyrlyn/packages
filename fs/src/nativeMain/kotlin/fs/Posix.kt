@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package fs

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.pointed
import kotlinx.cinterop.ptr
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.toKString
import kotlinx.cinterop.usePinned
import kotlinx.io.files.Path
import platform.posix.EEXIST
import platform.posix.EINTR
import platform.posix.O_CLOEXEC
import platform.posix.O_RDONLY
import platform.posix.PATH_MAX
import platform.posix.S_IFDIR
import platform.posix.S_IFLNK
import platform.posix.S_IFMT
import platform.posix.S_IFREG
import platform.posix.close
import platform.posix.closedir
import platform.posix.errno
import platform.posix.lstat
import platform.posix.mkdir
import platform.posix.open
import platform.posix.opendir
import platform.posix.read
import platform.posix.readdir
import platform.posix.readlink
import platform.posix.stat
import platform.posix.write

/** Rust `symlink_metadata().file_type()`; null when nothing, not even a dangling link, is there. */
fun entryKind(path: Path): EntryKind? =
    memScoped {
        val st = alloc<stat>()
        if (lstat(path.toString(), st.ptr) != 0) return null
        when (st.st_mode.toInt() and S_IFMT) {
            S_IFLNK -> EntryKind.Symlink
            S_IFREG -> EntryKind.File
            S_IFDIR -> EntryKind.Dir
            else -> EntryKind.Other
        }
    }

/** Rust `Path::is_dir`: a directory, following links. */
fun isDir(path: Path): Boolean = statMode(path)?.let { (it and S_IFMT) == S_IFDIR } ?: false

/** The permission bits (`0o777` and above it the set-id and sticky bits), following links; null when missing. */
fun mode(path: Path): Int? = statMode(path)?.let { it and MODE_BITS }

private fun statMode(path: Path): Int? =
    memScoped {
        val st = alloc<stat>()
        if (stat(path.toString(), st.ptr) == 0) st.st_mode.toInt() else null
    }

/** Rust `fs::read_link`: the link's target as written; null when [path] is not a link. */
fun readLink(path: Path): Path? =
    memScoped {
        val buf = allocArray<ByteVar>(PATH_MAX + 1)
        val n = readlink(path.toString(), buf, PATH_MAX.convert()).toInt()
        if (n < 0) null else Path(buf.readBytes(n).decodeToString())
    }

/** Rust `fs::create_dir_all`: every missing directory down to [dir]; an existing directory is fine. */
fun createDirAll(dir: Path) {
    if (isDir(dir)) return
    dir.parent?.let { createDirAll(it) }
    if (mkdir(dir.toString(), DIR_MODE.convert()) != 0 && !(errno == EEXIST && isDir(dir))) throw FsException.ofErrno(dir)
}

/** Entry names of [dir] without `.` and `..`, in the order the system gives them, like `fs::read_dir`. */
fun listDir(dir: Path): List<String> {
    val d = opendir(dir.toString()) ?: throw FsException.ofErrno(dir)
    try {
        return buildList {
            while (true) {
                val name = readdir(d)?.pointed?.d_name?.toKString() ?: break
                if (name != "." && name != "..") add(name)
            }
        }
    } finally {
        closedir(d)
    }
}

/** Every byte of [path]. */
internal fun readAll(path: Path): ByteArray {
    val fd = retry { open(path.toString(), O_RDONLY or O_CLOEXEC) }
    if (fd < 0) throw FsException.ofErrno(path)
    try {
        return readFd(fd, path)
    } finally {
        close(fd)
    }
}

private fun readFd(
    fd: Int,
    path: Path,
): ByteArray {
    val out = ArrayList<ByteArray>()
    val buf = ByteArray(CHUNK)
    while (true) {
        val n = buf.usePinned { p -> retry { read(fd, p.addressOf(0), CHUNK.convert()).toInt() } }
        if (n < 0) throw FsException.ofErrno(path)
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

/** Write the first [size] bytes of [bytes] to [fd]; a short write continues where it stopped. */
internal fun writeAll(
    fd: Int,
    bytes: ByteArray,
    path: Path,
    size: Int = bytes.size,
) {
    var at = 0
    while (at < size) {
        val n = bytes.usePinned { p -> retry { write(fd, p.addressOf(at), (size - at).convert()).toInt() } }
        if (n < 0) throw FsException.ofErrno(path)
        at += n
    }
}

/** Throw the current `errno` as an error about [path] unless [ok]. */
internal fun ensure(
    ok: Boolean,
    path: Path?,
) {
    if (!ok) throw FsException.ofErrno(path)
}

/** A system call again while a signal interrupts it. */
internal inline fun retry(call: () -> Int): Int {
    var r = call()
    while (r < 0 && errno == EINTR) r = call()
    return r
}

private const val MODE_BITS = 0b111_111_111_111
internal const val DIR_MODE = 0b111_111_111
private const val CHUNK = 64 * 1024
