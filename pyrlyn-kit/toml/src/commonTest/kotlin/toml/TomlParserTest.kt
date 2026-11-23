package toml

import kotlinx.serialization.json.*
import kotlin.test.*

class TomlParserTest {
    private fun p(s: String) = TomlParser.parse(s)

    @Test fun scalars() {
        val r = p("a = 1\nb = 1.5\nc = true\nd = \"x\"\ne = 'y'\n")
        assertEquals("1", r["a"]!!.jsonPrimitive.content)
        assertEquals(1.5, r["b"]!!.jsonPrimitive.double)
        assertEquals(true, r["c"]!!.jsonPrimitive.boolean)
        assertEquals("x", r["d"]!!.jsonPrimitive.content)
        assertEquals("y", r["e"]!!.jsonPrimitive.content)
    }

    @Test fun aKeyWithoutAValueIsAnError() {
        assertFailsWith<TomlException> { p("name = ") }
        assertFailsWith<TomlException> { p("[t]\nname = null\n") }
    }

    @Test fun nestedTables() {
        val r = p("[a.b]\nx = 1\n[a]\ny = 2\n")
        assertEquals(
            "1",
            r["a"]!!
                .jsonObject["b"]!!
                .jsonObject["x"]!!
                .jsonPrimitive.content,
        )
        assertEquals("2", r["a"]!!.jsonObject["y"]!!.jsonPrimitive.content)
    }

    @Test fun dottedKeys() {
        val r = p("a.b.c = 1\n")
        assertEquals(
            "1",
            r["a"]!!
                .jsonObject["b"]!!
                .jsonObject["c"]!!
                .jsonPrimitive.content,
        )
    }

    @Test fun inlineTable() {
        val r = p("t = { x = 1, y = \"s\" }\n")
        assertEquals("s", r["t"]!!.jsonObject["y"]!!.jsonPrimitive.content)
    }

    @Test fun arrays() {
        val r = p("a = [1, 2]\nb = [[1], [\"x\"]]\nc = [\n  \"m\",\n  \"n\",\n]\n")
        assertEquals(2, r["a"]!!.jsonArray.size)
        assertEquals(2, r["b"]!!.jsonArray.size)
        assertEquals(2, r["c"]!!.jsonArray.size)
    }

    @Test fun arrayOfTables() {
        val r = p("[[g]]\nn = 1\n[[g]]\nn = 2\n")
        assertEquals(2, r["g"]!!.jsonArray.size)
    }

    @Test fun datetimeAsString() {
        val r = p("d = 1979-05-27T07:32:00Z\n")
        assertTrue(r["d"]!!.jsonPrimitive.content.startsWith("1979-05-27"))
    }

    @Test fun multiline() {
        assertEquals("x\ny\n", TomlParser.parse("a = \"\"\"\nx\ny\n\"\"\"\nb = 1\n")["a"]!!.jsonPrimitive.content)
    }

    @Test fun lines() {
        val d = TomlParser.read("# c\na = 1\n\n[t]\nk = 2\n")
        assertEquals(2, d.lineOf("a"))
        assertEquals(4, d.lineOf("t"))
        assertEquals(5, d.lineOf("t.k"))
    }

    @Test fun errorHasLine() {
        val e = assertFailsWith<TomlException> { TomlParser.read("a = 1\nb = = 2\n") }
        assertTrue(e.message!!.contains("line 2"), e.message)
    }

    @Test fun duplicateKeyRejected() {
        assertFailsWith<TomlException> { TomlParser.read("a = 1\na = 2\n") }
    }

    // ktoml refuses this shape, which ketch manifests use for `extra_paths`.
    @Test fun inlineTablesInAMultiLineArray() {
        val r = p("e = [\n  \"man/a.1\",\n  { path = \"c/a.bash\", kind = \"completion\" },\n]\n")
        assertEquals(
            "completion",
            r["e"]!!
                .jsonArray[1]
                .jsonObject["kind"]!!
                .jsonPrimitive.content,
        )
    }

    @Test fun integersInEveryRadix() {
        val r = p("a = 0x1F\nb = 0o17\nc = 0b101\nd = 1_000\ne = -3\n")
        assertEquals(listOf(31L, 15L, 5L, 1000L, -3L), listOf("a", "b", "c", "d", "e").map { r[it]!!.jsonPrimitive.long })
    }

    @Test fun specialFloats() {
        val r = p("a = inf\nb = -inf\nc = nan\n")
        assertEquals(Double.POSITIVE_INFINITY, r["a"]!!.jsonPrimitive.double)
        assertEquals(Double.NEGATIVE_INFINITY, r["b"]!!.jsonPrimitive.double)
        assertTrue(r["c"]!!.jsonPrimitive.double.isNaN())
    }

    @Test fun anIntegerOutOfRangeIsAnError() {
        assertFailsWith<TomlException> { p("a = 9223372036854775808\n") }
    }

    @Test fun writtenTextReadsBack() {
        val tree = p("top = \"s\"\nn = 3\nlist = [1, 2]\n[t]\nk = \"v\"\n[[g]]\nn = 1\n[[g]]\nn = 2\n")
        assertEquals(tree, p(TomlParser.write(tree)))
    }

    // tomlkt refuses these; Rust `toml` writes such strings, e.g. one ending in a quote with a backslash in it.
    @Test fun quotesRightBeforeTheClosingDelimiter() {
        assertEquals("a'", p("v = '''a''''\n")["v"]!!.jsonPrimitive.content)
        assertEquals("a''", p("v = '''a'''''\n")["v"]!!.jsonPrimitive.content)
        assertEquals("a\"", p("v = \"\"\"a\"\"\"\"\n")["v"]!!.jsonPrimitive.content)
        assertFailsWith<TomlException> { p("v = '''a''''''\n") }
    }

    @Test fun everyStringTheWriterPicksReadsBack() {
        for (s in listOf("plain", "say \"hi\"", "C:\\bin", "both \" and \\ and '", "a\nb", "it's\n\"q\"\\", "x'''y", "\u0001\u007f\t", "é😀", "ends '")) {
            assertEquals(s, p("v = ${TomlWriter.string(s)}\n")["v"]!!.jsonPrimitive.content, s)
        }
    }

    @Test fun aByteOrderMarkIsSkipped() {
        assertEquals("1", p("\uFEFFa = 1\n")["a"]!!.jsonPrimitive.content)
    }

    // The input is untrusted; a deep nest must be an error, not a stack overflow.
    @Test fun nestingIsBoundedAsInRust() {
        assertFailsWith<TomlException> { p("a = " + "[".repeat(100_000)) }
        assertEquals(1, p("a = " + "[".repeat(79) + "]".repeat(79) + "\n")["a"]!!.jsonArray.size)
        assertFailsWith<TomlException> { p("a" + ".a".repeat(80) + " = 1\n") }
    }

    @Test fun aDatetimeReadsAsRustWritesIt() {
        val r = p("a = 1979-05-27 07:32:00.123456789123z\nb = 07:32\nc = 1979-05-27T00:00:00-00:00\n")
        assertEquals("1979-05-27T07:32:00.123456789Z", r["a"]!!.jsonPrimitive.content)
        assertEquals("07:32", r["b"]!!.jsonPrimitive.content)
        assertEquals("1979-05-27T00:00:00+00:00", r["c"]!!.jsonPrimitive.content)
    }
}
