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
import platform.posix.POLLIN
import platform.posix.SIGPIPE
import platform.posix.SIG_IGN
import platform.posix.SOCK_STREAM
import platform.posix.accept
import platform.posix.bind
import platform.posix.close
import platform.posix.connect
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

internal actual fun listenLoopback(): Listening {
    // A client that hangs up mid-reply would otherwise kill the whole test process on the next write.
    signal(SIGPIPE, SIG_IGN)
    val fd = socket(AF_INET, SOCK_STREAM, 0)
    check(fd >= 0) { "socket" }
    return memScoped {
        val addr = loopbackAddress(0)
        check(bind(fd, addr.ptr.reinterpret<sockaddr>(), sizeOf<sockaddr_in>().convert()) == 0) { "bind" }
        check(listen(fd, BACKLOG) == 0) { "listen" }
        val len = alloc<socklen_tVar>()
        len.value = sizeOf<sockaddr_in>().convert()
        check(getsockname(fd, addr.ptr.reinterpret(), len.ptr) == 0) { "getsockname" }
        Listening(fd.toLong(), posix_htons(addr.sin_port.toShort()).toUShort().toInt())
    }
}

internal actual fun connectLoopback(port: Int): Long {
    val fd = socket(AF_INET, SOCK_STREAM, 0)
    check(fd >= 0) { "socket" }
    memScoped {
        val addr = loopbackAddress(port)
        check(connect(fd, addr.ptr.reinterpret<sockaddr>(), sizeOf<sockaddr_in>().convert()) == 0) { "connect" }
    }
    return fd.toLong()
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
        val p = alloc<pollfd>()
        p.fd = socket.toInt()
        p.events = POLLIN.convert()
        poll(p.ptr, 1u, waitMs) > 0
    }

internal actual fun acceptOne(listener: Long): Long = accept(listener.toInt(), null, null).toLong()

internal actual fun receive(
    socket: Long,
    buf: ByteArray,
): Int = buf.usePinned { read(socket.toInt(), it.addressOf(0), buf.size.convert()).toInt() }

internal actual fun sendFrom(
    socket: Long,
    bytes: ByteArray,
    offset: Int,
): Int = bytes.usePinned { write(socket.toInt(), it.addressOf(offset), (bytes.size - offset).convert()).toInt() }

internal actual fun closeSocket(socket: Long) {
    close(socket.toInt())
}
