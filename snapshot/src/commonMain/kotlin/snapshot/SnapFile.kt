// The `.snap` file format insta writes: a `---` delimited YAML-like header, then the contents.
package snapshot

/**
 * One snapshot file. [header] keeps each value as written (quoted or not), so a file insta wrote
 * keeps its header byte for byte when it is rewritten here.
 */
public data class SnapFile(
    val header: Map<String, String>,
    val contents: String,
) {
    /** The file text: header, `---`, then the contents and one trailing newline. */
    public fun render(): String =
        buildString {
            append(DELIMITER).append('\n')
            for ((key, value) in header) append(key).append(": ").append(value).append('\n')
            append(DELIMITER).append('\n')
            append(contents).append('\n')
        }

    public companion object {
        private const val DELIMITER = "---"

        /** Parses a `.snap` file; a file without a header is all contents, as insta reads old files. */
        public fun parse(text: String): SnapFile {
            val src = text.replace("\r\n", "\n")
            val open = "$DELIMITER\n"
            val close = if (src.startsWith(open)) src.indexOf("\n$open", open.length - 1) else -1
            if (close < 0) return SnapFile(emptyMap(), normalize(src))
            val header = linkedMapOf<String, String>()
            for (line in src.substring(open.length, maxOf(open.length, close)).lines()) {
                val colon = line.indexOf(':')
                if (colon > 0) header[line.substring(0, colon).trim()] = line.substring(colon + 1).trim()
            }
            return SnapFile(header, normalize(src.substring(close + open.length + 1)))
        }

        /** A header value as insta's YAML would hold it: plain when safe, double-quoted otherwise. */
        public fun quote(value: String): String {
            val plain =
                value.isNotEmpty() &&
                    !value.first().isDigit() &&
                    value.all { it.isLetterOrDigit() || it in "_./-()" }
            if (plain) return value
            return buildString {
                append('"')
                for (c in value) {
                    when (c) {
                        '"' -> append("\\\"")
                        '\\' -> append("\\\\")
                        '\n' -> append("\\n")
                        '\t' -> append("\\t")
                        else -> append(c)
                    }
                }
                append('"')
            }
        }
    }
}

/** What is compared: line endings as `\n` and no trailing whitespace, as insta normalizes text. */
public fun normalize(text: String): String = text.replace("\r\n", "\n").trimEnd()

/** An inline snapshot literal as insta reads it: indentation and a leading blank line removed. */
public fun normalizeInline(text: String): String = normalize(text.replace("\r\n", "\n").trimIndent())
