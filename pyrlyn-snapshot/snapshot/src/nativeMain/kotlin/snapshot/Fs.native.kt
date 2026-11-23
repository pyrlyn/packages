// The C standard library on native targets; only `mkdir` differs per platform.
@file:OptIn(ExperimentalForeignApi::class)

package snapshot

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.toKString
import kotlinx.cinterop.usePinned
import platform.posix.fclose
import platform.posix.fopen
import platform.posix.fread
import platform.posix.fwrite
import platform.posix.getenv

private const val CHUNK = 65_536

internal actual fun readText(path: String): String? {
    val file = fopen(path, "rb") ?: return null
    try {
        var bytes = ByteArray(0)
        val buffer = ByteArray(CHUNK)
        while (true) {
            val n = buffer.usePinned { fread(it.addressOf(0), 1u, buffer.size.convert(), file) }.toInt()
            if (n <= 0) break
            bytes += buffer.copyOf(n)
        }
        return bytes.decodeToString()
    } finally {
        fclose(file)
    }
}

internal actual fun writeText(
    path: String,
    text: String,
) {
    val file = fopen(path, "wb") ?: throw IllegalStateException("cannot open $path for writing")
    try {
        val bytes = text.encodeToByteArray()
        if (bytes.isEmpty()) return
        val n = bytes.usePinned { fwrite(it.addressOf(0), 1u, bytes.size.convert(), file) }.toInt()
        check(n == bytes.size) { "short write to $path" }
    } finally {
        fclose(file)
    }
}

internal actual fun removeFile(path: String) {
    platform.posix.remove(path)
}

internal actual fun makeDirs(path: String) {
    val parts = path.split('/', '\\')
    for (i in parts.indices) {
        val prefix = parts.subList(0, i + 1).joinToString("/")
        if (prefix.isNotEmpty()) makeDir(prefix)
    }
}

internal actual fun env(name: String): String? = getenv(name)?.toKString()

/** `mkdir` of one directory, ignoring an existing one. */
internal expect fun makeDir(path: String)
