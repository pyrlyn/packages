package fs

import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TempDirTest {
    @Test
    fun aDirectoryMadeInsideAnotherIsRemovedWithItsContents() {
        TempDir.create().use { outer ->
            val inner = TempDir.createIn(outer.path, "stage-")
            assertEquals(outer.path, inner.path.parent)
            assertTrue(inner.path.name.startsWith("stage-"), inner.path.name)
            joinPath(inner.path, "file").let { atomic(it, "x".encodeToByteArray()) }
            inner.close()
            assertNull(entryKind(inner.path))
            assertEquals(emptyList(), SystemFileSystem.list(outer.path))
        }
    }

    @Test
    fun aMissingParentFailsAsAFileError() {
        TempDir.create().use { outer ->
            val err = assertFailsWith<FsException> { TempDir.createIn(joinPath(outer.path, "nope")) }
            assertEquals(FsException.Kind.NotFound, err.kind)
        }
    }

    @Test
    fun aNewTempDirIsMadeInTheSystemTempDir() {
        TempDir.create().use { assertEquals(canon(systemTempDir()), canon(it.path).parent) }
    }
}
