// What a child is made of, one set per system: POSIX spawn, pipes and process groups in unixMain; CreateProcessW,
// anonymous pipes and a job object in mingwMain. The loops that guard a child stay shared, in Bounded.kt.
package process

/** The read end of a child's stdout or stderr. */
internal interface Source {
    /** Bytes read into [buf]: positive, 0 at end of file, negative when interrupted and to be read again. */
    fun read(buf: ByteArray): Int

    fun close()
}

/** A started child: its tree, its status, and the handles held on it. */
internal interface Child {
    val pid: Int

    /** How it ended, or null while it runs. */
    fun tryWait(): Ended?

    /** Wait however long it takes. Throws [ProcessException] when it cannot be waited on. */
    fun waitFor(): ExitStatus

    /** Stop the child and everything it started. */
    fun killTree()

    /** After [killTree], give the child a moment to be reaped so it does not linger. */
    fun reap()

    fun close()
}

internal sealed interface Ended {
    class Done(
        val status: ExitStatus,
    ) : Ended

    class Failed(
        val detail: String,
        val stoppedPid: Int? = null,
    ) : Ended
}

internal class Piped(
    val child: Child,
    val out: Source,
    val err: Source,
)

/** Start [command] with stdin from its file or an empty one and both outputs piped, apart from this console. */
internal expect fun spawnPiped(command: Command): Piped

/** Start [command] sharing this process's stdin, stdout, stderr and console. */
internal expect fun spawnInherited(command: Command): Child

/** Start [command] with no stdin, its output discarded and in a group of its own, and leave it running; its pid. */
internal expect fun spawnDetached(command: Command): Int

/** For each of [sources], whether a read would not block, waiting up to [waitMs] for one to be. */
internal expect fun ready(
    sources: List<Source>,
    waitMs: Int,
): List<Boolean>

internal expect fun sleepMillis(ms: Int)

/** This process's environment as `KEY=value` entries, in its order. */
internal expect fun environEntries(): List<String>

/** A variable's name as the system compares it: as written on Unix, without case on Windows. */
internal expect fun envKey(name: String): String

/** A system error code worded as Rust prints an OS error: `No such file or directory (os error 2)`. */
internal expect fun osError(code: Int): String

/** How Rust's `ExitStatus` prints an exit code: `exit status: 3` on Unix, `exit code: 3` on Windows. */
internal expect fun exitText(code: Int): String

/** A signal's name for [ExitStatus.Signaled]; null where there are none. */
internal expect fun signalName(signal: Int): String?

/** The path this process was started from, links not resolved (Rust `std::env::current_exe`); null when the OS will not say. */
expect fun currentExe(): String?
