package process

/** How a child ended. [toString] reads as Rust's `ExitStatus` prints, which error messages quote. */
sealed interface ExitStatus {
    val success: Boolean

    /** It returned [code]; on Windows a code with the high bit set is an NTSTATUS such as `0xc0000005`. */
    data class Exited(
        val code: Int,
    ) : ExitStatus {
        override val success get() = code == 0

        override fun toString() = exitText(code)
    }

    /** [signal] ended it; Unix only. */
    data class Signaled(
        val signal: Int,
        val coreDumped: Boolean = false,
    ) : ExitStatus {
        override val success get() = false

        override fun toString() = "signal: $signal" + (signalName(signal)?.let { " ($it)" } ?: "") + if (coreDumped) " (core dumped)" else ""
    }
}
