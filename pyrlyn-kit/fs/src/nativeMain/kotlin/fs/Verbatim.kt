// dunce 1.0.5's `simplified`, which fs-kit's canonicalize applies: the `\\?\C:\…` form GetFinalPathNameByHandleW
// returns becomes `C:\…` unless dropping the prefix would change what the path names. Without the prefix Windows
// reads a reserved name as a device, trims a trailing dot or space, and refuses a path past MAX_PATH.
package fs

/** dunce `simplified` on Windows: only a verbatim disk path whose every name survives Win32's normalisation loses its prefix. */
internal fun withoutVerbatim(path: String): String = if (safeToStrip(path)) path.substring(VERBATIM_PREFIX.length) else path

private fun safeToStrip(path: String): Boolean {
    if (!path.startsWith(VERBATIM_PREFIX) || path.length > MAX_PATH) return false
    val rest = path.substring(VERBATIM_PREFIX.length)
    val disk = rest.length >= 2 && rest[0].isAsciiLetter() && rest[1] == ':' && (rest.length == 2 || rest[2] == '\\')
    // A verbatim path takes `.` and `..` literally; empty names between separators are skipped, as Rust parses them.
    return disk &&
        rest
            .substring(2)
            .split('\\')
            .filter { it.isNotEmpty() }
            .all { isPlainName(it) }
}

internal fun isPlainName(name: String): Boolean =
    name.length <= MAX_NAME &&
        name != "." &&
        name != ".." &&
        name.none { it.code < ' '.code || it in FORBIDDEN } &&
        name.last() != ' ' &&
        name.last() != '.' &&
        !isReserved(name)

/** `con.txt` is the console too: the stem before the last dot, trailing dots and spaces trimmed (Rust `file_stem`). */
private fun isReserved(name: String): Boolean {
    val dot = name.lastIndexOf('.')
    val stem = if (dot <= 0) name else name.substring(0, dot)
    val trimmed = stem.trimEnd(' ', '.')
    return trimmed.length <= RESERVED_LENGTH && RESERVED.any { it.equals(trimmed, ignoreCase = true) }
}

private fun Char.isAsciiLetter() = this in 'a'..'z' || this in 'A'..'Z'

internal const val VERBATIM_PREFIX = "\\\\?\\"
private const val FORBIDDEN = "<>:\"/\\|?*"
private const val MAX_PATH = 260
private const val MAX_NAME = 255
private const val RESERVED_LENGTH = 4
private val RESERVED =
    listOf("AUX", "NUL", "PRN", "CON") + (1..9).flatMap { listOf("COM$it", "LPT$it") }
