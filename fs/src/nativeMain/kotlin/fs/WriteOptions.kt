@file:OptIn(kotlin.concurrent.atomics.ExperimentalAtomicApi::class)

package fs

import kotlinx.io.files.Path
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
        val (tmp, file) = createTemp(target)
        try {
            fill(target, tmp, file, bytes)
        } catch (e: FsException) {
            file.close()
            unlinkEntry(tmp)
            throw e
        }
        // Windows renames no file that is still open, so the handle is closed first.
        file.close()
        swap(target, tmp)
    }

    private fun fill(
        target: Path,
        tmp: Path,
        file: WriteHandle,
        bytes: ByteArray,
    ) {
        writeAll(file, bytes, tmp)
        if (preservePermissions) keepPermissions(target, tmp)
        if (durable && !file.sync()) throw FsException.ofErrno(null)
    }

    private fun swap(
        target: Path,
        tmp: Path,
    ) {
        if (!renameEntry(tmp, target)) {
            val e = FsException.ofErrno(null)
            unlinkEntry(tmp)
            throw e
        }
        // Best effort: some filesystems refuse to fsync a directory, and the bytes are already safe by then.
        if (durable) syncDir(parentOf(target))
    }

    /**
     * A fresh temp file beside [target], created exclusively with the pid, a random part and a process-wide counter
     * in its name, so two writers never share a file and a leftover from a crashed run is never reused.
     */
    private fun createTemp(target: Path): Pair<Path, WriteHandle> {
        val dir = parentOf(target)
        repeat(TEMP_ATTEMPTS) {
            val tmp = Path(dir, ".${target.name}.tmp-${processId()}-${Random.nextInt().toUInt()}-${counter.fetchAndIncrement()}")
            openWrite(tmp, Create.New, mode ?: NEW_FILE_MODE)?.let { return tmp to it }
            val code = lastOsError()
            if (osKind(code) != FsException.Kind.AlreadyExists) throw FsException.ofErrno(null, code)
        }
        throw FsException.other(null, "no free temp file name")
    }

    private companion object {
        val counter = AtomicLong(0)
        const val TEMP_ATTEMPTS = 8
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
fun syncDir(dir: Path) = syncDirEntry(dir)

/** `Path::parent`, where a bare file name's parent is the current directory. */
internal fun parentOf(path: Path): Path = path.exactParent?.takeIf { it.toString().isNotEmpty() } ?: Path(".")

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
    // Windows: a replace cannot land on a read-only file, so the target loses that bit and the new file never gets
    // it (fs-kit's keep_permissions there). Elsewhere the new file takes the target's bits.
    val ok =
        when {
            bits == null -> osKind(lastOsError()) == FsException.Kind.NotFound
            keepsModeOnReplace -> chmodEntry(tmp, bits)
            else -> bits and OWNER_WRITE != 0 || chmodEntry(target, bits or OWNER_WRITE)
        }
    if (!ok) throw FsException.ofErrno(null)
}

/** Whether a replaced file's mode carries over to its replacement: false on Windows. */
internal expect val keepsModeOnReplace: Boolean

private const val OWNER_WRITE = 0b010_000_000

/** The kernel's own ELOOP limit. */
private const val MAX_LINKS = 40
