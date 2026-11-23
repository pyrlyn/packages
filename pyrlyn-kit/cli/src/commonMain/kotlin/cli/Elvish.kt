package cli

/** clap_complete's elvish generator. */
internal fun elvish(
    root: Node,
    bin: String,
): String =
    """
    |
    |use builtin;
    |use str;
    |
    |set edit:completion:arg-completer[$bin] = {|@words|
    |    fn spaces {|n|
    |        builtin:repeat ${'$'}n ' ' | str:join ''
    |    }
    |    fn cand {|text desc|
    |        edit:complex-candidate ${'$'}text &display=${'$'}text' '(spaces (- 14 (wcswidth ${'$'}text)))${'$'}desc
    |    }
    |    var command = '$bin'
    |    for word ${'$'}words[1..-1] {
    |        if (str:has-prefix ${'$'}word '-') {
    |            break
    |        }
    |        set command = ${'$'}command';'${'$'}word
    |    }
    |    var completions = [${elvishCases(root, listOf(bin))}
    |    ]
    |    ${'$'}completions[${'$'}command]
    |}
    |
    """.trimMargin()

private fun elvishCases(
    node: Node,
    names: List<String>,
): String {
    val cands = StringBuilder()

    // Without help, clap_complete shows the bare name: the letter, the long name, the subcommand.
    fun cand(
        text: String,
        help: String?,
        name: String,
    ) {
        cands
            .append("\n            cand ")
            .append(text)
            .append(" '")
            .append(help?.replace("'", "''") ?: name)
            .append('\'')
    }
    for (arg in node.opts() + node.flags()) {
        arg.short?.let { cand("-$it", arg.summary, it.toString()) }
        arg.long?.let { cand("--$it", arg.summary, it) }
    }
    node.cmd.subcommands.forEach { sub -> sub.names().forEach { cand(it, sub.about, it) } }
    val out = StringBuilder()
    names.forEach {
        out
            .append("\n        &'")
            .append(it)
            .append("'= {")
            .append(cands)
            .append("\n        }")
    }
    for (child in node.children()) {
        for (parent in names) out.append(elvishCases(child, child.cmd.names().map { "$parent;$it" }))
    }
    return out.toString()
}
