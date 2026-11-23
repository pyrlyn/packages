package cli

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toKString
import platform.posix.fflush
import platform.posix.fputs
import platform.posix.getenv
import platform.posix.stdout

/**
 * Settings for this process, as a clap program with `wrap_help` and `color` reads its terminal: the width of the
 * first of stdout, stderr and stdin that is a terminal, else `COLUMNS`, else [DEFAULT_WIDTH]; colour always, for
 * [write] to take back out where the stream shows none.
 */
fun terminalSettings(binName: String): Settings = Settings(binName, terminalWidth() ?: columns() ?: DEFAULT_WIDTH, color = true)

/** Print an [Parsed.Exit]'s text to its stream, without colour unless anstream would colour that stream. */
@OptIn(ExperimentalForeignApi::class)
fun Parsed.Exit.write() {
    val stream = if (stderr) platform.posix.stderr else stdout
    fputs(if (colors(if (stderr) Stream.Err else Stream.Out)) text else plain(text), stream)
    fflush(stream)
}

/** The standard streams, in the order clap asks them for the terminal's width. */
internal enum class Stream { Out, Err, In }

/** The width of the first standard stream that is a terminal. */
internal expect fun terminalWidth(): Int?

internal expect fun isTerminal(stream: Stream): Boolean

/** anstyle-query's `term_supports_color`, which differs on Windows. */
internal expect fun termSupportsColor(term: String?): Boolean

/** False where the stream is a terminal that cannot show escape sequences, as an old Windows console cannot. */
internal expect fun showsEscapes(stream: Stream): Boolean

/** clap's `parse_env("COLUMNS")`: a `usize`, or nothing. */
private fun columns(): Int? = env("COLUMNS")?.removePrefix("+")?.takeIf { it.isNotEmpty() && it.all(Char::isDigit) }?.toIntOrNull()

@OptIn(ExperimentalForeignApi::class)
internal fun env(name: String): String? = getenv(name)?.toKString()

/** anstream's `AutoStream::choice` under clap's `ColorChoice::Auto`. */
private fun colors(stream: Stream): Boolean {
    val clicolor = env("CLICOLOR")?.let { it != "0" }
    return when {
        env("NO_COLOR").orEmpty().isNotEmpty() -> false
        (env("CLICOLOR_FORCE") ?: "0") != "0" -> showsEscapes(stream)
        clicolor == false -> false
        else -> isTerminal(stream) && (termSupportsColor(env("TERM")) || clicolor == true || env("CI") != null) && showsEscapes(stream)
    }
}
