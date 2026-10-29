@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.UnsafeNumber::class)

package fs

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.LongVar
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
import kotlinx.cinterop.value
import kotlinx.io.files.Path
import platform.posix.EACCES
import platform.posix.EEXIST
import platform.posix.EINTR
import platform.posix.EINVAL
import platform.posix.EISDIR
import platform.posix.ENOENT
import platform.posix.ENOTDIR
import platform.posix.EPERM
import platform.posix.O_APPEND
import platform.posix.O_CLOEXEC
import platform.posix.O_CREAT
import platform.posix.O_EXCL
import platform.posix.O_RDONLY
import platform.posix.O_TRUNC
import platform.posix.O_WRONLY
import platform.posix.PATH_MAX
import platform.posix.S_IFDIR
import platform.posix.S_IFLNK
import platform.posix.S_IFMT
import platform.posix.S_IFREG
import platform.posix.chmod
import platform.posix.close
import platform.posix.closedir
import platform.posix.errno
import platform.posix.fchmod
import platform.posix.free
import platform.posix.fsync
import platform.posix.getenv
import platform.posix.getpid
import platform.posix.link
import platform.posix.lstat
import platform.posix.mkdir
import platform.posix.open
import platform.posix.opendir
import platform.posix.read
import platform.posix.readdir
import platform.posix.readlink
import platform.posix.realpath
import platform.posix.rename
import platform.posix.rmdir
import platform.posix.set_posix_errno
import platform.posix.strerror
import platform.posix.symlink
import platform.posix.unlink
import platform.posix.write

internal actual fun lastOsError(): Int = errno

internal actual fun osMessage(code: Int): String = strerror(code)?.toKString() ?: "Unknown error $code"

internal actual fun osKind(code: Int): FsException.Kind =
    when (code) {
        ENOENT -> FsException.Kind.NotFound
        EEXIST -> FsException.Kind.AlreadyExists
        EACCES, EPERM -> FsException.Kind.PermissionDenied
        EISDIR -> FsException.Kind.IsADirectory
        ENOTDIR -> FsException.Kind.NotADirectory
        EINVAL -> FsException.Kind.InvalidInput
        else -> FsException.Kind.Other
    }

internal actual fun stat(
    path: Path,
    follow: Boolean,
): Stat? =
    memScoped {
        val st = alloc<platform.posix.stat>()
        val rc = if (follow) platform.posix.stat(path.toString(), st.ptr) else lstat(path.toString(), st.ptr)
        if (rc != 0) return null
        val mode = st.st_mode.toInt()
        val kind =
            when (mode and S_IFMT) {
                S_IFLNK -> EntryKind.Symlink
                S_IFREG -> EntryKind.File
                S_IFDIR -> EntryKind.Dir
                else -> EntryKind.Other
            }
        Stat(kind, mode and MODE_BITS, st.st_size)
    }

internal actual fun readLinkText(path: Path): String? =
    memScoped {
        val buf = allocArray<ByteVar>(PATH_MAX + 1)
        val n = readlink(path.toString(), buf, PATH_MAX.convert()).toInt()
        if (n < 0) null else buf.readBytes(n).decodeToString()
    }

internal actual fun makeDir(path: Path): Boolean = mkdir(path.toString(), DIR_MODE.convert()) == 0

internal actual fun dirNames(dir: Path): List<String>? {
    val d = opendir(dir.toString()) ?: return null
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

internal actual fun realPath(path: Path): String? {
    val real = realpath(path.toString(), null) ?: return null
    try {
        return real.toKString()
    } finally {
        free(real)
    }
}

internal actual fun readWhole(path: Path): ByteArray? {
    val fd = retry { open(path.toString(), O_RDONLY or O_CLOEXEC) }
    if (fd < 0) return null
    try {
        return readChunks { buf -> buf.usePinned { p -> retry { read(fd, p.addressOf(0), buf.size.convert()).toInt() } } }
    } finally {
        close(fd)
    }
}

internal actual fun openWrite(
    path: Path,
    create: Create,
    mode: Int,
): WriteHandle? {
    val how =
        when (create) {
            Create.New -> O_EXCL
            Create.Truncate -> O_TRUNC
            Create.Append -> O_APPEND
        }
    val fd = retry { open(path.toString(), O_WRONLY or O_CREAT or how or O_CLOEXEC, mode) }
    return if (fd < 0) null else FdHandle(fd)
}

private class FdHandle(
    private val fd: Int,
) : WriteHandle {
    override fun write(
        bytes: ByteArray,
        at: Int,
        len: Int,
    ) = bytes.usePinned { p -> retry { write(fd, p.addressOf(at), len.convert()).toInt() } }

    override fun sync() = fsync(fd) == 0

    override fun setMode(bits: Int) = fchmod(fd, bits.convert()) == 0

    override fun close() {
        close(fd)
    }
}

internal actual fun renameEntry(
    from: Path,
    to: Path,
) = rename(from.toString(), to.toString()) == 0

internal actual fun unlinkEntry(path: Path) = unlink(path.toString()) == 0

internal actual fun removeDirEntry(path: Path) = rmdir(path.toString()) == 0

internal actual fun chmodEntry(
    path: Path,
    bits: Int,
) = chmod(path.toString(), bits.convert()) == 0

internal actual fun hardLinkEntry(
    original: Path,
    link: Path,
) = link(original.toString(), link.toString()) == 0

internal actual fun symlinkEntry(
    target: String,
    link: Path,
    dir: Boolean,
) = symlink(target, link.toString()) == 0

internal actual fun syncDirEntry(dir: Path) {
    val fd = retry { open(dir.toString(), O_RDONLY or O_CLOEXEC) }
    if (fd >= 0) {
        fsync(fd)
        close(fd)
    }
}

internal actual fun modifiedAgoNanos(path: Path): Long? =
    memScoped {
        val ago = alloc<LongVar>()
        val rc = fs.mtime.kit_modified_ago(path.toString(), ago.ptr)
        if (succeeded(rc)) ago.value else null
    }

internal actual fun setModifiedAgoNanos(
    path: Path,
    nanos: Long,
) = succeeded(fs.mtime.kit_set_modified_ago(path.toString(), nanos))

internal actual fun setTimesSeconds(
    path: Path,
    seconds: Long,
) = succeeded(fs.mtime.kit_set_times(path.toString(), seconds))

internal actual fun tempRoot(): Path = Path((getenv("TMPDIR")?.toKString() ?: "/tmp").trimEnd('/').ifEmpty { "/" })

internal actual fun processId(): Int = getpid()

internal actual fun simplified(path: String) = path

/** The mtime helpers return their errno rather than set it. */
private fun succeeded(rc: Int): Boolean {
    if (rc != 0) set_posix_errno(rc)
    return rc == 0
}

/** A system call again while a signal interrupts it. */
private inline fun retry(call: () -> Int): Int {
    var r = call()
    while (r < 0 && errno == EINTR) r = call()
    return r
}

private const val MODE_BITS = 0b111_111_111_111

internal actual val keepsModeOnReplace = true

internal actual val linksAreOptional = false

actual val PATH_SEPARATOR = '/'
