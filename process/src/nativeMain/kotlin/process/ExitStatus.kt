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

/** How a child ended. [toString] reads as Rust's `ExitStatus` prints, which error messages quote. */
sealed interface ExitStatus {
    val success: Boolean

    /** It returned [code]. */
    data class Exited(
        val code: Int,
    ) : ExitStatus {
        override val success get() = code == 0

        override fun toString() = "exit status: $code"
    }

    /** [signal] ended it. */
    data class Signaled(
        val signal: Int,
        val coreDumped: Boolean = false,
    ) : ExitStatus {
        override val success get() = false

        override fun toString() = "signal: $signal" + (NAMES[signal]?.let { " ($it)" } ?: "") + if (coreDumped) " (core dumped)" else ""
    }

    companion object {
        private const val SIGNAL_BITS = 0x7f
        private const val CORE_BIT = 0x80
        private const val CODE_SHIFT = 8
        private const val CODE_BITS = 0xff

        /** A `waitpid` status, decoded as `WIFEXITED`, `WEXITSTATUS` and `WTERMSIG` would, which Kotlin does not export. */
        internal fun of(status: Int): ExitStatus =
            when (val signal = status and SIGNAL_BITS) {
                0 -> Exited((status shr CODE_SHIFT) and CODE_BITS)
                else -> Signaled(signal, status and CORE_BIT != 0)
            }

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
    }
}
