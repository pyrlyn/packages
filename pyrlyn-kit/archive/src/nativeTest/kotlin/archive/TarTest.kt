package archive

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class TarTest {
    private fun read(bytes: ByteArray) = TarReader(source(bytes))

    private fun TarReader.member() = assertNotNull(next())

    private fun TarReader.bytes() = readAll(body())

    @Test
    fun membersRoundTripWithTheirKindModeAndLink() {
        val tar =
            TarWriter()
                .apply {
                    dir("pkg/", octal("750"))
                    file("pkg/tool", "#!/bin/sh\n".encodeToByteArray(), octal("755"))
                    symlink("pkg/alias", "tool")
                    hardLink("pkg/again", "pkg/tool")
                }.finish()
        read(tar).use { r ->
            r.member().let {
                assertEquals("pkg/", it.path)
                assertEquals(TarType.Directory, it.type)
                assertEquals(octal("750"), it.mode)
            }
            r.member().let {
                assertEquals(TarType.Regular, it.type)
                assertEquals(10, it.size)
                assertEquals(0L, it.mtime)
                assertEquals("#!/bin/sh\n", r.bytes().decodeToString())
            }
            r.member().let {
                assertEquals(TarType.Symlink, it.type)
                assertEquals("tool", it.linkName)
            }
            r.member().let {
                assertEquals(TarType.Link, it.type)
                assertEquals("pkg/tool", it.linkName)
            }
            assertNull(r.next())
            assertNull(r.next())
        }
    }

    @Test
    fun aBodyLeftUnreadIsSkipped() {
        val tar =
            TarWriter()
                .apply {
                    file("a", ByteArray(1000) { 1 })
                    file("b", "second".encodeToByteArray())
                }.finish()
        read(tar).use { r ->
            r.member()
            readAll(r.body()).let { assertEquals(1000, it.size) }
            r.member()
            assertEquals("second", r.bytes().decodeToString())
        }
        read(tar).use { r ->
            assertEquals("a", r.member().path)
            assertEquals("b", r.member().path)
        }
    }

    @Test
    fun longNamesAndLinksComeFromTheirGnuRecords() {
        val path = "d/".repeat(80) + "tool"
        val target = "../".repeat(50) + "x"
        val tar = TarWriter().apply { symlink(path, target) }.finish()
        read(tar).use { r ->
            val entry = r.member()
            assertEquals(path, entry.path)
            assertEquals(target, entry.linkName)
            assertNull(r.next())
        }
    }

    @Test
    fun paxPathLinkAndSizeOverrideTheHeader() {
        val tar =
            TarWriter()
                .apply {
                    pax(mapOf("path" to "from/pax", "linkpath" to "pax-target", "size" to "3"))
                    entry("header-name", '0', "abc".encodeToByteArray(), octal("644"), size = 99)
                }.finish()
        read(tar).use { r ->
            val entry = r.member()
            assertEquals("from/pax", entry.path)
            assertEquals("pax-target", entry.linkName)
            assertEquals(3, entry.size)
            assertEquals("abc", r.bytes().decodeToString())
            assertNull(r.next())
        }
    }

    @Test
    fun aUstarPrefixIsJoinedToTheName() {
        val tar =
            TarWriter()
                .apply {
                    entry("name", '0', ByteArray(0), octal("644")) { header ->
                        "ustar\u000000".encodeToByteArray().copyInto(header, MAGIC_AT)
                        "some/prefix".encodeToByteArray().copyInto(header, PREFIX_AT)
                    }
                }.finish()
        assertEquals("some/prefix/name", read(tar).member().path)
    }

    @Test
    fun aBase256SizeIsRead() {
        val tar =
            TarWriter()
                .apply {
                    entry("big", '0', "hello".encodeToByteArray(), octal("644")) { header ->
                        header.fill(0, SIZE_AT, SIZE_AT + NUMBER_LEN)
                        header[SIZE_AT] = 0x80.toByte()
                        header[SIZE_AT + NUMBER_LEN - 1] = 5
                    }
                }.finish()
        read(tar).use { r ->
            assertEquals(5, r.member().size)
            assertEquals("hello", r.bytes().decodeToString())
        }
    }

    @Test
    fun aHeaderWhoseChecksumDisagreesIsCorrupt() {
        val tar = TarWriter().apply { file("a", ByteArray(0)) }.finish()
        tar[0] = 'b'.code.toByte()
        assertKind(ArchiveException.Kind.Corrupt) { read(tar).next() }
    }

    @Test
    fun aSizeThatIsNotANumberIsCorrupt() {
        val tar =
            TarWriter()
                .apply { entry("a", '0', ByteArray(0), octal("644")) { "9zz".encodeToByteArray().copyInto(it, SIZE_AT) } }
                .finish()
        assertKind(ArchiveException.Kind.Corrupt) { read(tar).next() }
    }

    @Test
    fun aModeThatIsNotANumberIsNull() {
        val tar =
            TarWriter()
                .apply { entry("a", '0', ByteArray(0), octal("644")) { "9".encodeToByteArray().copyInto(it, MODE_AT) } }
                .finish()
        assertNull(read(tar).member().mode)
    }

    @Test
    fun anArchiveCutInsideAHeaderOrABodyIsTruncated() {
        val tar = TarWriter().apply { file("a", ByteArray(2000) { 7 }) }.finish()
        assertKind(ArchiveException.Kind.Truncated) { read(tar.copyOf(300)).next() }
        read(tar.copyOf(BLOCK + 1000)).use { r ->
            r.member()
            assertKind(ArchiveException.Kind.Truncated) { r.bytes() }
        }
        read(tar.copyOf(BLOCK + 1000)).use { r ->
            r.member()
            assertKind(ArchiveException.Kind.Truncated) { r.next() }
        }
    }

    @Test
    fun anArchiveWithoutItsEndBlocksJustEnds() {
        val tar = TarWriter().apply { file("a", "x".encodeToByteArray()) }.finish()
        read(tar.copyOf(2 * BLOCK)).use { r ->
            r.member()
            assertNull(r.next())
        }
    }

    @Test
    fun oldGnuSparseMembersAreExpanded() {
        val stored = ByteArray(512) { 'A'.code.toByte() } + ByteArray(100) { 'B'.code.toByte() }
        val tar =
            TarWriter()
                .apply {
                    entry("sparse", 'S', stored, octal("644")) { header ->
                        sparse(header, 0, 1024, 512)
                        sparse(header, 1, 2048, 100)
                        sparse(header, 2, 3000, 0)
                        header.putNumber(REAL_SIZE_AT, NUMBER_LEN, 3000)
                    }
                    file("after", "z".encodeToByteArray())
                }.finish()
        read(tar).use { r ->
            assertEquals(3000, r.member().size)
            val expected = ByteArray(3000)
            stored.copyInto(expected, 1024, 0, 512)
            stored.copyInto(expected, 2048, 512, 612)
            assertContentEquals(expected, r.bytes())
            assertEquals("after", r.member().path)
            assertEquals("z", r.bytes().decodeToString())
        }
    }

    @Test
    fun aSparseMapThatOverlapsOrOverrunsIsCorrupt() {
        fun withMap(vararg runs: Pair<Long, Long>) =
            TarWriter()
                .apply {
                    entry("sparse", 'S', ByteArray(512), octal("644")) { header ->
                        runs.forEachIndexed { i, (offset, length) -> sparse(header, i, offset, length) }
                        header.putNumber(REAL_SIZE_AT, NUMBER_LEN, 4096)
                    }
                }.finish()
        assertKind(ArchiveException.Kind.Corrupt) { read(withMap(1024L to 256L, 512L to 256L)).next() }
        assertKind(ArchiveException.Kind.Corrupt) { read(withMap(0L to 1024L)).next() }
        assertKind(ArchiveException.Kind.Corrupt) { read(withMap(0L to 512L)).next() }
    }

    @Test
    fun aGnuLongNameOverTheCapIsRefused() {
        val tar = TarWriter().apply { entry("././@LongLink", 'L', ByteArray(64 * 1024 + 1) { 'a'.code.toByte() }, octal("644")) }
        assertKind(ArchiveException.Kind.Unsupported) { read(tar.finish()).next() }
    }

    @Test
    fun twoLongNamesForOneMemberAreCorrupt() {
        val tar =
            TarWriter()
                .apply {
                    entry("././@LongLink", 'L', "one\u0000".encodeToByteArray(), octal("644"))
                    entry("././@LongLink", 'L', "two\u0000".encodeToByteArray(), octal("644"))
                    file("x", ByteArray(0))
                }.finish()
        assertKind(ArchiveException.Kind.Corrupt) { read(tar).next() }
    }

    @Test
    fun recordsOnlyFoldIntoAMemberWhereTarRsFoldsThem() {
        val tar =
            TarWriter()
                .apply {
                    entry("global", 'g', "9 a=bcd\n".encodeToByteArray(), octal("644"))
                    entry("not-gnu", 'L', "x\u0000".encodeToByteArray(), octal("644")) {
                        "ustar\u000000".encodeToByteArray().copyInto(it, MAGIC_AT)
                    }
                }.finish()
        read(tar).use { r ->
            assertEquals(TarType.PaxGlobal, r.member().type)
            assertEquals(TarType.GnuLongName, r.member().type)
            assertNull(r.next())
        }
    }

    private fun sparse(
        header: ByteArray,
        index: Int,
        offset: Long,
        length: Long,
    ) {
        val at = SPARSE_AT + index * SPARSE_PAIR
        header.putNumber(at, NUMBER_LEN, offset)
        header.putNumber(at + NUMBER_LEN, NUMBER_LEN, length)
    }
}
