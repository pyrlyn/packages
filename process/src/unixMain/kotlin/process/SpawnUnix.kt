// POSIX children: posix_spawn in its own process group (spawn.def), pipes read through poll, and the group killed
// with SIGKILL.
@file:OptIn(ExperimentalForeignApi::class, UnsafeNumber::class)

package process

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.CPointerVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.IntVar
import kotlinx.cinterop.UnsafeNumber
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
import process.spawn.kit_exe_path
import process.spawn.kit_pipe
import process.spawn.kit_spawn
import process.spawn.kit_spawn_inherit
import kotlin.time.TimeSource

/** Longest executable path [currentExe] reads; `PATH_MAX` on Linux and macOS alike. */
private const val EXE_PATH_MAX = 4096
private const val MICROS_PER_MILLI = 1000

private class FdSource(
    val fd: Int,
) : Source {
    override fun read(buf: ByteArray): Int {
        val n = buf.usePinned { read(fd, it.addressOf(0), buf.size.convert()) }.toInt()
        return if (n < 0 && errno == EINTR) -1 else maxOf(n, 0)
    }

    override fun close() {
        close(fd)
    }
}

private class PosixChild(
    override val pid: Int,
    private val program: String,
) : Child {
    override fun tryWait(): Ended? =
        memScoped {
            val status = alloc<IntVar>()
            val rc = waitpid(pid, status.ptr, WNOHANG)
            when {
                rc == pid -> Ended.Done(waitStatus(status.value))
                rc == 0 || (rc < 0 && errno == EINTR) -> null
                else -> Ended.Failed("could not be waited on: ${osError(errno)}")
            }
        }

    override fun waitFor(): ExitStatus =
        memScoped {
            val status = alloc<IntVar>()
            while (waitpid(pid, status.ptr, 0) != pid) {
                if (errno != EINTR) throw ProcessException("$program could not be waited on: ${osError(errno)}", ByteArray(0))
            }
            waitStatus(status.value)
        }

    /** The group first, then the child itself in case it left the group. */
    override fun killTree() {
        if (pid <= 0) return
        kill(-pid, SIGKILL)
        kill(pid, SIGKILL)
    }

    override fun reap() {
        val giveUp = TimeSource.Monotonic.markNow() + DRAIN
        while (!giveUp.hasPassedNow()) {
            if (memScoped { waitpid(pid, alloc<IntVar>().ptr, WNOHANG) } != 0) return
            usleep(POLL.inWholeMicroseconds.convert())
        }
    }

    override fun close() = Unit
}

internal actual fun spawnPiped(command: Command): Piped =
    memScoped {
        val outPipe = allocArray<IntVar>(2)
        val errPipe = allocArray<IntVar>(2)
        val outRc = kit_pipe(outPipe)
        if (outRc != 0) throw cannotRun(command, osError(outRc))
        val errRc = kit_pipe(errPipe)
        if (errRc != 0) {
            close(outPipe[0])
            close(outPipe[1])
            throw cannotRun(command, osError(errRc))
        }
        val pid = alloc<IntVar>()
        val rc = kit_spawn(pid.ptr, command.program, argv(command), envp(command), command.dir, command.stdin, outPipe[1], errPipe[1])
        // The child has its copies; the parent keeping the write ends would never see end of file.
        close(outPipe[1])
        close(errPipe[1])
        if (rc != 0) {
            close(outPipe[0])
            close(errPipe[0])
            throw cannotRun(command, osError(rc))
        }
        Piped(PosixChild(pid.value, command.program), FdSource(outPipe[0]), FdSource(errPipe[0]))
    }

internal actual fun spawnInherited(command: Command): Child =
    memScoped {
        val pid = alloc<IntVar>()
        val rc = kit_spawn_inherit(pid.ptr, command.program, argv(command), envp(command), command.dir)
        if (rc != 0) throw cannotRun(command, osError(rc))
        PosixChild(pid.value, command.program)
    }

private fun kotlinx.cinterop.MemScope.argv(command: Command) = allocArrayOf((listOf(command.program) + command.args).map { it.cstr.ptr } + listOf(null))

private fun kotlinx.cinterop.MemScope.envp(command: Command) = if (command.inheritsEnv) kit_environ() else allocArrayOf(command.mergedEnv().map { it.cstr.ptr } + listOf(null))

internal actual fun ready(
    sources: List<Source>,
    waitMs: Int,
): List<Boolean> =
    memScoped {
        val fds = allocArray<pollfd>(sources.size)
        sources.forEachIndexed { i, s ->
            fds[i].fd = (s as FdSource).fd
            fds[i].events = POLLIN.convert()
            fds[i].revents = 0
        }
        val any = poll(fds, sources.size.convert(), waitMs) > 0
        List(sources.size) { any && fds[it].revents.toInt() != 0 }
    }

internal actual fun sleepMillis(ms: Int) {
    usleep((ms * MICROS_PER_MILLI).convert())
}

internal actual fun environEntries(): List<String> =
    buildList {
        val inherited: CPointer<CPointerVar<ByteVar>>? = kit_environ()
        var i = 0
        while (inherited != null) add(inherited[i++]?.toKString() ?: break)
    }

internal actual fun envKey(name: String) = name

internal actual fun osError(code: Int) = "${strerror(code)?.toKString()} (os error $code)"

actual fun currentExe(): String? =
    memScoped {
        val buf = allocArray<ByteVar>(EXE_PATH_MAX)
        if (kit_exe_path(buf, EXE_PATH_MAX) == 0) buf.toKString().takeIf { it.isNotEmpty() } else null
    }
