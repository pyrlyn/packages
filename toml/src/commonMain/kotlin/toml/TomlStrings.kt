// Strings, keys and floats as `toml_writer` 1.1 renders them.
package toml

internal object TomlStrings {
    fun string(s: String): String {
        val m = Metrics(s)
        return when {
            m.basic -> quoted(s, BASIC, m.newline)
            m.literal -> "'$s'"
            m.mlBasic -> quoted(s, ML_BASIC, m.newline)
            m.mlLiteral -> "'''${if (m.newline) "\n" else ""}$s'''"
            else -> quoted(s, if (m.newline) ML_BASIC else BASIC, m.newline)
        }
    }

    fun key(s: String): String {
        val codes = s.any { isControl(it) && it != '\t' }
        return when {
            s.isNotEmpty() && s.all(::isBare) -> s
            !codes && '\\' !in s && '"' !in s -> quoted(s, BASIC, false)
            !codes && '\'' !in s -> "'$s'"
            else -> quoted(s, BASIC, false)
        }
    }

    /** Rust's f64 Display: the shortest digits that read back, never an exponent, `.0` on a whole number. */
    fun float(d: Double): String =
        when {
            d.isNaN() -> "nan"
            d.isInfinite() -> if (d > 0) "inf" else "-inf"
            d == 0.0 -> if (1.0 / d < 0) "-0.0" else "0.0"
            else -> plain(d.toString()).let { if ('.' in it) it else "$it.0" }
        }

    /** `1.0E20` → `100000000000000000000`, `1.0E-7` → `0.0000001`, `0.1` → `0.1`. */
    private fun plain(text: String): String {
        val e = text.indexOfFirst { it == 'E' || it == 'e' }
        if (e < 0) return text.removeSuffix(".0")
        val negative = text.startsWith('-')
        val mantissa = text.substring(if (negative) 1 else 0, e)
        val point = mantissa.indexOf('.').let { if (it < 0) mantissa.length else it }
        val digits = mantissa.replace(".", "").trimEnd('0').ifEmpty { "0" }
        val at = point + text.substring(e + 1).toInt()
        val body =
            when {
                at <= 0 -> "0." + "0".repeat(-at) + digits
                at >= digits.length -> digits + "0".repeat(at - digits.length)
                else -> digits.substring(0, at) + "." + digits.substring(at)
            }
        return if (negative) "-$body" else body
    }

    private fun quoted(
        s: String,
        delimiter: String,
        newline: Boolean,
    ): String {
        val ml = delimiter == ML_BASIC
        // A multi-line string may hold two quotes in a row; the third must be escaped or it would end the string.
        val maxQuotes = if (ml) 2 else 0
        return buildString {
            append(delimiter)
            if (ml && newline) append('\n')
            var quotes = 0
            for (c in s) {
                quotes = if (c == '"') quotes + 1 else 0
                append(if (c == '"') (if (quotes > maxQuotes) "\\\"" else "\"") else escaped(c, ml))
            }
            append(delimiter)
        }
    }

    private fun escaped(
        c: Char,
        ml: Boolean,
    ): String =
        when (c) {
            '\b' -> {
                "\\b"
            }

            '\t' -> {
                "\\t"
            }

            '\n' -> {
                if (ml) "\n" else "\\n"
            }

            '\u000c' -> {
                "\\f"
            }

            '\r' -> {
                "\\r"
            }

            '\\' -> {
                "\\\\"
            }

            else -> {
                if (isControl(c)) {
                    "\\u" +
                        c.code
                            .toString(HEX)
                            .uppercase()
                            .padStart(HEX_WIDTH, '0')
                } else {
                    c.toString()
                }
            }
        }

    private fun isBare(c: Char) = c in 'a'..'z' || c in 'A'..'Z' || c in '0'..'9' || c == '-' || c == '_'

    private fun isControl(c: Char) = c.code <= LAST_CONTROL || c.code == DEL

    /** What `toml_writer` measures to choose a string form, and the forms each measure allows. */
    private class Metrics(
        s: String,
    ) {
        private var maxSingleQuotes = 0
        private var maxDoubleQuotes = 0
        private var escapeCodes = false
        private var escape = false
        var newline = false
            private set

        init {
            var single = 0
            var double = 0
            for (c in s) {
                single = if (c == '\'') single + 1 else 0
                double = if (c == '"') double + 1 else 0
                maxSingleQuotes = maxOf(maxSingleQuotes, single)
                maxDoubleQuotes = maxOf(maxDoubleQuotes, double)
                if (c == '\\') escape = true
                if (c == '\n') {
                    newline = true
                } else if (c != '\t' && isControl(c)) {
                    escapeCodes = true
                }
            }
        }

        val basic get() = !escapeCodes && !escape && maxDoubleQuotes == 0 && !newline
        val literal get() = !escapeCodes && maxSingleQuotes == 0 && !newline
        val mlBasic get() = !escapeCodes && !escape && maxDoubleQuotes <= 2
        val mlLiteral get() = !escapeCodes && maxSingleQuotes <= 2
    }

    private const val BASIC = "\""
    private const val ML_BASIC = "\"\"\""
    private const val LAST_CONTROL = 0x1f
    private const val DEL = 0x7f
    private const val HEX = 16
    private const val HEX_WIDTH = 4
}
