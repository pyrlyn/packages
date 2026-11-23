// A strict TOML 1.1 reader, the spec Rust `toml` 1.1 reads, written here because ktoml and tomlkt each fail
// part of the official toml-test suite (inline tables in multi-line arrays, quotes before a closing delimiter).
package toml

import kotlinx.serialization.json.*

object TomlParser : TomlReader {
    override fun read(text: String): TomlDocument = Parser(text).document()

    override fun write(root: JsonObject): String = TomlWriter.document(root)
}

private class Parser(
    text: String,
) {
    private val c = TomlCursor(text)
    private val tree = TomlTree(c::fail)
    private var depth = 0

    fun document(): TomlDocument {
        while (!c.eof) expression()
        return tree.document()
    }

    private fun expression() {
        c.ws()
        when (c.at()) {
            '['.code -> header()
            '#'.code, LF, CR, -1 -> Unit
            else -> keyval(tree.current, c.line)
        }
        c.ws()
        c.comment()
        if (!c.eof && !c.newline()) c.fail("expected a newline after the expression")
    }

    private fun header() {
        val line = c.line
        val array = c.startsWith("[[")
        c.advance(if (array) 2 else 1)
        c.ws()
        val key = key()
        c.ws()
        if (array && !c.startsWith("]]")) c.fail("expected `]]`")
        c.advance(if (array) 2 else 0)
        if (!array) c.expect(']')
        tree.header(key, array, line)
    }

    private fun keyval(
        target: TomlNode.Table,
        line: Int?,
    ) {
        val key = key()
        c.ws()
        c.expect('=')
        c.ws()
        tree.put(target, key, value(), line)
    }

    private fun key(): List<String> {
        val parts = mutableListOf(simpleKey())
        while (true) {
            c.ws()
            if (c.at() != '.'.code) break
            c.advance()
            c.ws()
            parts += simpleKey()
            // Rust `toml` refuses a key this deep for the same reason it bounds nesting.
            if (parts.size >= LIMIT) c.fail("key is nested too deeply")
        }
        return parts
    }

    private fun simpleKey(): String =
        when (c.at()) {
            '"'.code -> {
                if (c.startsWith("\"\"\"")) c.fail("a key cannot be a multi-line string") else c.basicString()
            }

            '\''.code -> {
                if (c.startsWith("'''")) c.fail("a key cannot be a multi-line string") else c.literalString()
            }

            else -> {
                val from = c.pos
                while (c.at().let { it != -1 && isBare(it.toChar()) }) c.advance()
                c.slice(from).ifEmpty { c.fail("expected a key") }
            }
        }

    private fun value(): TomlNode {
        // The input is untrusted: without a bound, `[[[[…` would overflow the stack.
        if (++depth > LIMIT) c.fail("value is nested too deeply")
        val v =
            when (c.at()) {
                '"'.code -> TomlNode.Leaf(JsonPrimitive(c.basicString()))
                '\''.code -> TomlNode.Leaf(JsonPrimitive(c.literalString()))
                't'.code -> word("true", true)
                'f'.code -> word("false", false)
                '['.code -> array()
                '{'.code -> inlineTable()
                else -> scalar()
            }
        depth--
        return v
    }

    private fun word(
        text: String,
        value: Boolean,
    ): TomlNode {
        if (!c.startsWith(text)) c.fail("expected a value")
        c.advance(text.length)
        return TomlNode.Leaf(JsonPrimitive(value))
    }

    private fun array(): TomlNode {
        c.advance()
        val items = mutableListOf<TomlNode>()
        while (true) {
            c.wsCommentNewline()
            if (c.at() == ']'.code) break
            items += value()
            c.wsCommentNewline()
            if (c.at() == ','.code) {
                c.advance()
            } else if (c.at() != ']'.code) {
                c.fail("expected `,` or `]` in an array")
            }
        }
        c.advance()
        return TomlNode.Array(items)
    }

    private fun inlineTable(): TomlNode {
        c.advance()
        val table = TomlNode.Table(TomlNode.Origin.Inline)
        while (true) {
            c.wsCommentNewline()
            if (c.at() == '}'.code) break
            keyval(table, null)
            c.wsCommentNewline()
            if (c.at() == ','.code) {
                c.advance()
            } else if (c.at() != '}'.code) {
                c.fail("expected `,` or `}` in an inline table")
            }
        }
        c.advance()
        return table
    }

    private fun scalar(): TomlNode {
        val from = c.pos
        skipToken()
        // A date, a space and a time are one value.
        if (TomlScalars.DATE_ONLY.matches(c.slice(from)) && c.lookingAt(SPACE_TIME)) {
            c.advance()
            skipToken()
        }
        val token = c.slice(from).ifEmpty { c.fail("expected a value") }
        return TomlNode.Leaf(TomlScalars.of(token, c::fail) ?: c.fail("`$token` is not a valid value"))
    }

    private fun skipToken() {
        while (c.at().let { it != -1 && (isBare(it.toChar()) || it.toChar() in "+.:") }) c.advance()
    }

    private fun isBare(ch: Char) = ch in 'A'..'Z' || ch in 'a'..'z' || ch in '0'..'9' || ch == '-' || ch == '_'

    private companion object {
        const val LF = 0x0A
        const val CR = 0x0D
        val SPACE_TIME = Regex(" [0-9]{2}:")

        /** Rust `toml`'s bound on nesting and on key length. */
        const val LIMIT = 80
    }
}
