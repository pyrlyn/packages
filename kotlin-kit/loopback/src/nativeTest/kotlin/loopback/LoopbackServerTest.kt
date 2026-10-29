package loopback

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LoopbackServerTest {
    /** One request over a plain socket, so the server is tested apart from any HTTP client. */
    private fun exchange(
        port: Int,
        request: String,
    ): String {
        val socket = connectLoopback(port)
        try {
            val bytes = request.encodeToByteArray()
            sendFrom(socket, bytes, 0)
            var got = ByteArray(0)
            val buf = ByteArray(4096)
            while (true) {
                val n = receive(socket, buf)
                if (n <= 0) break
                got += buf.copyOf(n)
            }
            return got.decodeToString()
        } finally {
            closeSocket(socket)
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
