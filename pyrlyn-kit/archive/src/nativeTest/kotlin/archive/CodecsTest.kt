package archive

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class CodecsTest {
    private val plain = ByteArray(200_000) { (it * 31 % 251).toByte() }

    @Test
    fun gzipRoundTrips() = assertContentEquals(plain, readAll(gzipSource(source(gzip(plain)))))

    @Test
    fun anEmptyGzipMemberIsEmpty() = assertContentEquals(ByteArray(0), readAll(gzipSource(source(gzip(ByteArray(0))))))

    @Test
    fun gzipReadsTheFirstMemberOnly() {
        val two = gzip("first".encodeToByteArray()) + gzip("second".encodeToByteArray())
        assertEquals("first", readAll(gzipSource(source(two))).decodeToString())
    }

    @Test
    fun aGzipCutShortIsTruncated() {
        val whole = gzip(plain)
        assertKind(ArchiveException.Kind.Truncated) { readAll(gzipSource(source(whole.copyOf(whole.size / 2)))) }
    }

    @Test
    fun aGzipWhoseCrcDisagreesIsCorrupt() {
        val bytes = gzip(plain)
        bytes[bytes.size - 8] = (bytes[bytes.size - 8] + 1).toByte()
        assertKind(ArchiveException.Kind.Corrupt) { readAll(gzipSource(source(bytes))) }
    }

    @Test
    fun deflateRoundTrips() = assertContentEquals(plain, readAll(deflateSource(source(deflate(plain))), chunk = 4096))

    @Test
    fun bzip2RoundTrips() = assertContentEquals(plain, readAll(bzip2Source(source(bzip2(plain)))))

    @Test
    fun bzip2ReadsTheFirstStreamOnly() {
        val two = bzip2("first".encodeToByteArray()) + bzip2("second".encodeToByteArray())
        assertEquals("first", readAll(bzip2Source(source(two))).decodeToString())
    }

    @Test
    fun aBzip2CutShortIsTruncated() {
        val whole = bzip2(plain)
        assertKind(ArchiveException.Kind.Truncated) { readAll(bzip2Source(source(whole.copyOf(whole.size / 2)))) }
    }

    @Test
    fun bytesThatAreNotBzip2AreCorrupt() = assertKind(ArchiveException.Kind.Corrupt) { readAll(bzip2Source(source("BZh9 not really".encodeToByteArray()))) }

    @Test
    fun xzReadsConcatenatedStreams() = assertEquals("hellohello", readAll(xzSource(source(XZ_HELLO + XZ_HELLO), LIMIT)).decodeToString())

    @Test
    fun anXzDecoderOverTheMemoryLimitIsRefused() = assertKind(ArchiveException.Kind.MemoryLimit) { readAll(xzSource(source(XZ_HELLO), 1024)) }

    @Test
    fun anXzCutShortIsTruncated() = assertKind(ArchiveException.Kind.Truncated) { readAll(xzSource(source(XZ_HELLO.copyOf(XZ_HELLO.size - 6)), LIMIT)) }

    @Test
    fun bytesThatAreNotXzAreCorrupt() = assertKind(ArchiveException.Kind.Corrupt) { readAll(xzSource(source(plain.copyOf(64)), LIMIT)) }

    @Test
    fun crc32IsTheZipAndGzipOne() {
        assertEquals(0xCBF43926u, crc32("123456789".encodeToByteArray()))
        val whole = plain.copyOf(1000)
        assertEquals(crc32(whole), crc32(whole, 500, 500, crc32(whole, 0, 500)))
    }

    private companion object {
        const val LIMIT = 256L * 1024 * 1024

        /** `printf hello | xz`: one stream, check CRC64. */
        @OptIn(ExperimentalStdlibApi::class)
        val XZ_HELLO =
            (
                "fd377a585a000004e6d6b446020021010c0000008f98419c010004" +
                    "68656c6c6f00000000b137b9dbe5da1e9b00011d05b82d80af1fb6f37d010000000004595a"
            ).hexToByteArray()
    }
}
