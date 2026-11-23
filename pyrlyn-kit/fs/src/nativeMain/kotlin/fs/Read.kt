package fs

import kotlinx.io.files.Path

/** The text of [path], or `""` when there is no file: a config or state file nobody wrote yet. */
fun textOrEmpty(path: Path): String = bytesIfExists(path)?.let { utf8(it, path) } ?: ""

/** The bytes of [path], or null when there is no file. Any other error names the path. */
fun bytesIfExists(path: Path): ByteArray? =
    try {
        readAll(path)
    } catch (e: FsException) {
        if (e.kind != FsException.Kind.NotFound) throw e
        null
    }

/** The text of [path]; an error when it is missing or not UTF-8 (Rust `fs::read_to_string`). */
fun readText(path: Path): String = utf8(readAll(path), path)

private fun utf8(
    bytes: ByteArray,
    path: Path,
): String =
    try {
        bytes.decodeToString(throwOnInvalidSequence = true)
    } catch (_: CharacterCodingException) {
        throw FsException(path, FsException.Kind.InvalidData, null, "stream did not contain valid UTF-8")
    }
