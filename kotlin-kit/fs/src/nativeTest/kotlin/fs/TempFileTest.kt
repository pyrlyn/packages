package fs

import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class TempFileTest {
    @Test
    fun aPersistedFileHoldsEveryPieceWrittenAndStays() {
        TempDir.create().use { dir ->
            val dest = joinPath(dir.path, "out")
            TempFile.createIn(dir.path).use { tmp ->
                unixModes { assertEquals("600".toInt(8), mode(tmp.path)) }
                tmp.write("hello ".encodeToByteArray())
                tmp.write("world!!".encodeToByteArray(), size = 5)
                tmp.persist(dest)
            }
            assertContentEquals("hello world".encodeToByteArray(), readAll(dest))
            assertEquals(listOf("out"), SystemFileSystem.list(dir.path).map { it.name })
        }
    }

    @Test
    fun aFileClosedWithoutPersistingIsRemoved() {
        TempDir.create().use { dir ->
            val tmp = TempFile.createIn(dir.path)
            tmp.write(byteArrayOf(1, 2, 3))
            tmp.close()
            assertNull(entryKind(tmp.path))
            assertFailsWith<IllegalStateException> { tmp.write(byteArrayOf(1)) }
        }
    }

    @Test
    fun aMissingDirectoryFailsAsAnFsError() {
        TempDir.create().use { dir ->
            assertFailsWith<FsException> { TempFile.createIn(joinPath(dir.path, "nope")) }
        }
    }
}
