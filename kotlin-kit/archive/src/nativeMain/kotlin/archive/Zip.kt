@file:OptIn(ExperimentalForeignApi::class)

package archive

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.io.Buffer
import kotlinx.io.IOException
import kotlinx.io.RawSource
import kotlinx.io.files.Path
import kotlin.math.min

/** One member of a zip, from its central directory record. */
class ZipEntry internal constructor(
    /** UTF-8 when the archive says so (or an Info-ZIP Unicode path extra field does), CP437 otherwise. */
    val name: String,
    val method: Int,
    val flags: Int,
    val crc: UInt,
    val compressedSize: Long,
    val size: Long,
    val versionMadeBy: Int,
    val externalAttributes: UInt,
    internal val localHeaderOffset: Long,
) {
    /** Rust zip `ZipFile::is_dir`: the name ends in a slash of either kind. */
    val isDir get() = name.endsWith('/') || name.endsWith('\\')

    /**
     * Rust zip `ZipFile::unix_mode`: the high half of the external attributes when a unix system wrote the member; a
     * mode made up from the DOS directory and read-only bits when DOS did; null otherwise, or with no attributes.
     */
    val unixMode: Int?
        get() {
            if (externalAttributes == 0u) return null
            return when (versionMadeBy shr BITS) {
                SYSTEM_UNIX -> (externalAttributes shr HALF).toInt()
                SYSTEM_DOS -> dosMode(externalAttributes)
                else -> null
            }
        }

    private companion object {
        const val BITS = 8
        const val HALF = 16
        const val SYSTEM_DOS = 0
        const val SYSTEM_UNIX = 3
    }
}

private val S_IFDIR = "40000".toInt(8)
private val S_IFREG = "100000".toInt(8)
private const val DOS_DIR = 0x10u
private const val DOS_READ_ONLY = 0x01u

private val DOS_DIR_MODE = S_IFDIR or "775".toInt(8)
private val DOS_FILE_MODE = S_IFREG or "664".toInt(8)
private val DOS_READ_ONLY_MASK = "555".toInt(8)

private fun dosMode(attributes: UInt): Int {
    var mode = if (attributes and DOS_DIR != 0u) DOS_DIR_MODE else DOS_FILE_MODE
    if (attributes and DOS_READ_ONLY != 0u) mode = mode and DOS_READ_ONLY_MASK
    return mode
}

internal const val METHOD_STORED = 0
internal const val METHOD_DEFLATE = 8
internal const val FLAG_ENCRYPTED = 1
internal const val FLAG_UTF8 = 1 shl 11

/**
 * A zip read through its central directory (Rust `zip::ZipArchive`), stored and deflated members only.
 *
 * The archive is untrusted: every offset and length it states is checked against the file before it is used, and a
 * member's CRC is checked once it has been read to the end.
 */
class ZipReader(
    path: Path,
) : AutoCloseable {
    private val opened = openFile(path)
    private val file = PositionalFile(opened)

    /** Every member, in central directory order. */
    val entries: List<ZipEntry> =
        try {
            CentralDirectory(file).read()
        } catch (e: IOException) {
            opened.close()
            throw e
        }

    /** The plain bytes of [entry], its CRC checked at the end. */
    fun open(entry: ZipEntry): RawSource {
        if (entry.flags and FLAG_ENCRYPTED != 0) unsupported("${entry.name} is encrypted")
        if (entry.method != METHOD_STORED && entry.method != METHOD_DEFLATE) {
            unsupported("${entry.name} uses compression method ${entry.method}")
        }
        val local = file.read(entry.localHeaderOffset, LOCAL_HEADER)
        if (local.u32(0) != LOCAL_SIG) corrupt("${entry.name} has no local header")
        val start = entry.localHeaderOffset + LOCAL_HEADER + local.u16(LOCAL_NAME_LEN) + local.u16(LOCAL_EXTRA_LEN)
        file.check(start, entry.compressedSize, entry.name)
        val stored = FileRange(file, start, entry.compressedSize)
        val plain = if (entry.method == METHOD_DEFLATE) deflateSource(stored) else stored
        return CrcSource(plain, entry)
    }

    override fun close() {
        opened.close()
    }
}

/** An open file read at given offsets, every range checked against its length. */
internal class PositionalFile(
    private val file: OpenFile,
) {
    val length: Long = file.length

    fun check(
        at: Long,
        len: Long,
        what: String,
    ) {
        if (at < 0 || len < 0 || len > length - at) corrupt("$what lies outside the archive")
    }

    fun read(
        at: Long,
        len: Int,
    ): ByteArray {
        check(at, len.toLong(), "a zip record")
        val bytes = ByteArray(len)
        var got = 0
        while (got < len) {
            val n = readAt(bytes, got, len - got, at + got)
            if (n == 0) corrupt("zip ends early")
            got += n
        }
        return bytes
    }

    fun readAt(
        into: ByteArray,
        offset: Int,
        len: Int,
        at: Long,
    ): Int = file.readAt(into, offset, len, at)
}

/** The stored bytes of one member. */
internal class FileRange(
    private val file: PositionalFile,
    private var at: Long,
    private var left: Long,
) : RawSource {
    private val chunk = ByteArray(64 * 1024)

    override fun readAtMostTo(
        sink: Buffer,
        byteCount: Long,
    ): Long {
        if (left == 0L) return -1
        val want = min(min(byteCount, left), chunk.size.toLong()).toInt()
        val n = file.readAt(chunk, 0, want, at)
        if (n == 0) corrupt("zip ends early")
        sink.write(chunk, 0, n)
        at += n
        left -= n
        return n.toLong()
    }

    override fun close() = Unit
}

/** Rust zip's `Crc32Reader`: the CRC of everything read, checked when the member ends. */
private class CrcSource(
    private val upstream: RawSource,
    private val entry: ZipEntry,
) : RawSource {
    private var crc = 0u
    private val scratch = Buffer()

    override fun readAtMostTo(
        sink: Buffer,
        byteCount: Long,
    ): Long {
        val n = upstream.readAtMostTo(scratch, byteCount)
        if (n < 0) {
            if (crc != entry.crc) corrupt("${entry.name} fails its CRC check")
            return -1
        }
        val bytes = ByteArray(n.toInt())
        scratch.readAtMostTo(bytes, 0, bytes.size)
        crc = crc32(bytes, crc = crc)
        sink.write(bytes)
        return n
    }

    override fun close() = upstream.close()
}

/** A file opened for reading at offsets: `pread` on Unix, `ReadFile` at an offset on Windows. */
internal interface OpenFile {
    val length: Long

    /** Up to [len] bytes at [at] into [into] from [offset]; 0 at the end of the file. */
    fun readAt(
        into: ByteArray,
        offset: Int,
        len: Int,
        at: Long,
    ): Int

    fun close()
}

internal expect fun openFile(path: Path): OpenFile
