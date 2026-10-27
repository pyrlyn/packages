// A child run under three guards: no stdin, output capped, and a deadline after which its whole process tree is
// killed. A command a file or a plugin supplies is untrusted; without these, one that waits on input, prints forever
// or never exits takes the caller down with it.
@file:OptIn(ExperimentalForeignApi::class)

package process

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.CPointerVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.IntVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.allocArrayOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.cstr
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.set
import kotlinx.cinterop.toKString
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.posix.EINTR
import platform.posix.POLLIN
import platform.posix.SIGKILL
import platform.posix.WNOHANG
import platform.posix.close
import platform.posix.errno
import platform.posix.kill
import platform.posix.poll
import platform.posix.pollfd
import platform.posix.read
import platform.posix.strerror
import platform.posix.usleep
import platform.posix.waitpid
import process.spawn.kit_environ
import process.spawn.kit_pipe
import process.spawn.kit_spawn
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
) : Exception(detail)

/** How long the pipes may still drain once the child has ended or been killed. */
private val DRAIN = 500.milliseconds

/** How often a running child is checked on. */
private val POLL = 20.milliseconds

private const val READ_CHUNK = 64 * 1024

/**
 * Run the command with no stdin, keep at most [maxOutput] bytes (plus one) of each stream, and kill its whole process
 * group if it outlives [timeout]. Throws [ProcessException] when it cannot be started, waited on, or ends in time.
 */
fun Command.runBounded(
    timeout: Duration,
    maxOutput: Long,
): Output = Run(this, timeout, maxOutput + 1).run()

private class Stream(
    var fd: Int,
    private val cap: Long,
) {
    private var bytes = ByteArray(0)
    private var size = 0

    val open get() = fd >= 0

    fun append(
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
        if (fd >= 0) close(fd)
        fd = -1
    }

    fun take() = bytes.copyOf(size)
}

private class Run(
    private val command: Command,
    private val timeout: Duration,
    cap: Long,
) {
    private val deadline = TimeSource.Monotonic.markNow() + timeout
    private val out = Stream(-1, cap)
    private val err = Stream(-1, cap)
    private val buf = ByteArray(READ_CHUNK)

    fun run(): Output {
        val pid = spawn()
        try {
            val ended = awaitEnd(pid)
            // A child may exit while a grandchild still holds a pipe; killing only the child would leave the
            // pipes open past the deadline.
            killTree(pid)
            // On a timeout the deadline is already past; the short grace still lets the last of stderr through.
            drain(maxOf(deadline, TimeSource.Monotonic.markNow() + DRAIN))
            return when (ended) {
                is Ended.Done -> Output(ended.status, out.take(), err.take())
                is Ended.Failed -> throw ProcessException(ended.detail, err.take(), ended.stoppedPid)
            }
        } finally {
            out.close()
            err.close()
        }
    }

    private sealed interface Ended {
        class Done(
            val status: ExitStatus,
        ) : Ended

        class Failed(
            val detail: String,
            val stoppedPid: Int? = null,
        ) : Ended
    }

    private fun spawn(): Int =
        memScoped {
            val outPipe = allocArray<IntVar>(2)
            val errPipe = allocArray<IntVar>(2)
            failIfSet(kit_pipe(outPipe))
            out.fd = outPipe[0]
            val errRc = kit_pipe(errPipe)
            if (errRc != 0) {
                close(outPipe[1])
                failIfSet(errRc)
            }
            err.fd = errPipe[0]
            val argv = allocArrayOf((listOf(command.program) + command.args).map { it.cstr.ptr } + listOf(null))
            val envp = if (command.env.isEmpty()) kit_environ() else allocArrayOf(mergedEnv(command.env).map { it.cstr.ptr } + listOf(null))
            val pid = alloc<IntVar>()
            val rc = kit_spawn(pid.ptr, command.program, argv, envp, command.dir, outPipe[1], errPipe[1])
            // The child has its copies; the parent keeping the write ends would never see end of file.
            close(outPipe[1])
            close(errPipe[1])
            failIfSet(rc)
            pid.value
        }

    private fun failIfSet(rc: Int) {
        if (rc == 0) return
        out.close()
        err.close()
        throw ProcessException("could not run ${command.program}: ${osError(rc)}", ByteArray(0))
    }

    /** Read the pipes until the child ends, is killed at the deadline, or cannot be waited on. */
    private fun awaitEnd(pid: Int): Ended {
        while (true) {
            when (val waited = tryWait(pid)) {
                null -> {}

                else -> {
                    return waited
                }
            }
            if (deadline.hasPassedNow()) {
                killTree(pid)
                reap(pid)
                return Ended.Failed("did not answer within ${timeout.inWholeSeconds}s and was stopped", pid)
            }
            pump(POLL)
        }
    }

    private fun tryWait(pid: Int): Ended? =
        memScoped {
            val status = alloc<IntVar>()
            val rc = waitpid(pid, status.ptr, WNOHANG)
            when {
                rc == pid -> Ended.Done(ExitStatus.of(status.value))
                rc == 0 || (rc < 0 && errno == EINTR) -> null
                else -> Ended.Failed("could not be waited on: ${osError(errno)}").also { killTree(pid) }
            }
        }

    /** After the kill, give the child a moment to be reaped so it does not linger as a zombie. */
    private fun reap(pid: Int) {
        val giveUp = TimeSource.Monotonic.markNow() + DRAIN
        while (!giveUp.hasPassedNow()) {
            if (memScoped { waitpid(pid, alloc<IntVar>().ptr, WNOHANG) } != 0) return
            usleep(POLL.inWholeMicroseconds.convert())
        }
    }

    private fun drain(until: TimeSource.Monotonic.ValueTimeMark) {
        while ((out.open || err.open) && !until.hasPassedNow()) pump(minOf(POLL, until.elapsedNow().unaryMinus()))
    }

    /** Wait up to [wait] for either pipe, and read what it has. */
    private fun pump(wait: Duration) {
        val streams = listOf(out, err).filter { it.open }
        val ms = wait.inWholeMilliseconds.coerceAtLeast(0).toInt()
        if (streams.isEmpty()) {
            usleep((ms * MICROS_PER_MILLI).convert())
            return
        }
        memScoped {
            val fds = allocArray<pollfd>(streams.size)
            streams.forEachIndexed { i, s ->
                fds[i].fd = s.fd
                fds[i].events = POLLIN.convert()
                fds[i].revents = 0
            }
            if (poll(fds, streams.size.convert(), ms) <= 0) return
            streams.forEachIndexed { i, s -> if (fds[i].revents.toInt() != 0) readInto(s) }
        }
    }

    private fun readInto(stream: Stream) {
        val n = buf.usePinned { read(stream.fd, it.addressOf(0), buf.size.convert()) }.toInt()
        when {
            n > 0 -> {
                stream.append(buf, n)
            }

            n < 0 && errno == EINTR -> {}

            else -> {
                stream.close()
            }
        }
    }

    companion object {
        const val MICROS_PER_MILLI = 1000
    }
}

/** Stop the child and everything it started: the group first, then the child itself in case it left the group. */
private fun killTree(pid: Int) {
    if (pid <= 0) return
    kill(-pid, SIGKILL)
    kill(pid, SIGKILL)
}

/** This process's environment as `KEY=value` entries, [extra] set on top (Rust `Command::env`). */
internal fun mergedEnv(extra: Map<String, String>): List<String> =
    buildList {
        val env: CPointer<CPointerVar<ByteVar>>? = kit_environ()
        var i = 0
        while (env != null) {
            val entry = env[i++]?.toKString() ?: break
            if (entry.substringBefore('=') !in extra) add(entry)
        }
        extra.forEach { (k, v) -> add("$k=$v") }
    }

/** An errno worded as Rust prints an OS error: `No such file or directory (os error 2)`. */
private fun osError(code: Int) = "${strerror(code)?.toKString()} (os error $code)"
