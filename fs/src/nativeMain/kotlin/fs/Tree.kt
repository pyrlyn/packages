package fs

import kotlinx.io.files.Path

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

            link && links == Links.Keep && keepLink(from, to) -> Unit

            // A followed link to a directory is a directory to copy, not a file.
            kind == EntryKind.Dir || (link && isDir(from)) -> copyTree(from, to, links)

            else -> copyFile(from, to)
        }
    }
}

/** Recreate the link [from] at [to]; false when Windows refuses (no Developer Mode), so the caller copies. */
private fun keepLink(
    from: Path,
    to: Path,
): Boolean {
    val target = readLinkText(from) ?: throw FsException.ofErrno(from)
    if (symlinkEntry(target, to, isDir(from))) return true
    if (linksAreOptional) return false
    throw FsException.ofErrno(to)
}

/** Whether a link that cannot be made is copied instead, as fs-kit does on Windows. */
internal expect val linksAreOptional: Boolean

/** Rust `fs::copy`: the bytes and the permission bits, following a link on the source side. */
fun copyFile(
    from: Path,
    to: Path,
) {
    val bytes = readAll(from)
    val bits = mode(from) ?: throw FsException.ofErrno(from)
    val file = openWrite(to, Create.Truncate, bits) ?: throw FsException.ofErrno(to)
    try {
        writeAll(file, bytes, to)
        ensure(file.setMode(bits), to)
    } finally {
        file.close()
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
        else -> removeEntry(path) { unlinkEntry(it) }
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
    removeEntry(dir) { removeDirEntry(it) }
}

private inline fun removeEntry(
    path: Path,
    remove: (Path) -> Boolean,
) {
    if (remove(path)) return
    val e = FsException.ofErrno(path)
    if (e.kind != FsException.Kind.NotFound) throw e
}
