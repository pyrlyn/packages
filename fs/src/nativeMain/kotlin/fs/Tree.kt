@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package fs

import kotlinx.cinterop.convert
import kotlinx.io.files.Path
import platform.posix.O_CLOEXEC
import platform.posix.O_CREAT
import platform.posix.O_TRUNC
import platform.posix.O_WRONLY
import platform.posix.close
import platform.posix.fchmod
import platform.posix.open
import platform.posix.rmdir
import platform.posix.symlink
import platform.posix.unlink

/** Copy the directory [src] into [dest], creating [dest]. A regular file keeps its mode. */
fun copyTree(
    src: Path,
    dest: Path,
    links: Links,
) {
    try {
        createDirAll(dest)
    } catch (e: FsException) {
        throw e.at(dest)
    }
    for (name in listDir(src)) {
        val from = Path(src, name)
        val to = Path(dest, name)
        val kind = entryKind(from) ?: throw FsException.ofErrno(from)
        val link = kind == EntryKind.Symlink
        when {
            link && links == Links.Skip -> Unit

            link && links == Links.Keep -> keepLink(from, to)

            // A followed link to a directory is a directory to copy, not a file.
            kind == EntryKind.Dir || (link && isDir(from)) -> copyTree(from, to, links)

            else -> copyFile(from, to)
        }
    }
}

private fun keepLink(
    from: Path,
    to: Path,
) {
    val target = readLink(from) ?: throw FsException.ofErrno(from)
    if (symlink(target.toString(), to.toString()) != 0) throw FsException.ofErrno(to)
}

/** Rust `fs::copy`: the bytes and the permission bits, following a link on the source side. */
fun copyFile(
    from: Path,
    to: Path,
) {
    val bytes = readAll(from)
    val bits = mode(from) ?: throw FsException.ofErrno(from)
    val fd = retry { open(to.toString(), O_WRONLY or O_CREAT or O_TRUNC or O_CLOEXEC, bits) }
    ensure(fd >= 0, to)
    try {
        writeAll(fd, bytes, to)
        ensure(fchmod(fd, bits.convert()) == 0, to)
    } finally {
        close(fd)
    }
}

/**
 * Remove whatever is at [path] (file, symlink or directory tree) without following a link. Nothing there is
 * success, so a retried cleanup does not fail on what the first one did.
 */
fun removeAny(path: Path) {
    when (entryKind(path)) {
        null -> Unit
        EntryKind.Dir -> removeDir(path)
        else -> removeEntry(path) { unlink(it) }
    }
}

private fun removeDir(dir: Path) {
    val names =
        try {
            listDir(dir)
        } catch (e: FsException) {
            if (e.kind == FsException.Kind.NotFound) return
            throw e
        }
    names.forEach { removeAny(Path(dir, it)) }
    removeEntry(dir) { rmdir(it) }
}

private inline fun removeEntry(
    path: Path,
    remove: (String) -> Int,
) {
    if (remove(path.toString()) == 0) return
    val e = FsException.ofErrno(path)
    if (e.kind != FsException.Kind.NotFound) throw e
}
