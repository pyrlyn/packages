package cli

/** What every level of a parse shares: how output looks and how wide it may be. */
internal class Ctx(
    val style: Style,
    val width: Int,
)

/** What one command level was given, in the order clap's matcher records it. */
internal class Level(
    val node: Node,
) {
    /** Explicit arguments only: a flag maps to no values; a default is not here. */
    val values = LinkedHashMap<String, MutableList<String>>()
    var sub: Level? = null
}

/**
 * clap's `Parser::get_matches_with` for one command level.
 *
 * Values wait as clap's pending values do: an option's value, or a run of a positional's, is checked only when the
 * next argument is recorded, so errors come in clap's order. A subcommand is parsed, and validated, before the level
 * that named it is.
 */
internal class Parser(
    private val node: Node,
    private val tokens: List<String>,
    start: Int,
    private val ctx: Ctx,
) {
    private var pos = start
    private val level = Level(node)
    private val errors = Errors(node, ctx.style)
    private var trailing = false
    private var pending: Pending? = null
    private var positional = 0

    private class Pending(
        val arg: Arg,
    ) {
        val values = mutableListOf<String>()
    }

    fun parse(): Level {
        var sub: Node? = null
        while (pos < tokens.size && sub == null) {
            val token = tokens[pos++]
            if (trailing) positional(token) else sub = step(token)
        }
        if (sub != null) level.sub = Parser(sub, tokens, pos, ctx).parse()
        resolve()
        Validator(level, ctx).validate()
        return level
    }

    /** One argument before any `--`; the subcommand it names, if it names one. */
    private fun step(token: String): Node? {
        val wantsValue = pending?.let { !it.arg.positional && it.values.isEmpty() } == true
        // An option waiting for its value takes the next word even when a subcommand has that name.
        val sub = if (wantsValue) null else node.child(token)
        when {
            sub != null -> {
                return sub
            }

            token == "--" -> {
                trailing = true
                resolve()
            }

            token.startsWith("--") -> {
                long(token.substring(2))
            }

            token.length > 1 && token.startsWith("-") -> {
                short(token.substring(1))
            }

            wantsValue -> {
                pending?.values?.add(token)
            }

            else -> {
                positional(token)
            }
        }
        return null
    }

    private fun long(body: String) {
        val eq = body.indexOf('=')
        val name = if (eq < 0) body else body.substring(0, eq)
        val attached = if (eq < 0) null else body.substring(eq + 1)
        val arg = node.byLong(name)?.takeIf { name.isNotEmpty() }
        when {
            arg == null -> {
                quietly()
                throw unknownLong(name)
            }

            !arg.takesValue && attached != null -> {
                val used = usedIds() + listOfNotNull(arg.id.takeIf { arg.action != Action.Help })
                quietly()
                throw errors.tooManyValues(arg, attached, Usage(node, ctx.style).withUsed(used))
            }

            !arg.takesValue -> {
                react(arg, emptyList(), long = true)
            }

            attached != null -> {
                react(arg, listOf(attached), long = true)
            }

            else -> {
                expect(arg)
            }
        }
    }

    /** `-abc`: flags one by one, until an option takes the rest (less one leading `=`) as its value. */
    private fun short(body: String) {
        var i = 0
        while (i < body.length) {
            val arg = node.byShort(body[i]) ?: throw unknownShort("-${body[i]}")
            if (arg.takesValue) {
                val rest = body.substring(i + 1).ifEmpty { null }?.removePrefix("=")
                if (rest != null) react(arg, listOf(rest), long = false) else expect(arg)
                return
            }
            react(arg, emptyList(), long = false)
            i++
        }
    }

    /** An option given without a value: the next argument is its value. */
    private fun expect(arg: Arg) {
        resolve()
        pending = Pending(arg)
    }

    private fun positional(token: String) {
        val arg = node.positionals.getOrNull(positional) ?: throw unmatched(token)
        if (pending?.arg != arg || !arg.multiple) resolve()
        (pending ?: Pending(arg).also { pending = it }).values += token
        if (!arg.multiple) positional++
    }

    private fun react(
        arg: Arg,
        raw: List<String>,
        long: Boolean,
    ) {
        resolve()
        when (arg.action) {
            Action.Help -> throw Stop(Help(node, ctx.style, ctx.width, long).render(), stderr = false, code = 0)
            Action.Version -> throw Stop("${node.displayName} ${node.version}\n", stderr = false, code = 0)
            Action.Set -> record(arg, raw)
        }
    }

    private fun record(
        arg: Arg,
        raw: List<String>,
    ) {
        if (arg.takesValue && raw.isEmpty()) throw errors.emptyValue(arg)
        if (arg.id in level.values && !arg.multiple) throw errors.repeated(arg)
        // In before its values are checked, as clap's matcher has it, so a usage built after a bad value names it.
        val values = level.values.getOrPut(arg.id) { mutableListOf() }
        raw.forEach { values += convert(errors, arg, it) }
    }

    private fun resolve() {
        val waiting = pending ?: return
        pending = null
        record(waiting.arg, waiting.values)
    }

    /** clap resolves what is pending before an unknown-argument error and drops any error that raises. */
    private fun quietly() {
        try {
            resolve()
        } catch (_: Stop) {
            // The unknown argument is the error to report.
        }
    }

    private fun usedIds(): List<String> = level.values.keys.filter { node.find(it)?.hidden == false }

    private fun unknownLong(name: String): Stop {
        val own = didYouMean(name, node.longs).lastOrNull()
        val remaining = tokens.subList(pos, tokens.size)
        // clap then looks for the flag under a subcommand named later on the line, the nearest one first.
        val elsewhere =
            if (own != null) {
                null
            } else {
                node
                    .children()
                    .mapNotNull { child ->
                        val found = didYouMean(name, child.longs).lastOrNull()
                        val at = remaining.indexOf(child.cmd.name)
                        if (found != null && at >= 0) Triple(at, found, child.cmd.name) else null
                    }.minByOrNull { it.first }
            }
        val used = usedIds().toMutableList()
        own?.let { node.byLong(it) }?.takeIf { !it.hidden && it.id !in used }?.let { used += it.id }
        val tips =
            when {
                own != null -> listOf(errors.similarArgument("--$own"))
                elsewhere != null -> listOf("'${ctx.style.valid("${elsewhere.third} --${elsewhere.second}")}' exists")
                node.positionals.isNotEmpty() -> listOf(errors.trailingTip("--$name"))
                else -> emptyList()
            }
        return errors.unknownArgument("--$name", tips, Usage(node, ctx.style).withUsed(used))
    }

    private fun unknownShort(flag: String): Stop {
        quietly()
        val tips = if (node.positionals.isNotEmpty()) listOf(errors.trailingTip(flag)) else emptyList()
        return errors.unknownArgument(flag, tips, Usage(node, ctx.style).normal())
    }

    /** A word with no positional left to take it: clap's `match_arg_error`. */
    private fun unmatched(token: String): Stop {
        quietly()
        val similar = didYouMean(token, node.subcommandNames)
        return when {
            trailing && node.child(token) != null -> {
                val tip = "subcommand '${ctx.style.valid(token)}' exists; to use it, remove the '${ctx.style.invalid("--")}' before it"
                errors.unknownArgument(token, listOf(tip), Usage(node, ctx.style).normal())
            }

            node.cmd.subcommands.isNotEmpty() && (similar.isNotEmpty() || node.positionals.isEmpty()) -> {
                errors.unrecognizedSubcommand(token, similar)
            }

            else -> {
                errors.unknownArgument(token, emptyList(), Usage(node, ctx.style).normal())
            }
        }
    }
}
