@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package process

import kotlinx.cinterop.toKString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

class BoundedTest {
    private fun sh(script: String) = Command("sh", listOf("-c", script))

    private fun Command.run() = runBounded(10.seconds, 1 shl 20)

    @Test
    fun outputAndStatusComeBack() {
        val out = sh("echo hello; echo oops >&2; exit 3").run()
        assertEquals("hello\n", out.stdout.decodeToString())
        assertEquals("oops\n", out.stderr.decodeToString())
        assertEquals(ExitStatus.Exited(3), out.status)
        assertEquals("exit status: 3", out.status.toString())
    }

    @Test
    fun theChildGetsItsVariablesAndDirectoryOnTopOfOurs() {
        val out =
            sh("printf '%s|%s|%s' \"\$KIT_A\" \"\$PWD\" \"\${HOME:+home}\"")
                .env("KIT_A", "one")
                .currentDir("/")
                .run()
        assertEquals("one|/|home", out.stdout.decodeToString())
    }

    @Test
    fun aRemovedVariableIsNotInheritedAndASetOneWinsOverTheRemoval() {
        val out =
            sh("printf '%s|%s' \"\${HOME-unset}\" \"\$PATH\"")
                .envRemove("HOME")
                .envRemove("PATH")
                .env("PATH", "/kit")
                .run()
        assertEquals("unset|/kit", out.stdout.decodeToString())
    }

    @Test
    fun envVarsListsThisProcesssEnvironment() {
        val path = platform.posix.getenv("PATH")?.toKString()
        assertEquals(path, envVars().firstOrNull { it.first == "PATH" }?.second)
    }

    @Test
    fun stdinComesFromTheNamedFile() {
        val input = "${platform.posix.getenv("TMPDIR")?.toKString() ?: "/tmp"}/kit-process-stdin-${platform.posix.getpid()}"
        sh("printf 'typed ahead\\n' > \"\$0\"").let { Command(it.program, it.args + input) }.run()
        try {
            assertEquals(
                "typed ahead\n",
                sh("cat")
                    .stdinFrom(input)
                    .run()
                    .stdout
                    .decodeToString(),
            )
        } finally {
            platform.posix.unlink(input)
        }
    }

    @Test
    fun aProgramThatIsNotThereIsAnErrorNamingIt() {
        val e = assertFailsWith<ProcessException> { Command("kit-no-such-program").run() }
        assertTrue(e.detail.startsWith("could not run kit-no-such-program: "), e.detail)
        assertTrue("os error" in e.detail, e.detail)
    }

    @Test
    fun aDirectoryThatIsNotThereIsAnError() {
        assertFailsWith<ProcessException> { sh("true").currentDir("/kit-no-such-dir").run() }
    }

    @Test
    fun stdinIsEmpty() {
        val started = TimeSource.Monotonic.markNow()
        assertEquals("", sh("cat").run().stdout.decodeToString())
        assertTrue(started.elapsedNow() < 5.seconds)
    }

    @Test
    fun aChildThatHangsIsStoppedAtTheDeadline() {
        val started = TimeSource.Monotonic.markNow()
        val e = assertFailsWith<ProcessException> { sh("echo late >&2; sleep 30").runBounded(300.milliseconds, 1024) }
        assertTrue(started.elapsedNow() < 10.seconds, "waited the child out instead of stopping it")
        assertTrue("was stopped" in e.detail, e.detail)
        assertNotNull(e.stoppedPid)
        assertEquals("late\n", e.stderr.decodeToString())
    }

    @Test
    fun aChildThatCannotStartNamesNoStoppedPid() {
        val e = assertFailsWith<ProcessException> { Command("/nonexistent/ketch-kit-test", emptyList()).runBounded(5.seconds, 1024) }
        assertNull(e.stoppedPid, e.detail)
    }

    @Test
    fun aGrandchildHoldingThePipeDoesNotHoldTheRun() {
        val started = TimeSource.Monotonic.markNow()
        val out = sh("sleep 30 & echo hi").runBounded(5.seconds, 1024)
        assertEquals("hi\n", out.stdout.decodeToString())
        assertTrue(started.elapsedNow() < 5.seconds, "waited for the grandchild")
    }

    @Test
    fun outputStopsOneBytePastTheCap() {
        val out = sh("yes").runBounded(5.seconds, 1000)
        assertEquals(1001, out.stdout.size)
    }

    @Test
    fun aSignalIsReportedAsRustPrintsIt() {
        val out = sh("kill -9 \$\$").run()
        assertEquals("signal: 9 (SIGKILL)", out.status.toString())
        assertTrue(!out.status.success)
    }
}
