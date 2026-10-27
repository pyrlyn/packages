// The zip records the reader and the writer share, and the central directory reader. The numbers in it are offsets
// and lengths from the record layouts of PKWARE's APPNOTE, which read best as they are written there.
@file:Suppress("MagicNumber")

package archive

import kotlinx.io.Source
import kotlinx.io.buffered
import kotlinx.io.readByteArray

internal const val LOCAL_SIG = 0x04034b50u
internal const val CENTRAL_SIG = 0x02014b50u
internal const val EOCD_SIG = 0x06054b50u
internal const val ZIP64_LOCATOR_SIG = 0x07064b50u
internal const val ZIP64_EOCD_SIG = 0x06064b50u

internal const val LOCAL_HEADER = 30
internal const val LOCAL_NAME_LEN = 26
internal const val LOCAL_EXTRA_LEN = 28
internal const val CENTRAL_HEADER = 46
internal const val EOCD = 22
internal const val ZIP64_LOCATOR = 20
internal const val ZIP64_EOCD = 56
internal const val MAX_COMMENT = 0xFFFF

internal const val ZIP64_EXTRA = 0x0001
internal const val UNICODE_PATH_EXTRA = 0x7075
internal const val U16_MAX = 0xFFFF
internal const val U32_MAX = 0xFFFFFFFFL

private const val BYTE = 0xFF
private const val BITS = 8
private const val ASCII = 0x80

internal fun ByteArray.u16(at: Int): Int = (this[at].toInt() and BYTE) or ((this[at + 1].toInt() and BYTE) shl BITS)

internal fun ByteArray.u32(at: Int): UInt = (u16(at) or (u16(at + 2) shl (2 * BITS))).toUInt()

internal fun ByteArray.u64(at: Int): Long = (u32(at).toLong()) or (u32(at + 4).toLong() shl (4 * BITS))

/** CP437's upper half, the encoding of a zip name without the UTF-8 flag; the lower half is ASCII. */
private const val CP437_HIGH =
    "ÇüéâäàåçêëèïîìÄÅÉæÆôöòûùÿÖÜ¢£¥₧ƒáíóúñÑªº¿⌐¬½¼¡«»░▒▓│┤╡╢╖╕╣║╗╝╜╛┐└┴┬├─┼╞╟╚╔╩╦╠═╬╧╨╤╥╙╘╒╓╫╪┘┌█▄▌▐▀" +
        "αßΓπΣσµτΦΘΩδ∞φε∩≡±≥≤⌠⌡÷≈°∙·√ⁿ²■ "

internal fun cp437(bytes: ByteArray): String =
    buildString(bytes.size) {
        for (b in bytes) {
            val c = b.toInt() and BYTE
            append(if (c < ASCII) c.toChar() else CP437_HIGH[c - ASCII])
        }
    }

/** Where the central directory is, and how far the whole archive sits into the file (a self-extractor's stub). */
private class Directory(
    val start: Long,
    val size: Long,
    val count: Long,
)

internal class CentralDirectory(
    private val file: PositionalFile,
) {
    /** The directory's offset as the archive states it, before any shift for prepended data. */
    private var statedOffset = 0L

    fun read(): List<ZipEntry> {
        val dir = locate()
        val src = FileRange(file, dir.start, dir.size).buffered()
        val base = dir.start - statedOffset
        // Each record takes at least its fixed part, so a count the directory cannot hold is a lie.
        if (dir.count > dir.size / CENTRAL_HEADER) corrupt("zip claims ${dir.count} members in ${dir.size} bytes")
        return List(dir.count.toInt()) { entry(src, base) }
    }

    private fun locate(): Directory {
        val eocdAt = findEocd()
        val eocd = file.read(eocdAt, EOCD)
        var count = eocd.u16(10).toLong()
        var size = eocd.u32(12).toLong()
        var offset = eocd.u32(16).toLong()
        var recordAt = eocdAt
        zip64(eocdAt)?.let { (at, z) ->
            count = z.u64(32)
            size = z.u64(40)
            offset = z.u64(48)
            recordAt = at
        }
        if (count < 0 || size < 0 || offset < 0) corrupt("zip central directory is out of range")
        statedOffset = offset
        val shift = recordAt - size - offset
        if (shift < 0) corrupt("zip central directory lies outside the archive")
        // Data prepended to the zip moves every offset in it; the directory is found right before its end record.
        val start = if (signatureAt(offset, CENTRAL_SIG) || count == 0L) offset else offset + shift
        file.check(start, size, "the zip central directory")
        return Directory(start, size, count)
    }

    private fun findEocd(): Long {
        val tailLen = minOf(file.length, (EOCD + MAX_COMMENT).toLong()).toInt()
        if (tailLen < EOCD) corrupt("not a zip: too short for an end of central directory record")
        val tailAt = file.length - tailLen
        val tail = file.read(tailAt, tailLen)
        for (i in tailLen - EOCD downTo 0) {
            if (tail.u32(i) == EOCD_SIG && i + EOCD + tail.u16(i + EOCD - 2) <= tailLen) return tailAt + i
        }
        corrupt("not a zip: no end of central directory record")
    }

    private fun zip64(eocdAt: Long): Pair<Long, ByteArray>? {
        val locatorAt = eocdAt - ZIP64_LOCATOR
        if (locatorAt < 0 || !signatureAt(locatorAt, ZIP64_LOCATOR_SIG)) return null
        val at = file.read(locatorAt, ZIP64_LOCATOR).u64(8)
        if (at < 0 || !signatureAt(at, ZIP64_EOCD_SIG)) corrupt("zip64 end of central directory record is missing")
        return at to file.read(at, ZIP64_EOCD)
    }

    private fun signatureAt(
        at: Long,
        sig: UInt,
    ): Boolean = at >= 0 && at <= file.length - 4 && file.read(at, 4).u32(0) == sig

    private fun entry(
        src: Source,
        base: Long,
    ): ZipEntry {
        val fixed = bytes(src, CENTRAL_HEADER)
        if (fixed.u32(0) != CENTRAL_SIG) corrupt("invalid central directory file header")
        val rawName = bytes(src, fixed.u16(28))
        val extra = bytes(src, fixed.u16(30))
        bytes(src, fixed.u16(32))
        val flags = fixed.u16(8)
        val wide = Wide(fixed.u32(24).toLong(), fixed.u32(20).toLong(), fixed.u32(42).toLong())
        var name = if (flags and FLAG_UTF8 != 0) rawName.decodeToString() else cp437(rawName)
        forEachExtra(extra) { id, at, len ->
            when (id) {
                ZIP64_EXTRA -> wide.apply(extra, at, len)
                UNICODE_PATH_EXTRA -> unicodePath(extra, at, len, rawName)?.let { name = it }
            }
        }
        return ZipEntry(
            name = name,
            method = fixed.u16(10),
            flags = flags,
            crc = fixed.u32(16),
            compressedSize = wide.compressed,
            size = wide.size,
            versionMadeBy = fixed.u16(4),
            externalAttributes = fixed.u32(38),
            localHeaderOffset = wide.offset + base,
        ).also { if (it.size < 0 || it.compressedSize < 0 || it.localHeaderOffset < 0) corrupt("${it.name} is out of range") }
    }

    private fun bytes(
        src: Source,
        n: Int,
    ): ByteArray {
        if (!src.request(n.toLong())) corrupt("zip central directory ends early")
        return src.readByteArray(n)
    }
}

/** The three fields a zip64 extra field widens, in the order it lists them, each only when the narrow one is full. */
private class Wide(
    var size: Long,
    var compressed: Long,
    var offset: Long,
) {
    fun apply(
        extra: ByteArray,
        at: Int,
        len: Int,
    ) {
        var p = at
        val end = at + len

        fun next(narrow: Long): Long {
            if (narrow != U32_MAX || p + 8 > end) return narrow
            return extra.u64(p).also { p += 8 }
        }
        size = next(size)
        compressed = next(compressed)
        offset = next(offset)
    }
}

/** Each extra field's id, start and length; a field that overruns the block ends the walk. */
private inline fun forEachExtra(
    extra: ByteArray,
    each: (id: Int, at: Int, len: Int) -> Unit,
) {
    var p = 0
    while (p + 4 <= extra.size) {
        val len = extra.u16(p + 2)
        if (p + 4 + len > extra.size) break
        each(extra.u16(p), p + 4, len)
        p += 4 + len
    }
}

/** Info-ZIP's Unicode path: version 1, the CRC of the name it replaces, then UTF-8. A stale one is ignored. */
private fun unicodePath(
    extra: ByteArray,
    at: Int,
    len: Int,
    rawName: ByteArray,
): String? {
    val valid = len >= 5 && extra[at] == 1.toByte() && extra.u32(at + 1) == crc32(rawName)
    return if (valid) extra.copyOfRange(at + 5, at + len).decodeToString() else null
}
