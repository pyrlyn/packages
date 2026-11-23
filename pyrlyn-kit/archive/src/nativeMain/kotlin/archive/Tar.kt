package archive

import kotlinx.io.Buffer
import kotlinx.io.RawSource
import kotlinx.io.Source
import kotlinx.io.buffered
import kotlinx.io.readByteArray
import kotlin.math.min

/** A member's kind, from its type flag. */
enum class TarType {
    Regular,
    Link,
    Symlink,
    Char,
    Block,
    Directory,
    Fifo,
    Continuous,
    GnuSparse,
    PaxGlobal,

    /** Only seen when a header is not GNU: a GNU one is folded into the member it names. */
    GnuLongName,
    GnuLongLink,
    Other,
    ;

    internal companion object {
        fun of(flag: Byte): TarType =
            when (flag.toInt().toChar()) {
                '0', '\u0000' -> Regular
                '1' -> Link
                '2' -> Symlink
                '3' -> Char
                '4' -> Block
                '5' -> Directory
                '6' -> Fifo
                '7' -> Continuous
                'S' -> GnuSparse
                'g' -> PaxGlobal
                'L' -> GnuLongName
                'K' -> GnuLongLink
                else -> Other
            }
    }
}

/**
 * One tar member, as tar-rs `Entries` yields it: a GNU long name or long link and a PAX `path`, `linkpath` and `size`
 * already applied.
 */
class TarEntry(
    /** The path bytes as UTF-8; a byte that is not UTF-8 reads as U+FFFD. */
    val path: String,
    val type: TarType,
    /** Null when the mode field is not a number, as tar-rs `Header::mode` fails. */
    val mode: Int?,
    /** The unpacked size: a sparse member's holes included. */
    val size: Long,
    /** Null when the member names no link target. */
    val linkName: String?,
    /** Seconds since the epoch; null when the field is not a number. */
    val mtime: Long?,
)

/** A GNU long name or long link longer than this is refused; no file system takes a path near it. */
private const val MAX_LONG_NAME = 64 * 1024

/** A PAX header longer than this is refused; real ones are a few hundred bytes. */
private const val MAX_PAX = 1024 * 1024

/** A sparse map with more runs than this is refused, so its list cannot grow without bound. */
private const val MAX_SPARSE_RUNS = 100_000

/** The PAX and GNU records that describe the next member. */
private class Pending {
    var longName: ByteArray? = null
    var longLink: ByteArray? = null
    var pax: Map<String, ByteArray>? = null
}

/** A run of the unpacked body: stored bytes, or a hole that reads as zeros. */
private class Run(
    val hole: Boolean,
    var left: Long,
)

/**
 * Reads a tar stream member by member (tar-rs `Archive::entries`).
 *
 * The archive is untrusted: every header checksum is checked, a short read is [ArchiveException.Kind.Truncated] rather
 * than the end, and the records it holds in memory (long names, PAX headers, sparse maps) are capped.
 */
class TarReader(
    source: RawSource,
) : AutoCloseable {
    private val src: Source = source.buffered()
    private var runs = ArrayDeque<Run>()

    /** Stored bytes of the current member not read yet, padding included. */
    private var storedLeft = 0L
    private var ended = false

    /** The next member, its body readable through [body] until the next call; null at the end of the archive. */
    fun next(): TarEntry? {
        skip(storedLeft)
        storedLeft = 0
        runs = ArrayDeque()
        val pending = Pending()
        while (!ended) {
            val header = readHeader() ?: break
            val type = TarType.of(header[TYPE_AT])
            if (!absorb(header, type, pending)) return member(header, type, pending)
        }
        ended = true
        return null
    }

    /** The current member's body, holes filled with zeros. */
    fun body(): RawSource = Body()

    override fun close() = src.close()

    /** Fold a GNU or PAX record into [pending]; false when [header] is a member of its own. */
    private fun absorb(
        header: ByteArray,
        type: TarType,
        pending: Pending,
    ): Boolean {
        val gnu = header.isGnu
        when {
            type == TarType.GnuLongName && gnu -> {
                if (pending.longName != null) corrupt("two long name entries describing the same member")
                pending.longName = extension(header, MAX_LONG_NAME, "GNU long name")
            }

            type == TarType.GnuLongLink && gnu -> {
                if (pending.longLink != null) corrupt("two long link entries describing the same member")
                pending.longLink = extension(header, MAX_LONG_NAME, "GNU long link")
            }

            header[TYPE_AT] == 'x'.code.toByte() -> {
                if (pending.pax != null) corrupt("two pax extensions entries describing the same member")
                pending.pax = paxRecords(extension(header, MAX_PAX, "PAX header"))
            }

            else -> {
                return false
            }
        }
        return true
    }

    private fun member(
        header: ByteArray,
        type: TarType,
        pending: Pending,
    ): TarEntry {
        val pax = pending.pax.orEmpty()
        val storedSize =
            pax["size"]
                ?.decodeToString()
                ?.toULongOrNull()
                ?.toLong()
                ?.takeIf { it >= 0 } ?: size(header)
        storedLeft = storedSize + padding(storedSize)
        val path = pending.longName?.withoutTrailingNul() ?: pax["path"] ?: headerPath(header)
        val link =
            pending.longLink?.withoutTrailingNul() ?: pax["linkpath"] ?: header.field(LINK_AT, NAME_LEN).takeIf { it.isNotEmpty() }
        val size = if (type == TarType.GnuSparse) sparseRuns(header, storedSize) else plainRun(storedSize)
        return TarEntry(
            path = path.decodeToString(),
            type = type,
            mode = header.octal(MODE_AT, SHORT_NUMBER_LEN)?.toInt(),
            size = size,
            linkName = link?.decodeToString(),
            mtime = header.number(MTIME_AT, NUMBER_LEN),
        )
    }

    private fun plainRun(size: Long): Long {
        runs.addLast(Run(hole = false, left = size))
        return size
    }

    /** tar-rs `parse_sparse_header`: the GNU sparse map, validated, as runs; the unpacked size. */
    private fun sparseRuns(
        header: ByteArray,
        stored: Long,
    ): Long {
        if (!header.isGnu) corrupt("sparse entry type listed but not GNU header")
        val map = SparseMap(stored)
        map.add(header, SPARSE_AT, SPARSE_IN_HEADER)
        var extended = header[IS_EXTENDED_AT] != 0.toByte()
        while (extended) {
            val block = readBlock() ?: truncated("tar sparse map")
            map.add(block, 0, SPARSE_IN_EXTENSION)
            extended = block[EXTENSION_IS_EXTENDED_AT] != 0.toByte()
        }
        val real = header.number(REAL_SIZE_AT, NUMBER_LEN) ?: corrupt("sparse real size is not a number")
        if (map.end != real) corrupt("mismatch in sparse file chunks and size in header")
        if (map.remaining > 0) corrupt("mismatch in sparse file chunks and entry size in header")
        runs = map.runs
        return real
    }

    private fun extension(
        header: ByteArray,
        cap: Int,
        what: String,
    ): ByteArray {
        val size = size(header)
        if (size > cap) throw ArchiveException("$what of $size bytes is over the $cap-byte limit", ArchiveException.Kind.Unsupported)
        val bytes = readExactly(size.toInt(), what)
        skip(padding(size))
        return bytes
    }

    /** The next header, or null at the end: no bytes left, or a block of zeros. */
    private fun readHeader(): ByteArray? {
        val header = readBlock()?.takeUnless { block -> block.all { it == 0.toByte() } } ?: return null
        val stated = header.octal(CKSUM_AT, SHORT_NUMBER_LEN)
        if (stated != checksum(header)) corrupt("archive header checksum mismatch")
        return header
    }

    /** One block; null when the stream ends right before it. */
    private fun readBlock(): ByteArray? {
        val block = ByteArray(BLOCK)
        var got = 0
        while (got < BLOCK) {
            val n = src.readAtMostTo(block, got, BLOCK)
            if (n < 0) break
            got += n
        }
        if (got in 1 until BLOCK) truncated("tar header")
        return block.takeIf { got == BLOCK }
    }

    private fun readExactly(
        n: Int,
        what: String,
    ): ByteArray {
        if (!src.request(n.toLong())) truncated(what)
        return src.readByteArray(n)
    }

    private fun skip(n: Long) {
        if (n == 0L) return
        if (!src.request(n)) truncated("tar member")
        src.skip(n)
    }

    private fun size(header: ByteArray): Long = header.number(SIZE_AT, NUMBER_LEN) ?: corrupt("numeric field was not a number")

    private inner class Body : RawSource {
        override fun readAtMostTo(
            sink: Buffer,
            byteCount: Long,
        ): Long {
            while (runs.firstOrNull()?.left == 0L) runs.removeFirst()
            val run = runs.firstOrNull() ?: return -1
            val n = min(byteCount, run.left)
            val got = if (run.hole) zeros(sink, n) else stored(sink, n)
            run.left -= got
            return got
        }

        private fun stored(
            sink: Buffer,
            n: Long,
        ): Long {
            val got = src.readAtMostTo(sink, n)
            if (got < 0) truncated("tar member")
            storedLeft -= got
            return got
        }

        private fun zeros(
            sink: Buffer,
            n: Long,
        ): Long {
            val chunk = min(n, ZEROS.size.toLong()).toInt()
            sink.write(ZEROS, 0, chunk)
            return chunk.toLong()
        }

        override fun close() = Unit
    }

    private companion object {
        val ZEROS = ByteArray(8 * 1024)
    }
}

/** A GNU sparse map as it is read: holes between the stored runs, each run checked against the stored size. */
private class SparseMap(
    private val stored: Long,
) {
    val runs = ArrayDeque<Run>()
    var end = 0L
        private set
    var remaining = stored
        private set

    fun add(
        block: ByteArray,
        at: Int,
        count: Int,
    ) {
        for (i in 0 until count) {
            val pair = at + i * SPARSE_PAIR
            // tar-rs `GnuSparseHeader::is_empty`: either field starting with NUL ends the map.
            if (block[pair] == 0.toByte() || block[pair + NUMBER_LEN] == 0.toByte()) continue
            val offset = block.number(pair, NUMBER_LEN) ?: corrupt("sparse offset is not a number")
            val length = block.number(pair + NUMBER_LEN, NUMBER_LEN) ?: corrupt("sparse length is not a number")
            run(offset, length)
        }
    }

    private fun run(
        offset: Long,
        length: Long,
    ) {
        if (length != 0L && (stored - remaining) % BLOCK != 0L) {
            corrupt("previous block in sparse file was not aligned to 512-byte boundary")
        }
        if (offset < end) corrupt("out of order or overlapping sparse blocks")
        if (runs.size >= MAX_SPARSE_RUNS) {
            throw ArchiveException("sparse map with more than $MAX_SPARSE_RUNS runs", ArchiveException.Kind.Unsupported)
        }
        if (end < offset) runs.addLast(Run(hole = true, left = offset - end))
        end = offset + length
        if (end < 0) corrupt("more bytes listed in sparse file than u64 can hold")
        remaining -= length
        if (remaining < 0) corrupt("sparse file consumed more data than the header listed")
        runs.addLast(Run(hole = false, left = length))
    }
}

/** tar-rs `Header::path_bytes`: a ustar prefix joined to the name with `/`; GNU and v7 headers have only the name. */
private fun headerPath(header: ByteArray): ByteArray {
    val name = header.field(0, NAME_LEN)
    if (!header.isUstar) return name
    val prefix = header.field(PREFIX_AT, PREFIX_LEN)
    return if (prefix.isEmpty()) name else prefix + '/'.code.toByte() + name
}

/** tar-rs strips one trailing NUL from a GNU long name. */
private fun ByteArray.withoutTrailingNul(): ByteArray = if (lastOrNull() == 0.toByte()) copyOf(size - 1) else this

/**
 * PAX records, `<length> <key>=<value>\n` each; the first value for a key wins, as tar-rs looks it up. Reading stops at
 * the first malformed record, where tar-rs's iterator stops too.
 */
private fun paxRecords(body: ByteArray): Map<String, ByteArray> {
    val records = LinkedHashMap<String, ByteArray>()
    var at = 0
    while (at < body.size) {
        val (end, key, value) = paxRecord(body, at) ?: break
        records.getOrPut(key) { value }
        at = end
    }
    return records
}

/** The record at [at]: where it ends, its key and its value; null when it is malformed. */
private fun paxRecord(
    body: ByteArray,
    at: Int,
): Triple<Int, String, ByteArray>? {
    val space = (at until body.size).firstOrNull { body[it] == ' '.code.toByte() }
    val len = space?.let { body.copyOfRange(at, it).decodeToString().toIntOrNull() } ?: 0
    val end = at + len
    // A length of zero or less, or one past the body, leaves `end` outside (at, size].
    val inside = space != null && end in at + 1..body.size
    if (!inside || body[end - 1] != '\n'.code.toByte()) return null
    val record = body.copyOfRange(space + 1, end - 1)
    val eq = record.indexOf('='.code.toByte())
    return if (eq < 0) null else Triple(end, record.copyOf(eq).decodeToString(), record.copyOfRange(eq + 1, record.size))
}
