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
import platform.posix.usleep
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

class DetachedTest {
    /** Nobody waits on it, so what it wrote is the only sign it ran, with its variables and directory. */
    @Test
    fun aDetachedChildRunsOnItsOwn() {
        val file = "${getenv("TMPDIR")?.toKString()?.trimEnd('/') ?: "/tmp"}/kit-detached-${getpid()}"
        try {
            val pid =
                Command("sh", listOf("-c", "echo \"\$KIT_D \$PWD\" > \"\$KIT_FILE.part\" && mv \"\$KIT_FILE.part\" \"\$KIT_FILE\""))
                    .env("KIT_D", "ran")
                    .env("KIT_FILE", file)
                    .currentDir("/")
                    .spawnDetached()
            assertTrue(pid > 0)
            assertEquals("ran /", awaitLine(file))
        } finally {
            remove(file)
        }
    }

    @Test
    fun aMissingProgramFailsToStart() {
        val e = runCatching { Command("/nonexistent/kit-detached").spawnDetached() }.exceptionOrNull()
        assertTrue(e is ProcessException, "$e")
    }

    private fun awaitLine(file: String): String {
        val deadline = TimeSource.Monotonic.markNow() + 10.seconds
        while (!deadline.hasPassedNow()) {
            firstLine(file)?.let { return it }
            usleep(20_000u)
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
