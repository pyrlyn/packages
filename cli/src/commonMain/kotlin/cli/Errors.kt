package cli

/** clap's exit code for every usage error, and for help shown because nothing was given. */
internal const val USAGE_EXIT = 2

/** How a parse ends early: help, a version line or an error, ready to print. */
internal class Stop(
    val text: String,
    val stderr: Boolean,
    val code: Int,
) : Exception()

/** clap's `RichFormatter` for the errors a parse can end in. */
internal class Errors(
    private val node: Node,
    private val style: Style,
) {
    fun render(
        head: String,
        context: List<String> = emptyList(),
        tips: List<String> = emptyList(),
        usage: String? = null,
    ): Stop {
        val sb = StringBuilder(style.error("error:")).append(' ').append(head)
        context.forEach { sb.append('\n').append(it) }
        if (tips.isNotEmpty()) sb.append('\n')
        tips.forEach {
            sb
                .append("\n  ")
                .append(style.valid("tip:"))
                .append(' ')
                .append(it)
        }
        usage?.let {
            sb
                .append("\n\n")
                .append(style.header("Usage:"))
                .append(' ')
                .append(it)
        }
        if (!node.cmd.disableHelpFlag) sb.append("\n\nFor more information, try '").append(style.literal("--help")).append("'.")
        sb.append('\n')
        return Stop(sb.toString(), stderr = true, code = USAGE_EXIT)
    }

    private fun list(
        label: String,
        items: List<String>,
    ) = "  [$label: ${items.joinToString(", ") { style.valid(quoted(it)) }}]"

    private fun similar(
        what: String,
        found: List<String>,
    ): String {
        val names = found.joinToString(", ") { "'${style.valid(it)}'" }
        return if (found.size == 1) "a similar $what exists: $names" else "some similar ${what}s exist: $names"
    }

    fun unknownArgument(
        arg: String,
        tips: List<String>,
        usage: String,
    ) = render("unexpected argument '${style.invalid(arg)}' found", tips = tips, usage = usage)

    fun similarArgument(name: String) = similar("argument", listOf(name))

    fun trailingTip(arg: String) = "to pass '${style.invalid(arg)}' as a value, use '${style.valid("-- $arg")}'"

    fun unrecognizedSubcommand(
        name: String,
        similar: List<String>,
    ) = render(
        "unrecognized subcommand '${style.invalid(name)}'",
        tips = if (similar.isEmpty()) emptyList() else listOf(similar("subcommand", similar)),
        usage = Usage(node, style).normal(),
    )

    fun missingSubcommand() =
        render(
            "'${style.invalid(node.usageName)}' requires a subcommand but one was not provided",
            context = listOf(list("subcommands", node.subcommandNames)),
            usage = Usage(node, style).normal(),
        )

    fun emptyValue(arg: Arg) =
        render(
            "a value is required for '${style.invalid(node.display(arg))}' but none was supplied",
            context = values(arg),
        )

    fun invalidValue(
        arg: Arg,
        value: String,
    ): Stop {
        if (value.isEmpty()) return emptyValue(arg)
        val best = didYouMean(value, arg.possibleValues.filter { !it.hidden }.map { it.name }).lastOrNull()
        return render(
            "invalid value '${style.invalid(value)}' for '${style.literal(node.display(arg))}'",
            context = values(arg),
            tips = listOfNotNull(best?.let { similar("value", listOf(it)) }),
        )
    }

    fun badValue(
        arg: Arg,
        value: String,
        reason: String,
    ) = render("invalid value '${style.invalid(value)}' for '${style.literal(node.display(arg))}': $reason")

    fun tooManyValues(
        arg: Arg,
        value: String,
        usage: String,
    ) = render(
        "unexpected value '${style.invalid(value)}' for '${style.literal(node.display(arg))}' found; no more were expected",
        usage = usage,
    )

    fun repeated(arg: Arg) =
        render(
            "the argument '${style.invalid(node.display(arg))}' cannot be used multiple times",
            usage = Usage(node, style).normal(),
        )

    fun conflict(
        arg: Arg,
        with: List<Arg>,
        usage: String,
    ): Stop {
        val names = with.map { node.display(it) }.distinct()
        val head = StringBuilder("the argument '${style.invalid(node.display(arg))}' cannot be used with")
        if (names.size == 1) {
            head.append(" '").append(style.invalid(names[0])).append('\'')
        } else {
            head.append(':')
            names.forEach { head.append("\n  ").append(style.invalid(it)) }
        }
        return render(head.toString(), usage = usage)
    }

    fun missingRequired(
        lines: List<String>,
        usage: String,
    ) = render(
        "the following required arguments were not provided:" + lines.joinToString("") { "\n  " + style.valid(it) },
        usage = usage,
    )

    private fun values(arg: Arg): List<String> {
        val names = arg.possibleValues.filter { !it.hidden }.map { it.name }
        return if (names.isEmpty()) emptyList() else listOf(list("possible values", names))
    }
}
