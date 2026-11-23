@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package process

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.sizeOf
import kotlinx.cinterop.toKString
import kotlinx.cinterop.value
import platform.posix.fclose
import platform.posix.fgets
import platform.posix.fopen
import platform.posix.getenv
import platform.posix.getpid
import platform.posix.remove
import platform.windows.AssignProcessToJobObject
import platform.windows.BOOLVar
import platform.windows.CloseHandle
import platform.windows.CreateJobObjectW
import platform.windows.GetCurrentProcess
import platform.windows.IsProcessInJob
import platform.windows.JOBOBJECT_EXTENDED_LIMIT_INFORMATION
import platform.windows.JOB_OBJECT_LIMIT_BREAKAWAY_OK
import platform.windows.JobObjectExtendedLimitInformation
import platform.windows.OpenProcess
import platform.windows.PROCESS_QUERY_LIMITED_INFORMATION
import platform.windows.SetInformationJobObject
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

    /** A kit parent runs its child in a job that is ended when it closes; what that child detaches must not go with it. */
    @Test
    fun aDetachedChildLeavesTheJobItsParentRunsIn() {
        // Breakaway only, no kill on close: the test process stays in this job until it exits.
        val job = checkNotNull(CreateJobObjectW(null, null))
        memScoped {
            val limits = alloc<JOBOBJECT_EXTENDED_LIMIT_INFORMATION>()
            limits.BasicLimitInformation.LimitFlags = JOB_OBJECT_LIMIT_BREAKAWAY_OK.toUInt()
            val size = sizeOf<JOBOBJECT_EXTENDED_LIMIT_INFORMATION>().toUInt()
            assertTrue(SetInformationJobObject(job, JobObjectExtendedLimitInformation, limits.ptr, size) != 0)
        }
        assertTrue(AssignProcessToJobObject(job, GetCurrentProcess()) != 0)
        val pid = Command("cmd.exe", listOf("/d", "/c")).rawArg("ping -n 3 127.0.0.1 >NUL").spawnDetached()
        val child = checkNotNull(OpenProcess(PROCESS_QUERY_LIMITED_INFORMATION.toUInt(), 0, pid.toUInt()))
        try {
            memScoped {
                val inJob = alloc<BOOLVar>()
                assertTrue(IsProcessInJob(child, job, inJob.ptr) != 0)
                assertEquals(0, inJob.value, "the detached child is still in its parent's job")
            }
        } finally {
            CloseHandle(child)
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
