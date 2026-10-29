package cli

/** clap_complete's fish generator. */
internal fun fish(
    root: Node,
    bin: String,
): String {
    val name = bin.replace('-', '_')
    val out = StringBuilder()
    var needs = "__fish_${name}_needs_command"
    var using = "__fish_${name}_using_subcommand"
    if (root.cmd.subcommands.isNotEmpty()) {
        out.append(fishHelpers(root, name, needs, using))
    } else {
        needs = "__fish_use_subcommand"
        using = "__fish_seen_subcommand_from"
    }
    fishCommand(FishNames(bin, needs, using), emptyList(), root, out)
    return out.toString()
}

private class FishNames(
    val bin: String,
    val needs: String,
    val using: String,
)

private fun fishHelpers(
    root: Node,
    name: String,
    needs: String,
    using: String,
): String {
    val optspecs =
        root.args.filter { !it.positional }.joinToString("") { arg ->
            val spec = listOfNotNull(arg.short?.toString(), arg.long?.let { fishEscape(it) }).joinToString("/")
            " $spec${if (arg.takesValue) "=" else ""}"
        }
    return """
        |# Print an optspec for argparse to handle cmd's options that are independent of any subcommand.
        |function __fish_${name}_global_optspecs
        |    string join \n$optspecs
        |end
        |
        |function $needs
        |    # Figure out if the current invocation already has a command.
        |    set -l cmd (commandline -opc)
        |    set -e cmd[1]
        |    argparse -s (__fish_${name}_global_optspecs) -- ${'$'}cmd 2>/dev/null
        |    or return
        |    if set -q argv[1]
        |        # Also print the command, so this can be used to figure out what it is.
        |        echo ${'$'}argv[1]
        |        return 1
        |    end
        |    return 0
        |end
        |
        |function $using
        |    set -l cmd ($needs)
        |    test -z "${'$'}cmd"
        |    and return 1
        |    contains -- ${'$'}cmd[1] ${'$'}argv
        |end
        |
        |
        """.trimMargin()
}

private fun fishCommand(
    names: FishNames,
    parents: List<String>,
    node: Node,
    out: StringBuilder,
) {
    var base = "complete -c ${names.bin}"
    val subs = node.cmd.subcommands
    if (parents.isEmpty()) {
        if (subs.isNotEmpty()) base += " -n \"${names.needs}\""
    } else {
        var condition = names.using + " " + parents[0] + parents.drop(1).joinToString("") { "; and __fish_seen_subcommand_from $it" }
        // A command's own options are offered only until one of its subcommands is on the line.
        if (subs.isNotEmpty()) condition += "; and not __fish_seen_subcommand_from" + subs.flatMap { it.names() }.joinToString("") { " $it" }
        base += " -n \"$condition\""
    }
    for (arg in node.opts() + node.flags()) {
        val line = StringBuilder(base)
        arg.short?.let { line.append(" -s ").append(it) }
        arg.long?.let { line.append(" -l ").append(fishEscape(it)) }
        arg.summary?.let { line.append(" -d '").append(fishHelp(it)).append('\'') }
        line.append(fishValues(arg))
        out.append(line).append('\n')
    }
    if (node.positionals.isEmpty()) base += " -f"
    for (sub in subs) {
        for (name in sub.names()) {
            out
                .append(base)
                .append(" -a \"")
                .append(name)
                .append('"')
            sub.about?.let { out.append(" -d '").append(fishHelp(it)).append('\'') }
            out.append('\n')
        }
    }
    val children = node.children()
    for (child in children) {
        for (name in child.cmd.names()) fishCommand(names, parents + name, child, out)
    }
}

private fun fishValues(arg: Arg): String {
    val values = arg.possibleValues
    return when {
        !arg.takesValue -> ""
        values.isNotEmpty() -> " -r -f -a \"" + values.filter { !it.hidden }.joinToString("\n") { "${fishEscape(it.name, comma = true)}\\t'${fishHelp(it.help.orEmpty())}'" } + "\""
        arg.isPath -> " -r -F"
        else -> " -r"
    }
}

private fun fishEscape(
    s: String,
    comma: Boolean = false,
): String {
    val escaped = s.replace("\\", "\\\\").replace("'", "\\'")
    return if (comma) escaped.replace(",", "\\,") else escaped
}

private fun fishHelp(s: String) = fishEscape(s.replace('\n', ' '))
