@file:OptIn(ExperimentalForeignApi::class)

package fs

import fs.win.kit_CreateFileW
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.convert
import kotlinx.io.files.Path
import platform.windows.CloseHandle
import platform.windows.FILE_ATTRIBUTE_NORMAL
import platform.windows.GENERIC_READ
import platform.windows.OPEN_EXISTING
import kotlin.test.Test
import kotlin.test.assertEquals

class RenameWindowsTest {
    /** A reader holding the target, as another process reading a lock or a state file does; MoveFileExW alone refuses. */
    @Test
    fun renameReplacesAFileThatIsOpenElsewhere(): Unit =
        withTempDir { dir ->
            val target = Path(dir, "target")
            createNew(target, "old".encodeToByteArray())
            val reader = checkNotNull(opened(kit_CreateFileW(target.win, GENERIC_READ, SHARE_ALL, OPEN_EXISTING.convert(), FILE_ATTRIBUTE_NORMAL.convert())))
            try {
                atomic(target, "new".encodeToByteArray())
            } finally {
                CloseHandle(reader)
            }
            assertEquals("new", readText(target))
        }
}
