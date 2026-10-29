// How Rust's std (1.99, library/std/src/sys/process/windows.rs and sys/args/windows.rs) turns a program and its
// arguments into what CreateProcessW takes: the file it runs, found as Windows itself would not (no current
// directory first), and one command line that CommandLineToArgvW splits back into the same arguments. Pure, so it
// is tested on every host; only mingwMain runs it.
package process

/** What CreateProcessW gets: the file to run and the whole command line. */
internal class WindowsLaunch(
    val application: String,
    val commandLine: String,
)

/** The places a bare program name is looked up, as Rust's `search_paths` takes them. */
internal class SearchDirs(
    /** The child's `PATH` when the command sets or removes it, else null. */
    val childPath: String?,
    val appDir: String?,
    val systemDir: String?,
    val windowsDir: String?,
    val parentPath: String?,
)

/** [command] as Rust's `Command::spawn` launches it on Windows; a batch file runs through `cmd.exe /c`. */
internal fun windowsLaunch(
    command: Command,
    dirs: SearchDirs,
    exists: (String) -> Boolean,
    fullPath: (String) -> String,
): WindowsLaunch {
    val program = resolveExe(command.program, dirs, exists)
    return if (isBatch(fullPath(program))) {
        WindowsLaunch("${dirs.systemDir}\\cmd.exe", batCommandLine(program, command.args, command.rawArgs))
    } else {
        WindowsLaunch(program, commandLine(command.program, command.args, command.rawArgs))
    }
}

/** Rust `resolve_exe`: a path is taken as given (with `.exe` tried), a bare name searched for, never in the current directory. */
internal fun resolveExe(
    program: String,
    dirs: SearchDirs,
    exists: (String) -> Boolean,
): String {
    if (program.isEmpty() || program.last().isSeparator()) throw InvalidProgram("program path has no file name")
    requireNoNul(program)
    val hasExe = program.endsWith(EXE, ignoreCase = true)
    if (program.any { it.isSeparator() }) {
        // Let CreateProcessW say whether a path to an `.exe` exists; otherwise try it with `.exe` added first.
        return if (hasExe || !exists(program + EXE)) program else program + EXE
    }
    val name = if ('.' in program) program else program + EXE
    val searched =
        sequence {
            dirs.childPath?.let { yieldAll(splitPaths(it)) }
            listOfNotNull(dirs.appDir, dirs.systemDir, dirs.windowsDir).forEach { yield(it) }
            dirs.parentPath?.let { yieldAll(splitPaths(it)) }
        }
    return searched.map { joinDir(it, name) }.firstOrNull(exists) ?: throw ProgramNotFound()
}

/** Rust `env::split_paths` on Windows: `;`-separated, a `"…"` part may hold a `;`, the quotes dropped; empty parts skipped. */
internal fun splitPaths(paths: String): List<String> =
    buildList {
        val part = StringBuilder()
        var quoted = false
        for (c in paths) {
            when {
                c == '"' -> quoted = !quoted
                c == ';' && !quoted -> add(part.toString()).also { part.clear() }
                else -> part.append(c)
            }
        }
        add(part.toString())
    }.filter { it.isNotEmpty() }

/** Rust `make_command_line`: the program always quoted (a path holds no `"`), each argument as [appendArg] writes it. */
internal fun commandLine(
    program: String,
    args: List<String>,
    raw: List<String> = emptyList(),
): String =
    buildString {
        requireNoNul(program)
        append('"').append(program).append('"')
        for (arg in args) {
            append(' ')
            appendArg(arg)
        }
        appendRaw(raw)
    }

/** Rust `Arg::Raw`: each one after a space, exactly as given; only a NUL is refused. */
private fun StringBuilder.appendRaw(raw: List<String>) {
    for (arg in raw) {
        requireNoNul(arg)
        append(' ').append(arg)
    }
}

/** Rust `append_arg`: quoted when empty or holding a space or tab; backslashes doubled only before a quote. */
private fun StringBuilder.appendArg(arg: String) {
    requireNoNul(arg)
    val quote = arg.isEmpty() || arg.any { it == ' ' || it == '\t' }
    if (quote) append('"')
    var backslashes = 0
    for (c in arg) {
        if (c == '\\') {
            backslashes++
        } else {
            // n backslashes before a quote become 2n+1, the quote itself escaped.
            if (c == '"') repeat(backslashes + 1) { append('\\') }
            backslashes = 0
        }
        append(c)
    }
    if (quote) {
        repeat(backslashes) { append('\\') }
        append('"')
    }
}

/**
 * Rust `make_bat_command_line`, its guard against BatBadBut: `cmd.exe /e:ON /v:OFF /d /c ""script" args"`, where an
 * argument that cmd could read as more than text is quoted and `%` cannot expand a variable.
 */
internal fun batCommandLine(
    script: String,
    args: List<String>,
    raw: List<String> = emptyList(),
): String =
    buildString {
        append("cmd.exe /e:ON /v:OFF /d /c \"")
        if ('"' in script || script.endsWith('\\')) throw InvalidProgram("Windows file names may not contain `\"` or end with `\\`")
        append('"').append(script).append('"')
        for (arg in args) {
            append(' ')
            // A line break would end the command there; what follows would run as a command of its own.
            if (arg.any { it == '\r' || it == '\n' }) throw InvalidProgram("batch file arguments are invalid")
            appendBatArg(arg)
        }
        // Raw arguments are passed on as they are: what cmd makes of them is the caller's to decide.
        appendRaw(raw)
        append('"')
    }

/** Rust `append_bat_arg`: every ASCII symbol but a known-safe few forces quotes; `"` doubled; `%` defused. */
private fun StringBuilder.appendBatArg(arg: String) {
    requireNoNul(arg)
    // A trailing backslash would escape the closing quote of a script's `"%~2"`.
    val quote = arg.isEmpty() || arg.endsWith('\\') || arg.any { it.needsBatQuote() }
    if (quote) append('"')
    var backslashes = 0
    for (c in arg) {
        if (c == '\\') {
            backslashes++
        } else {
            when (c) {
                '"' -> {
                    repeat(backslashes) { append('\\') }
                    append('"')
                }

                // `%cd:~,%` expands to nothing, so cmd never sees the `%` that would start `%VAR%`.
                '%', '\r' -> {
                    append("%%cd:~,")
                }
            }
            backslashes = 0
        }
        append(c)
    }
    if (quote) {
        repeat(backslashes) { append('\\') }
        append('"')
    }
}

private fun Char.needsBatQuote() = (code < ASCII_END && !(isAsciiLetterOrDigit() || this in BAT_UNQUOTED)) || isISOControl()

private fun Char.isAsciiLetterOrDigit() = this in 'a'..'z' || this in 'A'..'Z' || this in '0'..'9'

private fun Char.isSeparator() = this == '\\' || this == '/'

/** Whether the resolved file is a `.bat` or `.cmd` script, which only cmd.exe runs. */
internal fun isBatch(path: String) = path.endsWith(".bat", ignoreCase = true) || path.endsWith(".cmd", ignoreCase = true)

private fun joinDir(
    dir: String,
    name: String,
) = if (dir.last().isSeparator()) dir + name else "$dir\\$name"

private fun requireNoNul(s: String) {
    if ('\u0000' in s) throw InvalidProgram("nul byte found in provided data")
}

/** A program Rust's std refuses before CreateProcessW is called; its words are what Rust prints. */
internal sealed class LaunchRefused(
    message: String,
) : Exception(message)

/** Rust's `InvalidInput` errors. */
internal class InvalidProgram(
    message: String,
) : LaunchRefused(message)

/** Rust `resolve_exe`'s `NotFound`. */
internal class ProgramNotFound : LaunchRefused("program not found")

private const val EXE = ".exe"
private const val ASCII_END = 0x80
private const val BAT_UNQUOTED = "#\$*+-./:?@\\_"
