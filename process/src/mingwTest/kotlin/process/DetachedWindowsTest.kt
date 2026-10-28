@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package process

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.toKString
import platform.posix.fclose
import platform.posix.fgets
import platform.posix.fopen
import platform.posix.getenv
import platform.posix.getpid
import platform.posix.remove
import platform.windows.Sleep
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

class DetachedWindowsTest {
    /** Nobody waits on it, so what it wrote is the only sign it ran, with its variables. */
    @Test
    fun aDetachedChildRunsOnItsOwn() {
        val file = "${checkNotNull(getenv("TEMP")).toKString().trimEnd('\\')}\\kit-detached-${getpid()}"
        try {
            // cmd parses its own command line, so the redirection goes in as written.
            val pid =
                Command("cmd.exe", listOf("/d", "/c"))
                    .rawArg("echo %KIT_D%>\"%KIT_FILE%.part\" && move /y \"%KIT_FILE%.part\" \"%KIT_FILE%\" >NUL")
                    .env("KIT_D", "ran")
                    .env("KIT_FILE", file)
                    .spawnDetached()
            assertTrue(pid > 0)
            assertEquals("ran", awaitLine(file))
        } finally {
            remove(file)
        }
    }

    @Test
    fun aMissingProgramFailsToStart() {
        val e = runCatching { Command("C:\\nonexistent\\kit-detached.exe").spawnDetached() }.exceptionOrNull()
        assertTrue(e is ProcessException, "$e")
    }

    private fun awaitLine(file: String): String {
        val deadline = TimeSource.Monotonic.markNow() + 10.seconds
        while (!deadline.hasPassedNow()) {
            firstLine(file)?.let { return it }
            Sleep(20u)
        }
        fail("$file never appeared")
    }

    private fun firstLine(file: String): String? {
        val f = fopen(file, "r") ?: return null
        try {
            return memScoped {
                val buf = allocArray<ByteVar>(256)
                fgets(buf, 256, f)?.toKString()?.trimEnd()
            }
        } finally {
            fclose(f)
        }
    }
}
