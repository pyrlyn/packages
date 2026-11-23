package fs

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** REPARSE_DATA_BUFFERs built by hand, as FSCTL_GET_REPARSE_POINT returns them. */
class ReparseTest {
    private fun buffer(
        tag: Int,
        name: String,
        flags: Int? = null,
    ): ByteArray {
        val utf16 = name.flatMap { little(it.code, U16) }
        // Tag and data length, the substitute name at 0 of the path buffer, the print name right after it.
        val head = little(tag, U32) + little(0, U32) + little(0, U16) + little(utf16.size, U16) + little(utf16.size, U16) + little(0, U16)
        return (head + (flags?.let { little(it, U32) } ?: emptyList()) + utf16).toByteArray()
    }

    private fun little(
        value: Int,
        bytes: Int,
    ) = List(bytes) { (value ushr (BYTE_BITS * it)).toByte() }

    @Test
    fun anAbsoluteSymlinkLosesTheNtPrefix() {
        assertEquals("C:\\tools\\x", reparseTarget(buffer(SYMLINK, "\\??\\C:\\tools\\x", flags = 0)))
    }

    @Test
    fun aRelativeSymlinkIsKeptAsWritten() {
        assertEquals("..\\a", reparseTarget(buffer(SYMLINK, "..\\a", flags = 1)))
    }

    @Test
    fun aJunctionToAShareBecomesAUncPath() {
        assertEquals("\\\\host\\share", reparseTarget(buffer(MOUNT_POINT, "\\??\\UNC\\host\\share")))
        assertEquals("\\\\?\\Volume{1}\\", reparseTarget(buffer(MOUNT_POINT, "\\??\\Volume{1}\\")))
    }

    @Test
    fun anotherTagOrATruncatedBufferHasNoTarget() {
        assertNull(reparseTarget(buffer(OTHER_TAG, "x")))
        assertNull(reparseTarget(buffer(SYMLINK, "\\??\\C:\\x", flags = 0).copyOf(SYMLINK_HEAD + U16)))
        assertNull(reparseTarget(ByteArray(U16 + 1)))
    }

    private companion object {
        const val SYMLINK = 0xA000000C.toInt()
        const val MOUNT_POINT = 0xA0000003.toInt()
        const val OTHER_TAG = 0x80000017.toInt()
        const val SYMLINK_HEAD = 20
        const val U16 = 2
        const val U32 = 4
        const val BYTE_BITS = 8
    }
}
