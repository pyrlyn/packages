package process

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * The unix BoundedTest and InteractiveTest with cmd.exe for sh; `ping` to the loopback address stands in for `sleep`.
 * No script quotes a word: cmd does not read the `\"` a quoted argument escapes its quotes with.
 */
class BoundedWindowsTest {
    private fun cmd(script: String) = Command("cmd", listOf("/d", "/c", script))

    private fun Command.run() = runBounded(10.seconds, 1 shl 20)

    private fun sleep(seconds: Int) = "ping -n ${seconds + 1} 127.0.0.1 >nul"

    @Test
    fun outputAndStatusComeBack() {
        val out = cmd("echo hello& echo oops 1>&2& exit /b 3").run()
        assertEquals("hello\r\n", out.stdout.decodeToString())
        assertEquals("oops \r\n", out.stderr.decodeToString())
        assertEquals(ExitStatus.Exited(3), out.status)
        assertEquals("exit code: 3", out.status.toString())
    }

    @Test
    fun anNtStatusPrintsInHexAsRustDoes() {
        val out = cmd("exit /b -1073741819").run()
        assertEquals("exit code: 0xc0000005", out.status.toString())
        assertTrue(!out.status.success)
    }

    @Test
    fun theChildGetsItsVariablesAndDirectoryOnTopOfOurs() {
        val out = cmd("echo %KIT_A%;%CD%").env("KIT_A", "one").currentDir("C:\\").run()
        assertEquals("one;C:\\\r\n", out.stdout.decodeToString())
    }

    @Test
    fun aVariableIsReplacedWhateverItsCaseAndARemovedOneIsGone() {
        val out =
            cmd("echo %PATH%;%USERPROFILE%")
                .envRemove("USERPROFILE")
                .env("Path", "C:\\kit")
                .run()
        assertEquals("C:\\kit;%USERPROFILE%\r\n", out.stdout.decodeToString())
    }

    @Test
    fun envVarsListsThisProcesssEnvironment() {
        val path = envVars().firstOrNull { envKey(it.first) == "PATH" }?.second
        assertTrue(!path.isNullOrEmpty())
    }

    @Test
    fun stdinComesFromTheNamedFile() {
        val input = "${envVars().first { envKey(it.first) == "TEMP" }.second}\\kit-process-stdin-${TimeSource.Monotonic.markNow().hashCode()}"
        cmd("echo typed ahead>$input").run()
        try {
            assertEquals(
                "typed ahead\r\n",
                cmd("sort")
                    .stdinFrom(input)
                    .run()
                    .stdout
                    .decodeToString(),
            )
        } finally {
            cmd("del $input").run()
        }
    }

    @Test
    fun aProgramThatIsNotThereIsAnErrorNamingIt() {
        val e = assertFailsWith<ProcessException> { Command("kit-no-such-program").run() }
        assertEquals("could not run kit-no-such-program: program not found", e.detail)
    }

    @Test
    fun aDirectoryThatIsNotThereIsAnError() {
        val e = assertFailsWith<ProcessException> { cmd("exit /b 0").currentDir("C:\\kit-no-such-dir").run() }
        assertTrue("os error" in e.detail, e.detail)
    }

    @Test
    fun stdinIsEmpty() {
        val started = TimeSource.Monotonic.markNow()
        assertEquals("", cmd("sort").run().stdout.decodeToString())
        assertTrue(started.elapsedNow() < 5.seconds)
    }

    @Test
    fun aChildThatHangsIsStoppedAtTheDeadline() {
        val started = TimeSource.Monotonic.markNow()
        val e = assertFailsWith<ProcessException> { cmd("echo late 1>&2& ${sleep(30)}").runBounded(2.seconds, 1024) }
        assertTrue(started.elapsedNow() < 10.seconds, "waited the child out instead of stopping it")
        assertTrue("was stopped" in e.detail, e.detail)
        assertNotNull(e.stoppedPid)
        assertEquals("late \r\n", e.stderr.decodeToString())
    }

    @Test
    fun aChildThatCannotStartNamesNoStoppedPid() {
        val e = assertFailsWith<ProcessException> { Command("C:\\nonexistent\\ketch-kit-test.exe").runBounded(5.seconds, 1024) }
        assertNull(e.stoppedPid, e.detail)
    }

    @Test
    fun aGrandchildHoldingThePipeDoesNotHoldTheRun() {
        val started = TimeSource.Monotonic.markNow()
        val out = cmd("start /b ${sleep(30)}& echo hi").runBounded(5.seconds, 1024)
        assertEquals("hi\r\n", out.stdout.decodeToString())
        assertTrue(started.elapsedNow() < 5.seconds, "waited for the grandchild")
    }

    @Test
    fun outputStopsOneBytePastTheCap() {
        val out = cmd("for /l %i in (0,0,1) do @echo y").runBounded(5.seconds, 1000)
        assertEquals(1001, out.stdout.size)
    }

    @Test
    fun aBatchFileGetsItsArgumentsAsTextAndRunsNothingElse() {
        val dir = envVars().first { envKey(it.first) == "TEMP" }.second
        val script = "$dir\\kit-args-${TimeSource.Monotonic.markNow().hashCode()}.cmd"
        cmd("(echo @echo [%1] [%2])>$script").run()
        try {
            val out = Command(script, listOf("a&echo pwned", "%PATH%")).runBounded(10.seconds, 1024)
            assertEquals("[\"a&echo pwned\"] [\"%PATH%\"]\r\n", out.stdout.decodeToString())
        } finally {
            cmd("del $script").run()
        }
    }

    @Test
    fun statusIsHowTheChildEnded() {
        assertEquals(ExitStatus.Exited(0), cmd("exit /b 0").status())
        assertEquals(ExitStatus.Exited(4), cmd("exit /b %KIT_CODE%").env("KIT_CODE", "4").status())
    }

    @Test
    fun aProgramThatIsNotThereCannotBeRun() {
        val e = assertFailsWith<ProcessException> { Command("kit-no-such-program").status() }
        assertTrue(e.detail.startsWith("could not run kit-no-such-program: "), e.detail)
    }

    @Test
    fun theTestBinaryKnowsWhereItIs() {
        val exe = assertNotNull(currentExe())
        assertTrue(exe.length > 2 && exe[1] == ':' || exe.startsWith("\\\\"), exe)
        assertTrue(exe.endsWith(".exe"), exe)
    }
}
