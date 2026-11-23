@file:OptIn(ExperimentalForeignApi::class)

package archive

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.io.Buffer
import kotlinx.io.RawSource
import kotlinx.io.readTo
import kotlin.math.min

/** One call into a C streaming decoder; [input] is null when there is no input left to give it. */
internal interface Step {
    fun run(
        input: CPointer<ByteVar>?,
        inLen: Int,
        output: CPointer<ByteVar>,
        outLen: Int,
        finish: Boolean,
    ): Progress

    /** Release the decoder's native state. */
    fun end()
}

internal class Progress(
    val consumed: Int,
    val produced: Int,
    val done: Boolean,
)

private const val CHUNK = 64 * 1024

/**
 * A C decoder as a [RawSource]: compressed bytes come from [upstream], plain bytes go out.
 *
 * Plain bytes are handed out as the decoder produces them, never gathered first, so how far a stream expands is the
 * caller's to bound as it reads.
 */
internal class DecoderSource(
    private val upstream: RawSource,
    private val step: Step,
    private val format: String,
) : RawSource {
    private val input = ByteArray(CHUNK)
    private val output = ByteArray(CHUNK)
    private var start = 0
    private var end = 0
    private var upstreamDone = false
    private var done = false
    private var closed = false

    override fun readAtMostTo(
        sink: Buffer,
        byteCount: Long,
    ): Long {
        require(byteCount >= 0) { "byteCount < 0: $byteCount" }
        check(!closed) { "closed" }
        if (byteCount == 0L) return 0
        var produced = 0
        while (!done && produced == 0) {
            if (start == end && !upstreamDone) fill()
            val want = min(byteCount, CHUNK.toLong()).toInt()
            val progress =
                input.usePinned { i ->
                    output.usePinned { o ->
                        step.run(if (end > start) i.addressOf(start) else null, end - start, o.addressOf(0), want, upstreamDone)
                    }
                }
            start += progress.consumed
            done = progress.done
            produced = progress.produced
            sink.write(output, 0, produced)
            if (!done && produced == 0 && progress.consumed == 0) stalled()
        }
        return if (produced > 0) produced.toLong() else -1
    }

    /** The decoder took nothing and gave nothing: it needs more input than is buffered. */
    private fun stalled() {
        if (upstreamDone) throw ArchiveException("$format stream ends early", ArchiveException.Kind.Truncated)
        if (start == 0 && end == input.size) throw ArchiveException("$format stream is corrupt", ArchiveException.Kind.Corrupt)
        fill()
    }

    /** Keep what the decoder has not taken yet and read more behind it. */
    private fun fill() {
        input.copyInto(input, 0, start, end)
        end -= start
        start = 0
        val buffer = Buffer()
        val n = upstream.readAtMostTo(buffer, (input.size - end).toLong())
        if (n < 0) {
            upstreamDone = true
            return
        }
        buffer.readTo(input, end, end + n.toInt())
        end += n.toInt()
    }

    override fun close() {
        if (closed) return
        closed = true
        try {
            step.end()
        } finally {
            upstream.close()
        }
    }
}
