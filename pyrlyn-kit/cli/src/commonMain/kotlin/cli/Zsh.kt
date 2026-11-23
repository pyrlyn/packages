package cli

/** clap_complete's zsh generator. */
internal fun zsh(
    root: Node,
    bin: String,
): String =
    buildString {
        append("#compdef $bin\n\nautoload -U is-at-least\n\n_$bin() {\n")
        append("    typeset -A opt_args\n    typeset -a _arguments_options\n    local ret=1\n\n")
        append("    if is-at-least 5.2; then\n        _arguments_options=(-s -S -C)\n")
        append("    else\n        _arguments_options=(-s -C)\n    fi\n\n")
        append("    local context curcontext=\"\$curcontext\" state line\n    ")
        append(zshArgs(root))
        append(zshSubcommands(root))
        append("\n}\n\n")
        append(zshDetails(root))
        append("\n\nif [ \"\$funcstack[1]\" = \"_$bin\" ]; then\n    _$bin \"\$@\"\nelse\n    compdef _$bin $bin\nfi\n")
    }

/** `_ketch__subcmd__config__subcmd__create_commands`, the function that lists a command's subcommands. */
private val Node.zshFunction: String get() = "_${usageName.replace(" ", "__subcmd__")}_commands"

private fun zshArgs(node: Node): String {
    val segments = mutableListOf("_arguments \"\${_arguments_options[@]}\" : \\")
    val opts =
        node.opts().flatMap { arg ->
            val prefix = zshConflicts(node, arg) + if (arg.multiple) "*" else ""
            val help = zshHelp(arg.help.orEmpty())
            val value = ":${arg.valueName}:${zshValues(arg)}"
            listOfNotNull(
                arg.short?.let { "'$prefix-$it+[$help]$value' \\" },
                arg.long?.let { "'$prefix--$it=[$help]$value' \\" },
            )
        }
    val flags =
        node.flags().flatMap { arg ->
            val prefix = zshConflicts(node, arg)
            val help = zshHelp(arg.help.orEmpty())
            listOfNotNull(arg.short?.let { "'$prefix-$it[$help]' \\" }, arg.long?.let { "'$prefix--$it[$help]' \\" })
        }
    listOf(opts, flags, zshPositionals(node)).filter { it.isNotEmpty() }.forEach { segments += it.joinToString("\n") }
    if (node.cmd.subcommands.isNotEmpty()) {
        segments += "\":: :${node.zshFunction}\" \\"
        segments += "\"*::: :->${node.cmd.name}\" \\"
    }
    segments += "&& ret=0"
    return segments.joinToString("\n")
}

/** `(-q --quiet)`: what [arg] conflicts with, as zsh excludes it; a positional adds nothing but the parentheses. */
private fun zshConflicts(
    node: Node,
    arg: Arg,
): String {
    val others = arg.conflictsWith.mapNotNull { node.find(it) }
    if (others.isEmpty()) return ""
    return others.flatMap { listOfNotNull(it.short?.let { s -> "-$s" }, it.long?.let { l -> "--$l" }) }.joinToString(" ", "(", ")")
}

private fun zshPositionals(node: Node): List<String> {
    // The first catch-all takes every word after it, so a later multi-valued positional would never be reached.
    var catchAll = false
    return node.positionals.mapNotNull { arg ->
        if (catchAll && arg.multiple) return@mapNotNull null
        val cardinality =
            when {
                arg.multiple && node.cmd.subcommands.isEmpty() -> "*:".also { catchAll = true }
                !arg.required -> ":"
                else -> ""
            }
        val help =
            arg.help
                ?.let { " -- $it" }
                .orEmpty()
                .replace("[", "\\[")
                .replace("]", "\\]")
                .replace("'", "'\\''")
                .replace(":", "\\:")
        "'$cardinality:${arg.id}$help:${zshValues(arg)}' \\"
    }
}

private fun zshValues(arg: Arg): String {
    val values = arg.possibleValues.filter { !it.hidden }
    return when {
        values.any { it.help != null } -> {
            values.joinToString("\n", "((", "))") { "${zshValue(it.name)}\\:\"${zshHelp(it.help.orEmpty())}\"" }
        }

        arg.possibleValues.isNotEmpty() -> {
            values.joinToString(" ", "(", ")") { it.name }
        }

        arg.isPath -> {
            "_files"
        }

        else -> {
            "_default"
        }
    }
}

private fun zshSubcommands(node: Node): String {
    val children = node.children()
    if (children.isEmpty()) return ""
    val cases =
        children.flatMap { child ->
            val body = listOf(zshArgs(child), zshSubcommands(child)).filter { it.isNotEmpty() }.joinToString("\n")
            child.cmd.names().map { "($it)\n$body\n;;" }
        }
    val pos = "\$line[${node.positionals.size + 1}]"
    val context = node.usageName.replace(' ', '-')
    return "\n    case \$state in\n    (${node.cmd.name})\n        words=($pos \"\${words[@]}\")\n        (( CURRENT += 1 ))\n" +
        "        curcontext=\"\${curcontext%:*:*}:$context-command-$pos:\"\n        case $pos in\n            " +
        cases.joinToString("\n") + "\n        esac\n    ;;\nesac"
}

/** The `_commands` function of [root], then of every command below it, sorted by bin name. */
private fun zshDetails(root: Node): String {
    fun below(node: Node): List<Node> = node.children().flatMap { listOf(it) + below(it) }
    return (listOf(root) + below(root).sortedBy { it.usageName }).joinToString("\n") { node ->
        val lines = node.cmd.subcommands.flatMap { sub -> sub.names().map { "'$it:${zshHelp(sub.about.orEmpty())}' \\" } }
        val commands = if (lines.isEmpty()) "" else lines.joinToString("\n", "\n", "\n    ")
        "(( \$+functions[${node.zshFunction}] )) ||\n${node.zshFunction}() {\n    local commands; commands=($commands)\n" +
            "    _describe -t commands '${node.usageName} commands' commands \"\$@\"\n}"
    }
}

/** clap_complete's `escape_help`. */
private fun zshHelp(text: String): String =
    text
        .replace("\\", "\\\\")
        .replace("'", "'\\''")
        .replace("[", "\\[")
        .replace("]", "\\]")
        .replace(":", "\\:")
        .replace("$", "\\$")
        .replace("`", "\\`")
        .replace("\n", " ")

/** clap_complete's `escape_value`. */
private fun zshValue(text: String): String =
    zshHelp(text)
        .replace("(", "\\(")
        .replace(")", "\\)")
        .replace(" ", "\\ ")
