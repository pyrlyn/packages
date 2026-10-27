package archive

import kotlinx.io.Buffer
import kotlinx.io.readByteArray

private val DEFAULT_FILE_MODE = "644".toInt(8)
private val DEFAULT_DIR_MODE = "755".toInt(8)
private val LINK_MODE = "777".toInt(8)

/**
 * Builds a tar archive in memory the way tar-rs `Builder` does with GNU headers: a path or link target over 100 bytes
 * goes in a GNU long name or long link record first.
 */
class TarWriter {
    private val out = Buffer()

    fun file(
        path: String,
        body: ByteArray,
        mode: Int = DEFAULT_FILE_MODE,
    ) = entry(path, '0', body, mode)

    fun dir(
        path: String,
        mode: Int = DEFAULT_DIR_MODE,
    ) = entry(path, '5', ByteArray(0), mode)

    fun symlink(
        path: String,
        target: String,
        mode: Int = LINK_MODE,
    ) = entry(path, '2', ByteArray(0), mode, target)

    fun hardLink(
        path: String,
        target: String,
    ) = entry(path, '1', ByteArray(0), DEFAULT_FILE_MODE, target)

    /** A PAX header for the next member. */
    fun pax(records: Map<String, String>) {
        val body = Buffer()
        for ((key, value) in records) {
            val tail = " $key=$value\n".encodeToByteArray().size
            // The length counts its own digits, which can carry it over a power of ten.
            var len = tail + tail.toString().length
            while (tail + len.toString().length != len) len = tail + len.toString().length
            body.write("$len $key=$value\n".encodeToByteArray())
        }
        entry("././@PaxHeader", 'x', body.readByteArray(), DEFAULT_FILE_MODE)
    }

    /**
     * Any member. [tweak] edits the header before its checksum is set, for an archive no well-behaved writer makes;
     * [size] is what the header claims, [body] what follows it.
     */
    fun entry(
        path: String,
        type: Char,
        body: ByteArray,
        mode: Int,
        link: String = "",
        size: Long = body.size.toLong(),
        tweak: (ByteArray) -> Unit = {},
    ) {
        val name = longRecord(path, 'L')
        val target = longRecord(link, 'K')
        val header = gnuHeader(name, type, mode, size)
        target.copyInto(header, LINK_AT)
        tweak(header)
        seal(header)
        write(header, body)
    }

    /** The archive, ended by two blocks of zeros. */
    fun finish(): ByteArray {
        out.write(ByteArray(2 * BLOCK))
        return out.readByteArray()
    }

    /** What fits in the header for [value], after a GNU long record holding all of it when it does not fit. */
    private fun longRecord(
        value: String,
        type: Char,
    ): ByteArray {
        val bytes = value.encodeToByteArray()
        if (bytes.size <= NAME_LEN) return bytes
        val record = gnuHeader("././@LongLink".encodeToByteArray(), type, DEFAULT_FILE_MODE, bytes.size + 1L)
        seal(record)
        write(record, bytes + 0)
        return bytes.copyOf(NAME_LEN)
    }

    private fun gnuHeader(
        name: ByteArray,
        type: Char,
        mode: Int,
        size: Long,
    ): ByteArray {
        val header = ByteArray(BLOCK)
        name.copyInto(header, 0)
        header.putNumber(MODE_AT, SHORT_NUMBER_LEN, mode.toLong())
        header.putNumber(UID_AT, SHORT_NUMBER_LEN, 0)
        header.putNumber(GID_AT, SHORT_NUMBER_LEN, 0)
        header.putNumber(SIZE_AT, NUMBER_LEN, size)
        header.putNumber(MTIME_AT, NUMBER_LEN, 0)
        header[TYPE_AT] = type.code.toByte()
        "ustar  ".encodeToByteArray().copyInto(header, MAGIC_AT)
        return header
    }

    private fun seal(header: ByteArray) = header.putNumber(CKSUM_AT, SHORT_NUMBER_LEN, checksum(header))

    private fun write(
        header: ByteArray,
        body: ByteArray,
    ) {
        out.write(header)
        out.write(body)
        out.write(ByteArray(padding(body.size.toLong()).toInt()))
    }
}
