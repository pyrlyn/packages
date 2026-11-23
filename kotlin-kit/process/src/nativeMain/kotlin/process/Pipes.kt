// A child that keeps running while this process talks to it over its stdin and stdout: a helper process, an MCP
// server on stdio, a plugin. Unlike `runBounded` it has no deadline and no output cap of its own; the caller reads as
// it goes and decides when the child has gone wrong.
@file:OptIn(ExperimentalAtomicApi::class)

package process

import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.decrementAndFetch
import kotlin.concurrent.atomics.incrementAndFetch
import kotlin.time.Duration
import kotlin.time.TimeSource

/**
 * Start the command with its stdin, stdout and stderr piped to this process, apart from its console and in a process
 * group (on Windows a job) of its own, and leave it running. [Command.stdinFrom] does not apply. Read its stdout and
 * stderr as it goes, both of them: a pipe nobody reads fills and stops the child. Throws [ProcessException] when it
 * cannot be started.
 */
fun Command.spawnPipes(): PipedChild {
    require(stdin == null) { "spawnPipes gives the child a pipe as stdin, not $stdin" }
    return PipedChild(spawnPipes(this), program)
}

/**
 * A child started by [spawnPipes]. One thread may read [stdout], one [stderr] and one write [stdin] at the same time;
 * [kill] and [close] may come from any thread and unblock them all. [close] it when done: that stops the child and its
 * tree if it still runs and frees the pipes.
 */
class PipedChild internal constructor(
    piped: Piped,
    private val program: String,
) : AutoCloseable {
    private val child = piped.child
    private val closed = AtomicBoolean(false)
    private val ended = AtomicReference<ExitStatus?>(null)

    val pid: Int get() = child.pid

    /** The child's stdin. */
    val stdin = ChildInput(Guarded(piped.input!!) { it.close() }, program)

    /** The child's stdout. */
    val stdout = ChildOutput(Guarded(piped.out) { it.close() })

    /** The child's stderr. */
    val stderr = ChildOutput(Guarded(piped.err) { it.close() })

    /**
     * How the child ended, or null while it runs; after [close], null unless it had ended before. Throws
     * [ProcessException] when it cannot be waited on.
     */
    fun exitStatus(): ExitStatus? =
        ended.load() ?: if (closed.load()) {
            null
        } else {
            when (val now = child.tryWait()) {
                null -> null
                is Ended.Done -> now.status.also { ended.compareAndSet(null, it) }
                is Ended.Failed -> throw ProcessException("$program ${now.detail}", ByteArray(0))
            }
        }

    /**
     * [exitStatus], waiting up to [timeout] for the child to end: for the moment between a child closing its stdout
     * and the system reporting its exit.
     */
    fun waitFor(timeout: Duration): ExitStatus? {
        val giveUp = TimeSource.Monotonic.markNow() + timeout
        while (true) {
            exitStatus()?.let { return it }
            if (closed.load() || giveUp.hasPassedNow()) return null
            sleepMillis(POLL.inWholeMilliseconds.toInt())
        }
    }

    /** Stop the child and everything it started. Reads then end at end of file and writes fail. */
    fun kill() = child.killTree()

    override fun close() {
        // How it ended counts only if it ended on its own, so look before the kill.
        runCatching { exitStatus() }
        if (!closed.compareAndSet(expectedValue = false, newValue = true)) return
        child.killTree()
        child.reap()
        stdin.close()
        stdout.close()
        stderr.close()
        child.close()
    }
}

/** The write end of a [PipedChild]'s stdin. */
class ChildInput internal constructor(
    private val sink: Guarded<Sink>,
    private val program: String,
) {
    /**
     * Write all of [count] bytes of [buf] from [offset], blocking while the pipe is full. Throws [ProcessException]
     * once the child has closed its stdin or ended, or this end was closed.
     */
    fun write(
        buf: ByteArray,
        offset: Int = 0,
        count: Int = buf.size - offset,
    ) {
        require(offset >= 0 && count >= 0 && offset + count <= buf.size) { "range $offset+$count outside ${buf.size}" }
        var at = offset
        val end = offset + count
        while (at < end) {
            val n = sink.use { it.write(buf, at, end - at) } ?: throw broken("its stdin is closed")
            if (n <= 0) throw broken("stopped reading its stdin")
            at += n
        }
    }

    /** Close the pipe, so the child reads end of file. */
    fun close() = sink.close()

    private fun broken(why: String) = ProcessException("$program $why", ByteArray(0))
}

/** The read end of a [PipedChild]'s stdout or stderr. */
class ChildOutput internal constructor(
    private val source: Guarded<Source>,
) {
    /**
     * Read at most [count] bytes into [buf] from [offset], blocking until some arrive: the number read, or -1 at end of
     * file, which comes once the child and everything it started have closed the pipe, or this end was closed.
     */
    fun read(
        buf: ByteArray,
        offset: Int = 0,
        count: Int = buf.size - offset,
    ): Int {
        require(offset >= 0 && count >= 0 && offset + count <= buf.size) { "range $offset+$count outside ${buf.size}" }
        if (count == 0) return 0
        // Null while nothing is ready, negative when a read was interrupted.
        var n: Int? = null
        while (n == null || n < 0) {
            // Waiting in short polls rather than one blocking read lets close() free the pipe without racing a read
            // that would otherwise still hold it.
            n =
                if (source.isClosed) {
                    0
                } else {
                    source.use { s -> if (ready(listOf(s), POLL.inWholeMilliseconds.toInt())[0]) s.read(buf, offset, count) else null }
                }
        }
        return if (n > 0) n else -1
    }

    /** Close the pipe; a read in progress returns -1. */
    fun close() = source.close()
}

/**
 * A pipe end several threads may use while another closes it: [close] waits until no call is inside [use], so a
 * descriptor is never closed under a call, nor reused by the system while one still holds its number.
 */
internal class Guarded<T>(
    private val resource: T,
    private val release: (T) -> Unit,
) {
    private val users = AtomicInt(0)
    private val closed = AtomicBoolean(false)

    val isClosed: Boolean get() = closed.load()

    /** [block]'s result, or null once closed. */
    fun <R : Any> use(block: (T) -> R?): R? {
        users.incrementAndFetch()
        try {
            if (closed.load()) return null
            return block(resource)
        } finally {
            users.decrementAndFetch()
        }
    }

    fun close() {
        if (!closed.compareAndSet(expectedValue = false, newValue = true)) return
        // Calls inside are a poll of at most POLL, a read of ready bytes, or a write the killed child no longer blocks.
        while (users.load() != 0) sleepMillis(1)
        release(resource)
    }
}
