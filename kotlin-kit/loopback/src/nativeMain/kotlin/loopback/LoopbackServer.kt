// An HTTP/1.1 server on 127.0.0.1 for tests, spoken by hand over plain sockets. A real server cannot be told to break
// its own framing, and the tests that need this one send exactly that: a lying `Content-Length`, a body cut short, no
// length at all.
@file:OptIn(ObsoleteWorkersApi::class, ExperimentalAtomicApi::class)

package loopback

import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.incrementAndFetch
import kotlin.native.concurrent.ObsoleteWorkersApi
import kotlin.native.concurrent.TransferMode
import kotlin.native.concurrent.Worker

/** How often the accept loop looks at the stop flag, and how long a client may take to send its request. */
private const val POLL_MS = 20
private const val REQUEST_WAIT_MS = 2_000

/** A request head or body past this is cut off; no test sends one. */
private const val MAX_REQUEST = 1 shl 20

private const val CHUNK = 64 * 1024

private val HEAD_END = "\r\n\r\n".encodeToByteArray()

/**
 * Serve `127.0.0.1:<free port>` until closed. [reply] gets each raw request (the head, and the body when it carries a
 * `Content-Length`) and returns the bytes to write back; the connection is closed after them. Connections are served
 * one at a time, in order.
 */
class LoopbackServer(
    reply: (ByteArray) -> ByteArray,
) : AutoCloseable {
    private val hits = AtomicInt(0)

    /** Requests served so far. */
    val hitCount get() = hits.load()

    val port: Int

    val url get() = "http://127.0.0.1:$port"

    private val listener: Long
    private val stop = AtomicBoolean(false)
    private val worker = Worker.start(name = "loopback-server")

    init {
        val listening = listenLoopback()
        listener = listening.socket
        port = listening.port
        val loop = Loop(listener, reply, hits, stop)
        worker.execute(TransferMode.SAFE, { loop }) { it.run() }
    }

    /** Stop accepting and wait for the connection in flight to finish. */
    override fun close() {
        stop.store(true)
        worker.requestTermination().result
        closeSocket(listener)
    }
}

private class Loop(
    val listener: Long,
    val reply: (ByteArray) -> ByteArray,
    val hits: AtomicInt,
    val stop: AtomicBoolean,
) {
    fun run() {
        while (!stop.load()) {
            val fd = if (readable(listener, POLL_MS)) acceptOne(listener) else -1
            if (fd >= 0) serve(fd)
        }
    }

    private fun serve(fd: Long) {
        try {
            val request = readRequest(fd)
            hits.incrementAndFetch()
            writeAll(fd, reply(request))
        } finally {
            closeSocket(fd)
        }
    }
}

/** The head and any `Content-Length` body; whatever arrived when the client stops sending or the wait runs out. */
private fun readRequest(fd: Long): ByteArray {
    var got = ByteArray(0)
    val chunk = ByteArray(CHUNK)
    while (got.size < MAX_REQUEST && !complete(got) && readable(fd, REQUEST_WAIT_MS)) {
        val n = receive(fd, chunk)
        if (n <= 0) break
        got += chunk.copyOf(n)
    }
    return got
}

private fun complete(request: ByteArray): Boolean {
    val end = indexOf(request, HEAD_END)
    if (end < 0) return false
    val length =
        request
            .copyOf(end)
            .decodeToString()
            .lines()
            .firstNotNullOfOrNull { line ->
                line.substringBefore(':').takeIf { it.equals("content-length", ignoreCase = true) && ':' in line }?.let {
                    line.substringAfter(':').trim().toIntOrNull()
                }
            } ?: 0
    return request.size >= end + HEAD_END.size + length
}

private fun indexOf(
    hay: ByteArray,
    needle: ByteArray,
): Int = (0..hay.size - needle.size).firstOrNull { i -> needle.indices.all { hay[i + it] == needle[it] } } ?: -1

/** Write everything, or as much as the client takes before it hangs up. */
private fun writeAll(
    fd: Long,
    bytes: ByteArray,
) {
    var off = 0
    while (off < bytes.size) {
        val n = sendFrom(fd, bytes, off)
        if (n <= 0) return
        off += n
    }
}
