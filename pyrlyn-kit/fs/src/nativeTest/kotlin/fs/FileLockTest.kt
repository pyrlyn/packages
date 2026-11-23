package fs

import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.milliseconds

class FileLockTest {
    @Test
    fun aSecondOpenOfTheSameFileIsExcludedUntilTheFirstCloses(): Unit =
        withTempDir { dir ->
            val path = joinPath(dir, ".lock").toString()
            val first = assertNotNull(FileLock.tryExclusive(path))
            assertNull(FileLock.tryExclusive(path))
            first.close()
            FileLock.tryExclusive(path).let { assertNotNull(it).close() }
        }

    @Test
    fun exclusiveGivesUpAfterTheTimeout(): Unit =
        withTempDir { dir ->
            val path = joinPath(dir, ".lock").toString()
            FileLock.tryExclusive(path).let { assertNotNull(it) }.use {
                assertNull(FileLock.exclusive(path, 50.milliseconds, poll = 5.milliseconds))
            }
            FileLock.exclusive(path, 50.milliseconds).let { assertNotNull(it).close() }
        }

    @Test
    fun closeIsIdempotent(): Unit =
        withTempDir { dir ->
            val path = joinPath(dir, ".lock").toString()
            val lock = assertNotNull(FileLock.tryExclusive(path))
            lock.close()
            lock.close()
            FileLock.tryExclusive(path).let { assertNotNull(it).close() }
        }

    @Test
    fun aMissingDirectoryIsAnFsError(): Unit =
        withTempDir { dir ->
            val err = assertFailsWith<FsException> { FileLock.tryExclusive(joinPath(dir, "nope", ".lock").toString()) }
            kotlin.test.assertEquals(FsException.Kind.NotFound, err.kind)
        }
}
