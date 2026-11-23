package cli

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs

// The shape of a completion callback: no help flag, and a prefix that may itself look like a flag.
private val callback =
    command("__complete") {
        disableHelpFlag = true
        option("root") { valueName = "DIR" }
        positional("kind") {
            required = true
            value = ValueKind.OneOf(listOf(PossibleValue("installed"), PossibleValue("registry")))
        }
        positional("prefix") { allowHyphenValues = true }
    }

private fun run(vararg args: String): Parsed = parse(callback, args.toList(), Settings("__complete"))

private fun prefix(vararg args: String): String? = assertIs<Parsed.Run>(run(*args)).matches.value("prefix")

private fun error(vararg args: String): String = assertIs<Parsed.Exit>(run(*args)).also { assertEquals(2, it.code) }.text

private const val USAGE = "\n\nUsage: __complete [OPTIONS] <KIND> [PREFIX]\n"

private fun unexpected(arg: String) = "error: unexpected argument '$arg' found\n\n  tip: to pass '$arg' as a value, use '-- $arg'$USAGE"

class HyphenValuesTest {
    @Test
    fun aPositionalThatTakesHyphensTakesFlagsTheCommandLacks() {
        assertEquals("-x", prefix("installed", "-x"))
        assertEquals("--x", prefix("installed", "--x"))
        assertEquals("-xh", prefix("installed", "-xh"))
        assertEquals("--help", prefix("installed", "--help"))
        assertEquals("-h", prefix("installed", "-h"))
    }

    @Test
    fun aFlagTheCommandHasIsStillAFlag() {
        val matches = assertIs<Parsed.Run>(run("installed", "--root", "/r")).matches
        assertEquals("/r", matches.value("root"))
        assertEquals(null, matches.value("prefix"))
        assertEquals(null, prefix("installed", "--"))
    }

    @Test
    fun onlyThePositionalThatTakesHyphensTakesThem() {
        assertEquals(unexpected("-x"), error("-x", "installed"))
        assertEquals(unexpected("--bad"), error("--bad", "installed"))
        // The prefix is full, so the next dash word has nowhere to go.
        assertEquals(unexpected("-y"), error("installed", "-x", "-y"))
    }

    @Test
    fun withoutAHelpFlagHelpIsAnUnknownArgumentAndErrorsPointAtNone() {
        assertEquals(unexpected("--help"), error("--help"))
        assertEquals(unexpected("-h"), error("-h"))
        assertEquals("error: a value is required for '--root <DIR>' but none was supplied\n", error("--root"))
        assertEquals(
            "error: the following required arguments were not provided:\n  <KIND>\n\nUsage: __complete <KIND> [PREFIX]\n",
            error(),
        )
        assertFalse("--help" in help(callback, emptyList(), Settings("__complete")))
    }
}
