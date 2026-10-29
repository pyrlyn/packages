package cli

/*
 * A command tree with clap 4's semantics, declared through a small builder:
 *
 *     val tree = command("tool") {
 *         version = "1.0.0"
 *         flag("verbose") { short = 'v'; global = true; doc("Say more") }
 *         subcommand("run") { doc("Run it"); positional("name") { required = true } }
 *     }
 *
 * [parse] reads arguments against it and answers with help, a version line or an error exactly as a clap binary
 * built from the same tree would, so a port of a clap program keeps its command line byte for byte.
 */

/** One value an argument accepts, as clap's `PossibleValue`. */
class PossibleValue(
    val name: String,
    val help: String? = null,
    val hidden: Boolean = false,
)

/** How an argument's value is read, with clap's own wording for a value that fails. */
sealed interface ValueKind {
    /** Any text, the empty string included (clap's `String`). */
    data object Text : ValueKind

    /** Any text but the empty string, which reads as no value at all (clap's `PathBuf`). */
    data object NonEmpty : ValueKind

    /** A whole number as clap reads `u32` and its kin: a 64-bit signed number, then checked against [min]..[max]. */
    data class Ranged(
        val min: Long,
        val max: Long,
    ) : ValueKind

    /** An unsigned machine-sized number, read as Rust's `usize::from_str`, with no range of its own to check. */
    data object Size : ValueKind

    /** One of [values] (clap's `ValueEnum`). */
    data class OneOf(
        val values: List<PossibleValue>,
    ) : ValueKind
}

/** What an argument does when it is given; [Help] and [Version] are the two clap adds itself. */
internal enum class Action { Set, Help, Version }

/** One argument: a flag, an option that takes a value, or a positional. */
class Arg internal constructor(
    b: ArgBuilder,
) {
    val id = b.id
    val long: String? = if (b.positional) null else b.long ?: b.id.replace('_', '-')
    val short: Char? = b.short
    val positional = b.positional
    val takesValue = b.takesValue
    val multiple = b.multiple
    val valueName = b.valueName ?: b.id.uppercase()
    val value = b.value
    val help = b.help
    val longHelp = b.longHelp
    val required = b.required
    val requiredUnlessPresent = b.requiredUnlessPresent.toList()
    val conflictsWith = b.conflictsWith.toList()
    val requires = b.requires.toList()
    val global = b.global
    val hidden = b.hidden
    val default = b.default
    val allowHyphenValues = b.allowHyphenValues
    internal val action = b.action

    internal val possibleValues: List<PossibleValue> get() = (value as? ValueKind.OneOf)?.values.orEmpty()
}

/** A command: its own arguments and, when it has them, its subcommands. */
class Command internal constructor(
    b: CommandBuilder,
) {
    val name = b.name
    val about = b.about
    val longAbout = b.longAbout
    val version = b.version
    val aliases = b.aliases.toList()
    val hidden = b.hidden
    val args = b.args.toList()
    val subcommands = b.subcommands.toList()

    /** clap's derive sets `subcommand_required` and `arg_required_else_help` together for a required subcommand enum. */
    val subcommandRequired = b.subcommandRequired

    /** Every subcommand shows the root's version, as clap's `propagate_version`. */
    val propagateVersion = b.propagateVersion

    /** No `-h`/`--help` of its own, as clap's `disable_help_flag`; its errors then point at no help either. */
    val disableHelpFlag = b.disableHelpFlag
}

@DslMarker
annotation class CliDsl

@CliDsl
class ArgBuilder internal constructor(
    internal val id: String,
    internal val positional: Boolean,
    internal val takesValue: Boolean,
) {
    var long: String? = null
    var short: Char? = null
    var valueName: String? = null
    var value: ValueKind = ValueKind.Text
    var help: String? = null
    var longHelp: String? = null

    /** A positional that takes every remaining value, or an option that may be given again (clap's `Append`). */
    var multiple = false
    var required = false
    var global = false
    var hidden = false

    /** Shown in help as `[default: …]` and read when the argument is absent; never counts as given. */
    var default: String? = null

    /** A word that looks like a flag this command does not have is taken as a value, as clap's `allow_hyphen_values`. */
    var allowHyphenValues = false
    internal val requiredUnlessPresent = mutableListOf<String>()
    internal val conflictsWith = mutableListOf<String>()
    internal val requires = mutableListOf<String>()
    internal var action = Action.Set

    /** The help from a doc comment, as clap's derive reads one (see [docText]). */
    fun doc(text: String) {
        val (short, long) = docText(text)
        help = short
        longHelp = long
    }

    fun requiredUnlessPresent(vararg ids: String) {
        requiredUnlessPresent += ids
    }

    fun conflictsWith(vararg ids: String) {
        conflictsWith += ids
    }

    fun requires(vararg ids: String) {
        requires += ids
    }
}

@CliDsl
class CommandBuilder internal constructor(
    internal val name: String,
) {
    var about: String? = null
    var longAbout: String? = null
    var version: String? = null
    var hidden = false
    var subcommandRequired = false
    var propagateVersion = false
    var disableHelpFlag = false
    internal val aliases = mutableListOf<String>()
    internal val args = mutableListOf<Arg>()
    internal val subcommands = mutableListOf<Command>()

    /** The about text from a doc comment, as clap's derive reads one (see [docText]). */
    fun doc(text: String) {
        val (short, long) = docText(text)
        about = short
        longAbout = long
    }

    /** Names that also select this command, listed in help as `[aliases: …]`. */
    fun alias(vararg names: String) {
        aliases += names
    }

    /** A boolean switch: present or not, given at most once. */
    fun flag(
        id: String,
        block: ArgBuilder.() -> Unit = {},
    ) {
        args += Arg(ArgBuilder(id, positional = false, takesValue = false).apply(block))
    }

    /** A `--name <VALUE>` option. */
    fun option(
        id: String,
        block: ArgBuilder.() -> Unit = {},
    ) {
        args += Arg(ArgBuilder(id, positional = false, takesValue = true).apply(block))
    }

    /** A value given by position, in declaration order. */
    fun positional(
        id: String,
        block: ArgBuilder.() -> Unit = {},
    ) {
        args += Arg(ArgBuilder(id, positional = true, takesValue = true).apply(block))
    }

    fun subcommand(
        name: String,
        block: CommandBuilder.() -> Unit,
    ) {
        subcommands += command(name, block)
    }
}

fun command(
    name: String,
    block: CommandBuilder.() -> Unit,
): Command = Command(CommandBuilder(name).apply(block))

/**
 * Split a doc comment as clap's derive does.
 *
 * Paragraphs are separated by blank lines and each one's lines are joined with a space. The short text is the first
 * paragraph without its final period; the long text is every paragraph, and only exists when there are several.
 */
fun docText(text: String): Pair<String, String?> {
    val paragraphs = mutableListOf<String>()
    val current = mutableListOf<String>()
    for (line in text.lines() + "") {
        if (line.isBlank()) {
            if (current.isNotEmpty()) paragraphs += current.joinToString(" ")
            current.clear()
        } else {
            current += line.trim()
        }
    }
    val first = paragraphs.firstOrNull().orEmpty()
    val short = if (first.endsWith('.') && !first.endsWith("..")) first.dropLast(1) else first
    return short to if (paragraphs.size > 1) paragraphs.joinToString("\n\n") else null
}
