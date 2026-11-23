package fs

import kotlinx.io.files.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PathsTest {
    /** A Windows name may be any UTF-16; one that is not valid Unicode must reach the OS as it was written. */
    @Test
    fun parentAndNameKeepAnUnpairedSurrogate() {
        val path = joinPath("dir\uDC00", "tool\uD800")
        assertEquals(Path("dir\uDC00"), path.exactParent)
        assertEquals("tool\uD800", path.exactName)
    }

    @Test
    fun parentAndNameMatchKotlinxIoForValidNames() {
        for (path in listOf(joinPath("a", "b", "c"), Path("/a"), joinPath("Zoë", "工具"), Path("tool"), Path("/"))) {
            assertEquals(path.parent, path.exactParent, "$path")
            assertEquals(path.name, path.exactName, "$path")
        }
        assertNull(Path("tool").exactParent)
    }

    /** The parent [atomic] stages its temp file in, and the directories [createDirAll] makes, keep the name too. */
    @Test
    fun aFileIsWrittenUnderADirectoryWhoseNameIsNotValidUnicode(): Unit =
        withTempDir { dir ->
            val file = joinPath(dir, "a\uD800", "b\uDC00", "file")
            createDirAll(file.exactParent!!)
            atomic(file, "x".encodeToByteArray())
            assertEquals("x", readText(file))
        }

    /** Rust's `PathBuf::join`: the host's separator between parts, and none doubled after a base that ends in one. */
    @Test
    fun joinPathPutsTheHostSeparatorBetweenParts() {
        val sep = PATH_SEPARATOR.toString()
        assertEquals(listOf("root", "logs", "ketch.log").joinToString(sep), joinPath("root", "logs/ketch.log").toString())
        assertEquals("root${sep}bin", joinPath(Path("root"), "bin").toString())
        assertEquals("root/bin", joinPath("root/", "bin").toString())
        assertEquals("bin", joinPath("", "bin").toString())
    }
}
