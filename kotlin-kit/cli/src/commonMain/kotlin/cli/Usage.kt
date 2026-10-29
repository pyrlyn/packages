package cli

/**
 * clap's `Usage`: the line after `Usage:` in help and errors.
 *
 * [required] is what counts as required: the arguments marked so, unless a validator has added what present arguments
 * require.
 */
internal class Usage(
    private val node: Node,
    private val style: Style,
    private val required: List<String> = requiredGraph(node),
) {
    /** The usage help prints: `name [OPTIONS] <REQUIRED>... [POSITIONAL] <COMMAND>`. */
    fun normal(): String {
        val sb = StringBuilder(style.literal(node.usageName))
        if (needsOptionsTag()) sb.append(" [OPTIONS]")
        args(emptyList()).forEach { sb.append(' ').append(it) }
        if (node.hasVisibleSubcommands) sb.append(if (node.cmd.subcommandRequired) " <COMMAND>" else " [COMMAND]")
        return sb.toString().trimEnd()
    }

    /** clap's `create_usage_with_title`: the normal usage, or one naming [used] when anything was. */
    fun withUsed(used: List<String>): String {
        if (used.isEmpty()) return normal()
        val sb = StringBuilder(style.literal(node.usageName))
        args(used).forEach { sb.append(' ').append(it) }
        if (node.cmd.subcommandRequired) sb.append(" <COMMAND>")
        return sb.toString()
    }

    /** clap's `get_args`: the required arguments and [incls] as required, then every other visible positional. */
    private fun args(incls: List<String>): List<String> {
        val opts = LinkedHashSet<String>()
        val positionals = mutableMapOf<Int, String>()
        for (id in unrolled() + incls) {
            val arg = node.find(id) ?: continue
            val shown = node.display(arg, required = true, style = style)
            if (arg.positional) positionals[node.index(arg)] = shown else opts += shown
        }
        for (arg in node.positionals) {
            if (!arg.hidden && node.index(arg) !in positionals) positionals[node.index(arg)] = node.display(arg, style = style)
        }
        return opts.toList() + positionals.keys.sorted().map { positionals.getValue(it) }
    }

    /** clap's `get_required_usage_from` with a matcher: what is required and in [incls], less what is [present]. */
    fun requiredFrom(
        incls: List<String>,
        present: Collection<String>,
    ): List<String> {
        val opts = LinkedHashSet<String>()
        val positionals = mutableMapOf<Int, String>()
        for (id in unrolled() + incls) {
            val arg = node.find(id)?.takeIf { id !in present } ?: continue
            val shown = node.display(arg, required = true, style = style)
            if (arg.positional) positionals[node.index(arg)] = shown else opts += shown
        }
        return opts.toList() + positionals.keys.sorted().map { positionals.getValue(it) }
    }

    /** Each required argument after everything it requires, as clap unrolls them. */
    private fun unrolled(): List<String> = required.flatMap { requiresOf(node, it) + it }

    private fun needsOptionsTag(): Boolean = node.args.any { !it.positional && it.action == Action.Set && !it.hidden && !it.required }

    companion object {
        fun requiredGraph(node: Node): List<String> = node.args.filter { it.required }.map { it.id }

        /** Everything [id] requires, directly or through what it requires. */
        fun requiresOf(
            node: Node,
            id: String,
        ): List<String> {
            val seen = LinkedHashSet<String>()
            val queue = ArrayDeque(node.find(id)?.requires.orEmpty())
            while (queue.isNotEmpty()) {
                val next = queue.removeFirst()
                if (next != id && seen.add(next)) queue += node.find(next)?.requires.orEmpty()
            }
            return seen.toList()
        }
    }
}
