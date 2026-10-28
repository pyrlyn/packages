package cli

/**
 * A [Command] at its place in the tree, with what clap adds when it builds one: the globals of its ancestors, `-h`
 * and `-V`, the names usage and `--version` print.
 */
internal class Node(
    val cmd: Command,
    val parent: Node?,
    binName: String,
) {
    /** `ketch registry push`: the bin name, then the canonical name of every command down to this one. */
    val usageName: String = parent?.let { "${it.usageName} ${cmd.name}" } ?: binName

    /** `ketch-registry-push`, which `--version` prints. */
    val displayName: String = parent?.let { "${it.displayName}-${cmd.name}" } ?: cmd.name

    private val propagatesVersion: Boolean = cmd.propagateVersion || parent?.propagatesVersion == true

    val version: String? = cmd.version ?: parent?.takeIf { it.propagatesVersion }?.version

    private val inherited: List<Arg> =
        parent?.childGlobals.orEmpty().filter { global -> cmd.args.none { it.id == global.id } }

    private val childGlobals: List<Arg> = inherited + cmd.args.filter { it.global }

    private val userArgs: List<Arg> = cmd.args + inherited

    /** clap shows a separate `--help` only when it would say more than `-h`; subcommands' own long text does not count. */
    val longHelpExists: Boolean =
        cmd.longAbout != null ||
            userArgs.any { arg -> !arg.hidden && (arg.longHelp != null || arg.possibleValues.any { !it.hidden && it.help != null }) }

    /** In the order help lists them: own, inherited globals, help, version. */
    val args: List<Arg> =
        userArgs + listOfNotNull(helpArg(longHelpExists).takeUnless { cmd.disableHelpFlag }, version?.let { versionArg() })

    val positionals: List<Arg> = args.filter { it.positional }

    val hasVisibleSubcommands: Boolean = cmd.subcommands.any { !it.hidden }

    /** Every name and alias a subcommand answers to, hidden ones included, as clap lists them in errors. */
    val subcommandNames: List<String> = cmd.subcommands.flatMap { listOf(it.name) + it.aliases }

    /** The long names in clap's key map order, which decides between equally good suggestions. */
    val longs: List<String> = args.mapNotNull { it.long }

    fun find(id: String): Arg? = args.firstOrNull { it.id == id }

    fun byLong(name: String): Arg? = args.firstOrNull { it.long == name }

    fun byShort(c: Char): Arg? = args.firstOrNull { it.short == c }

    /** A positional's place, counted from 1; 0 for anything else. */
    fun index(arg: Arg): Int = positionals.indexOf(arg) + 1

    fun child(token: String): Node? = cmd.subcommands.firstOrNull { it.name == token || token in it.aliases }?.let { Node(it, this, "") }

    fun children(): List<Node> = cmd.subcommands.map { Node(it, this, "") }

    /** clap's `stylized`: `--long <VALUE>` (or `-s`) for an option, `<NAME>` or `[NAME]` for a positional. */
    fun display(
        arg: Arg,
        required: Boolean = arg.required,
        style: Style = Style(false),
    ): String {
        val name =
            when {
                arg.long != null -> style.literal("--${arg.long}")
                arg.short != null -> style.literal("-${arg.short}")
                else -> ""
            }
        return name + suffix(arg, required)
    }

    companion object {
        /** clap's `stylize_arg_suffix`; an option given several times still shows one value. */
        fun suffix(
            arg: Arg,
            required: Boolean,
        ): String =
            when {
                arg.positional -> {
                    val name = if (required) "<${arg.valueName}>" else "[${arg.valueName}]"
                    if (arg.multiple) "$name..." else name
                }

                arg.takesValue -> {
                    " <${arg.valueName}>"
                }

                else -> {
                    ""
                }
            }

        private fun helpArg(long: Boolean) =
            Arg(
                ArgBuilder("help", positional = false, takesValue = false).apply {
                    short = 'h'
                    help = if (long) "Print help (see more with '--help')" else "Print help"
                    longHelp = if (long) "Print help (see a summary with '-h')" else null
                    action = Action.Help
                },
            )

        private fun versionArg() =
            Arg(
                ArgBuilder("version", positional = false, takesValue = false).apply {
                    short = 'V'
                    help = "Print version"
                    action = Action.Version
                },
            )
    }
}
