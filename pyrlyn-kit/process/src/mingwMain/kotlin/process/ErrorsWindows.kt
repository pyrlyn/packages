@file:OptIn(ExperimentalForeignApi::class)

package process

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.toKStringFromUtf16
import platform.windows.FORMAT_MESSAGE_FROM_SYSTEM
import platform.windows.FORMAT_MESSAGE_IGNORE_INSERTS
import platform.windows.FormatMessageW
import platform.windows.WCHARVar

/** The system's words for a Win32 error, without the CRLF FormatMessageW ends them with, as Rust trims them. */
internal actual fun osError(code: Int): String =
    memScoped {
        val buf = allocArray<WCHARVar>(MESSAGE_CHARS)
        val flags = FORMAT_MESSAGE_FROM_SYSTEM or FORMAT_MESSAGE_IGNORE_INSERTS
        val n = FormatMessageW(flags.convert(), null, code.convert(), 0u, buf, MESSAGE_CHARS.convert(), null).toInt()
        val words = if (n == 0) "Unknown error $code" else buf.toKStringFromUtf16().trimEnd()
        "$words (os error $code)"
    }

private const val MESSAGE_CHARS = 512
