package snapshot

import kotlin.test.Test
import kotlin.test.assertEquals

class FormatTest {
    @Test fun parsesAnInstaFile() {
        val f = SnapFile.parse("---\nsource: tests/c.rs\nexpression: \"f(x, y)\"\n---\nbody\n```\n")
        val want = mapOf("source" to "tests/c.rs", "expression" to "\"f(x, y)\"")
        assertEquals(want, f.header)
        assertEquals("body\n```", f.contents)
    }

    @Test fun aFileWithoutAHeaderIsAllContents() =
        assertEquals(
            SnapFile(emptyMap(), "plain"),
            SnapFile.parse("plain\n"),
        )

    @Test fun rendersAndParsesBack() {
        val f = SnapFile(mapOf("source" to "a.kt"), "x\n\ny")
        assertEquals(f, SnapFile.parse(f.render()))
    }

    @Test fun quotesOnlyWhenNeeded() {
        assertEquals("out", SnapFile.quote("out"))
        assertEquals("to_table(rows)", SnapFile.quote("to_table(rows)"))
        assertEquals("\"a: \\\"b\\\"\\n\"", SnapFile.quote("a: \"b\"\n"))
    }

    @Test fun updateModesFollowInsta() {
        assertEquals(UpdateMode.Always, UpdateMode.parse("always", ci = true))
        assertEquals(UpdateMode.New, UpdateMode.parse("new", ci = true))
        assertEquals(UpdateMode.No, UpdateMode.parse("no", ci = false))
        assertEquals(UpdateMode.New, UpdateMode.parse(null, ci = false))
        assertEquals(UpdateMode.No, UpdateMode.parse("auto", ci = true))
    }

    @Test fun theDiffKeepsThreeLinesOfContext() {
        val old = (1..10).joinToString("\n")
        val diff = lineDiff(old, old.replace("\n9\n", "\nnine\n"))
        assertEquals(
            listOf("    6     6 │ 6", "    9       │-9", "          9 │+nine", "   10    10 │ 10"),
            diff.lines().filter { "│" in it && (it.endsWith(" 6") || "9" in it || "nine" in it || it.endsWith("10")) },
        )
        assertEquals(false, diff.contains("│ 5"))
    }

    @Test fun debugFormatKeepsEmptyCollectionsInline() = assertEquals("[]", debugFormat(emptyList<Int>()))
}
