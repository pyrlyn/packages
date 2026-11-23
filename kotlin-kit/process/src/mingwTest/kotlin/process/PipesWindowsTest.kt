@file:OptIn(ObsoleteWorkersApi::class)

package process

import kotlin.native.concurrent.ObsoleteWorkersApi
import kotlin.native.concurrent.TransferMode
import kotlin.native.concurrent.Worker
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/** The unix PipesTest with cmd.exe; `sort` stands in for `cat` and `ping` to the loopback address for `sleep`. */
class PipesWindowsTest {
    private fun cmd(script: String) = Command("cmd", listOf("/d", "/c", script))

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
        cmd("echo started 1>&2& sort").spawnPipes().use { child ->
            child.stdin.write("b\r\na\r\n".encodeToByteArray())
            child.stdin.close()
            assertEquals("a\r\nb\r\n", child.stdout.readAll())
            assertEquals("started \r\n", child.stderr.readAll())
            assertEquals(ExitStatus.Exited(0), child.waitFor(5.seconds))
        }
    }

    @Test
    fun theExitStatusIsKeptPastClose() {
        val child = cmd("exit /b 3").spawnPipes()
        assertEquals(-1, child.stdout.read(ByteArray(8)))
        assertEquals(ExitStatus.Exited(3), child.waitFor(5.seconds))
        child.close()
        assertEquals(ExitStatus.Exited(3), child.exitStatus())
    }

    @Test
    fun writingToAChildThatHasEndedFails() {
        cmd("exit /b 0").spawnPipes().use { child ->
            child.waitFor(5.seconds)
            val e = assertFailsWith<ProcessException> { child.stdin.write(ByteArray(4 shl 20)) }
            assertTrue("stopped reading" in e.detail, e.detail)
        }
    }

    @Test
    fun killEndsABlockedReadAndTheTree() {
        cmd("ping -n 31 127.0.0.1 >nul").spawnPipes().use { child ->
            val killer = Worker.start()
            killer.execute(TransferMode.SAFE, { child }) {
                platform.windows.Sleep(200u)
                it.kill()
            }
            assertEquals(-1, child.stdout.read(ByteArray(8)))
            killer.requestTermination().result
            assertEquals(ExitStatus.Exited(1), child.waitFor(5.seconds))
        }
    }

    @Test
    fun closeFromAnotherThreadEndsABlockedRead() {
        val child = cmd("ping -n 31 127.0.0.1 >nul").spawnPipes()
        val reader = Worker.start()
        val read = reader.execute(TransferMode.SAFE, { child }) { it.stdout.read(ByteArray(8)) }
        platform.windows.Sleep(200u)
        child.close()
        assertEquals(-1, read.result)
        reader.requestTermination().result
    }
}
