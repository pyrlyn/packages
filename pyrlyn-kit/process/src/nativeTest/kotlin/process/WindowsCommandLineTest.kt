package process

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** Rust std's `test_make_command_line` (1.99, sys/process/windows/tests.rs), and its rules for batch files and lookup. */
class WindowsCommandLineTest {
    @Test
    fun argumentsAreQuotedAsCommandLineToArgvWReadsThem() {
        assertEquals("\"prog\" aaa bbb ccc", commandLine("prog", listOf("aaa", "bbb", "ccc")))
        assertEquals("\"prog\" C:\\", commandLine("prog", listOf("C:\\")))
        assertEquals("\"prog\" 2slashes\\\\", commandLine("prog", listOf("2slashes\\\\")))
        assertEquals("\"prog\" \" C:\\\\\"", commandLine("prog", listOf(" C:\\")))
        assertEquals("\"prog\" \" 2slashes\\\\\\\\\"", commandLine("prog", listOf(" 2slashes\\\\")))
        assertEquals("\"C:\\Program Files\\blah\\blah.exe\" aaa", commandLine("C:\\Program Files\\blah\\blah.exe", listOf("aaa")))
        assertEquals("\"C:\\Program Files\\blah\\blah.exe\" aaa v*", commandLine("C:\\Program Files\\blah\\blah.exe", listOf("aaa", "v*")))
        assertEquals("\"C:\\Program Files\\test\" aa\\\"bb", commandLine("C:\\Program Files\\test", listOf("aa\"bb")))
        assertEquals("\"echo\" \"a b c\"", commandLine("echo", listOf("a b c")))
        assertEquals("\"echo\" \"\\\" \\\\\\\" \\\\\" \\", commandLine("echo", listOf("\" \\\" \\", "\\")))
        assertEquals("\"\u03c0\u042f\u97f3\u00e6\u221e\"", commandLine("\u03c0\u042f\u97f3\u00e6\u221e", emptyList()))
        assertEquals("\"prog\" \"\"", commandLine("prog", listOf("")))
    }

    /** Rust std's `test_raw_args`, with the raw arguments after the regular ones, the only order kit builds. */
    @Test
    fun rawArgumentsGoInExactlyAsWritten() {
        assertEquals(
            "\"quoted exe\" \"quote me\" optional-quotes quote me *not* \t\\ internal \\\"backslash-\"quote",
            commandLine("quoted exe", listOf("quote me", "optional-quotes"), listOf("quote me *not*", "\t\\", "internal \\\"backslash-\"quote")),
        )
        assertEquals("cmd.exe /e:ON /v:OFF /d /c \"\"x.cmd\" a echo %PATH% & dir\"", batCommandLine("x.cmd", listOf("a"), listOf("echo %PATH% & dir")))
        assertFailsWith<InvalidProgram> { commandLine("prog", emptyList(), listOf("a\u0000b")) }
    }

    @Test
    fun aBatchFileRunsThroughCmdWithEveryArgumentDefused() {
        assertEquals("cmd.exe /e:ON /v:OFF /d /c \"\"C:\\x.bat\" plain\"", batCommandLine("C:\\x.bat", listOf("plain")))
        assertEquals("cmd.exe /e:ON /v:OFF /d /c \"\"x.cmd\" \"a&b\" \"\" \"dir\\\\\"\"", batCommandLine("x.cmd", listOf("a&b", "", "dir\\")))
        assertEquals("cmd.exe /e:ON /v:OFF /d /c \"\"x.cmd\" \"say \"\"hi\"\"\"\"", batCommandLine("x.cmd", listOf("say \"hi\"")))
        assertEquals("cmd.exe /e:ON /v:OFF /d /c \"\"x.cmd\" \"%%cd:~,%PATH%%cd:~,%\"\"", batCommandLine("x.cmd", listOf("%PATH%")))
        assertEquals("cmd.exe /e:ON /v:OFF /d /c \"\"x.cmd\" C:\\a/b-c_d.e:f?@#\$*+\"", batCommandLine("x.cmd", listOf("C:\\a/b-c_d.e:f?@#\$*+")))
    }

    @Test
    fun aBatchFileRefusesWhatCmdWouldReadAsAnotherCommand() {
        assertFailsWith<InvalidProgram> { batCommandLine("x.bat", listOf("a\r\nrd /s /q C:\\")) }
        assertFailsWith<InvalidProgram> { batCommandLine("x\".bat", emptyList()) }
        assertFailsWith<InvalidProgram> { batCommandLine("dir\\", emptyList()) }
        assertFailsWith<InvalidProgram> { commandLine("prog", listOf("a\u0000b")) }
    }

    @Test
    fun aBareNameIsSearchedNeverInTheCurrentDirectory() {
        val there = setOf("C:\\child\\tool.exe", "C:\\app\\tool.exe", "C:\\Windows\\System32\\cmd.exe", "D:\\bin\\x.exe", "D:\\bin\\run.bat")
        val dirs = SearchDirs("C:\\child;", "C:\\app", "C:\\Windows\\System32", "C:\\Windows", "\"D:\\bin\";C:\\other")
        assertEquals("C:\\child\\tool.exe", resolveExe("tool", dirs, there::contains))
        assertEquals("C:\\app\\tool.exe", resolveExe("tool", SearchDirs(null, "C:\\app", null, null, null), there::contains))
        assertEquals("C:\\Windows\\System32\\cmd.exe", resolveExe("cmd", dirs, there::contains))
        assertEquals("D:\\bin\\x.exe", resolveExe("x", dirs, there::contains))
        // A name with an extension is looked up as written.
        assertEquals("D:\\bin\\run.bat", resolveExe("run.bat", dirs, there::contains))
        assertFailsWith<ProgramNotFound> { resolveExe("run", dirs, there::contains) }
    }

    @Test
    fun aPathIsTakenAsGivenWithExeTriedFirst() {
        val there = setOf("bin\\tool.exe")
        val none = SearchDirs(null, null, null, null, null)
        assertEquals("bin\\tool.exe", resolveExe("bin\\tool", none, there::contains))
        assertEquals("bin/other", resolveExe("bin/other", none, there::contains))
        assertEquals("C:\\x.EXE", resolveExe("C:\\x.EXE", none, there::contains))
        assertFailsWith<InvalidProgram> { resolveExe("", none, there::contains) }
        assertFailsWith<InvalidProgram> { resolveExe("bin\\", none, there::contains) }
    }

    @Test
    fun aScriptIsLaunchedByCmdFromTheSystemDirectory() {
        val dirs = SearchDirs(null, null, "C:\\Windows\\System32", null, "C:\\tools")
        val launch = windowsLaunch(Command("lint.CMD", listOf("a b")), dirs, { it == "C:\\tools\\lint.CMD" }, { it })
        assertEquals("C:\\Windows\\System32\\cmd.exe", launch.application)
        assertEquals("cmd.exe /e:ON /v:OFF /d /c \"\"C:\\tools\\lint.CMD\" \"a b\"\"", launch.commandLine)
        val exe = windowsLaunch(Command("git", listOf("a b")), dirs, { it == "C:\\tools\\git.exe" }, { it })
        assertEquals("C:\\tools\\git.exe", exe.application)
        assertEquals("\"git\" \"a b\"", exe.commandLine)
    }

    @Test
    fun pathListsSplitOnSemicolonsOutsideQuotes() {
        assertEquals(listOf("C:\\a", "C:\\b;c", "D:\\"), splitPaths("C:\\a;;\"C:\\b;c\";D:\\;"))
    }

    @Test
    fun aNameEndsAtTheFirstEqualsAfterItsFirstCharacter() {
        assertEquals("=C:", nameOf("=C:=C:\\work"))
        assertEquals("KEY", nameOf("KEY=a=b"))
        assertEquals("BARE", nameOf("BARE"))
    }
}
