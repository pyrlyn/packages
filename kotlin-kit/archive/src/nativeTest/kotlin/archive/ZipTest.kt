package archive

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ZipTest {
    private fun <T> zip(
        bytes: ByteArray,
        body: (ZipReader) -> T,
    ): T = withFile(bytes) { path -> ZipReader(path).use(body) }

    private fun ZipReader.text(name: String) = readAll(open(entries.single { it.name == name })).decodeToString()

    private fun sample(): ZipWriter =
        ZipWriter().apply {
            dir("pkg/")
            file("pkg/tool", "#!/bin/sh\n".repeat(100).encodeToByteArray(), octal("755"))
            file("pkg/readme", "stored".encodeToByteArray(), deflated = false)
            symlink("pkg/alias", "tool")
        }

    @Test
    fun membersRoundTripWithTheirModes() =
        zip(sample().finish()) { z ->
            assertEquals(listOf("pkg/", "pkg/tool", "pkg/readme", "pkg/alias"), z.entries.map { it.name })
            assertTrue(z.entries[0].isDir)
            assertFalse(z.entries[1].isDir)
            assertEquals(octal("100755"), z.entries[1].unixMode)
            assertEquals(octal("120777"), z.entries[3].unixMode)
            assertEquals("#!/bin/sh\n".repeat(100), z.text("pkg/tool"))
            assertEquals("stored", z.text("pkg/readme"))
            assertEquals("tool", z.text("pkg/alias"))
        }

    @Test
    fun dosAttributesBecomeAModeAsRustZipMakesOne() =
        zip(
            ZipWriter()
                .apply {
                    dosFile("plain", ByteArray(0))
                    dosFile("read-only", ByteArray(0), 0x01u)
                    dosFile("folder\\", ByteArray(0), 0x10u)
                    dosFile("archived", ByteArray(0), 0x20u)
                }.finish(),
        ) { z ->
            assertNull(z.entries[0].unixMode)
            assertEquals(octal("444"), z.entries[1].unixMode)
            assertEquals(octal("40775"), z.entries[2].unixMode)
            assertTrue(z.entries[2].isDir)
            assertEquals(octal("100664"), z.entries[3].unixMode)
        }

    @Test
    fun zip64EndRecordsAreRead() = zip(sample().finish(zip64 = true)) { z -> assertEquals("stored", z.text("pkg/readme")) }

    @Test
    fun aZipBehindAStubIsFound() =
        zip(ByteArray(4096) { 'M'.code.toByte() } + sample().finish(comment = "made by a test")) { z ->
            assertEquals("tool", z.text("pkg/alias"))
        }

    @Test
    fun aMemberWhoseCrcDisagreesIsCorrupt() {
        val bytes = ZipWriter().apply { file("a", "payload".encodeToByteArray(), deflated = false) }.finish()
        bytes[LOCAL_HEADER + 1 + 2] = 'X'.code.toByte()
        zip(bytes) { z -> assertKind(ArchiveException.Kind.Corrupt) { readAll(z.open(z.entries[0])) } }
    }

    @Test
    fun anEncryptedOrUnknownMethodMemberIsUnsupported() {
        val bytes = ZipWriter().apply { file("a", "payload".encodeToByteArray()) }.finish()
        val central = centralAt(bytes)
        bytes[central + 8] = 1
        zip(bytes) { z -> assertKind(ArchiveException.Kind.Unsupported) { z.open(z.entries[0]) } }
        bytes[central + 8] = 0
        bytes[central + 10] = 14
        zip(bytes) { z -> assertKind(ArchiveException.Kind.Unsupported) { z.open(z.entries[0]) } }
    }

    @Test
    fun aFileThatIsNotAZipIsCorrupt() {
        assertKind(ArchiveException.Kind.Corrupt) { withFile(ByteArray(100)) { ZipReader(it) } }
        val cut = sample().finish()
        assertKind(ArchiveException.Kind.Corrupt) { withFile(cut.copyOf(cut.size - 30)) { ZipReader(it) } }
    }

    @Test
    fun aMemberCountTheDirectoryCannotHoldIsCorrupt() {
        val bytes = sample().finish()
        val eocd = bytes.size - EOCD
        bytes[eocd + 10] = 0xFF.toByte()
        bytes[eocd + 11] = 0x7F
        assertKind(ArchiveException.Kind.Corrupt) { withFile(bytes) { ZipReader(it) } }
    }

    @Test
    fun aLocalHeaderOutsideTheFileIsCorrupt() {
        val bytes = ZipWriter().apply { file("a", "payload".encodeToByteArray()) }.finish()
        val central = centralAt(bytes)
        bytes[central + 45] = 0x70
        zip(bytes) { z -> assertKind(ArchiveException.Kind.Corrupt) { z.open(z.entries[0]) } }
    }

    @Test
    fun aNameWithoutTheUtf8FlagIsCp437() = assertEquals("Ç╬ a", cp437(byteArrayOf(0x80.toByte(), 0xCE.toByte(), 0x20, 0x61)))

    @Test
    fun aMemberLargerThanOneBufferSegmentSurvivesALargeRead() {
        // Past kotlinx.io's 8 KiB segment: the copy of a large read has to cross from one segment to the next.
        val body = ByteArray(100_000) { (it * 31 % 251).toByte() }
        val bytes =
            ZipWriter()
                .apply {
                    file("deflated", body)
                    file("stored", body, deflated = false)
                }.finish()
        zip(bytes) { z ->
            for (entry in z.entries) assertContentEquals(body, readAll(z.open(entry), chunk = 64 * 1024L), entry.name)
        }
    }

    private fun centralAt(bytes: ByteArray): Int = (0 until bytes.size - 4).first { bytes.u32(it) == CENTRAL_SIG }
}
