@file:OptIn(ObsoleteWorkersApi::class)

package process

import kotlin.native.concurrent.ObsoleteWorkersApi
import kotlin.native.concurrent.TransferMode
import kotlin.native.concurrent.Worker
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

class PipesTest {
    private fun sh(script: String) = Command("sh", listOf("-c", script))

    /** Everything until end of file. */
    private fun ChildOutput.readAll(): String {
        val buf = ByteArray(7)
        val all = StringBuilder()
        while (true) {
            val n = read(buf)
            if (n < 0) return all.toString()
            all.append(buf.decodeToString(0, n))
        }
    }

    @Test
    fun theChildReadsWhatIsWrittenAndAnswersOnBothOutputs() {
        sh("echo started >&2; cat").spawnPipes().use { child ->
            child.stdin.write("hello\n".encodeToByteArray())
            val buf = ByteArray(64)
            val n = child.stdout.read(buf)
            assertEquals("hello\n", buf.decodeToString(0, n))
            child.stdin.write("xxbye\nxx".encodeToByteArray(), offset = 2, count = 4)
            child.stdin.close()
            assertEquals("bye\n", child.stdout.readAll())
            assertEquals("started\n", child.stderr.readAll())
            assertEquals(ExitStatus.Exited(0), child.waitFor(5.seconds))
            assertTrue(child.pid > 0)
        }
    }

    @Test
    fun theExitStatusIsKeptPastClose() {
        val child = sh("exit 3").spawnPipes()
        assertEquals(-1, child.stdout.read(ByteArray(8)))
        assertEquals(ExitStatus.Exited(3), child.waitFor(5.seconds))
        child.close()
        assertEquals(ExitStatus.Exited(3), child.exitStatus())
    }

    @Test
    fun writingToAChildThatHasEndedFailsInsteadOfEndingThisProcess() {
        sh("exec 0<&-; exit 0").spawnPipes().use { child ->
            child.waitFor(5.seconds)
            // A pipe buffer is at most a few hundred KiB, so this cannot all fit while nobody reads.
            val big = ByteArray(4 shl 20)
            val e = assertFailsWith<ProcessException> { child.stdin.write(big) }
            assertTrue("stopped reading" in e.detail, e.detail)
        }
    }

    @Test
    fun killEndsABlockedRead() {
        sh("sleep 30").spawnPipes().use { child ->
            val killer = Worker.start()
            val started = TimeSource.Monotonic.markNow()
            killer.execute(TransferMode.SAFE, { child }) {
                platform.posix.usleep(200_000u)
                it.kill()
            }
            assertEquals(-1, child.stdout.read(ByteArray(8)))
            assertTrue(started.elapsedNow() < 10.seconds)
            killer.requestTermination().result
            assertTrue(child.waitFor(5.seconds) is ExitStatus.Signaled)
        }
    }

    @Test
    fun closeFromAnotherThreadEndsABlockedRead() {
        val child = sh("sleep 30").spawnPipes()
        val reader = Worker.start()
        val read = reader.execute(TransferMode.SAFE, { child }) { it.stdout.read(ByteArray(8)) }
        platform.posix.usleep(200_000u)
        child.close()
        assertEquals(-1, read.result)
        reader.requestTermination().result
        assertNull(child.exitStatus())
        assertFailsWith<ProcessException> { child.stdin.write(byteArrayOf(1)) }
    }

    @Test
    fun aProgramThatIsNotThereCannotStart() {
        val e = assertFailsWith<ProcessException> { Command("kit-no-such-program").spawnPipes() }
        assertTrue("could not run kit-no-such-program" in e.detail, e.detail)
    }

    @Test
    fun aStdinFileDoesNotApply() {
        assertFailsWith<IllegalArgumentException> { Command("cat").stdinFrom("/dev/null").spawnPipes() }
    }
}
