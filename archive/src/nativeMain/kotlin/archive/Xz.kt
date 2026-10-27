// xz over liblzma, linked statically from Conan.
@file:OptIn(ExperimentalForeignApi::class)

package archive

import archive.codecs.LZMA_BUF_ERROR
import archive.codecs.LZMA_FINISH
import archive.codecs.LZMA_MEMLIMIT_ERROR
import archive.codecs.LZMA_MEM_ERROR
import archive.codecs.LZMA_OK
import archive.codecs.LZMA_RUN
import archive.codecs.LZMA_STREAM_END
import archive.codecs.lzma_code
import archive.codecs.lzma_end
import archive.codecs.lzma_stream
import archive.codecs.lzma_stream_decoder
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.free
import kotlinx.cinterop.nativeHeap
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.sizeOf
import kotlinx.io.RawSource
import platform.posix.memset

/** liblzma's flag to read streams written back to back as one, as `xz` writes when two archives are appended. */
private const val CONCATENATED = 0x08u

/**
 * The plain bytes of every xz stream in [compressed], back to back. A stream whose decoder needs more than
 * [memLimit] bytes (a huge LZMA2 dictionary, mostly) fails as [ArchiveException.Kind.MemoryLimit] before that memory
 * is taken.
 */
fun xzSource(
    compressed: RawSource,
    memLimit: Long,
): RawSource = DecoderSource(compressed, Unxz(memLimit), "xz")

private class Unxz(
    private val memLimit: Long,
) : Step {
    private val strm = nativeHeap.alloc<lzma_stream>()

    init {
        // LZMA_STREAM_INIT is all zeros.
        memset(strm.ptr, 0, sizeOf<lzma_stream>().convert())
        val rc = lzma_stream_decoder(strm.ptr, memLimit.convert(), CONCATENATED)
        if (rc != LZMA_OK) {
            nativeHeap.free(strm)
            error("liblzma refused to start a decoder ($rc)")
        }
    }

    override fun run(
        input: CPointer<ByteVar>?,
        inLen: Int,
        output: CPointer<ByteVar>,
        outLen: Int,
        finish: Boolean,
    ): Progress {
        strm.next_in = input?.reinterpret()
        strm.avail_in = inLen.convert()
        strm.next_out = output.reinterpret()
        strm.avail_out = outLen.convert()
        // A concatenated decoder only ends the last stream once it is told no more input follows.
        val rc = lzma_code(strm.ptr, if (finish) LZMA_FINISH else LZMA_RUN)
        when (rc) {
            LZMA_OK, LZMA_STREAM_END, LZMA_BUF_ERROR -> {}

            LZMA_MEMLIMIT_ERROR, LZMA_MEM_ERROR -> {
                throw ArchiveException("xz stream needs more than $memLimit bytes to decode", ArchiveException.Kind.MemoryLimit)
            }

            else -> {
                throw ArchiveException("xz stream is corrupt (error $rc)", ArchiveException.Kind.Corrupt)
            }
        }
        return Progress(inLen - strm.avail_in.toInt(), outLen - strm.avail_out.toInt(), rc == LZMA_STREAM_END)
    }

    override fun end() {
        lzma_end(strm.ptr)
        nativeHeap.free(strm)
    }
}
