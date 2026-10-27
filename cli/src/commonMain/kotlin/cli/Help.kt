package cli

private const val TAB = "  "
private const val TAB_WIDTH = 2

/** `--long` takes this much more room than the `-s, ` in front of it. */
private const val SHORT_SIZE = 4

/** Help printed under an argument, not beside it, starts this far in. */
private const val NEXT_LINE_INDENT = 10

/** clap moves help under its arguments when they take more than this share of the width. */
private const val NEXT_LINE_SHARE = 0.40f

/** clap 4.6's help, byte for byte: `-h` gives the short form, `--help` the long one where there is one. */
internal class Help(
    private val node: Node,
    private val style: Style,
    private val width: Int,
    long: Boolean,
) {
    private val useLong = long && node.longHelpExists

    fun render(): String {
        val out = StringBuilder()
        val about = if (useLong) node.cmd.longAbout ?: node.cmd.about else node.cmd.about
        about?.let { out.append(wrap(it, width)).append('\n') }
        out
            .append('\n')
            .append(style.header("Usage:"))
            .append(' ')
            .append(Usage(node, style).normal())
            .append("\n\n")
        out.append(sections().joinToString("\n\n"))
        var text = out.toString()
        val firstLine = text.indexOf('\n')
        if (firstLine >= 0 && text.substring(0, firstLine).isBlank()) text = text.substring(firstLine + 1)
        return text.trimEnd() + "\n"
    }

    private fun sections(): List<String> {
        val sections = mutableListOf<String>()
        if (node.hasVisibleSubcommands) sections += style.header("Commands:") + "\n" + subcommands()
        val positionals = node.positionals.filter { !it.hidden }
        if (positionals.isNotEmpty()) sections += style.header("Arguments:") + "\n" + args(positionals)
        val options = node.args.filter { !it.positional && !it.hidden }
        if (options.isNotEmpty()) sections += style.header("Options:") + "\n" + args(options)
        return sections
    }

    private fun args(args: List<Arg>): String {
        var longest = 2
        for (arg in args.filter { it.takesValue || it.long != null || it.short == null }) {
            longest = maxOf(longest, width(node.display(arg)) + if (arg.positional) 0 else SHORT_SIZE)
        }
        val nextLine = args.any { nextLine(it.help.orEmpty(), specs(it), longest) }
        val separator = if (nextLine && useLong) "\n\n" else "\n"
        return args.joinToString(separator) { row(it, nextLine, longest) }
    }

    private fun row(
        arg: Arg,
        nextLine: Boolean,
        longest: Int,
    ): String {
        val sb = StringBuilder(TAB)
        when {
            arg.short != null -> sb.append(style.literal("-${arg.short}"))
            arg.long != null -> sb.append(spaces(SHORT_SIZE))
        }
        arg.long?.let {
            if (arg.short != null) sb.append(", ")
            sb.append(style.literal("--$it"))
        }
        sb.append(Node.suffix(arg, arg.required))
        val own = width(node.display(arg))
        val padding =
            when {
                useLong || nextLine -> 0

                arg.positional -> longest + TAB_WIDTH - own

                // `-s, ` is already written for an argument without a long name, so it gets those four columns back.
                else -> longest + (if (arg.long != null) TAB_WIDTH else TAB_WIDTH + SHORT_SIZE) - (own + SHORT_SIZE)
            }
        sb.append(spaces(padding))
        val about = if (useLong) arg.longHelp ?: arg.help else arg.help ?: arg.longHelp
        sb.append(help(arg, about.orEmpty(), specs(arg), nextLine, longest))
        return sb.toString()
    }

    private fun subcommands(): String {
        val visible = node.cmd.subcommands.filter { !it.hidden }
        val longest = maxOf(2, visible.maxOf { width(it.name) })
        val nextLine = visible.any { nextLine(it.about.orEmpty(), aliases(it), longest) }
        return visible.joinToString("\n") { sub ->
            val padding = if (nextLine) "" else spaces(longest + TAB_WIDTH - width(sub.name))
            TAB + style.literal(sub.name) + padding + help(null, sub.about ?: sub.longAbout.orEmpty(), aliases(sub), nextLine, longest)
        }
    }

    private fun aliases(sub: Command): String =
        when (sub.aliases.size) {
            0 -> ""
            1 -> "[alias: ${sub.aliases[0]}]"
            else -> "[aliases: ${sub.aliases.joinToString(", ")}]"
        }

    /** clap's `spec_vals`: the default and the possible values, when they are not listed one per line below. */
    private fun specs(arg: Arg): String {
        val specs = mutableListOf<String>()
        if (arg.takesValue) arg.default?.let { specs += "[default: ${quoted(it)}]" }
        val values = arg.possibleValues.filter { !it.hidden }
        if (values.isNotEmpty() && !longValues(arg)) specs += "[possible values: ${values.joinToString(", ") { quoted(it.name) }}]"
        return specs.joinToString(if (useLong) "\n" else " ")
    }

    /** In long help, possible values that have help of their own get a list instead of a bracket. */
    private fun longValues(arg: Arg) = useLong && arg.possibleValues.any { !it.hidden && it.help != null }

    private fun nextLine(
        help: String,
        specs: String,
        longest: Int,
    ): Boolean {
        if (useLong) return true
        val taken = longest + TAB_WIDTH * 2
        return width >= taken && taken.toFloat() / width > NEXT_LINE_SHARE && width(help) + width(specs) > width - taken
    }

    private fun help(
        arg: Arg?,
        about: String,
        specs: String,
        nextLine: Boolean,
        longest: Int,
    ): String {
        val out = StringBuilder()
        if (nextLine) out.append('\n').append(spaces(NEXT_LINE_INDENT))
        val indentBy = if (nextLine) NEXT_LINE_INDENT else longest + TAB_WIDTH * 2
        var text = about
        if (specs.isNotEmpty()) {
            if (text.isNotEmpty()) text += if (useLong && arg != null) "\n\n" else " "
            text += specs
        }
        text = indent(wrap(text, maxOf(width - indentBy, 0)), spaces(indentBy))
        out.append(text)
        if (arg != null && longValues(arg)) out.append(valueList(arg, indentBy, text.isEmpty()))
        return out.toString()
    }

    private fun valueList(
        arg: Arg,
        indentBy: Int,
        helpIsEmpty: Boolean,
    ): String {
        val values = arg.possibleValues.filter { !it.hidden }
        val longest = values.maxOf { width(it.name) }
        val trailing = indentBy + 2
        val out = StringBuilder()
        if (!helpIsEmpty) out.append("\n\n").append(spaces(indentBy))
        out.append("Possible values:")
        for (value in values) {
            var line = style.literal(value.name)
            value.help?.let { line += ": " + spaces(longest - width(value.name)) + it }
            val avail = if (width > trailing) width - trailing else Int.MAX_VALUE
            out
                .append('\n')
                .append(spaces(indentBy))
                .append("- ")
                .append(indent(wrap(line, avail), spaces(trailing)))
        }
        return out.toString()
    }
}
