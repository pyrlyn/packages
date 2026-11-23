// Character-level reading for TomlParser: position, whitespace, comments and the string forms of TOML 1.1.
package toml

internal class TomlCursor(
    private val text: String,
) {
    var pos = if (text.startsWith('﻿')) 1 else 0
        private set
    var line = 1
        private set
    private var lineStart = pos

    val eof get() = pos >= text.length

    /** The character [off] ahead, or -1 past the end. */
    fun at(off: Int = 0): Int = if (pos + off < text.length) text[pos + off].code else -1

    fun startsWith(s: String) = text.startsWith(s, pos)

    fun slice(from: Int) = text.substring(from, pos)

    fun advance(n: Int = 1) {
        repeat(n) {
            if (text[pos] == '\n') {
                line++
                lineStart = pos + 1
            }
            pos++
        }
    }

    fun fail(message: String): Nothing = throw TomlException("TOML parse error at line $line, column ${pos - lineStart + 1}: $message")

    fun expect(ch: Char) {
        if (at() != ch.code) fail("expected `$ch`")
        advance()
    }

    fun ws() {
        while (at() == SPACE || at() == TAB) advance()
    }

    /** One LF or CRLF; false when neither is here. */
    fun newline(): Boolean {
        val n =
            when {
                at() == LF -> 1
                at() == CR && at(1) == LF -> 2
                else -> 0
            }
        advance(n)
        return n > 0
    }

    fun comment() {
        if (at() != '#'.code) return
        advance()
        while (!eof && !lineEnd()) {
            val ch = at()
            if (ch != TAB && (ch < SPACE || ch == DEL)) fail("control character ${hex(ch)} in a comment")
            take(null)
        }
    }

    private fun lineEnd() = at() == LF || (at() == CR && at(1) == LF)

    /** Whether [regex] matches right here. */
    fun lookingAt(regex: Regex) = regex.matchAt(text, pos) != null

    /** Whitespace, comments and newlines, as between array items and inline-table entries. */
    fun wsCommentNewline() {
        do {
            ws()
            comment()
        } while (newline())
    }

    fun basicString(): String = if (startsWith("\"\"\"")) multiLine('"') else singleLine('"')

    fun literalString(): String = if (startsWith("'''")) multiLine('\'') else singleLine('\'')

    private fun singleLine(quote: Char): String {
        advance()
        val out = StringBuilder()
        while (at() != quote.code) {
            when {
                at() == -1 || at() == LF || at() == CR -> fail("unterminated string")
                at() == BACKSLASH && quote == '"' -> escape(out)
                else -> plain(out)
            }
        }
        advance()
        return out.toString()
    }

    private fun multiLine(quote: Char): String {
        advance(DELIMITER)
        newline()
        val out = StringBuilder()
        while (true) {
            val ch = at()
            when {
                ch == -1 -> fail("unterminated multi-line string")
                ch == quote.code && closes(quote, out) -> return out.toString()
                ch == quote.code -> plain(out)
                ch == LF || ch == CR -> if (newline()) out.append('\n') else fail("a carriage return must be followed by a newline")
                ch == BACKSLASH && quote == '"' -> mlEscape(out)
                else -> plain(out)
            }
        }
    }

    /** At a run of [quote]s: three to five end the string, the ones before the last three belonging to it. */
    private fun closes(
        quote: Char,
        out: StringBuilder,
    ): Boolean {
        var n = 0
        while (at(n) == quote.code) n++
        if (n < DELIMITER) return false
        if (n > DELIMITER + 2) fail("too many quotes at the end of a multi-line string")
        repeat(n - DELIMITER) { out.append(quote) }
        advance(n)
        return true
    }

    /** A backslash ending a line drops it and the whitespace and newlines after it. */
    private fun mlEscape(out: StringBuilder) {
        var n = 1
        while (at(n) == SPACE || at(n) == TAB) n++
        val trimmed = at(n) == LF || (at(n) == CR && at(n + 1) == LF)
        if (!trimmed) return escape(out)
        advance(n)
        while (true) {
            ws()
            if (!newline()) return
        }
    }

    private fun escape(out: StringBuilder) {
        advance()
        val simple = SIMPLE_ESCAPES[at().toChar()]
        if (at() != -1 && simple != null) {
            out.append(simple)
            advance()
            return
        }
        val digits =
            when (at()) {
                'x'.code -> 2
                'u'.code -> 4
                'U'.code -> 8
                else -> fail("invalid escape sequence")
            }
        advance()
        val from = pos
        repeat(digits) { if (at() == -1 || !at().toChar().isHex()) fail("expected $digits hex digits") else advance() }
        val code = slice(from).toLong(HEX)
        if (code > MAX_CODE_POINT || code in SURROGATES) fail("escape `${slice(from)}` is not a Unicode scalar value")
        appendCodePoint(out, code.toInt())
    }

    /** A character allowed in any string: a tab or anything but a control character. */
    private fun plain(out: StringBuilder) {
        val ch = at()
        if (ch != TAB && (ch < SPACE || ch == DEL)) fail("control character ${hex(ch)} in a string")
        take(out)
    }

    /** Take one character, a valid surrogate pair as one; a lone surrogate is not text. */
    private fun take(out: StringBuilder?) {
        val c = text[pos]
        val n =
            when {
                c.isHighSurrogate() && pos + 1 < text.length && text[pos + 1].isLowSurrogate() -> 2
                c.isSurrogate() -> fail("lone surrogate ${hex(c.code)}")
                else -> 1
            }
        out?.append(text, pos, pos + n)
        advance(n)
    }

    private fun Char.isHex() = this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'

    private fun hex(code: Int) = "U+" + code.toString(HEX).uppercase().padStart(HEX_WIDTH, '0')

    private fun appendCodePoint(
        out: StringBuilder,
        code: Int,
    ) {
        if (code < SUPPLEMENTARY) {
            out.append(code.toChar())
        } else {
            val v = code - SUPPLEMENTARY
            out.append((HIGH_BASE + (v shr TEN_BITS)).toChar()).append((LOW_BASE + (v and LOW_MASK)).toChar())
        }
    }

    private companion object {
        const val TAB = 0x09
        const val LF = 0x0A
        const val CR = 0x0D
        const val SPACE = 0x20
        const val DEL = 0x7F
        const val BACKSLASH = 0x5C
        const val DELIMITER = 3
        const val HEX = 16
        const val HEX_WIDTH = 4
        const val MAX_CODE_POINT = 0x10FFFFL
        val SURROGATES = 0xD800L..0xDFFFL
        const val SUPPLEMENTARY = 0x10000
        const val HIGH_BASE = 0xD800
        const val LOW_BASE = 0xDC00
        const val TEN_BITS = 10
        const val LOW_MASK = 0x3FF
        val SIMPLE_ESCAPES =
            mapOf('b' to '\b', 't' to '\t', 'n' to '\n', 'f' to '\u000C', 'r' to '\r', 'e' to '\u001B', '"' to '"', '\\' to '\\')
    }
}
