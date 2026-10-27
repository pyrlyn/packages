package cli

/** A shell [completions] writes a script for: clap_complete's `Shell`, in its order. */
enum class CompletionShell(
    val id: String,
) {
    Bash("bash"),
    Elvish("elvish"),
    Fish("fish"),
    PowerShell("powershell"),
    Zsh("zsh"),
}

/** The completion script clap_complete 4.6 writes for [root] under [binName], byte for byte. */
fun completions(
    root: Command,
    shell: CompletionShell,
    binName: String,
): String {
    val node = Node(root, null, binName)
    return when (shell) {
        CompletionShell.Fish -> fish(node, binName)
        CompletionShell.Elvish -> elvish(node, binName)
        CompletionShell.PowerShell -> powerShell(node, binName)
        CompletionShell.Bash -> bash(node, binName)
        CompletionShell.Zsh -> zsh(node, binName)
    }
}

/** The options that take a value, then the flags, as clap_complete's `get_opts` and `flags` list them. */
internal fun Node.opts(): List<Arg> = args.filter { !it.positional && it.takesValue }

internal fun Node.flags(): List<Arg> = args.filter { !it.positional && !it.takesValue }

/** A subcommand's name and its aliases. */
internal fun Command.names(): List<String> = listOf(name) + aliases

/** The help clap_complete prints for an argument: the short help, else the long one. */
internal val Arg.summary: String? get() = help ?: longHelp

/** clap derives `ValueHint::AnyPath` for a `PathBuf`, which [ValueKind.NonEmpty] stands for. */
internal val Arg.isPath: Boolean get() = value == ValueKind.NonEmpty

/**
 * Every spelling of the options that take a value on the command at [path] below [root], inherited globals included,
 * longs before shorts: the words after which a completion script must skip one more.
 */
fun valueOptions(
    root: Command,
    vararg path: String,
): List<String> {
    var node = Node(root, null, root.name)
    for (name in path) node = node.child(name) ?: return emptyList()
    return node.opts().flatMap { listOfNotNull(it.long?.let { l -> "--$l" }, it.short?.let { s -> "-$s" }) }
}
