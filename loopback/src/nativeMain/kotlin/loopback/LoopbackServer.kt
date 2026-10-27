// An HTTP/1.1 server on 127.0.0.1 for tests, spoken by hand over POSIX sockets. A real server cannot be told to break
// its own framing, and the tests that need this one send exactly that: a lying `Content-Length`, a body cut short, no
// length at all.
@file:OptIn(ExperimentalForeignApi::class, ObsoleteWorkersApi::class, ExperimentalAtomicApi::class)

package loopback

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.set
import kotlinx.cinterop.sizeOf
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.posix.AF_INET
import platform.posix.POLLIN
import platform.posix.SIGPIPE
import platform.posix.SIG_IGN
import platform.posix.SOCK_STREAM
import platform.posix.accept
import platform.posix.bind
import platform.posix.close
import platform.posix.getsockname
import platform.posix.listen
import platform.posix.poll
import platform.posix.pollfd
import platform.posix.posix_htons
import platform.posix.read
import platform.posix.signal
import platform.posix.sockaddr
import platform.posix.sockaddr_in
import platform.posix.socket
import platform.posix.socklen_tVar
import platform.posix.write
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.incrementAndFetch
import kotlin.native.concurrent.ObsoleteWorkersApi
import kotlin.native.concurrent.TransferMode
import kotlin.native.concurrent.Worker

private const val BACKLOG = 16

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

    private val listener = socket(AF_INET, SOCK_STREAM, 0)
    private val stop = AtomicBoolean(false)
    private val worker = Worker.start(name = "loopback-server")

    init {
        // A client that hangs up mid-reply would otherwise kill the whole test process on the next write.
        signal(SIGPIPE, SIG_IGN)
        check(listener >= 0) { "socket" }
        port = bindLoopback(listener)
        val loop = Loop(listener, reply, hits, stop)
        worker.execute(TransferMode.SAFE, { loop }) { it.run() }
    }

    /** Stop accepting and wait for the connection in flight to finish. */
    override fun close() {
        stop.store(true)
        worker.requestTermination().result
        close(listener)
    }
}

private class Loop(
    val listener: Int,
    val reply: (ByteArray) -> ByteArray,
    val hits: AtomicInt,
    val stop: AtomicBoolean,
) {
    fun run() {
        while (!stop.load()) {
            val fd = if (readable(listener, POLL_MS)) accept(listener, null, null) else -1
            if (fd >= 0) serve(fd)
        }
    }

    private fun serve(fd: Int) {
        try {
            val request = readRequest(fd)
            hits.incrementAndFetch()
            writeAll(fd, reply(request))
        } finally {
            close(fd)
        }
    }
}

private fun readable(
    fd: Int,
    timeoutMs: Int,
): Boolean =
    memScoped {
        val p = alloc<pollfd>()
        p.fd = fd
        p.events = POLLIN.convert()
        poll(p.ptr, 1u, timeoutMs) > 0
    }

/** The head and any `Content-Length` body; whatever arrived when the client stops sending or the wait runs out. */
private fun readRequest(fd: Int): ByteArray {
    var got = ByteArray(0)
    val chunk = ByteArray(CHUNK)
    while (got.size < MAX_REQUEST && !complete(got) && readable(fd, REQUEST_WAIT_MS)) {
        val n = chunk.usePinned { read(fd, it.addressOf(0), CHUNK.convert()).toInt() }
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
    fd: Int,
    bytes: ByteArray,
) {
    var off = 0
    while (off < bytes.size) {
        val n = bytes.usePinned { write(fd, it.addressOf(off), (bytes.size - off).convert()).toInt() }
        if (n <= 0) return
        off += n
    }
}

/** `127.0.0.1`, in the network byte order `sin_addr` holds. */
private val LOOPBACK = "127.0.0.1".split('.').map { it.toUByte().toByte() }

/** Point [addr] at the loopback address. */
internal fun setLoopback(addr: sockaddr_in) {
    val raw = addr.sin_addr.ptr.reinterpret<ByteVar>()
    LOOPBACK.forEachIndexed { i, b -> raw[i] = b }
}

/** Bind [fd] to a free loopback port, start listening and return the port. */
private fun bindLoopback(fd: Int): Int =
    memScoped {
        val addr = alloc<sockaddr_in>()
        addr.sin_family = AF_INET.convert()
        addr.sin_port = 0u
        setLoopback(addr)
        check(bind(fd, addr.ptr.reinterpret<sockaddr>(), sizeOf<sockaddr_in>().convert()) == 0) { "bind" }
        check(listen(fd, BACKLOG) == 0) { "listen" }
        val len = alloc<socklen_tVar>()
        len.value = sizeOf<sockaddr_in>().convert()
        check(getsockname(fd, addr.ptr.reinterpret(), len.ptr) == 0) { "getsockname" }
        posix_htons(addr.sin_port.toShort()).toUShort().toInt()
    }
