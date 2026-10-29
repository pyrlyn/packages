package toml

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals

// Every expected text below is what Rust `toml` 1.1.8 (`toml_writer` 1.1.3) printed for the same JSON input.
class TomlWriterTest {
    private fun doc(json: String): String = TomlWriter.document(Json.parseToJsonElement(json).jsonObject)

    @Test
    fun plainKeysComeBeforeTablesAndLongArraysTakeALinePerItem() {
        val json =
            """{"version":1,"name":"x","tags":["a","b"],"one":["a"],"empty":[],"nums":[1,2],"f":1.5,"f2":2.0,"b":true,""" +
                """"multi":"a\nb","q":"say \"hi\"","t":{"k":"v","sub":{"z":1}},"after":"late",""" +
                """"package":[{"name":"fd","bin":[{"name":"x"}],"meta":{"a":1}},{"name":"rg"}]}"""
        val expected =
            """
            version = 1
            name = "x"
            tags = [
                "a",
                "b",
            ]
            one = ["a"]
            empty = []
            nums = [
                1,
                2,
            ]
            f = 1.5
            f2 = 2.0
            b = true
            multi = ""${'"'}
            a
            b""${'"'}
            q = 'say "hi"'
            after = "late"

            [t]
            k = "v"

            [t.sub]
            z = 1

            [[package]]
            name = "fd"

            [[package.bin]]
            name = "x"

            [package.meta]
            a = 1

            [[package]]
            name = "rg"

            """.trimIndent()
        assertEquals(expected, doc(json))
    }

    @Test
    fun nestedArraysMixedArraysAndQuotedKeys() {
        val json = """{"a":{"b":{"c":1}},"key with space":"v","dot.key":{"x":1},"arr":[[1,2],["a"]],"inl":[{"a":1},{"b":"c"}],"mixed":[{"a":1},2]}"""
        val expected =
            """
            "key with space" = "v"
            arr = [
                [
                1,
                2,
            ],
                ["a"],
            ]
            mixed = [
                { a = 1 },
                2,
            ]

            [a.b]
            c = 1

            ["dot.key"]
            x = 1

            [[inl]]
            a = 1

            [[inl]]
            b = "c"

            """.trimIndent()
        assertEquals(expected, doc(json))
    }

    @Test
    fun inlineTablesInsideArrays() {
        val expected =
            """
            arr = [
                {},
                1,
            ]
            e = [
                { a = { b = 1 }, c = [
                1,
                2,
            ] },
                1,
            ]

            [[one]]
            x = [
                1,
                2,
            ]

            """.trimIndent()
        assertEquals(expected, doc("""{"arr":[{},1],"e":[{"a":{"b":1},"c":[1,2]},1],"one":[{"x":[1,2]}]}"""))
    }

    @Test
    fun emptyTablesKeepTheirHeaderUnlessTheyOnlyHoldTables() {
        assertEquals("[[p]]\n\n[[q]]\n\n[q.t]\nk = 1\n", doc("""{"p":[{}],"q":[{"t":{"k":1}}]}"""))
        assertEquals("x = 1\n\n[t.sub]\n", doc("""{"t":{"sub":{}}, "x":1}"""))
        assertEquals("", doc("{}"))
        assertEquals("[t]\n", doc("""{"t":{}}"""))
        assertEquals("a = 1\n", doc("""{"n":null,"a":1}"""))
    }

    @Test
    fun floatsNeverUseAnExponent() {
        val expected = "f = [\n    100000000000000000000.0,\n    0.0000001,\n    0.1,\n    -0.0,\n    100.0,\n    3,\n]\n"
        assertEquals(expected, doc("""{"f":[1e20, 1.0e-7, 0.1, -0.0, 100.0, 3]}"""))
    }

    @Test
    fun keysAreBareOrQuotedTheWayRustQuotesThem() {
        val expected = "'k\"q' = 1\n\"k'q\" = 2\n\"ünï\" = 3\n\"\" = 4\n\n[\"a b\"]\n\"c d\" = 1\n"
        assertEquals(expected, doc("""{"k\"q":1, "k'q":2, "ünï":3, "":4, "a b":{"c d":1}}"""))
    }

    @Test
    fun stringsTakeTheFormRustPicks() {
        val cases =
            listOf(
                "plain" to "\"plain\"",
                "say \"hi\"" to "'say \"hi\"'",
                "C:\\bin" to "'C:\\bin'",
                "both \" and \\ and '" to "'''both \" and \\ and ''''",
                "a\u0001b\u007fc\td" to "\"a\\u0001b\\u007Fc\\td\"",
                "q\" b\\ n\n t\t \u0000 é" to "\"\"\"\nq\" b\\\\ n\n t\\t \\u0000 é\"\"\"",
                "it's" to "\"it's\"",
                "line1\nline2" to "\"\"\"\nline1\nline2\"\"\"",
                "tab\there" to "\"tab\\there\"",
                "" to "\"\"",
            )
        for ((text, expected) in cases) assertEquals(expected, TomlWriter.string(text), text)
    }

    @Test
    fun aValueIsWrittenOnOneLine() {
        fun v(json: String) = TomlWriter.value(Json.parseToJsonElement(json))
        assertEquals("[]", v("[]"))
        assertEquals("[\"a\", 'b\"c', 'd\\e']", v("""["a","b\"c","d\\e"]"""))
        assertEquals("[1, [2, 3], { a = 1, b = {} }]", v("""[1,[2,3],{"a":1,"b":{}}]"""))
        assertEquals("100000000000000000000.0", v("1e20"))
        assertEquals("{ a = 1 }", v("""{"a":1}"""))
    }
}
