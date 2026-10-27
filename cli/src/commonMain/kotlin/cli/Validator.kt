package cli

/** clap's `Validator`, run on a level once everything under it has passed. */
internal class Validator(
    private val level: Level,
    private val ctx: Ctx,
) {
    private val node = level.node
    private val errors = Errors(node, ctx.style)
    private val present = level.values.keys.toList()

    fun validate() {
        if (level.sub == null && node.cmd.subcommandRequired) {
            // With nothing given at all, clap's `arg_required_else_help` shows the help instead of an error.
            throw if (present.isEmpty()) {
                Stop(Help(node, ctx.style, ctx.width, long = false).render(), stderr = true, code = USAGE_EXIT)
            } else {
                errors.missingSubcommand()
            }
        }
        conflicts()
        required()
    }

    private fun arg(id: String): Arg = node.find(id) ?: error("no argument '$id' in '${node.usageName}'")

    /** The present arguments [id] conflicts with, whichever of the two declared it, in the order they were given. */
    private fun conflictsOf(id: String): List<String> {
        val mine = node.find(id)?.conflictsWith.orEmpty()
        return present.filter { it != id }.flatMap { other ->
            listOfNotNull(other.takeIf { it in mine }, other.takeIf { id in arg(it).conflictsWith })
        }
    }

    private fun conflicts() {
        val (id, with) = present.asSequence().map { it to conflictsOf(it) }.firstOrNull { it.second.isNotEmpty() } ?: return
        val used = present.filter { !arg(it).hidden && it !in with }
        val incls = used.flatMap { arg(it).requires }.filter { it !in used && it !in with } + used
        throw errors.conflict(arg(id), with.map(::arg), Usage(node, ctx.style).withUsed(incls))
    }

    private fun required() {
        val required = (Usage.requiredGraph(node) + present.flatMap { Usage.requiresOf(node, it) }).distinct()
        val missing = required.filter { it !in present && conflictsOf(it).isEmpty() }.toMutableList()
        node.args
            .filter { a -> a.id !in present && a.requiredUnlessPresent.isNotEmpty() && a.requiredUnlessPresent.none { it in present } }
            .forEach { missing += it.id }
        // For display, every positional before the last missing one is listed too.
        val highest = missing.maxOfOrNull { node.index(arg(it)) } ?: 0
        node.positionals.filter { it.id !in present && node.index(it) < highest }.forEach { missing += it.id }
        if (missing.isEmpty()) return
        val lines = Usage(node, Style(false), required).requiredFrom(missing, present)
        val used = present.filter { !arg(it).hidden } + missing
        throw errors.missingRequired(lines, Usage(node, ctx.style, required).withUsed(used))
    }
}
