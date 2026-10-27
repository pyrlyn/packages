package cli

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

private val tree =
    command("tool") {
        about = "Do things with packages."
        version = "1.2.3"
        propagateVersion = true
        subcommandRequired = true
        option("root") {
            valueName = "DIR"
            value = ValueKind.NonEmpty
            global = true
            doc("Root directory")
        }
        flag("verbose") {
            short = 'v'
            global = true
            conflictsWith("quiet")
            doc("Say more")
        }
        flag("quiet") {
            short = 'q'
            global = true
            doc("Say less")
        }
        subcommand("add") {
            doc("Add packages.")
            alias("a")
            positional("pkg") {
                multiple = true
                doc("Packages to add")
            }
            flag("force") {
                short = 'f'
                doc("Replace what is there")
            }
            flag("pre") { doc("Take prereleases") }
            option("jobs") {
                short = 'j'
                valueName = "N"
                value = ValueKind.Size
                doc("Work at once")
            }
        }
        subcommand("show") {
            doc("Show a package")
            alias("s", "info")
            positional("pkg") {
                required = true
                doc("The package")
            }
            flag("releases") { doc("List releases") }
            flag("draft") {
                requires("releases")
                doc("Drafts too")
            }
            option("limit") {
                short = 'n'
                value = ValueKind.Ranged(1, 4_294_967_295)
                default = "20"
                doc("At most this many")
            }
        }
        subcommand("list") {
            doc("List packages")
            positional("mode") {
                value = ValueKind.OneOf(listOf(PossibleValue("local", "Installed only"), PossibleValue("remote", "From the registry")))
                doc("Where to look")
            }
            flag("json") { doc("Emit JSON") }
            flag("names_only") {
                conflictsWith("json")
                doc("Names only")
            }
        }
        subcommand("group") {
            doc("A group of commands")
            subcommandRequired = true
            subcommand("one") { doc("The first") }
            subcommand("two") { doc("The second") }
        }
        subcommand("secret") {
            hidden = true
            doc("Not listed")
        }
    }

private fun run(vararg args: String): Parsed = parse(tree, args.toList(), Settings("tool"))

private fun matches(vararg args: String): Matches = assertIs<Parsed.Run>(run(*args)).matches

private fun exit(vararg args: String): Parsed.Exit = assertIs<Parsed.Exit>(run(*args))

private fun error(vararg args: String): String {
    val exit = exit(*args)
    assertTrue(exit.stderr)
    assertEquals(2, exit.code)
    return exit.text
}

private fun row(
    left: String,
    help: String,
    column: Int,
) = left.padEnd(column) + help

private const val MORE = "\n\nFor more information, try '--help'.\n"

private val globalsShort =
    listOf(
        row("      --root <DIR>", "Root directory", 20),
        row("  -v, --verbose", "Say more", 20),
        row("  -q, --quiet", "Say less", 20),
    )

class HelpTest {
    @Test
    fun shortHelpAlignsEverySectionOnItsOwnLongestEntry() {
        val expected =
            listOf(
                "Add packages",
                "",
                "Usage: tool add [OPTIONS] [PKG]...",
                "",
                "Arguments:",
                "  [PKG]...  Packages to add",
                "",
                "Options:",
                row("  -f, --force", "Replace what is there", 20),
                row("      --pre", "Take prereleases", 20),
                row("  -j, --jobs <N>", "Work at once", 20),
            ) + globalsShort +
                listOf(row("  -h, --help", "Print help", 20), row("  -V, --version", "Print version", 20))
        assertEquals(expected.joinToString("\n") + "\n", help(tree, listOf("add"), Settings("tool")))
    }

    @Test
    fun rootListsVisibleCommandsWithTheirAliases() {
        val text = help(tree, emptyList(), Settings("tool"))
        val commands =
            listOf(
                "Commands:",
                row("  add", "Add packages [alias: a]", 9),
                row("  show", "Show a package [aliases: s, info]", 9),
                row("  list", "List packages", 9),
                row("  group", "A group of commands", 9),
            ).joinToString("\n")
        assertTrue(text.startsWith("Do things with packages.\n\nUsage: tool [OPTIONS] <COMMAND>\n\n$commands\n\nOptions:\n"), text)
        assertFalse("secret" in text)
    }

    @Test
    fun defaultsArePartOfTheHelp() {
        assertTrue(row("  -n, --limit <LIMIT>", "At most this many [default: 20]", 23) in help(tree, listOf("show"), Settings("tool")))
    }

    @Test
    fun possibleValuesWithHelpGiveALongHelpThatListsThem() {
        val short = help(tree, listOf("list"), Settings("tool"))
        assertTrue(row("  [MODE]", "Where to look [possible values: local, remote]", 10) in short, short)
        assertTrue(row("  -h, --help", "Print help (see more with '--help')", 20) in short, short)

        val long = help(tree, listOf("list"), Settings("tool"), long = true)
        val arguments =
            listOf(
                "Arguments:",
                "  [MODE]",
                "          Where to look",
                "",
                "          Possible values:",
                "          - local:  Installed only",
                "          - remote: From the registry",
                "",
                "Options:",
                "      --json",
                "          Emit JSON",
                "",
                "      --names-only",
            ).joinToString("\n")
        assertTrue(arguments in long, long)
        assertTrue(long.endsWith("  -h, --help\n          Print help (see a summary with '-h')\n\n  -V, --version\n          Print version\n"), long)
    }

    @Test
    fun longHelpMovesArgumentHelpDownButNotSubcommandAbouts() {
        val tool =
            command("tool") {
                about = "Short"
                longAbout = "Long.\n\nMore."
                flag("x") { doc("An x") }
                subcommand("run") { doc("Run it") }
            }
        val text = help(tool, emptyList(), Settings("tool"), long = true)
        assertTrue(Regex("\n  run +Run it\n").containsMatchIn(text), text)
        assertTrue("      --x\n          An x\n" in text, text)
    }

    @Test
    fun aNarrowTerminalMovesHelpUnderTheArguments() {
        val text = help(tree, listOf("add"), Settings("tool", width = 40))
        assertTrue("  [PKG]...  Packages to add\n" in text, text)
        assertTrue("  -f, --force\n          Replace what is there\n      --pre\n          Take prereleases\n" in text, text)
    }

    @Test
    fun helpAndVersionFlagsPrintAndStop() {
        val short = exit("add", "-h")
        assertEquals(0, short.code)
        assertFalse(short.stderr)
        assertEquals(help(tree, listOf("add"), Settings("tool")), short.text)
        assertEquals(help(tree, listOf("list"), Settings("tool"), long = true), exit("list", "--help").text)
        assertEquals("tool 1.2.3\n", exit("-V").text)
        assertEquals("tool-group-one 1.2.3\n", exit("group", "one", "--version").text)
    }

    @Test
    fun nothingGivenToACommandThatNeedsASubcommandShowsItsHelpAsAnError() {
        assertEquals(help(tree, emptyList(), Settings("tool")), error())
        assertEquals(help(tree, listOf("group"), Settings("tool")), error("-v", "group"))
    }
}

class ParseTest {
    @Test
    fun subcommandsAnswerToAliasesUnderTheirOwnName() {
        val sub = matches("a", "x", "-f", "y").subcommand!!
        assertEquals("add", sub.name)
        assertEquals(listOf("x", "y"), sub.matches.values("pkg"))
        assertTrue(sub.matches.flag("force"))
        assertFalse(sub.matches.flag("pre"))
    }

    @Test
    fun theDeepestGlobalWinsEverywhere() {
        val top = matches("--root", "a", "add", "--root=b", "-v")
        val add = top.subcommand!!.matches
        assertEquals("b", top.value("root"))
        assertEquals("b", add.value("root"))
        assertTrue(top.flag("verbose"))
        assertEquals("a", matches("--root", "a", "add").subcommand!!.matches.value("root"))
    }

    @Test
    fun valuesAttachInEveryWayClapAccepts() {
        assertEquals("3", matches("add", "-j3").subcommand!!.matches.value("jobs"))
        assertEquals("3", matches("add", "-fj=3").subcommand!!.matches.value("jobs"))
        assertEquals("3", matches("add", "--jobs=+3").subcommand!!.matches.value("jobs"))
        assertEquals(20L, matches("show", "x").subcommand!!.matches.long("limit"))
        assertEquals(listOf("-x", "add"), matches("add", "--", "-x", "add").subcommand!!.matches.values("pkg"))
    }

    @Test
    fun anOptionWaitingForAValueTakesASubcommandsName() {
        assertEquals(
            "error: 'tool' requires a subcommand but one was not provided\n" +
                "  [subcommands: add, a, show, s, info, list, group, secret]\n\nUsage: tool [OPTIONS] <COMMAND>$MORE",
            error("--root", "add"),
        )
    }
}

class ErrorTest {
    @Test
    fun anUnknownLongSuggestsTheNearestAndUsesItInTheUsage() {
        assertEquals(
            "error: unexpected argument '--forc' found\n\n  tip: a similar argument exists: '--force'\n\nUsage: tool add --force [PKG]...$MORE",
            error("add", "--forc", "x"),
        )
    }

    @Test
    fun anUnknownLongFoundUnderALaterSubcommandSaysWhere() {
        assertEquals(
            "error: unexpected argument '--json' found\n\n  tip: 'list --json' exists\n\nUsage: tool [OPTIONS] <COMMAND>$MORE",
            error("--json", "list"),
        )
    }

    @Test
    fun aDashedWordWhereAPositionalCouldGoTellsHowToPassIt() {
        assertEquals(
            "error: unexpected argument '-1' found\n\n  tip: to pass '-1' as a value, use '-- -1'\n\nUsage: tool add [OPTIONS] [PKG]...$MORE",
            error("add", "-1"),
        )
        assertEquals("error: unexpected argument '-x' found\n\nUsage: tool [OPTIONS] <COMMAND>$MORE", error("-x"))
    }

    @Test
    fun aMistypedSubcommandListsTheSimilarOnes() {
        assertEquals(
            "error: unrecognized subcommand 'lst'\n\n  tip: a similar subcommand exists: 'list'\n\nUsage: tool [OPTIONS] <COMMAND>$MORE",
            error("lst"),
        )
        assertEquals("error: unrecognized subcommand 'help'\n\nUsage: tool [OPTIONS] <COMMAND>$MORE", error("help"))
    }

    @Test
    fun aSubcommandAfterDoubleDashIsAnArgument() {
        assertEquals(
            "error: unexpected argument 'add' found\n\n  tip: subcommand 'add' exists; to use it, remove the '--' before it\n\n" +
                "Usage: tool [OPTIONS] <COMMAND>$MORE",
            error("--", "add"),
        )
    }

    @Test
    fun conflictsAreReportedWhereTheyWereGiven() {
        assertEquals("error: the argument '--verbose' cannot be used with '--quiet'\n\nUsage: tool --verbose <COMMAND>$MORE", error("-v", "-q", "add"))
        assertEquals("error: the argument '--verbose' cannot be used with '--quiet'\n\nUsage: tool add --verbose [PKG]...$MORE", error("add", "-vq"))
        assertEquals("error: the argument '--json' cannot be used with '--names-only'\n\nUsage: tool list --json [MODE]$MORE", error("list", "--json", "--names-only"))
    }

    @Test
    fun missingRequiredArgumentsIncludeWhatPresentOnesRequire() {
        assertEquals(
            "error: the following required arguments were not provided:\n  --releases\n\nUsage: tool show --releases --draft <PKG>$MORE",
            error("show", "x", "--draft"),
        )
        assertEquals("error: the following required arguments were not provided:\n  <PKG>\n\nUsage: tool show <PKG>$MORE", error("show"))
    }

    @Test
    fun aSubcommandNamedWithArgumentsButNoSubcommandIsAnError() {
        assertEquals(
            "error: 'tool group' requires a subcommand but one was not provided\n  [subcommands: one, two]\n\n" +
                "Usage: tool group [OPTIONS] <COMMAND>$MORE",
            error("group", "-v"),
        )
    }

    @Test
    fun valuesAreCheckedWithRustsWording() {
        assertEquals("error: invalid value '0' for '--limit <LIMIT>': 0 is not in 1..=4294967295$MORE", error("show", "x", "-n", "0"))
        assertEquals("error: invalid value '-1' for '--limit <LIMIT>': -1 is not in 1..=4294967295$MORE", error("show", "x", "-n=-1"))
        assertEquals(
            "error: invalid value '99999999999999999999' for '--limit <LIMIT>': number too large to fit in target type$MORE",
            error("show", "x", "--limit", "99999999999999999999"),
        )
        assertEquals("error: invalid value 'abc' for '--jobs <N>': invalid digit found in string$MORE", error("add", "-j", "abc", "x"))
        assertEquals("error: invalid value '-3' for '--jobs <N>': invalid digit found in string$MORE", error("add", "--jobs=-3"))
        assertEquals("error: invalid value '' for '--jobs <N>': cannot parse integer from empty string$MORE", error("add", "-j="))
        assertEquals("error: a value is required for '--root <DIR>' but none was supplied$MORE", error("--root="))
    }

    @Test
    fun aValueMissingAtTheEndOrBeforeAFlagIsEmpty() {
        assertEquals("error: a value is required for '--limit <LIMIT>' but none was supplied$MORE", error("show", "x", "-n"))
        assertEquals("error: a value is required for '--limit <LIMIT>' but none was supplied$MORE", error("show", "x", "-n", "--releases"))
        assertEquals(
            "error: unexpected argument '-5' found\n\n  tip: to pass '-5' as a value, use '-- -5'\n\nUsage: tool show [OPTIONS] <PKG>$MORE",
            error("show", "x", "-n", "-5"),
        )
    }

    @Test
    fun possibleValuesAreListedAndTheNearestOneSuggested() {
        assertEquals(
            "error: invalid value 'loc' for '[MODE]'\n  [possible values: local, remote]\n\n  tip: a similar value exists: 'local'$MORE",
            error("list", "loc"),
        )
        assertEquals("error: a value is required for '[MODE]' but none was supplied\n  [possible values: local, remote]$MORE", error("list", ""))
    }

    @Test
    fun aFlagGivenTwiceOrWithAValueIsRefused() {
        assertEquals("error: the argument '--pre' cannot be used multiple times\n\nUsage: tool add [OPTIONS] [PKG]...$MORE", error("add", "--pre", "--pre"))
        assertEquals(
            "error: unexpected value '1' for '--pre' found; no more were expected\n\nUsage: tool add --pre [PKG]...$MORE",
            error("add", "--pre=1"),
        )
    }

    @Test
    fun colourUsesClapsStyles() {
        val text = assertIs<Parsed.Exit>(parse(tree, listOf("-x"), Settings("tool", color = true))).text
        assertTrue(text.startsWith("\u001b[1m\u001b[31merror:\u001b[0m unexpected argument '\u001b[33m-x\u001b[0m' found"), text)
        val empty = assertIs<Parsed.Exit>(parse(tree, listOf("add", "--jobs"), Settings("tool", color = true))).text
        assertTrue("a value is required for '\u001b[33m--jobs <N>\u001b[0m' but none was supplied" in empty, empty)
    }
}

class TextTest {
    @Test
    fun wrapBreaksBeforeTheWordThatCrossesTheWidth() {
        assertEquals("aaa bbb\nccc", wrap("aaa bbb ccc", 7))
        assertEquals("aaa\nbbb", wrap("aaa\nbbb", 7))
        assertEquals("aaaaaaaaaa\nb", wrap("aaaaaaaaaa b", 4))
    }

    @Test
    fun docCommentsSplitAsClapsDeriveDoes() {
        assertEquals("One line" to null, docText("One line."))
        assertEquals("Wraps across lines" to null, docText("Wraps across\n  lines."))
        assertEquals("Keeps an ellipsis.." to null, docText("Keeps an ellipsis.."))
        assertEquals("First" to "First.\n\nSecond.", docText("First.\n\nSecond."))
    }

    @Test
    fun jaroMatchesStrsim() {
        assertEquals(0.9166666666666666, jaro("lst", "list"), 1e-12)
        assertEquals(listOf("pin", "i", "info"), didYouMean("infx", listOf("install", "i", "info", "pin")))
    }
}
