package fs

import kotlinx.io.files.Path

/** Rust `fs::canonicalize`: the existing path with every link and `..` resolved; an error for a missing one. */
fun canonicalize(path: Path): Path = realOrNull(path) ?: throw FsException.ofErrno(null)

/** The canonical path when it exists, otherwise [path] with only the prefix Windows can drop without disk access. */
fun canon(path: Path): Path = realOrNull(path) ?: Path(simplified(path.toString()))

/** `realpath(3)`, `GetFinalPathNameByHandleW` on Windows; null with the OS error left when it fails. */
internal fun realOrNull(path: Path): Path? = realPath(path)?.let(::Path)
