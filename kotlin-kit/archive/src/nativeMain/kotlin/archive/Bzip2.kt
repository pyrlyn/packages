// bzip2 over libbz2, linked statically from Conan.
@file:OptIn(ExperimentalForeignApi::class)

package archive

import archive.codecs.BZ2_bzCompress
import archive.codecs.BZ2_bzCompressEnd
import archive.codecs.BZ2_bzCompressInit
import archive.codecs.BZ2_bzDecompress
import archive.codecs.BZ2_bzDecompressEnd
import archive.codecs.BZ2_bzDecompressInit
import archive.codecs.BZ_FINISH
import archive.codecs.BZ_FINISH_OK
import archive.codecs.BZ_OK
import archive.codecs.BZ_STREAM_END
import archive.codecs.bz_stream
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.free
import kotlinx.cinterop.nativeHeap
import kotlinx.cinterop.ptr
import kotlinx.cinterop.sizeOf
import kotlinx.cinterop.usePinned
import kotlinx.io.Buffer
import kotlinx.io.RawSource
import kotlinx.io.readByteArray
import platform.posix.memset

/** bzip2's largest block, 900 KB: what `bzip2` writes by default. */
private const val BLOCK_100K = 9

/** The plain bytes of the first bzip2 stream in [compressed] (Rust `bzip2::read::BzDecoder`); anything after it is not read. */
fun bzip2Source(compressed: RawSource): RawSource = DecoderSource(compressed, Bunzip(), "bzip2")

/** [plain] as one bzip2 stream. */
fun bzip2(plain: ByteArray): ByteArray {
    val strm = newBzStream()
    try {
        check(BZ2_bzCompressInit(strm.ptr, BLOCK_100K, 0, 0) == BZ_OK) { "libbz2 refused to start an encoder" }
        val out = Buffer()
        val chunk = ByteArray(64 * 1024)
        plain.usePinned { input ->
            strm.next_in = if (plain.isEmpty()) null else input.addressOf(0)
            strm.avail_in = plain.size.convert()
            do {
                val rc =
                    chunk.usePinned { o ->
                        strm.next_out = o.addressOf(0)
                        strm.avail_out = chunk.size.convert()
                        BZ2_bzCompress(strm.ptr, BZ_FINISH)
                    }
                check(rc == BZ_FINISH_OK || rc == BZ_STREAM_END) { "libbz2 failed to encode ($rc)" }
                out.write(chunk, 0, chunk.size - strm.avail_out.toInt())
            } while (rc != BZ_STREAM_END)
        }
        BZ2_bzCompressEnd(strm.ptr)
        return out.readByteArray()
    } finally {
        nativeHeap.free(strm)
    }
}

private fun newBzStream(): bz_stream {
    val strm = nativeHeap.alloc<bz_stream>()
    // bzalloc, bzfree and opaque must be null for libbz2's own allocator.
    memset(strm.ptr, 0, sizeOf<bz_stream>().convert())
    return strm
}

private class Bunzip : Step {
    private val strm = newBzStream()

    init {
        val rc = BZ2_bzDecompressInit(strm.ptr, 0, 0)
        if (rc != BZ_OK) {
            nativeHeap.free(strm)
            error("libbz2 refused to start a decoder ($rc)")
        }
    }

    override fun run(
        input: CPointer<ByteVar>?,
        inLen: Int,
        output: CPointer<ByteVar>,
        outLen: Int,
        finish: Boolean,
    ): Progress {
        strm.next_in = input
        strm.avail_in = inLen.convert()
        strm.next_out = output
        strm.avail_out = outLen.convert()
        val rc = BZ2_bzDecompress(strm.ptr)
        if (rc != BZ_OK && rc != BZ_STREAM_END) {
            throw ArchiveException("bzip2 stream is corrupt (error $rc)", ArchiveException.Kind.Corrupt)
        }
        return Progress(inLen - strm.avail_in.toInt(), outLen - strm.avail_out.toInt(), rc == BZ_STREAM_END)
    }

    override fun end() {
        BZ2_bzDecompressEnd(strm.ptr)
        nativeHeap.free(strm)
    }
}
