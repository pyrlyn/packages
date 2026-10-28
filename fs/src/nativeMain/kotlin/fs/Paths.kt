package fs

import kotlinx.io.files.Path

/** Rust `fs::canonicalize`: the existing path with every link and `..` resolved; an error for a missing one. */
fun canonicalize(path: Path): Path = realOrNull(path) ?: throw FsException.ofErrno(null)

/** The canonical path when it exists, otherwise [path] with only the prefix Windows can drop without disk access. */
fun canon(path: Path): Path = realOrNull(path) ?: Path(simplified(path.toString()))

/** `realpath(3)`, `GetFinalPathNameByHandleW` on Windows; null with the OS error left when it fails. */
internal fun realOrNull(path: Path): Path? = realPath(path)?.let(::Path)

/**
 * [Path.parent] without its loss. kotlinx-io hands the path to C `dirname` as UTF-8, which turns an unpaired surrogate
 * (a Windows name that is not valid Unicode) into U+FFFD. Each is one UTF-16 unit either way and the parent is a
 * prefix, so it is cut from the original text; anything else is kotlinx-io's answer unchanged.
 */
val Path.exactParent: Path? get() = parent?.let { Path(restored(toString(), it.toString(), fromEnd = false)) }

/** [Path.name] without its loss, for the same reason as [exactParent]: the name is a suffix of the original text. */
val Path.exactName: String get() = restored(toString(), name, fromEnd = true)

private fun restored(
    original: String,
    lossy: String,
    fromEnd: Boolean,
): String {
    if (lossy.length > original.length) return lossy
    val start = if (fromEnd) original.length - lossy.length else 0
    val exact = original.substring(start, start + lossy.length)
    return if (exact.indices.all { exact[it] == lossy[it] || lossy[it] == '�' }) exact else lossy
}
