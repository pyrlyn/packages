// A child run under three guards: no stdin but a file the caller names, output capped, and a deadline after which its whole process tree is
// killed. A command a file or a plugin supplies is untrusted; without these, one that waits on input, prints forever
// or never exits takes the caller down with it.
package process

import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

/** What a finished child printed and how it ended. Each stream holds at most the cap plus one byte, so a caller can tell "exactly the limit" from "did not stop". */
class Output(
    val status: ExitStatus,
    val stdout: ByteArray,
    val stderr: ByteArray,
)

/**
 * The child could not be run, waited on or finished in time: [detail] in words, and the stderr drained before that.
 * [stoppedPid] is the child killed at the deadline, so a caller can say "ran too long" apart from "could not start".
 */
class ProcessException(
    val detail: String,
    val stderr: ByteArray,
    val stoppedPid: Int? = null,
    cause: Throwable? = null,
) : Exception(detail, cause)

/** How long the pipes may still drain once the child has ended or been killed. */
internal val DRAIN = 500.milliseconds

/** How often a running child is checked on. */
internal val POLL = 20.milliseconds

private const val READ_CHUNK = 64 * 1024

/**
 * Run the command with no stdin, keep at most [maxOutput] bytes (plus one) of each stream, and kill its whole process
 * tree if it outlives [timeout]. Throws [ProcessException] when it cannot be started, waited on, or ends in time.
 */
fun Command.runBounded(
    timeout: Duration,
    maxOutput: Long,
): Output = Run(this, timeout, maxOutput + 1).run()

/** The failure to start [command], as Rust words it. */
internal fun cannotRun(
    command: Command,
    why: String,
    cause: Throwable? = null,
) = ProcessException("could not run ${command.program}: $why", ByteArray(0), cause = cause)

private class Stream(
    private val source: Source,
    private val cap: Long,
) {
    private var bytes = ByteArray(0)
    private var size = 0

    var open = true
        private set

    fun read(buf: ByteArray) {
        val n = source.read(buf)
        when {
            n > 0 -> append(buf, n)
            n == 0 -> close()
        }
    }

    private fun append(
        chunk: ByteArray,
        n: Int,
    ) {
        val keep = minOf(n.toLong(), cap - size).toInt()
        if (size + keep > bytes.size) bytes = bytes.copyOf(maxOf(size + keep, bytes.size * 2))
        chunk.copyInto(bytes, size, 0, keep)
        size += keep
        // Past the cap the child would block on a full pipe; closing it ends that write instead.
        if (size.toLong() >= cap) close()
    }

    fun close() {
        if (open) source.close()
        open = false
    }

    fun take() = bytes.copyOf(size)

    val handle get() = source
}

private class Run(
    private val command: Command,
    private val timeout: Duration,
    private val cap: Long,
) {
    private val deadline = TimeSource.Monotonic.markNow() + timeout
    private val buf = ByteArray(READ_CHUNK)

    fun run(): Output {
        val piped = spawnPiped(command)
        val child = piped.child
        val out = Stream(piped.out, cap)
        val err = Stream(piped.err, cap)
        try {
            val ended = awaitEnd(child, out, err)
            // A child may exit while a grandchild still holds a pipe; killing only the child would leave the
            // pipes open past the deadline.
            child.killTree()
            // On a timeout the deadline is already past; the short grace still lets the last of stderr through.
            drain(maxOf(deadline, TimeSource.Monotonic.markNow() + DRAIN), out, err)
            return when (ended) {
                is Ended.Done -> Output(ended.status, out.take(), err.take())
                is Ended.Failed -> throw ProcessException(ended.detail, err.take(), ended.stoppedPid)
            }
        } finally {
            out.close()
            err.close()
            child.close()
        }
    }

    /** Read the pipes until the child ends, is killed at the deadline, or cannot be waited on. */
    private fun awaitEnd(
        child: Child,
        out: Stream,
        err: Stream,
    ): Ended {
        while (true) {
            val waited = child.tryWait()
            if (waited is Ended.Failed) child.killTree()
            if (waited != null) return waited
            if (deadline.hasPassedNow()) {
                child.killTree()
                child.reap()
                return Ended.Failed("did not answer within ${timeout.inWholeSeconds}s and was stopped", child.pid)
            }
            pump(POLL, out, err)
        }
    }

    private fun drain(
        until: TimeSource.Monotonic.ValueTimeMark,
        out: Stream,
        err: Stream,
    ) {
        while ((out.open || err.open) && !until.hasPassedNow()) pump(minOf(POLL, until.elapsedNow().unaryMinus()), out, err)
    }

    /** Wait up to [wait] for either pipe, and read what it has. */
    private fun pump(
        wait: Duration,
        vararg all: Stream,
    ) {
        val streams = all.filter { it.open }
        val ms = wait.inWholeMilliseconds.coerceAtLeast(0).toInt()
        if (streams.isEmpty()) {
            sleepMillis(ms)
            return
        }
        ready(streams.map { it.handle }, ms).forEachIndexed { i, now -> if (now) streams[i].read(buf) }
    }
}

/** Whether the child gets this process's environment unchanged. */
internal val Command.inheritsEnv get() = env.isEmpty() && removedEnv.isEmpty()

/** This process's environment with the command's removals and settings on top (Rust `Command::env`). */
internal fun Command.mergedEnv(): List<String> {
    val set = env.keys.mapTo(HashSet(), ::envKey)
    val removed = removedEnv.mapTo(HashSet(), ::envKey)
    return buildList {
        for (entry in environEntries()) {
            val key = envKey(nameOf(entry))
            if (key !in set && key !in removed) add(entry)
        }
        env.forEach { (k, v) -> add("$k=$v") }
    }
}

/** This process's environment, in its order (Rust `std::env::vars`); an entry without `=` is a name with no value. */
fun envVars(): List<Pair<String, String>> = environEntries().map { nameOf(it) to it.substring(minOf(nameOf(it).length + 1, it.length)) }

/** An entry's name: up to the first `=` after the first character, since Windows names such as `=C:` start with one. */
internal fun nameOf(entry: String): String = entry.indexOf('=', startIndex = 1).let { if (it < 0) entry else entry.substring(0, it) }
