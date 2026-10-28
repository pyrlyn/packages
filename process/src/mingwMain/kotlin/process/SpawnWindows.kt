// Windows children: CreateProcessW through spawn_win.def, the command line Rust's std would build, pipes polled with
// PeekNamedPipe (anonymous pipes have no wait of their own), and a job object for the whole tree.
@file:OptIn(ExperimentalForeignApi::class)

package process

import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.MemScope
import kotlinx.cinterop.UShortVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.convert
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.plus
import kotlinx.cinterop.ptr
import kotlinx.cinterop.set
import kotlinx.cinterop.toKStringFromUtf16
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import kotlinx.cinterop.wcstr
import platform.windows.CloseHandle
import platform.windows.DWORDVar
import platform.windows.FreeEnvironmentStringsW
import platform.windows.GetEnvironmentStringsW
import platform.windows.GetFileAttributesW
import platform.windows.GetFullPathNameW
import platform.windows.GetModuleFileNameW
import platform.windows.GetSystemDirectoryW
import platform.windows.GetWindowsDirectoryW
import platform.windows.HANDLE
import platform.windows.INFINITE
import platform.windows.INVALID_FILE_ATTRIBUTES
import platform.windows.Sleep
import platform.windows.WCHARVar
import process.spawnwin.kit_child
import process.spawnwin.kit_kill_tree
import process.spawnwin.kit_peek
import process.spawnwin.kit_read
import process.spawnwin.kit_spawn_detached
import process.spawnwin.kit_spawn_inherit
import process.spawnwin.kit_spawn_piped
import process.spawnwin.kit_wait

private class PipeSource(
    val handle: HANDLE,
) : Source {
    /** A broken pipe is the end of file: the child, and everything holding the write end, is gone. */
    override fun read(buf: ByteArray): Int =
        memScoped {
            val got = alloc<DWORDVar>()
            val rc = buf.usePinned { kit_read(handle, it.addressOf(0), buf.size.convert(), got.ptr) }
            if (rc == 0u) got.value.toInt() else 0
        }

    /** Bytes waiting, or -1 when the pipe has ended or failed and a read would say so at once. */
    fun available(): Long =
        memScoped {
            val n = alloc<DWORDVar>()
            if (kit_peek(handle, n.ptr) == 0u) n.value.toLong() else -1
        }

    override fun close() {
        CloseHandle(handle)
    }
}

private class WinChild(
    private val process: HANDLE,
    private val job: HANDLE?,
    override val pid: Int,
    private val program: String,
) : Child {
    override fun tryWait(): Ended? =
        memScoped {
            val code = alloc<DWORDVar>()
            when (val rc = kit_wait(process, 0u, code.ptr)) {
                0u -> Ended.Done(ExitStatus.Exited(code.value.toInt()))
                WAIT_TIMEOUT -> null
                else -> Ended.Failed("could not be waited on: ${osError(rc.toInt())}")
            }
        }

    override fun waitFor(): ExitStatus =
        memScoped {
            val code = alloc<DWORDVar>()
            val rc = kit_wait(process, INFINITE, code.ptr)
            if (rc != 0u) throw ProcessException("$program could not be waited on: ${osError(rc.toInt())}", ByteArray(0))
            ExitStatus.Exited(code.value.toInt())
        }

    override fun killTree() = kit_kill_tree(job, process)

    override fun reap() {
        memScoped { kit_wait(process, DRAIN.inWholeMilliseconds.convert(), alloc<DWORDVar>().ptr) }
    }

    override fun close() {
        CloseHandle(process)
        job?.let { CloseHandle(it) }
    }
}

internal actual fun spawnPiped(command: Command): Piped =
    memScoped {
        val launch = launchOf(command)
        val child = alloc<kit_child>()
        val rc =
            kit_spawn_piped(
                launch.application,
                launch.commandLine.wcstr.ptr,
                envBlock(command),
                command.dir,
                command.stdin,
                child.ptr,
            )
        if (rc != 0u) throw cannotRun(command, osError(rc.toInt()))
        val process = WinChild(child.process!!, child.job, child.pid.toInt(), command.program)
        Piped(process, PipeSource(child.out!!), PipeSource(child.err!!))
    }

internal actual fun spawnInherited(command: Command): Child =
    memScoped {
        val launch = launchOf(command)
        val child = alloc<kit_child>()
        val rc = kit_spawn_inherit(launch.application, launch.commandLine.wcstr.ptr, envBlock(command), command.dir, child.ptr)
        if (rc != 0u) throw cannotRun(command, osError(rc.toInt()))
        WinChild(child.process!!, null, child.pid.toInt(), command.program)
    }

internal actual fun spawnDetached(command: Command): Int =
    memScoped {
        val launch = launchOf(command)
        val pid = alloc<DWORDVar>()
        val rc = kit_spawn_detached(launch.application, launch.commandLine.wcstr.ptr, envBlock(command), command.dir, pid.ptr)
        if (rc != 0u) throw cannotRun(command, osError(rc.toInt()))
        pid.value.toInt()
    }

private fun launchOf(command: Command): WindowsLaunch =
    try {
        windowsLaunch(command, searchDirs(command), ::exists, ::fullPath)
    } catch (e: LaunchRefused) {
        throw cannotRun(command, e.message.orEmpty(), e)
    }

/** The child's `PATH` counts only when the command touches it, as in Rust; the rest is where Windows keeps programs. */
private fun searchDirs(command: Command): SearchDirs {
    val touchesPath = (command.env.keys + command.removedEnv).any { envKey(it) == PATH }
    val childPath = if (touchesPath) command.mergedEnv().firstOrNull { envKey(nameOf(it)) == PATH }?.let(::valueOf) else null
    return SearchDirs(
        childPath = childPath,
        appDir = currentExe()?.substringBeforeLast('\\', ""),
        systemDir = systemDir(),
        windowsDir = windowsDir(),
        parentPath = environEntries().firstOrNull { envKey(nameOf(it)) == PATH }?.let(::valueOf),
    )
}

private fun valueOf(entry: String) = entry.substring(minOf(nameOf(entry).length + 1, entry.length))

/** Rust's `program_exists`: GetFileAttributesW does not follow a link, and a link that is there is enough. */
private fun exists(path: String) = GetFileAttributesW(path) != INVALID_FILE_ATTRIBUTES

private fun fullPath(path: String): String = wideCall { buf, size -> GetFullPathNameW(path, size, buf, null) } ?: path

private fun systemDir() = wideCall { buf, size -> GetSystemDirectoryW(buf, size) }

private fun windowsDir() = wideCall { buf, size -> GetWindowsDirectoryW(buf, size) }

/** Rust's `fill_utf16_buf`: a call that says how much room it needs when the buffer is too small; null when it fails. */
private inline fun wideCall(call: (CPointer<WCHARVar>, UInt) -> UInt): String? {
    var size = WIDE_START
    repeat(2) {
        val (text, need) =
            memScoped {
                val buf = allocArray<WCHARVar>(size)
                val n = call(buf, size.convert()).toInt()
                (if (n in 1 until size) buf.toKStringFromUtf16() else null) to n
            }
        if (text != null || need == 0) return text
        size = need + 1
    }
    return null
}

/** Rust `make_envp`: null to inherit; otherwise `KEY=value\0…\0`, sorted by name without case as Windows expects. */
private fun MemScope.envBlock(command: Command): CPointer<UShortVar>? {
    if (command.inheritsEnv) return null
    val entries = command.mergedEnv().sortedBy { envKey(nameOf(it)) }
    val text = if (entries.isEmpty()) "\u0000" else entries.joinToString("") { it + "\u0000" }
    val block = allocArray<UShortVar>(text.length + 1)
    text.forEachIndexed { i, c -> block[i] = c.code.toUShort() }
    block[text.length] = 0u
    return block
}

internal actual fun ready(
    sources: List<Source>,
    waitMs: Int,
): List<Boolean> {
    var left = waitMs
    while (true) {
        val now = sources.map { (it as PipeSource).available() != 0L }
        if (now.any { it } || left <= 0) return now
        val nap = minOf(left, READY_NAP_MS)
        Sleep(nap.convert())
        left -= nap
    }
}

internal actual fun sleepMillis(ms: Int) {
    Sleep(ms.convert())
}

/** GetEnvironmentStringsW: `KEY=value\0` entries up to an empty one, including the `=C:=C:\…` drive entries. */
internal actual fun environEntries(): List<String> {
    val block = GetEnvironmentStringsW() ?: return emptyList()
    try {
        return buildList {
            var at = block
            while (at[0].toInt() != 0) {
                val entry = at.toKStringFromUtf16()
                add(entry)
                at = (at + (entry.length + 1))!!
            }
        }
    } finally {
        FreeEnvironmentStringsW(block)
    }
}

/** Windows compares variable names without case; Rust's `EnvKey` too. */
internal actual fun envKey(name: String) = name.uppercase()

actual fun currentExe(): String? = wideCall { buf, size -> GetModuleFileNameW(null, buf, size).let { if (it == size) size else it } }

/** Rust's `Display` for a Windows exit status: decimal, or hex when the high bit marks an NTSTATUS. */
internal actual fun exitText(code: Int) = if (code < 0) "exit code: 0x${code.toUInt().toString(HEX)}" else "exit code: $code"

internal actual fun signalName(signal: Int): String? = null

private const val PATH = "PATH"
private const val WAIT_TIMEOUT = 258u
private const val READY_NAP_MS = 5
private const val WIDE_START = 512
private const val HEX = 16
