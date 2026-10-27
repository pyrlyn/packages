package fs

import kotlinx.io.files.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class EntriesTest {
    /** A link's own bits are not its target's: a tree digest that followed it would miss a retargeted link. */
    @Test
    fun linkModeReadsTheLinkNotItsTarget(): Unit =
        withTempDir { dir ->
            val file = Path(dir, "f")
            createNew(file, "x".encodeToByteArray())
            setMode(file, "600".toInt(8))
            symlink(file, Path(dir, "l"))
            assertEquals("600".toInt(8), linkMode(file))
            assertEquals("600".toInt(8), mode(Path(dir, "l")))
            assertTrue(linkMode(Path(dir, "l")) != "600".toInt(8))
            assertEquals(null, linkMode(Path(dir, "missing")))
        }

    @Test
    fun createNewWritesOnceAndThenRefuses(): Unit =
        withTempDir { dir ->
            val file = Path(dir, "f")
            createNew(file, "1".encodeToByteArray())
            val err = assertFailsWith<FsException> { createNew(file, "2".encodeToByteArray()) }
            assertEquals(FsException.Kind.AlreadyExists, err.kind)
            assertEquals("1", readText(file))
        }

    @Test
    fun aHardLinkSharesTheFileAndRefusesAnExistingName(): Unit =
        withTempDir { dir ->
            val a = Path(dir, "a")
            val b = Path(dir, "b")
            createNew(a, "x".encodeToByteArray())
            hardLink(a, b)
            assertEquals("x", readText(b))
            assertEquals(FsException.Kind.AlreadyExists, assertFailsWith<FsException> { hardLink(a, b) }.kind)
        }

    @Test
    fun removeFileSaysWhenNothingWasThere(): Unit =
        withTempDir { dir ->
            val file = Path(dir, "f")
            createNew(file, ByteArray(0))
            removeFile(file)
            assertEquals(FsException.Kind.NotFound, assertFailsWith<FsException> { removeFile(file) }.kind)
        }

    @Test
    fun aModificationTimeCanBeReadAndMovedBack(): Unit =
        withTempDir { dir ->
            val file = Path(dir, "f")
            createNew(file, ByteArray(0))
            assertTrue(modifiedAgo(file) < 1.minutes)
            setModifiedAgo(file, 20.seconds)
            assertTrue(modifiedAgo(file) in 20.seconds..1.minutes, modifiedAgo(file).toString())
            assertEquals(FsException.Kind.NotFound, assertFailsWith<FsException> { modifiedAgo(Path(dir, "nope")) }.kind)
        }

    @Test
    fun timesCanBeSetToAMomentLongAgo(): Unit =
        withTempDir { dir ->
            val file = Path(dir, "f")
            file.write("x")
            setTimes(file, 1)
            val age = modifiedAgo(file)
            assertTrue(age > (50 * 365).days, "$age")
            assertEquals(FsException.Kind.NotFound, assertFailsWith<FsException> { setTimes(Path(dir, "nope"), 1) }.kind)
        }

    @Test
    fun aSymlinkKeepsItsTargetAsWritten(): Unit =
        withTempDir { dir ->
            val link = Path(dir, "l")
            symlink(Path("../elsewhere"), link)
            assertEquals(Path("../elsewhere"), readLink(link))
            assertEquals(EntryKind.Symlink, entryKind(link))
            assertEquals(FsException.Kind.AlreadyExists, assertFailsWith<FsException> { symlink(Path("x"), link) }.kind)
        }

    @Test
    fun createDirMakesOneLevelAndRefusesWhatIsThere(): Unit =
        withTempDir { dir ->
            val sub = Path(dir, "a")
            createDir(sub)
            assertTrue(isDir(sub))
            assertEquals(FsException.Kind.AlreadyExists, assertFailsWith<FsException> { createDir(sub) }.kind)
            assertEquals(FsException.Kind.NotFound, assertFailsWith<FsException> { createDir(Path(dir, "b/c")) }.kind)
        }

    @Test
    fun setModeSetsTheBitsAsGiven(): Unit =
        withTempDir { dir ->
            val file = Path(dir, "f")
            createNew(file, ByteArray(0))
            setMode(file, 0b111_101_000)
            assertEquals(0b111_101_000, mode(file))
            assertEquals(FsException.Kind.NotFound, assertFailsWith<FsException> { setMode(Path(dir, "nope"), 0) }.kind)
        }
}
