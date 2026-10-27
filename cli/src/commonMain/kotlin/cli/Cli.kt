package cli

/** clap's width when it cannot ask the terminal, and the most it uses even when it can. */
const val DEFAULT_WIDTH = 100

/**
 * How [parse] writes.
 *
 * [binName] is what usage calls the program (clap takes it from `argv[0]`); [width] is the terminal's columns, at
 * most [DEFAULT_WIDTH] as clap caps it; [color] paints help and errors with clap's default styles.
 */
class Settings(
    val binName: String,
    val width: Int = DEFAULT_WIDTH,
    val color: Boolean = false,
)

/** What [parse] found: arguments to run with, or a text to print and an exit code. */
sealed interface Parsed {
    class Run(
        val matches: Matches,
    ) : Parsed

    /** Help or a version line on stdout with code 0, or an error (or help, when nothing was given) on stderr with 2. */
    class Exit(
        val text: String,
        val stderr: Boolean,
        val code: Int,
    ) : Parsed
}

/** The subcommand a level ran, by its canonical name even when an alias selected it. */
class Subcommand(
    val name: String,
    val matches: Matches,
)

/**
 * The arguments one command level ended up with: given here or, for a global, at any level of the line, the deepest
 * one winning.
 *
 * Asking for an id the command does not declare is a programming error and throws, as clap panics on one.
 */
class Matches internal constructor(
    private val node: Node,
    private val given: Map<String, List<String>>,
    val subcommand: Subcommand?,
) {
    private fun arg(id: String): Arg = node.find(id) ?: throw IllegalArgumentException("'${node.usageName}' has no argument '$id'")

    /** Whether the argument was given; its default does not count. */
    fun present(id: String): Boolean = arg(id).let { id in given }

    fun flag(id: String): Boolean = present(id)

    /** The value given, else the default, else null. */
    fun value(id: String): String? = values(id).firstOrNull()

    fun values(id: String): List<String> = given[id] ?: listOfNotNull(arg(id).default)

    /** The value of a [ValueKind.Ranged] argument. */
    fun long(id: String): Long? = value(id)?.toLong()
}

/** Read [args] (without the program name) against [root], answering exactly as a clap 4.6 program would. */
fun parse(
    root: Command,
    args: List<String>,
    settings: Settings,
): Parsed {
    val ctx = Ctx(Style(settings.color), settings.width)
    return try {
        Parsed.Run(matches(Parser(Node(root, null, settings.binName), args, 0, ctx).parse()))
    } catch (stop: Stop) {
        Parsed.Exit(stop.text, stop.stderr, stop.code)
    }
}

/** The help `-h` (or, with [long], `--help`) prints for the command at [path] below [root]. */
fun help(
    root: Command,
    path: List<String>,
    settings: Settings,
    long: Boolean = false,
): String {
    var node = Node(root, null, settings.binName)
    for (name in path) node = node.child(name) ?: throw IllegalArgumentException("'${node.usageName}' has no subcommand '$name'")
    return Help(node, Style(settings.color), settings.width, long).render()
}

/** Give every level the deepest explicit value of each global it knows, as clap's `propagate_globals` does. */
private fun matches(root: Level): Matches {
    val chain = generateSequence(root) { it.sub }.toList()
    val globals =
        chain
            .flatMap { level ->
                level.node.args
                    .filter { it.global }
                    .map { it.id }
            }.distinct()
    for (id in globals) {
        val winner = chain.lastOrNull { id in it.values }?.values?.get(id) ?: continue
        chain.filter { it.node.find(id) != null }.forEach { it.values[id] = winner }
    }
    return build(root)
}

private fun build(level: Level): Matches = Matches(level.node, level.values, level.sub?.let { Subcommand(it.node.cmd.name, build(it)) })
