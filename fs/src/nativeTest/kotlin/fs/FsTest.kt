@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package fs

import kotlinx.cinterop.convert
import kotlinx.io.files.Path
import platform.posix.chmod
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FsTest {
    @Test
    fun aWrappedErrorKeepsItsKindOsCodeAndPath() =
        withTempDir { dir ->
            val missing = Path(dir, "nope")
            val err = assertFailsWith<FsException> { readText(missing) }
            assertEquals(FsException.Kind.NotFound, err.kind)
            assertEquals("$missing: No such file or directory (os error 2)", err.message)
            assertEquals(missing, err.path)
            val bare = err.withoutPath()
            assertNull(bare.path)
            assertEquals(err.errno, bare.errno)
            assertEquals("No such file or directory (os error 2)", bare.message)
        }

    @Test
    fun aMissingFileReadsAsEmptyAndADirectoryIsAnError() =
        withTempDir { dir ->
            assertEquals("", textOrEmpty(Path(dir, "nope")))
            assertNull(bytesIfExists(Path(dir, "nope")))
            Path(dir, "a").write("x")
            assertEquals("x", textOrEmpty(Path(dir, "a")))
            assertContentEquals("x".encodeToByteArray(), bytesIfExists(Path(dir, "a")))
            val err = assertFailsWith<FsException> { textOrEmpty(dir) }
            assertTrue(dir.toString() in err.message!!, err.message)
        }

    @Test
    fun createsAndReplacesWithoutLeftovers() =
        withTempDir { dir ->
            val f = Path(dir, "state.json")
            f.write("one")
            assertEquals("one", f.text())
            f.write("two")
            assertEquals("two", f.text())
            assertEquals(listOf("state.json"), entries(dir))
        }

    @Test
    fun missingParentFailsUnlessAskedToCreateIt() =
        withTempDir { dir ->
            val f = Path(dir, "x/y/z.toml")
            assertEquals(FsException.Kind.NotFound, assertFailsWith<FsException> { f.write("v") }.kind)
            WriteOptions(createParent = true).write(f, "v".encodeToByteArray())
            assertEquals("v", f.text())
        }

    @Test
    fun pathWithoutAFileNameIsRefused() {
        val err = assertFailsWith<FsException> { atomic(Path(".."), "v".encodeToByteArray()) }
        assertEquals(FsException.Kind.InvalidInput, err.kind)
    }

    @Test
    fun notDurableStillReplaces() =
        withTempDir { dir ->
            val f = Path(dir, "c")
            WriteOptions(durable = false).write(f, "fast".encodeToByteArray())
            assertEquals("fast", f.text())
        }

    @Test
    fun ifChangedCreatesTheParentAndSkipsIdenticalBytes() =
        withTempDir { dir ->
            val f = Path(dir, "sub/a.txt")
            assertTrue(ifChanged(f, "x".encodeToByteArray()))
            assertFalse(ifChanged(f, "x".encodeToByteArray()))
            assertTrue(ifChanged(f, "y".encodeToByteArray()))
            assertEquals("y", f.text())
        }

    @Test
    fun anErrorNamesTheFile() =
        withTempDir { dir ->
            val err = assertFailsWith<FsException> { Path(dir, "gone/z.toml").write("v") }
            assertEquals(FsException.Kind.NotFound, err.kind)
            assertTrue("z.toml" in err.message!!, err.message)
        }

    @Test
    fun keepsTheOldMode() =
        withTempDir { dir ->
            val f = Path(dir, "secret")
            f.write("old")
            chmod(f.toString(), "600".toInt(8).convert())
            f.write("new")
            assertEquals("600".toInt(8), mode(f))
        }

    @Test
    fun modeAppliesToANewFileAndYieldsToAnExistingOne() =
        withTempDir { dir ->
            val f = Path(dir, "key")
            val private = WriteOptions(mode = "600".toInt(8))
            private.write(f, "k".encodeToByteArray())
            assertEquals("600".toInt(8), mode(f))
            chmod(f.toString(), "640".toInt(8).convert())
            private.write(f, "k2".encodeToByteArray())
            assertEquals("640".toInt(8), mode(f))
            private.copy(preservePermissions = false).write(f, "k3".encodeToByteArray())
            assertEquals("600".toInt(8), mode(f))
        }

    @Test
    fun writesThroughASymlinkAndKeepsIt() =
        withTempDir { dir ->
            val real = Path(dir, "real.json")
            val linked = Path(dir, "link.json")
            real.write("old")
            link("real.json", linked)
            linked.write("new")
            assertEquals(EntryKind.Symlink, entryKind(linked))
            assertEquals("new", real.text())
        }

    @Test
    fun createsTheTargetOfADanglingSymlink() =
        withTempDir { dir ->
            val linked = Path(dir, "link")
            link("missing", linked)
            linked.write("made")
            assertEquals(EntryKind.Symlink, entryKind(linked))
            assertEquals("made", Path(dir, "missing").text())
        }

    @Test
    fun notFollowingReplacesTheLinkItself() =
        withTempDir { dir ->
            val real = Path(dir, "real")
            val linked = Path(dir, "link")
            real.write("old")
            link(real.toString(), linked)
            WriteOptions(followSymlinks = false).write(linked, "new".encodeToByteArray())
            assertEquals(EntryKind.File, entryKind(linked))
            assertEquals("old", real.text())
        }

    @Test
    fun symlinkCycleEnds() =
        withTempDir { dir ->
            val a = Path(dir, "a")
            val b = Path(dir, "b")
            link(b.toString(), a)
            link(a.toString(), b)
            assertFailsWith<FsException> { a.write("v") }
            assertEquals(listOf("a", "b"), entries(dir))
        }

    @Test
    fun linksAreKeptOrFollowedAsAsked() =
        withTempDir { dir ->
            val src = Path(dir, "src")
            createDirAll(Path(src, "real"))
            Path(src, "real/f").write("f")
            link("real", Path(src, "link"))
            copyTree(src, Path(dir, "kept"), Links.Keep)
            assertEquals(EntryKind.Symlink, entryKind(Path(dir, "kept/link")))
            copyTree(src, Path(dir, "followed"), Links.Follow)
            assertEquals(EntryKind.Dir, entryKind(Path(dir, "followed/link")))
            assertEquals("f", Path(dir, "followed/link/f").text())
            copyTree(src, Path(dir, "skipped"), Links.Skip)
            assertNull(entryKind(Path(dir, "skipped/link")))
            assertEquals("f", Path(dir, "skipped/real/f").text())
        }

    @Test
    fun copyTreeKeepsAFilesMode() =
        withTempDir { dir ->
            val src = Path(dir, "src")
            createDirAll(Path(src, "bin"))
            Path(src, "bin/tool").write("#!/bin/sh\n")
            chmod(Path(src, "bin/tool").toString(), "755".toInt(8).convert())
            copyTree(src, Path(dir, "dest"), Links.Follow)
            assertEquals("755".toInt(8), mode(Path(dir, "dest/bin/tool")))
            assertEquals("#!/bin/sh\n", Path(dir, "dest/bin/tool").text())
        }

    @Test
    fun removeAnyTakesAFileATreeAndNothing() =
        withTempDir { dir ->
            Path(dir, "f").write("f")
            createDirAll(Path(dir, "d/e"))
            removeAny(Path(dir, "f"))
            removeAny(Path(dir, "d"))
            removeAny(Path(dir, "never"))
            assertNull(entryKind(Path(dir, "f")))
            assertNull(entryKind(Path(dir, "d")))
        }

    @Test
    fun removeAnyNeverFollowsALinkIntoItsTarget() =
        withTempDir { dir ->
            createDirAll(Path(dir, "keep"))
            Path(dir, "keep/f").write("f")
            link(Path(dir, "keep").toString(), Path(dir, "link"))
            removeAny(Path(dir, "link"))
            assertEquals("f", Path(dir, "keep/f").text())
        }

    @Test
    fun canonResolvesLinksAndLeavesAMissingPathAlone() =
        withTempDir { dir ->
            createDirAll(Path(dir, "real"))
            link("real", Path(dir, "link"))
            assertEquals(canonicalize(Path(dir, "real")), canonicalize(Path(dir, "link")))
            val missing = Path(dir, "missing")
            assertEquals(missing, canon(missing))
            assertEquals(FsException.Kind.NotFound, assertFailsWith<FsException> { canonicalize(missing) }.kind)
        }
}
