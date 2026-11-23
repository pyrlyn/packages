package cli

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import platform.windows.CONSOLE_SCREEN_BUFFER_INFO
import platform.windows.DWORDVar
import platform.windows.ENABLE_VIRTUAL_TERMINAL_PROCESSING
import platform.windows.GetConsoleMode
import platform.windows.GetConsoleScreenBufferInfo
import platform.windows.GetStdHandle
import platform.windows.HANDLE
import platform.windows.STD_ERROR_HANDLE
import platform.windows.STD_INPUT_HANDLE
import platform.windows.STD_OUTPUT_HANDLE
import platform.windows.SetConsoleMode

@OptIn(ExperimentalForeignApi::class)
private fun Stream.handle(): HANDLE? =
    GetStdHandle(
        when (this) {
            Stream.Out -> STD_OUTPUT_HANDLE
            Stream.Err -> STD_ERROR_HANDLE
            Stream.In -> STD_INPUT_HANDLE
        },
    )

/** terminal_size on Windows: the visible window of the console's screen buffer. */
@OptIn(ExperimentalForeignApi::class)
internal actual fun terminalWidth(): Int? =
    Stream.entries.firstNotNullOfOrNull { stream ->
        memScoped {
            val info = alloc<CONSOLE_SCREEN_BUFFER_INFO>()
            if (GetConsoleScreenBufferInfo(stream.handle(), info.ptr) != 0) info.srWindow.Right - info.srWindow.Left + 1 else null
        }
    }

/** A console handle; a pipe or file has no console mode. */
@OptIn(ExperimentalForeignApi::class)
internal actual fun isTerminal(stream: Stream): Boolean = memScoped { GetConsoleMode(stream.handle(), alloc<DWORDVar>().ptr) != 0 }

// Windows Terminal and conhost set no TERM, so only `dumb` turns colour off there.
internal actual fun termSupportsColor(term: String?): Boolean = term != "dumb"

/**
 * anstream turns on the console's escape processing before it writes colour; a console that refuses (before
 * Windows 10) would print the escapes raw, so the text goes out plain. A pipe or file takes them as they are.
 */
@OptIn(ExperimentalForeignApi::class)
internal actual fun showsEscapes(stream: Stream): Boolean =
    memScoped {
        val handle = stream.handle()
        val mode = alloc<DWORDVar>()
        when {
            GetConsoleMode(handle, mode.ptr) == 0 -> true
            mode.value and ENABLE_VIRTUAL_TERMINAL_PROCESSING.convert() != 0u -> true
            else -> SetConsoleMode(handle, mode.value or ENABLE_VIRTUAL_TERMINAL_PROCESSING.convert()) != 0
        }
    }
