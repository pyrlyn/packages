package cli

/** clap_complete's PowerShell generator. */
internal fun powerShell(
    root: Node,
    bin: String,
): String =
    """
    |
    |using namespace System.Management.Automation
    |using namespace System.Management.Automation.Language
    |
    |Register-ArgumentCompleter -Native -CommandName '$bin' -ScriptBlock {
    |    param(${'$'}wordToComplete, ${'$'}commandAst, ${'$'}cursorPosition)
    |
    |    ${'$'}commandElements = ${'$'}commandAst.CommandElements
    |    ${'$'}command = @(
    |        '$bin'
    |        for (${'$'}i = 1; ${'$'}i -lt ${'$'}commandElements.Count; ${'$'}i++) {
    |            ${'$'}element = ${'$'}commandElements[${'$'}i]
    |            if (${'$'}element -isnot [StringConstantExpressionAst] -or
    |                ${'$'}element.StringConstantType -ne [StringConstantType]::BareWord -or
    |                ${'$'}element.Value.StartsWith('-') -or
    |                ${'$'}element.Value -eq ${'$'}wordToComplete) {
    |                break
    |        }
    |        ${'$'}element.Value
    |    }) -join ';'
    |
    |    ${'$'}completions = @(switch (${'$'}command) {${powerShellCases(root, listOf(bin))}
    |    })
    |
    |    ${'$'}completions.Where{ ${'$'}_.CompletionText -like "${'$'}wordToComplete*" } |
    |        Sort-Object -Property ListItemText
    |}
    |
    """.trimMargin()

private fun powerShellCases(
    node: Node,
    names: List<String>,
): String {
    val results = StringBuilder()

    fun result(
        text: String,
        listed: String,
        type: String,
        tooltip: String,
    ) {
        results.append("\n            [CompletionResult]::new('$text', '$listed', [CompletionResultType]::$type, '$tooltip')")
    }
    for (arg in node.opts() + node.flags()) {
        val tooltip = arg.summary?.replace("'", "''") ?: arg.id
        // PowerShell matches case-insensitively; the space keeps `-V` apart from `-v`.
        arg.short?.let { result("-$it", "-$it" + if (it.isUpperCase()) " " else "", "ParameterName", tooltip) }
        arg.long?.let { result("--$it", "--$it", "ParameterName", tooltip) }
    }
    node.cmd.subcommands.forEach { sub ->
        sub.names().forEach { result(it, it, "ParameterValue", sub.about?.replace("'", "''") ?: it) }
    }
    val out = StringBuilder()
    names.forEach {
        out
            .append("\n        '")
            .append(it)
            .append("' {")
            .append(results)
            .append("\n            break\n        }")
    }
    for (child in node.children()) {
        for (parent in names) out.append(powerShellCases(child, child.cmd.names().map { "$parent;$it" }))
    }
    return out.toString()
}
