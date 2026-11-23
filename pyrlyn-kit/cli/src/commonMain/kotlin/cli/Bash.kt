package cli

/** clap_complete's bash generator. */
internal fun bash(
    root: Node,
    bin: String,
): String {
    val name = root.cmd.name.replace("-", "__")
    return """
        |_$name() {
        |    local i cur prev opts cmd
        |    COMPREPLY=()
        |    if [[ "${'$'}{BASH_VERSINFO[0]}" -ge 4 ]]; then
        |        cur="${'$'}2"
        |    else
        |        cur="${'$'}{COMP_WORDS[COMP_CWORD]}"
        |    fi
        |    prev="${'$'}3"
        |    cmd=""
        |    opts=""
        |
        |    for i in "${'$'}{COMP_WORDS[@]:0:COMP_CWORD}"
        |    do
        |        case "${'$'}{cmd},${'$'}{i}" in
        |            ",${'$'}1")
        |                cmd="$name"
        |                ;;${bashCases(root, name)}
        |            *)
        |                ;;
        |        esac
        |    done
        |
        |    case "${'$'}{cmd}" in
        |        $name)${bashDetails(root, name)}
        |    esac
        |}
        |
        |if [[ "${'$'}{BASH_VERSINFO[0]}" -eq 4 && "${'$'}{BASH_VERSINFO[1]}" -ge 4 || "${'$'}{BASH_VERSINFO[0]}" -gt 4 ]]; then
        |    complete -F _$name -o nosort -o bashdefault -o default $bin
        |else
        |    complete -F _$name -o bashdefault -o default $bin
        |fi
        |
        """.trimMargin()
}

/** Every command below [root] with the function name it gets, `ketch__subcmd__self__subcmd__upgrade`. */
private fun bashNodes(
    node: Node,
    fn: String,
): List<Pair<String, Node>> =
    node.children().flatMap { child ->
        val childFn = "${fn}__subcmd__${child.cmd.name.replace("-", "__")}"
        listOf(childFn to child) + bashNodes(child, childFn)
    }

/** One `parent,word)` case per name and alias, sorted as clap_complete sorts them. */
private fun bashCases(
    root: Node,
    name: String,
): String {
    val cases = mutableListOf<Triple<String, String, String>>()

    fun add(
        node: Node,
        fn: String,
    ) {
        for (child in node.children()) {
            val childFn = "${fn}__subcmd__${child.cmd.name.replace("-", "__")}"
            child.cmd.names().forEach { cases += Triple(fn, it, childFn) }
            add(child, childFn)
        }
    }
    add(root, name)
    return cases
        .sortedWith(compareBy<Triple<String, String, String>> { it.first }.thenBy { it.second }.thenBy { it.third })
        .joinToString("") { (parent, word, fn) -> "\n            $parent,$word)\n                cmd=\"$fn\"\n                ;;" }
}

private fun bashDetails(
    root: Node,
    name: String,
): String {
    val blocks = (listOf(name to root) + bashNodes(root, name)).sortedBy { it.first }
    return blocks.withIndex().joinToString("") { (i, block) ->
        val (fn, node) = block
        val head = if (i == 0) "" else "\n        $fn)"
        head + bashBlock(node, fn.split("__subcmd__").size)
    }
}

private fun bashBlock(
    node: Node,
    level: Int,
): String {
    val words =
        node.args.mapNotNull { it.short?.let { s -> "-$s" } } +
            node.args.mapNotNull { it.long?.let { l -> "--$l" } } +
            node.positionals.flatMap { arg -> arg.possibleValues.filter { !it.hidden }.map { it.name } } +
            node.cmd.subcommands.flatMap { it.names() }
    val details =
        node.opts().joinToString("") { arg ->
            listOfNotNull(arg.long?.let { "--$it" }, arg.short?.let { "-$it" }).joinToString("") { flag ->
                "\n                $flag)\n                    COMPREPLY=(${bashValues(arg)})\n                    return 0\n                    ;;"
            }
        }
    return """
        |
        |            opts="${words.joinToString(" ")}"
        |            if [[ ${'$'}{cur} == -* || ${'$'}{COMP_CWORD} -eq $level ]] ; then
        |                COMPREPLY=( ${'$'}(compgen -W "${'$'}{opts}" -- "${'$'}{cur}") )
        |                return 0
        |            fi
        |            case "${'$'}{prev}" in$details
        |                *)
        |                    COMPREPLY=()
        |                    ;;
        |            esac
        |            COMPREPLY=( ${'$'}(compgen -W "${'$'}{opts}" -- "${'$'}{cur}") )
        |            return 0
        |            ;;
        """.trimMargin()
}

private fun bashValues(arg: Arg): String {
    val values = arg.possibleValues.filter { !it.hidden }
    return if (values.isNotEmpty()) {
        "${'$'}(compgen -W \"${values.joinToString(" ") { it.name }}\" -- \"${'$'}{cur}\")"
    } else {
        "${'$'}(compgen -f \"${'$'}{cur}\")"
    }
}
