package cli

private const val ESC = '\u001b'
private const val RESET = "\u001b[0m"

/** clap's default `Styles`, or none: what help and errors look like on a colour terminal. */
internal class Style(
    private val on: Boolean,
) {
    fun header(s: String) = paint(s, "\u001b[1m\u001b[4m")

    fun literal(s: String) = paint(s, "\u001b[1m")

    fun error(s: String) = paint(s, "\u001b[1m\u001b[31m")

    fun valid(s: String) = paint(s, "\u001b[32m")

    fun invalid(s: String) = paint(s, "\u001b[33m")

    private fun paint(
        s: String,
        code: String,
    ) = if (on && s.isNotEmpty()) "$code$s$RESET" else s
}

/** Where the escape sequence starting at [start] ends: after its final byte, as anstyle writes them. */
private fun escapeEnd(
    s: String,
    start: Int,
): Int {
    var i = start + 1
    if (i < s.length && s[i] == '[') i++
    while (i < s.length && s[i] !in '@'..'~') i++
    return minOf(i + 1, s.length)
}

/** The width clap gives text: one column per code point, escape sequences none. */
internal fun width(s: String): Int {
    var n = 0
    var i = 0
    while (i < s.length) {
        when {
            s[i] == ESC -> {
                i = escapeEnd(s, i)
            }

            else -> {
                if (!s[i].isLowSurrogate()) n++
                i++
            }
        }
    }
    return n
}

internal fun spaces(n: Int) = " ".repeat(maxOf(n, 0))

/** clap's `StyledStr::indent("", trailing)`: every line after the first starts with [trailing]. */
internal fun indent(
    text: String,
    trailing: String,
) = text.replace("\n", "\n$trailing")

/**
 * Wrap [text] at [hardWidth] columns exactly as clap's `wrap_help` does.
 *
 * Words are runs of non-spaces with the spaces after them; a line breaks before a word that would cross the width,
 * never before the first word of a line. The column count carries across escape sequences but restarts at each
 * newline inside a run of plain text, which is what clap does too.
 */
internal fun wrap(
    text: String,
    hardWidth: Int,
): String {
    val out = StringBuilder()
    val wrapper = LineWrapper(hardWidth)
    var i = 0
    while (i < text.length) {
        if (text[i] == ESC) {
            val end = escapeEnd(text, i)
            out.append(text, i, end)
            i = end
        } else {
            var end = i
            while (end < text.length && text[end] != ESC) end++
            linesInclusive(text.substring(i, end)).forEachIndexed { index, line ->
                if (index > 0) wrapper.reset()
                wrapper.wrap(words(line)).forEach { out.append(it) }
            }
            i = end
        }
    }
    return out.toString()
}

/** Rust's `split_inclusive('\n')`. */
private fun linesInclusive(s: String): List<String> {
    val lines = mutableListOf<String>()
    var start = 0
    while (start < s.length) {
        val nl = s.indexOf('\n', start)
        val end = if (nl < 0) s.length else nl + 1
        lines += s.substring(start, end)
        start = end
    }
    return lines
}

/** clap's `find_words_ascii_space`: each word keeps the spaces that follow it; leading spaces are a word of their own. */
private fun words(line: String): MutableList<String> {
    val words = mutableListOf<String>()
    var start = 0
    var inSpace = false
    line.forEachIndexed { idx, ch ->
        if (inSpace && ch != ' ') {
            words += line.substring(start, idx)
            start = idx
        }
        inSpace = ch == ' '
    }
    if (start < line.length) words += line.substring(start)
    return words
}

private class LineWrapper(
    private val hardWidth: Int,
) {
    private var lineWidth = 0

    fun reset() {
        lineWidth = 0
    }

    fun wrap(words: MutableList<String>): List<String> {
        var i = 0
        while (i < words.size) {
            val word = words[i]
            val trimmed = word.trimEnd()
            val wordWidth = width(trimmed)
            if (i != 0 && hardWidth < lineWidth + wordWidth) {
                words[i - 1] = words[i - 1].trimEnd()
                lineWidth = 0
                words.add(i, "\n")
                i++
            }
            lineWidth += wordWidth + (word.length - trimmed.length)
            i++
        }
        return words
    }
}

/** A value as clap quotes it in a list: Rust's `{:?}` when it holds whitespace, so the list stays readable. */
internal fun quoted(value: String): String =
    if (value.none { it.isWhitespace() }) {
        value
    } else {
        "\"" +
            value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\t", "\\t") + "\""
    }
