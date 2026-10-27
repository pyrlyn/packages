package cli

/** One rendered man page: its file name (`tool-add.1`) and roff source. */
class ManPage(
    val fileName: String,
    val roff: String,
)

/** What clap_mangen's `Man` takes besides the command: the `.TH` date, source and manual. */
class ManHeader(
    val date: String? = null,
    val source: String? = null,
    val manual: String? = null,
)

/**
 * One page per visible command, depth first, parents before their subcommands, as clap_mangen 0.2 renders each of
 * them byte for byte: `tool.1`, `tool-add.1`, titled with the command's display name in capitals.
 */
fun manPages(
    root: Command,
    binName: String,
    header: ManHeader,
): List<ManPage> {
    val out = mutableListOf<ManPage>()

    fun walk(node: Node) {
        out += ManPage("${node.displayName}.1", manPage(node, header))
        node.children().filter { !it.cmd.hidden }.forEach(::walk)
    }
    walk(Node(root, null, binName))
    return out
}

private fun manPage(
    node: Node,
    header: ManHeader,
): String {
    val roff = Roff()
    val title = listOfNotNull(node.displayName.uppercase(), "1", header.date, header.source, header.manual)
    roff.control("TH", title)
    roff.control("SH", listOf("NAME"))
    val about = node.cmd.about ?: node.cmd.longAbout
    roff.text(Inline.Roman(about?.let { "${node.displayName} - $it" } ?: node.displayName))
    roff.control("SH", listOf("SYNOPSIS"))
    roff.text(*synopsis(node).toTypedArray())
    roff.control("SH", listOf("DESCRIPTION"))
    (node.cmd.longAbout ?: node.cmd.about)?.lines()?.forEach { line ->
        if (line.isBlank()) roff.control("PP") else roff.text(Inline.Roman(line))
    }
    val items = node.args.filter { !it.hidden }
    if (items.isNotEmpty()) {
        roff.control("SH", listOf("OPTIONS"))
        manOptions(roff, items)
    }
    val subs = node.cmd.subcommands.filter { !it.hidden }
    if (subs.isNotEmpty()) {
        roff.control("SH", listOf("SUBCOMMANDS"))
        for (sub in subs) {
            roff.control("TP")
            roff.text(Inline.Roman("${node.displayName}-${sub.name}(1)"))
            (sub.about ?: sub.longAbout)?.lines()?.forEach { roff.text(Inline.Roman(it)) }
        }
    }
    node.version?.let {
        roff.control("SH", listOf("VERSION"))
        roff.text(Inline.Roman("v$it"))
    }
    return roff.render()
}

private fun markers(required: Boolean) = if (required) "<" to ">" else "[" to "]"

private fun synopsis(node: Node): List<Inline> {
    val line = mutableListOf<Inline>(Inline.Bold(node.usageName), Inline.Roman(" "))
    for (arg in node.args.filter { !it.hidden && !it.positional }) {
        val (lhs, rhs) = markers(arg.required)
        val names =
            when {
                arg.short != null && arg.long != null -> {
                    listOf(Inline.Bold("-${arg.short}"), Inline.Roman("|"), Inline.Bold("--${arg.long}"))
                }

                arg.short != null -> {
                    listOf(Inline.Bold("-${arg.short} "))
                }

                else -> {
                    listOf(Inline.Bold("--${arg.long}"))
                }
            }
        line += listOf(Inline.Roman(lhs)) + names + Inline.Roman(rhs) + Inline.Roman(" ")
    }
    for (arg in node.positionals) {
        val (lhs, rhs) = markers(arg.required)
        line += listOf(Inline.Roman(lhs), Inline.Italic(arg.valueName), Inline.Roman(rhs), Inline.Roman(" "))
    }
    if (node.cmd.subcommands.isNotEmpty()) {
        val (lhs, rhs) = markers(node.cmd.subcommandRequired)
        line += listOf(Inline.Roman(lhs), Inline.Italic("subcommands"), Inline.Roman(rhs))
    }
    return line
}

private fun manOptions(
    roff: Roff,
    items: List<Arg>,
) {
    for (arg in items.filter { !it.positional }) {
        val head =
            listOfNotNull(
                arg.short?.let { Inline.Bold("-$it") },
                Inline.Roman(", ").takeIf { arg.short != null && arg.long != null },
                arg.long?.let { Inline.Bold("--$it") },
            ).toMutableList()
        if (arg.takesValue) head += listOf(Inline.Roman(" "), Inline.Italic("<${arg.valueName}>"))
        manDefault(arg)?.let { head += listOf(Inline.Roman(" "), Inline.Roman(it)) }
        val help = arg.longHelp ?: arg.help
        roff.control("TP")
        roff.text(*head.toTypedArray())
        roff.text(*listOfNotNull(help?.let { Inline.Roman(it) }).toTypedArray())
        manValues(roff, arg, help != null)
    }
    for (arg in items.filter { it.positional }) {
        val (lhs, rhs) = markers(arg.required)
        val head = mutableListOf<Inline>(Inline.Roman(lhs), Inline.Italic(arg.valueName), Inline.Roman(rhs))
        manDefault(arg)?.let { head += Inline.Roman(" $it") }
        val help = arg.longHelp ?: arg.help
        roff.control("TP")
        roff.text(*head.toTypedArray())
        roff.text(*listOfNotNull(help?.let { Inline.Roman(it) }).toTypedArray())
        manValues(roff, arg, help != null)
    }
}

private fun manDefault(arg: Arg): String? = arg.default?.takeIf { arg.takesValue }?.let { "[default: $it]" }

private fun manValues(
    roff: Roff,
    arg: Arg,
    helpWritten: Boolean,
) {
    val values = arg.possibleValues.filter { !it.hidden }
    if (values.isEmpty()) return
    // A gap between the help and the values reads better.
    if (helpWritten) roff.text(Inline.LineBreak)
    roff.text(Inline.LineBreak, Inline.Italic("Possible values:"))
    // The `.TP` heading's indent does not carry over to `.IP`, so the bullets are indented twice.
    roff.control("RS", listOf("14"))
    for (value in values) {
        roff.control("IP", listOf("\\(bu", "2"))
        roff.text(Inline.Roman(value.help?.let { "${value.name}: $it" } ?: value.name))
    }
    roff.control("RE")
}

private sealed interface Inline {
    data class Roman(
        val text: String,
    ) : Inline

    data class Bold(
        val text: String,
    ) : Inline

    data class Italic(
        val text: String,
    ) : Inline

    data object LineBreak : Inline
}

/** The roff crate's writer, with its apostrophe handling on, as clap_mangen uses it. */
private class Roff {
    private val out = StringBuilder(".ie \\n(.g .ds Aq \\(aq\n.el .ds Aq '\n")

    fun control(
        name: String,
        args: List<String> = emptyList(),
    ) {
        out.append('.').append(name)
        args.forEach { out.append(' ').append(if (' ' in it) "\"$it\"" else it) }
        out.append('\n')
    }

    fun text(vararg inlines: Inline) {
        var lineStart = true
        for (inline in inlines) {
            when (inline) {
                Inline.LineBreak -> {
                    out.append(if (lineStart) ".br\n" else "\n.br\n")
                    lineStart = true
                }

                is Inline.Bold -> {
                    out.append("\\fB").append(escape(inline.text)).append("\\fR")
                    lineStart = false
                }

                is Inline.Italic -> {
                    out.append("\\fI").append(escape(inline.text)).append("\\fR")
                    lineStart = false
                }

                is Inline.Roman -> {
                    val text = escape(inline.text)
                    // A line that starts with a control character would be read as a request.
                    if (lineStart && (text.startsWith('.') || text.startsWith('\''))) out.append("\\&")
                    out.append(text)
                    lineStart = false
                }
            }
        }
        out.append('\n')
    }

    fun render(): String = out.toString()

    private fun escape(text: String): String =
        text
            .replace("\\", "\\e")
            .replace("-", "\\-")
            .replace("'", "\\*(Aq")
            .replace("\n.", "\n\\&.")
            .replace("\n'", "\n\\&'")
}
