// Port of fs-kit's backup: copy a file to `<name>.bak-<unix-seconds>` beside it before replacing it. A missing file,
// or a sibling `.bak-*` that already holds the same bytes, is null: a second reset or setup must not grow a stack of
// identical undos.
@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package fs

import kotlinx.io.files.Path
import platform.posix.time

/** Copy [path] to `<name>.bak-<unix-seconds>` beside it; null when there is no file yet or an identical copy exists. */
fun backup(path: Path): Path? = backupAt(path, time(null).toULong())

/** [backup] with the clock passed in, so a test can pin the second. */
fun backupAt(
    path: Path,
    ts: ULong,
): Path? {
    val body = bytesIfExists(path) ?: return null
    val dir = parentOf(path)
    return if (identicalBackupExists(dir, path.name, body)) null else claim(dir, path.name, ts, body)
}

/**
 * Write [body] to the first free `<name>.bak-<ts>[-<n>]` in [dir]. Exclusive create claims the name: two commands
 * inside one second cannot pick the same one and truncate the first copy, and a planted link is never followed.
 */
private fun claim(
    dir: Path,
    name: String,
    ts: ULong,
    body: ByteArray,
): Path {
    var n = 0u
    while (true) {
        val bak = joinPath(dir, if (n == 0u) "$name.bak-$ts" else "$name.bak-$ts-$n")
        try {
            createNew(bak, body, durable = true)
            return bak
        } catch (e: FsException) {
            if (e.kind != FsException.Kind.AlreadyExists) {
                runCatching { removeFile(bak) }
                throw e
            }
        }
        n++
    }
}

/** True when any `<name>.bak-*` in [dir] is byte-equal to [body]. */
private fun identicalBackupExists(
    dir: Path,
    name: String,
    body: ByteArray,
): Boolean {
    val prefix = "$name.bak-"
    val names = runCatching { listDir(dir) }.getOrNull() ?: return false
    return names.filter { it.startsWith(prefix) }.any { entry ->
        val sibling = joinPath(dir, entry)
        // The size check comes first so a sibling that is no copy at all is never read.
        linkSize(sibling) == body.size.toLong() && runCatching { readAll(sibling) }.getOrNull()?.contentEquals(body) == true
    }
}
