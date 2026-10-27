package cli

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.toKString
import platform.posix.STDERR_FILENO
import platform.posix.STDIN_FILENO
import platform.posix.STDOUT_FILENO
import platform.posix.TIOCGWINSZ
import platform.posix.fflush
import platform.posix.fputs
import platform.posix.getenv
import platform.posix.ioctl
import platform.posix.isatty
import platform.posix.stdout
import platform.posix.winsize

/**
 * Settings for this process, as a clap program with `wrap_help` and `color` reads its terminal: the width of the
 * first of stdout, stderr and stdin that is a terminal, else `COLUMNS`, else [DEFAULT_WIDTH]; colour always, for
 * [write] to take back out where the stream shows none.
 */
fun terminalSettings(binName: String): Settings = Settings(binName, terminalWidth() ?: columns() ?: DEFAULT_WIDTH, color = true)

/** Print an [Parsed.Exit]'s text to its stream, without colour unless anstream would colour that stream. */
@OptIn(ExperimentalForeignApi::class)
fun Parsed.Exit.write() {
    val fd = if (stderr) STDERR_FILENO else STDOUT_FILENO
    val stream = if (stderr) platform.posix.stderr else stdout
    fputs(if (colors(fd)) text else plain(text), stream)
    fflush(stream)
}

@OptIn(ExperimentalForeignApi::class)
private fun terminalWidth(): Int? =
    listOf(STDOUT_FILENO, STDERR_FILENO, STDIN_FILENO).firstNotNullOfOrNull { fd ->
        memScoped {
            val size = alloc<winsize>()
            if (isatty(fd) == 1 && ioctl(fd, TIOCGWINSZ.convert(), size.ptr) == 0) size.ws_col.toInt() else null
        }
    }

/** clap's `parse_env("COLUMNS")`: a `usize`, or nothing. */
private fun columns(): Int? = env("COLUMNS")?.removePrefix("+")?.takeIf { it.isNotEmpty() && it.all(Char::isDigit) }?.toIntOrNull()

@OptIn(ExperimentalForeignApi::class)
private fun env(name: String): String? = getenv(name)?.toKString()

/** anstream's `AutoStream::choice` under clap's `ColorChoice::Auto`. */
private fun colors(fd: Int): Boolean {
    val clicolor = env("CLICOLOR")?.let { it != "0" }
    return when {
        env("NO_COLOR").orEmpty().isNotEmpty() -> false
        (env("CLICOLOR_FORCE") ?: "0") != "0" -> true
        clicolor == false -> false
        else -> isatty(fd) == 1 && (env("TERM")?.let { it != "dumb" } == true || clicolor == true || env("CI") != null)
    }
}
