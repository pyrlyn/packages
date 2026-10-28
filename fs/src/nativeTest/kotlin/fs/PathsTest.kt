package fs

import kotlinx.io.files.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PathsTest {
    /** A Windows name may be any UTF-16; one that is not valid Unicode must reach the OS as it was written. */
    @Test
    fun parentAndNameKeepAnUnpairedSurrogate() {
        val path = Path("dir\uDC00", "tool\uD800")
        assertEquals(Path("dir\uDC00"), path.exactParent)
        assertEquals("tool\uD800", path.exactName)
    }

    @Test
    fun parentAndNameMatchKotlinxIoForValidNames() {
        for (path in listOf(Path("a", "b", "c"), Path("/a"), Path("Zoë", "工具"), Path("tool"), Path("/"))) {
            assertEquals(path.parent, path.exactParent, "$path")
            assertEquals(path.name, path.exactName, "$path")
        }
        assertNull(Path("tool").exactParent)
    }

    /** The parent [atomic] stages its temp file in, and the directories [createDirAll] makes, keep the name too. */
    @Test
    fun aFileIsWrittenUnderADirectoryWhoseNameIsNotValidUnicode(): Unit =
        withTempDir { dir ->
            val file = Path(dir, "a\uD800", "b\uDC00", "file")
            createDirAll(file.exactParent!!)
            atomic(file, "x".encodeToByteArray())
            assertEquals("x", readText(file))
        }
}
