// The numbers here are field widths and offsets from the record layouts of PKWARE's APPNOTE.
@file:Suppress("MagicNumber")

package archive

import kotlinx.io.Buffer
import kotlinx.io.readByteArray

private val S_IFREG = "100000".toInt(8)
private val S_IFLNK = "120000".toInt(8)
private val S_IFDIR = "40000".toInt(8)
private val DEFAULT_FILE_MODE = "644".toInt(8)
private val DEFAULT_DIR_MODE = "755".toInt(8)
private val LINK_MODE = "777".toInt(8)

/** A unix system and zip 2.0, as Rust zip `ZipWriter` writes them. */
private const val MADE_BY_UNIX = (3 shl 8) or 20
private const val MADE_BY_DOS = 20
private const val NEEDED = 20
private const val HALF = 16

/** Builds a zip in memory, members written as Rust zip `ZipWriter` writes them with `unix_permissions`. */
class ZipWriter {
    private class Member(
        val name: ByteArray,
        val method: Int,
        val flags: Int,
        val crc: UInt,
        val compressed: Int,
        val size: Int,
        val madeBy: Int,
        val attributes: UInt,
        val offset: Long,
    )

    private val out = Buffer()
    private var written = 0L
    private val members = mutableListOf<Member>()

    fun file(
        name: String,
        body: ByteArray,
        mode: Int = DEFAULT_FILE_MODE,
        deflated: Boolean = true,
    ) = entry(name, body, ((S_IFREG or mode).toUInt() shl HALF), MADE_BY_UNIX, deflated)

    /** A member from a DOS or Windows writer: [attributes] are the DOS bits, no unix mode. */
    fun dosFile(
        name: String,
        body: ByteArray,
        attributes: UInt = 0u,
    ) = entry(name, body, attributes, MADE_BY_DOS, deflated = true)

    fun symlink(
        name: String,
        target: String,
    ) = entry(name, target.encodeToByteArray(), ((S_IFLNK or LINK_MODE).toUInt() shl HALF), MADE_BY_UNIX, deflated = false)

    fun dir(name: String) = entry(name, ByteArray(0), ((S_IFDIR or DEFAULT_DIR_MODE).toUInt() shl HALF), MADE_BY_UNIX, deflated = false)

    fun entry(
        name: String,
        body: ByteArray,
        attributes: UInt,
        madeBy: Int,
        deflated: Boolean,
    ) {
        val nameBytes = name.encodeToByteArray()
        val data = if (deflated) deflate(body) else body
        val method = if (deflated) METHOD_DEFLATE else METHOD_STORED
        val member = Member(nameBytes, method, FLAG_UTF8, crc32(body), data.size, body.size, madeBy, attributes, written)
        members += member
        val local = Record()
        local
            .u32(LOCAL_SIG)
            .u16(NEEDED)
            .u16(member.flags)
            .u16(method)
            .u16(0)
            .u16(0)
        local
            .u32(member.crc)
            .u32(data.size.toUInt())
            .u32(body.size.toUInt())
            .u16(nameBytes.size)
            .u16(0)
        emit(local.bytes() + nameBytes + data)
    }

    /** The archive; [zip64] adds the zip64 end records an archive past 4 GiB or 65535 members needs. */
    fun finish(
        zip64: Boolean = false,
        comment: String = "",
    ): ByteArray {
        val cdStart = written
        for (m in members) {
            val central = Record()
            central
                .u32(CENTRAL_SIG)
                .u16(m.madeBy)
                .u16(NEEDED)
                .u16(m.flags)
                .u16(m.method)
                .u16(0)
                .u16(0)
            central
                .u32(m.crc)
                .u32(m.compressed.toUInt())
                .u32(m.size.toUInt())
                .u16(m.name.size)
                .u16(0)
                .u16(0)
            central
                .u16(0)
                .u16(0)
                .u32(m.attributes)
                .u32(m.offset.toUInt())
            emit(central.bytes() + m.name)
        }
        val cdSize = written - cdStart
        if (zip64) zip64End(cdStart, cdSize)
        val count = if (zip64) U16_MAX else members.size
        val eocd = Record()
        eocd
            .u32(EOCD_SIG)
            .u16(0)
            .u16(0)
            .u16(count)
            .u16(count)
        eocd.u32(if (zip64) U32_MAX.toUInt() else cdSize.toUInt()).u32(if (zip64) U32_MAX.toUInt() else cdStart.toUInt())
        val commentBytes = comment.encodeToByteArray()
        eocd.u16(commentBytes.size)
        emit(eocd.bytes() + commentBytes)
        return out.readByteArray()
    }

    private fun zip64End(
        cdStart: Long,
        cdSize: Long,
    ) {
        val at = written
        val record = Record()
        record
            .u32(ZIP64_EOCD_SIG)
            .u64(ZIP64_EOCD - 12L)
            .u16(MADE_BY_UNIX)
            .u16(NEEDED)
            .u32(0u)
            .u32(0u)
        record
            .u64(members.size.toLong())
            .u64(members.size.toLong())
            .u64(cdSize)
            .u64(cdStart)
        emit(record.bytes())
        val locator = Record()
        locator
            .u32(ZIP64_LOCATOR_SIG)
            .u32(0u)
            .u64(at)
            .u32(1u)
        emit(locator.bytes())
    }

    private fun emit(bytes: ByteArray) {
        out.write(bytes)
        written += bytes.size
    }

    /** Little-endian fields, as every zip record lays them out. */
    private class Record {
        private val buffer = Buffer()

        fun u16(v: Int) = apply { buffer.write(byteArrayOf(v.toByte(), (v shr 8).toByte())) }

        fun u32(v: UInt) = apply { u16((v and 0xFFFFu).toInt()).u16((v shr HALF).toInt()) }

        fun u64(v: Long) = apply { u32(v.toUInt()).u32((v ushr 32).toUInt()) }

        fun bytes() = buffer.readByteArray()
    }
}
