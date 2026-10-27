// gzip and raw deflate over the zlib Kotlin/Native ships as a platform library.
@file:OptIn(ExperimentalForeignApi::class)

package archive

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.UByteVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.free
import kotlinx.cinterop.nativeHeap
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.sizeOf
import kotlinx.cinterop.toKString
import kotlinx.cinterop.usePinned
import kotlinx.io.Buffer
import kotlinx.io.RawSource
import kotlinx.io.readByteArray
import platform.posix.memset
import platform.zlib.ZLIB_VERSION
import platform.zlib.Z_BUF_ERROR
import platform.zlib.Z_DEFAULT_STRATEGY
import platform.zlib.Z_DEFLATED
import platform.zlib.Z_FINISH
import platform.zlib.Z_NO_FLUSH
import platform.zlib.Z_OK
import platform.zlib.Z_STREAM_END
import platform.zlib.crc32
import platform.zlib.deflate
import platform.zlib.deflateEnd
import platform.zlib.deflateInit2_
import platform.zlib.inflate
import platform.zlib.inflateEnd
import platform.zlib.inflateInit2_
import platform.zlib.z_stream

/** zlib's `MAX_WBITS`, a 32 KiB window; Linux's platform library leaves the macro out. */
private const val MAX_WBITS = 15

/** zlib's window-bits offset that selects a gzip header and trailer rather than a zlib one. */
private const val GZIP_WRAPPER = 16

/** zlib's memory level for deflate; its own default. */
private const val MEM_LEVEL = 8

/**
 * The plain bytes of the first gzip member in [compressed], its CRC and length checked at the end (Rust
 * `flate2::read::GzDecoder`). Anything after that member is not read.
 */
fun gzipSource(compressed: RawSource): RawSource = DecoderSource(compressed, Inflate(MAX_WBITS + GZIP_WRAPPER, "gzip"), "gzip")

/** The plain bytes of a raw deflate stream, as a zip member stores it. */
fun deflateSource(compressed: RawSource): RawSource = DecoderSource(compressed, Inflate(-MAX_WBITS, "deflate"), "deflate")

/** [plain] as one gzip member, at zlib [level] 0–9. */
fun gzip(
    plain: ByteArray,
    level: Int = 6,
): ByteArray = deflateAll(plain, MAX_WBITS + GZIP_WRAPPER, level)

/** [plain] as a raw deflate stream, as a zip member stores it. */
fun deflate(
    plain: ByteArray,
    level: Int = 6,
): ByteArray = deflateAll(plain, -MAX_WBITS, level)

/** The CRC-32 a gzip trailer and a zip header carry, continued from [crc]. */
fun crc32(
    bytes: ByteArray,
    offset: Int = 0,
    length: Int = bytes.size - offset,
    crc: UInt = 0u,
): UInt =
    if (length == 0) {
        crc
    } else {
        bytes.usePinned { crc32(crc.convert(), it.addressOf(offset).reinterpret(), length.convert()).toUInt() }
    }

private fun newStream(): z_stream {
    val strm = nativeHeap.alloc<z_stream>()
    // zalloc, zfree and opaque must be null for zlib's own allocator.
    memset(strm.ptr, 0, sizeOf<z_stream>().convert())
    return strm
}

private class Inflate(
    windowBits: Int,
    private val format: String,
) : Step {
    private val strm = newStream()

    init {
        val rc = inflateInit2_(strm.ptr, windowBits, ZLIB_VERSION, sizeOf<z_stream>().toInt())
        if (rc != Z_OK) {
            nativeHeap.free(strm)
            error("zlib refused to start a $format decoder ($rc)")
        }
    }

    override fun run(
        input: CPointer<ByteVar>?,
        inLen: Int,
        output: CPointer<ByteVar>,
        outLen: Int,
        finish: Boolean,
    ): Progress {
        strm.next_in = input?.reinterpret<UByteVar>()
        strm.avail_in = inLen.convert()
        strm.next_out = output.reinterpret()
        strm.avail_out = outLen.convert()
        val rc = inflate(strm.ptr, Z_NO_FLUSH)
        val progress = Progress(inLen - strm.avail_in.toInt(), outLen - strm.avail_out.toInt(), rc == Z_STREAM_END)
        if (rc != Z_OK && rc != Z_STREAM_END && rc != Z_BUF_ERROR) {
            val why = strm.msg?.toKString() ?: "error $rc"
            throw ArchiveException("$format stream is corrupt: $why", ArchiveException.Kind.Corrupt)
        }
        return progress
    }

    override fun end() {
        inflateEnd(strm.ptr)
        nativeHeap.free(strm)
    }
}

private fun deflateAll(
    plain: ByteArray,
    windowBits: Int,
    level: Int,
): ByteArray {
    val strm = newStream()
    try {
        check(deflateInit2_(strm.ptr, level, Z_DEFLATED, windowBits, MEM_LEVEL, Z_DEFAULT_STRATEGY, ZLIB_VERSION, sizeOf<z_stream>().toInt()) == Z_OK) {
            "zlib refused to start an encoder"
        }
        val out = Buffer()
        val chunk = ByteArray(64 * 1024)
        plain.usePinned { input ->
            strm.next_in = if (plain.isEmpty()) null else input.addressOf(0).reinterpret()
            strm.avail_in = plain.size.convert()
            do {
                val rc =
                    chunk.usePinned { o ->
                        strm.next_out = o.addressOf(0).reinterpret()
                        strm.avail_out = chunk.size.convert()
                        deflate(strm.ptr, Z_FINISH)
                    }
                check(rc == Z_OK || rc == Z_STREAM_END || rc == Z_BUF_ERROR) { "zlib failed to encode ($rc)" }
                out.write(chunk, 0, chunk.size - strm.avail_out.toInt())
            } while (rc != Z_STREAM_END)
        }
        deflateEnd(strm.ptr)
        return out.readByteArray()
    } finally {
        nativeHeap.free(strm)
    }
}
