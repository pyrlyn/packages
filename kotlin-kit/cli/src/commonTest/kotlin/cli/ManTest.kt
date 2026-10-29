package cli

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private val tree =
    command("tool") {
        about = "Do things."
        longAbout = "Do things with packages.\n\nThe second paragraph."
        version = "1.0"
        propagateVersion = true
        subcommandRequired = true
        flag("verbose") {
            short = 'v'
            global = true
            doc("Say more")
        }
        subcommand("add") {
            doc("Add a package's files")
            positional("pkg") {
                required = true
                doc("The package")
            }
            option("limit") {
                short = 'n'
                default = "20"
                value = ValueKind.Size
                doc("At most this many")
            }
            option("mode") {
                value = ValueKind.OneOf(listOf(PossibleValue("fast", "Skip checks"), PossibleValue("safe")))
            }
        }
        subcommand("secret") { hidden = true }
    }

private fun pages() = manPages(tree, "tool", ManHeader("2026-01-02", "tool 1.0", "tool manual"))

class ManTest {
    @Test
    fun everyVisibleCommandGetsAPageNamedForItsDisplayName() {
        assertEquals(listOf("tool.1", "tool-add.1"), pages().map { it.fileName })
    }

    @Test
    fun theRootPageCarriesTheHeaderLongAboutAndSubcommands() {
        val roff = pages().first().roff
        assertTrue(roff.startsWith(".ie \\n(.g .ds Aq \\(aq\n.el .ds Aq '\n.TH TOOL 1 2026-01-02 \"tool 1.0\" \"tool manual\"\n"), roff)
        assertTrue(roff.contains(".SH NAME\ntool \\- Do things.\n"), roff)
        assertTrue(roff.contains("\\fBtool\\fR [\\fB\\-v\\fR|\\fB\\-\\-verbose\\fR] [\\fB\\-h\\fR|\\fB\\-\\-help\\fR] [\\fB\\-V\\fR|\\fB\\-\\-version\\fR] <\\fIsubcommands\\fR>\n"), roff)
        assertTrue(roff.contains(".SH DESCRIPTION\nDo things with packages.\n.PP\nThe second paragraph.\n"), roff)
        assertTrue(roff.contains(".SH SUBCOMMANDS\n.TP\ntool\\-add(1)\nAdd a package\\*(Aqs files\n.SH VERSION\nv1.0\n"), roff)
    }

    @Test
    fun aSubcommandPageListsDefaultsValuesAndPositionals() {
        val roff = pages().last().roff
        assertTrue(roff.contains(".TP\n\\fB\\-n\\fR, \\fB\\-\\-limit\\fR \\fI<LIMIT>\\fR [default: 20]\nAt most this many\n"), roff)
        assertTrue(
            roff.contains(
                ".TP\n\\fB\\-\\-mode\\fR \\fI<MODE>\\fR\n\n.br\n\\fIPossible values:\\fR\n.RS 14\n.IP \\(bu 2\nfast: Skip checks\n.IP \\(bu 2\nsafe\n.RE\n",
            ),
            roff,
        )
        assertTrue(roff.contains("[\\fB\\-\\-mode\\fR] [\\fB\\-v\\fR|\\fB\\-\\-verbose\\fR] [\\fB\\-h\\fR|\\fB\\-\\-help\\fR] [\\fB\\-V\\fR|\\fB\\-\\-version\\fR] <\\fIPKG\\fR> \n"), roff)
        assertTrue(roff.contains(".TP\n<\\fIPKG\\fR>\nThe package\n.SH VERSION\nv1.0\n"), roff)
    }
}
