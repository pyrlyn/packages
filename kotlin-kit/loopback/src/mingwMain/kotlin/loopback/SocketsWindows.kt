// Winsock: started once per process, a SOCKET carried as a Long, WSAPoll for poll. Windows has no SIGPIPE to ignore;
// a write to a peer that hung up just fails.
@file:OptIn(ExperimentalForeignApi::class)

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
import platform.posix.INVALID_SOCKET
import platform.posix.POLLRDNORM
import platform.posix.SOCKET
import platform.posix.SOCK_STREAM
import platform.posix.WSADATA
import platform.posix.WSAPOLLFD
import platform.posix.WSAPoll
import platform.posix.WSAStartup
import platform.posix.accept
import platform.posix.bind
import platform.posix.closesocket
import platform.posix.connect
import platform.posix.getsockname
import platform.posix.listen
import platform.posix.posix_htons
import platform.posix.recv
import platform.posix.send
import platform.posix.sockaddr
import platform.posix.sockaddr_in
import platform.posix.socket

/** WSAStartup is counted; one call that is never undone keeps winsock up for every server the process starts. */
private val started: Boolean by lazy {
    memScoped { WSAStartup(WINSOCK_2_2, alloc<WSADATA>().ptr) == 0 }
}

private fun newSocket(): SOCKET {
    check(started) { "WSAStartup" }
    val s = socket(AF_INET, SOCK_STREAM, 0)
    check(s != INVALID_SOCKET) { "socket" }
    return s
}

internal actual fun listenLoopback(): Listening {
    val s = newSocket()
    return memScoped {
        val addr = loopbackAddress(0)
        check(bind(s, addr.ptr.reinterpret<sockaddr>(), sizeOf<sockaddr_in>().convert()) == 0) { "bind" }
        check(listen(s, BACKLOG) == 0) { "listen" }
        val len = alloc<kotlinx.cinterop.IntVar>()
        len.value = sizeOf<sockaddr_in>().convert()
        check(getsockname(s, addr.ptr.reinterpret(), len.ptr) == 0) { "getsockname" }
        Listening(s.toLong(), posix_htons(addr.sin_port.toShort()).toUShort().toInt())
    }
}

internal actual fun connectLoopback(port: Int): Long {
    val s = newSocket()
    memScoped {
        val addr = loopbackAddress(port)
        check(connect(s, addr.ptr.reinterpret<sockaddr>(), sizeOf<sockaddr_in>().convert()) == 0) { "connect" }
    }
    return s.toLong()
}

private fun kotlinx.cinterop.MemScope.loopbackAddress(port: Int): sockaddr_in {
    val addr = alloc<sockaddr_in>()
    addr.sin_family = AF_INET.convert()
    addr.sin_port = posix_htons(port.toShort()).toUShort()
    val raw = addr.sin_addr.ptr.reinterpret<ByteVar>()
    LOOPBACK.forEachIndexed { i, b -> raw[i] = b }
    return addr
}

internal actual fun readable(
    socket: Long,
    waitMs: Int,
): Boolean =
    memScoped {
        val p = alloc<WSAPOLLFD>()
        p.fd = socket.toULong()
        p.events = POLLRDNORM.convert()
        WSAPoll(p.ptr, 1u, waitMs) > 0
    }

internal actual fun acceptOne(listener: Long): Long = accept(listener.toULong(), null, null).let { if (it == INVALID_SOCKET) -1 else it.toLong() }

internal actual fun receive(
    socket: Long,
    buf: ByteArray,
): Int = buf.usePinned { recv(socket.toULong(), it.addressOf(0), buf.size, 0) }

internal actual fun sendFrom(
    socket: Long,
    bytes: ByteArray,
    offset: Int,
): Int = bytes.usePinned { send(socket.toULong(), it.addressOf(offset), bytes.size - offset, 0) }

internal actual fun closeSocket(socket: Long) {
    closesocket(socket.toULong())
}

/** `MAKEWORD(2, 2)`: the winsock version every Windows since 98 has. */
private const val WINSOCK_2_2: UShort = 0x0202u
