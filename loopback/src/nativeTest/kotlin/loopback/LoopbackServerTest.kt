@file:OptIn(ExperimentalForeignApi::class)

package loopback

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.sizeOf
import kotlinx.cinterop.usePinned
import platform.posix.AF_INET
import platform.posix.SOCK_STREAM
import platform.posix.close
import platform.posix.connect
import platform.posix.posix_htons
import platform.posix.read
import platform.posix.sockaddr
import platform.posix.sockaddr_in
import platform.posix.socket
import platform.posix.write
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LoopbackServerTest {
    /** One request over a plain socket, so the server is tested apart from any HTTP client. */
    private fun exchange(
        port: Int,
        request: String,
    ): String {
        val fd = socket(AF_INET, SOCK_STREAM, 0)
        try {
            memScoped {
                val addr = alloc<sockaddr_in>()
                addr.sin_family = AF_INET.convert()
                addr.sin_port = posix_htons(port.toShort()).toUShort()
                setLoopback(addr)
                check(connect(fd, addr.ptr.reinterpret<sockaddr>(), sizeOf<sockaddr_in>().convert()) == 0) { "connect" }
            }
            val bytes = request.encodeToByteArray()
            bytes.usePinned { write(fd, it.addressOf(0), bytes.size.convert()) }
            var got = ByteArray(0)
            val buf = ByteArray(4096)
            while (true) {
                val n = buf.usePinned { read(fd, it.addressOf(0), buf.size.convert()).toInt() }
                if (n <= 0) break
                got += buf.copyOf(n)
            }
            return got.decodeToString()
        } finally {
            close(fd)
        }
    }

    @Test
    fun theReplyGetsTheWholeRequestAndItsBytesGoBackAsWritten() {
        var seen = ""
        LoopbackServer { req ->
            seen = req.decodeToString()
            "HTTP/1.1 200 OK\r\nContent-Length: 999\r\n\r\ncut".encodeToByteArray()
        }.use { server ->
            val reply = exchange(server.port, "POST /x HTTP/1.1\r\nHost: h\r\nContent-Length: 4\r\n\r\nbody")
            assertEquals("HTTP/1.1 200 OK\r\nContent-Length: 999\r\n\r\ncut", reply)
            assertEquals(1, server.hitCount)
        }
        assertTrue(seen.startsWith("POST /x HTTP/1.1\r\n"), seen)
        assertTrue(seen.endsWith("\r\n\r\nbody"), seen)
    }

    @Test
    fun connectionsAreServedOneAfterAnother() {
        LoopbackServer { req -> req.decodeToString().substringBefore(' ').encodeToByteArray() }.use { server ->
            assertEquals("GET", exchange(server.port, "GET / HTTP/1.1\r\n\r\n"))
            assertEquals("HEAD", exchange(server.port, "HEAD / HTTP/1.1\r\n\r\n"))
            assertEquals("http://127.0.0.1:${server.port}", server.url)
        }
    }
}
