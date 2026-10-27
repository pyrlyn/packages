// A child that shares the terminal, and this process's own executable. Kept apart from `runBounded` because the
// guards there (no stdin, captured output, a deadline) are exactly what a program asking for a password must not have.
@file:OptIn(ExperimentalForeignApi::class)

package process

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.IntVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.allocArrayOf
import kotlinx.cinterop.cstr
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.toKString
import kotlinx.cinterop.value
import platform.posix.EINTR
import platform.posix.errno
import platform.posix.strerror
import platform.posix.waitpid
import process.spawn.kit_environ
import process.spawn.kit_exe_path
import process.spawn.kit_spawn_inherit

/** Longest executable path [currentExe] reads; `PATH_MAX` on Linux and macOS alike. */
private const val EXE_PATH_MAX = 4096

/**
 * Run the command with this process's stdin, stdout and stderr, in its process group, and wait however long it takes
 * (Rust `Command::status`). For a program the person drives — a package manager that may ask for a password — and so
 * only for a program the caller chose, never one a file supplied. Throws [ProcessException] when it cannot be started
 * or waited on.
 */
fun Command.status(): ExitStatus =
    memScoped {
        val argv = allocArrayOf((listOf(program) + args).map { it.cstr.ptr } + listOf(null))
        val envp = if (env.isEmpty()) kit_environ() else allocArrayOf(mergedEnv(env).map { it.cstr.ptr } + listOf(null))
        val pid = alloc<IntVar>()
        val rc = kit_spawn_inherit(pid.ptr, program, argv, envp, dir)
        if (rc != 0) throw ProcessException("could not run $program: ${osError(rc)}", ByteArray(0))
        waitFor(program, pid.value)
    }

private fun waitFor(
    program: String,
    pid: Int,
): ExitStatus =
    memScoped {
        val status = alloc<IntVar>()
        while (waitpid(pid, status.ptr, 0) != pid) {
            if (errno != EINTR) throw ProcessException("$program could not be waited on: ${osError(errno)}", ByteArray(0))
        }
        ExitStatus.of(status.value)
    }

/** The path this process was started from, links not resolved (Rust `std::env::current_exe`); null when the OS will not say. */
fun currentExe(): String? =
    memScoped {
        val buf = allocArray<ByteVar>(EXE_PATH_MAX)
        if (kit_exe_path(buf, EXE_PATH_MAX) == 0) buf.toKString().takeIf { it.isNotEmpty() } else null
    }

internal fun osError(code: Int) = "${strerror(code)?.toKString()} (os error $code)"
