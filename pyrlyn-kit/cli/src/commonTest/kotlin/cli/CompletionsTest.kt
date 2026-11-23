package cli

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// The full scripts are checked byte for byte against clap_complete's output by the programs that use this module;
// these pin the rules each generator follows on a tree small enough to read.
private val tree =
    command("tool") {
        version = "1.0"
        propagateVersion = true
        option("root") {
            valueName = "DIR"
            value = ValueKind.NonEmpty
            global = true
            doc("Root (default: ~/.tool)")
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
            doc("Add the package's files")
            alias("a")
            positional("pkg") {
                multiple = true
                doc("Packages: `owner/repo`")
            }
            option("jobs") {
                short = 'j'
                valueName = "N"
                doc("Work at once")
            }
        }
        subcommand("list") {
            doc("List packages")
            positional("mode") {
                value = ValueKind.OneOf(listOf(PossibleValue("local", "Installed ones"), PossibleValue("remote", "The registry")))
            }
        }
        subcommand("shell") {
            doc("Pick a shell")
            option("shell") {
                multiple = true
                value = ValueKind.OneOf(listOf(PossibleValue("bash"), PossibleValue("zsh")))
            }
        }
    }

private fun script(shell: CompletionShell) = completions(tree, shell, "tool")

private fun assertHas(
    script: String,
    part: String,
) = assertTrue(script.contains(part), "missing:\n$part\nin:\n$script")

class CompletionsTest {
    @Test
    fun valueOptionsListLongsThenShortsWithInheritedGlobals() {
        assertEquals(listOf("--root"), valueOptions(tree))
        assertEquals(listOf("--jobs", "-j", "--root"), valueOptions(tree, "add"))
        assertEquals(emptyList(), valueOptions(tree, "nope"))
    }

    @Test
    fun zshEscapesHelpAndMarksConflictsRepeatsAndValues() {
        val zsh = script(CompletionShell.Zsh)
        assertTrue(zsh.startsWith("#compdef tool\n"))
        assertHas(zsh, "'--root=[Root (default\\: ~/.tool)]:DIR:_files' \\\n")
        assertHas(zsh, "'(-q --quiet)-v[Say more]' \\\n")
        assertHas(zsh, "'-j+[Work at once]:N:_default' \\\n'--jobs=[Work at once]:N:_default' \\\n")
        assertHas(zsh, "'*::pkg -- Packages\\: `owner/repo`:_default' \\\n")
        assertHas(zsh, "'::mode:((local\\:\"Installed ones\"\nremote\\:\"The registry\"))' \\\n")
        assertHas(zsh, "'*--shell=[]:SHELL:(bash zsh)' \\\n")
        assertHas(zsh, "'add:Add the package'\\''s files' \\\n'a:Add the package'\\''s files' \\\n")
        assertHas(zsh, "(( \$+functions[_tool__subcmd__add_commands] )) ||\n")
        assertTrue(zsh.endsWith("    compdef _tool tool\nfi\n"))
    }

    @Test
    fun fishCompletesPathsAsFilesAndOptionValues() {
        val fish = script(CompletionShell.Fish)
        assertHas(fish, "-l root -d 'Root (default: ~/.tool)' -r -F")
        assertHas(fish, "-l shell -r -f -a \"bash\\t''\nzsh\\t''\"")
        assertHas(fish, "__fish_tool_using_subcommand a\"")
    }

    @Test
    fun bashListsWordsAndValuesPerCommand() {
        val bash = script(CompletionShell.Bash)
        assertHas(bash, "            tool,add)\n                cmd=\"tool__subcmd__add\"\n")
        assertHas(bash, "            tool,a)\n                cmd=\"tool__subcmd__add\"\n")
        assertHas(bash, "opts=\"-v -q -h -V --root --verbose --quiet --help --version local remote\"")
        assertHas(bash, "COMPREPLY=(\$(compgen -W \"bash zsh\" -- \"\${cur}\"))")
        assertTrue(bash.endsWith("complete -F _tool -o bashdefault -o default tool\nfi\n"))
    }

    @Test
    fun powerShellAndElvishNameEveryCommandAndAlias() {
        val ps = script(CompletionShell.PowerShell)
        assertTrue(ps.startsWith("\nusing namespace System.Management.Automation"), ps)
        assertHas(ps, "'tool;add'")
        assertHas(ps, "[CompletionResult]::new('-V', '-V ', [CompletionResultType]::ParameterName, 'Print version')")
        val elvish = script(CompletionShell.Elvish)
        assertHas(elvish, "&'tool;a'= {")
    }
}
