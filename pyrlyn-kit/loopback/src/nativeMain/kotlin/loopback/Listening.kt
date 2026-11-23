// The few socket calls the server makes, over a handle that is a file descriptor on POSIX and a SOCKET on Windows.
package loopback

/** A listening socket on `127.0.0.1` and the free port it got. */
internal class Listening(
    val socket: Long,
    val port: Int,
)

/** Bind a free loopback port and start listening; fails with [IllegalStateException] naming the call that failed. */
internal expect fun listenLoopback(): Listening

/** A client end connected to `127.0.0.1:[port]`, for tests that speak to the server without an HTTP client. */
internal expect fun connectLoopback(port: Int): Long

/** Whether [socket] has something to read (or a connection to accept) within [waitMs]. */
internal expect fun readable(
    socket: Long,
    waitMs: Int,
): Boolean

/** The next connection, or a negative value when there was none after all. */
internal expect fun acceptOne(listener: Long): Long

/** Bytes read into [buf]; 0 at end of stream, negative on an error. */
internal expect fun receive(
    socket: Long,
    buf: ByteArray,
): Int

/** Bytes of [bytes] from [offset] that the peer took; 0 or negative when it is gone. */
internal expect fun sendFrom(
    socket: Long,
    bytes: ByteArray,
    offset: Int,
): Int

internal expect fun closeSocket(socket: Long)

/** `127.0.0.1`, in the network byte order `sin_addr` holds. */
internal val LOOPBACK = "127.0.0.1".split('.').map { it.toUByte().toByte() }

internal const val BACKLOG = 16
