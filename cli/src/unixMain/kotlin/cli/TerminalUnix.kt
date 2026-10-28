package cli

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import platform.posix.STDERR_FILENO
import platform.posix.STDIN_FILENO
import platform.posix.STDOUT_FILENO
import platform.posix.ioctl
import platform.posix.isatty
import platform.posix.winsize

/** `TIOCGWINSZ`: macOS and Linux number it differently, so the shared POSIX code cannot name it. */
internal expect val WINSIZE_REQUEST: ULong

private fun Stream.fd() =
    when (this) {
        Stream.Out -> STDOUT_FILENO
        Stream.Err -> STDERR_FILENO
        Stream.In -> STDIN_FILENO
    }

@OptIn(ExperimentalForeignApi::class)
internal actual fun terminalWidth(): Int? =
    Stream.entries.firstNotNullOfOrNull { stream ->
        memScoped {
            val size = alloc<winsize>()
            if (isTerminal(stream) && ioctl(stream.fd(), WINSIZE_REQUEST, size.ptr) == 0) size.ws_col.toInt() else null
        }
    }

internal actual fun isTerminal(stream: Stream): Boolean = isatty(stream.fd()) == 1

internal actual fun termSupportsColor(term: String?): Boolean = term != null && term != "dumb"

internal actual fun showsEscapes(stream: Stream): Boolean = true
