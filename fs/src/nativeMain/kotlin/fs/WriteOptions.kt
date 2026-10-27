@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlin.concurrent.atomics.ExperimentalAtomicApi::class)

package fs

import kotlinx.cinterop.convert
import kotlinx.io.files.Path
import platform.posix.EEXIST
import platform.posix.ENOENT
import platform.posix.O_CLOEXEC
import platform.posix.O_CREAT
import platform.posix.O_EXCL
import platform.posix.O_RDONLY
import platform.posix.O_WRONLY
import platform.posix.chmod
import platform.posix.close
import platform.posix.errno
import platform.posix.fsync
import platform.posix.getpid
import platform.posix.open
import platform.posix.rename
import platform.posix.unlink
import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.fetchAndIncrement
import kotlin.random.Random

/**
 * How [write] replaces a file: the new bytes go to a sibling temp file that is renamed over the target, so a reader,
 * a crash or a full disk never sees a half-written file. The defaults are the safe ones.
 */
data class WriteOptions(
    /**
     * Fsync the file before the rename and its directory after it. Without the file fsync the kernel may record
     * the rename and lose the bytes, leaving an empty file after a crash.
     */
    val durable: Boolean = true,
    /** Copy an existing target's permissions onto the new file, so a 0600 file stays 0600. */
    val preservePermissions: Boolean = true,
    /** When the target is a symlink, write the file it points to and keep the link; off replaces the link itself. */
    val followSymlinks: Boolean = true,
    /** Create the target's missing parent directories. */
    val createParent: Boolean = false,
    /** Mode of a new file; an existing target's permissions still win while [preservePermissions] is on. */
    val mode: Int? = null,
) {
    /** Replace [path] with [bytes]. */
    fun write(
        path: Path,
        bytes: ByteArray,
    ) {
        try {
            replace(path, bytes)
        } catch (e: FsException) {
            throw e.at(path)
        }
    }

    private fun replace(
        path: Path,
        bytes: ByteArray,
    ) {
        if (path.name in NO_FILE_NAMES) throw FsException(null, FsException.Kind.InvalidInput, null, "names no file")
        if (createParent) createDirAll(parentOf(path))
        val target = if (followSymlinks) linkTarget(path) else path
        val (tmp, fd) = createTemp(target)
        try {
            fillAndSwap(target, tmp, fd, bytes)
        } catch (e: FsException) {
            unlink(tmp.toString())
            throw e
        } finally {
            close(fd)
        }
    }

    private fun fillAndSwap(
        target: Path,
        tmp: Path,
        fd: Int,
        bytes: ByteArray,
    ) {
        writeAll(fd, bytes, tmp)
        if (preservePermissions) keepPermissions(target, tmp)
        if (durable && fsync(fd) != 0) throw FsException.ofErrno(null)
        if (rename(tmp.toString(), target.toString()) != 0) throw FsException.ofErrno(null)
        // Best effort: some filesystems refuse to fsync a directory, and the bytes are already safe by then.
        if (durable) syncDir(parentOf(target))
    }

    /**
     * A fresh temp file beside [target], created exclusively with the pid, a random part and a process-wide counter
     * in its name, so two writers never share a file and a leftover from a crashed run is never reused.
     */
    private fun createTemp(target: Path): Pair<Path, Int> {
        val dir = parentOf(target)
        repeat(TEMP_ATTEMPTS) {
            val tmp = Path(dir, ".${target.name}.tmp-${getpid()}-${Random.nextInt().toUInt()}-${counter.fetchAndIncrement()}")
            val fd = retry { open(tmp.toString(), O_WRONLY or O_CREAT or O_EXCL or O_CLOEXEC, mode ?: NEW_FILE_MODE) }
            if (fd >= 0) return tmp to fd
            if (errno != EEXIST) throw FsException.ofErrno(null)
        }
        throw FsException.other(null, "no free temp file name")
    }

    private companion object {
        val counter = AtomicLong(0)
        const val TEMP_ATTEMPTS = 8
        const val NEW_FILE_MODE = 0b110_110_110
        val NO_FILE_NAMES = setOf("", ".", "..")
    }
}

/** Replace [path] with [bytes] using the default [WriteOptions]. */
fun atomic(
    path: Path,
    bytes: ByteArray,
) = WriteOptions().write(path, bytes)

/** Write [bytes] to [path] unless it already holds exactly them, creating the parent; true when it wrote. */
fun ifChanged(
    path: Path,
    bytes: ByteArray,
): Boolean {
    if (bytesIfExists(path)?.contentEquals(bytes) == true) return false
    WriteOptions(createParent = true).write(path, bytes)
    return true
}

/** Fsync a directory so a rename or create inside it survives a crash; failures are ignored. */
fun syncDir(dir: Path) {
    val fd = retry { open(dir.toString(), O_RDONLY or O_CLOEXEC) }
    if (fd >= 0) {
        fsync(fd)
        close(fd)
    }
}

/** `Path::parent`, where a bare file name's parent is the current directory. */
internal fun parentOf(path: Path): Path = path.parent?.takeIf { it.toString().isNotEmpty() } ?: Path(".")

/**
 * The file [path] resolves to. `realpath` fails on a dangling link (a dotfile manager's link whose target is not
 * there yet), and the rename would then replace the link with a plain file; following the links by hand writes the
 * target and keeps the link. A cycle is an error: replacing one of its links would silently break the others.
 */
private fun linkTarget(path: Path): Path {
    var p = path
    repeat(MAX_LINKS) {
        realOrNull(p)?.let { return it }
        val next = readLink(p) ?: return p
        p = if (next.isAbsolute) next else Path(parentOf(p), next.toString())
    }
    throw FsException.other(null, "too many levels of symbolic links")
}

private fun keepPermissions(
    target: Path,
    tmp: Path,
) {
    val bits = mode(target)
    if (bits == null) {
        if (errno == ENOENT) return
        throw FsException.ofErrno(null)
    }
    if (chmod(tmp.toString(), bits.convert()) != 0) throw FsException.ofErrno(null)
}

/** The kernel's own ELOOP limit. */
private const val MAX_LINKS = 40
