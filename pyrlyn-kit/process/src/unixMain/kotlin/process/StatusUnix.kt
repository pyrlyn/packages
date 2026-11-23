package process

import platform.posix.SIGABRT
import platform.posix.SIGALRM
import platform.posix.SIGBUS
import platform.posix.SIGFPE
import platform.posix.SIGHUP
import platform.posix.SIGILL
import platform.posix.SIGINT
import platform.posix.SIGKILL
import platform.posix.SIGPIPE
import platform.posix.SIGQUIT
import platform.posix.SIGSEGV
import platform.posix.SIGTERM
import platform.posix.SIGTRAP
import platform.posix.SIGUSR1
import platform.posix.SIGUSR2

private const val SIGNAL_BITS = 0x7f
private const val CORE_BIT = 0x80
private const val CODE_SHIFT = 8
private const val CODE_BITS = 0xff

/** A `waitpid` status, decoded as `WIFEXITED`, `WEXITSTATUS` and `WTERMSIG` would, which Kotlin does not export. */
internal fun waitStatus(status: Int): ExitStatus =
    when (val signal = status and SIGNAL_BITS) {
        0 -> ExitStatus.Exited((status shr CODE_SHIFT) and CODE_BITS)
        else -> ExitStatus.Signaled(signal, status and CORE_BIT != 0)
    }

internal actual fun exitText(code: Int) = "exit status: $code"

internal actual fun signalName(signal: Int): String? = NAMES[signal]

private val NAMES =
    mapOf(
        SIGHUP to "SIGHUP",
        SIGINT to "SIGINT",
        SIGQUIT to "SIGQUIT",
        SIGILL to "SIGILL",
        SIGTRAP to "SIGTRAP",
        SIGABRT to "SIGABRT",
        SIGBUS to "SIGBUS",
        SIGFPE to "SIGFPE",
        SIGKILL to "SIGKILL",
        SIGUSR1 to "SIGUSR1",
        SIGSEGV to "SIGSEGV",
        SIGUSR2 to "SIGUSR2",
        SIGPIPE to "SIGPIPE",
        SIGALRM to "SIGALRM",
        SIGTERM to "SIGTERM",
    )
