package snapshot

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

private fun tempDir(): String {
    val root = env("TMPDIR")?.trimEnd('/') ?: "/tmp"
    return "$root/kotlin-snapshot-${Random.nextLong().toULong()}"
}

private fun settings(mode: UpdateMode) = SnapshotSettings(snapshotDir = tempDir(), prefix = "t", update = mode)

class AssertTest {
    @Test fun aNewSnapshotIsWrittenPendingAndFails() {
        val s = settings(UpdateMode.New)
        val e = assertFailsWith<AssertionError> { withSettings(s) { assertSnapshot("first", "hello\n", "greet()") } }
        assertTrue(e.message!!.contains("is new"), e.message)
        assertNull(readText("${s.snapshotDir}/t__first.snap"))
        assertEquals("---\nexpression: greet()\n---\nhello\n", readText("${s.snapshotDir}/t__first.snap.new"))
    }

    @Test fun alwaysAcceptsThenAMatchPassesAndDropsThePendingFile() {
        val s = settings(UpdateMode.Always)
        withSettings(s) { assertSnapshot("x", "a\nb") }
        assertEquals("---\n---\na\nb\n", readText("${s.snapshotDir}/t__x.snap"))
        writeText("${s.snapshotDir}/t__x.snap.new", "stale")
        withSettings(s.copy(update = UpdateMode.No)) { assertSnapshot("x", "a\r\nb\n\n") }
        assertNull(readText("${s.snapshotDir}/t__x.snap.new"))
    }

    @Test fun aMismatchShowsTheDiffAndKeepsTheInstaHeader() {
        val s = settings(UpdateMode.New)
        makeDirs(s.snapshotDir)
        val insta = "---\nsource: src/r.rs\nassertion_line: 9\nexpression: out\n---\n"
        writeText("${s.snapshotDir}/t__r.snap", "${insta}one\ntwo\nthree\n")
        val e = assertFailsWith<AssertionError> { withSettings(s) { assertSnapshot("r", "one\n2\nthree") } }
        assertTrue(e.message!!.contains("    2       │-two"), e.message)
        assertTrue(e.message!!.contains("          2 │+2"), e.message)
        assertEquals(
            "${insta}one\n2\nthree\n",
            readText("${s.snapshotDir}/t__r.snap.new"),
        )
    }

    @Test fun noWritesNothing() {
        val s = settings(UpdateMode.No)
        assertFailsWith<AssertionError> { withSettings(s) { assertSnapshot("n", "v") } }
        assertNull(readText("${s.snapshotDir}/t__n.snap.new"))
    }

    @Test fun redactionsApplyBeforeComparing() {
        val s = settings(UpdateMode.Always).redact("""\d{4}-\d{2}-\d{2}""", "[date]")
        withSettings(s) { assertSnapshot("d", "at 2026-10-27") }
        assertEquals("at [date]", SnapFile.parse(readText("${s.snapshotDir}/t__d.snap")!!).contents)
        withSettings(s.copy(update = UpdateMode.No)) { assertSnapshot("d", "at 1999-01-01") }
    }

    @Test fun debugSnapshotsAreMultiLine() {
        data class P(
            val name: String,
            val tags: List<String>,
        )
        val s = settings(UpdateMode.Always)
        withSettings(s) { assertDebugSnapshot("p", P("a", listOf("x", "y"))) }
        val want = "P(\n    name=a,\n    tags=[\n        x,\n        y,\n    ],\n)"
        assertEquals(want, SnapFile.parse(readText("${s.snapshotDir}/t__p.snap")!!).contents)
    }

    @Test fun inlineSnapshotsIgnoreIndentation() {
        assertInlineSnapshot(
            "a\n  b\n",
            """
            a
              b
            """,
        )
        val e = assertFailsWith<AssertionError> { assertInlineSnapshot("a\nc", "a\nb") }
        assertTrue(e.message!!.contains("│-b") && e.message!!.contains("│+c"), e.message)
    }
}
