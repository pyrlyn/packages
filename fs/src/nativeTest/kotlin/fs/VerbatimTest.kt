package fs

import kotlin.test.Test
import kotlin.test.assertEquals

/** dunce 1.0.5's own cases for `simplified`, `is_safe_to_strip_unc`, `is_valid_filename` and `is_reserved`. */
class VerbatimTest {
    private fun stripped(path: String) = withoutVerbatim(path) != path

    private fun nameKept(name: String) = isPlainName(name)

    @Test
    fun aPlainDiskPathLosesThePrefix() {
        assertEquals("C:\\foo\\\uD83D\uDE00", withoutVerbatim("\\\\?\\C:\\foo\\\uD83D\uDE00"))
        listOf("\\\\?\\C:\\foo\\bar", "\\\\?\\Z:\\foo\\bar\\", "\\\\?\\c:\\foo", "\\\\?\\c:\\${"®".repeat(LONG)}").forEach {
            assertEquals(true, stripped(it), it)
        }
    }

    @Test
    fun anythingButAVerbatimDiskKeepsItsForm() {
        listOf(
            "\\\\?\\serv\\",
            "\\\\.\\C:\\notdisk",
            "\\\\?\\GLOBALROOT\\Device\\ImDisk0\\path\\to\\file.txt",
            "\\\\?\\c\\foo",
            "\\\\?\\c\\foo/bar",
            "\\\\?\\c:foo",
            "\\\\?\\cc:foo",
            "\\\\?\\c:foo\\bar",
            "C:\\plain",
        ).forEach { assertEquals(it, withoutVerbatim(it)) }
    }

    @Test
    fun dotsAndLengthPastMaxPathKeepThePrefix() {
        val long = "®".repeat(LONG)
        listOf("\\\\?\\C:\\foo\\.\\bar", "\\\\?\\C:\\foo\\..\\bar", "\\\\?\\c:\\$long\\$long").forEach {
            assertEquals(it, withoutVerbatim(it))
        }
    }

    @Test
    fun aNameWin32WouldRewriteKeepsThePrefix() {
        listOf("aaaaaaaaaa:", "ą:ą", "a ", " a. ", "<x>", "a*", "?x", "a\u0000a", "\u001f", "a".repeat(TOO_LONG_NAME)).forEach {
            assertEquals(false, nameKept(it), it)
        }
        listOf("®".repeat(LONGEST_NAME), "ファイル", "a", "a.aaaaaaaa", "a........a", "       b").forEach {
            assertEquals(true, nameKept(it), it)
        }
    }

    @Test
    fun aReservedDeviceNameKeepsThePrefix() {
        listOf("CON", "con", "con.con", "COM4", "COM4.txt", "COM4 .txt", "con . .txt", "con.....txt").forEach {
            assertEquals(false, nameKept(it), it)
        }
        listOf(" CON", "COM0", "COM77", ".CON", "@CON", "not.CON", "CON。").forEach {
            assertEquals(true, nameKept(it), it)
        }
    }

    private companion object {
        const val LONG = 160
        const val LONGEST_NAME = 254
        const val TOO_LONG_NAME = 257
    }
}
