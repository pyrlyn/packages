package fs

import kotlinx.io.files.Path
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
            val missing = joinPath(dir, "nope")
            val err = assertFailsWith<FsException> { readText(missing) }
            assertEquals(FsException.Kind.NotFound, err.kind)
            assertEquals("$missing: $NOT_FOUND", err.message)
            assertEquals(missing, err.path)
            val bare = err.withoutPath()
            assertNull(bare.path)
            assertEquals(err.errno, bare.errno)
            assertEquals(NOT_FOUND, bare.message)
        }

    @Test
    fun aMissingFileReadsAsEmptyAndADirectoryIsAnError() =
        withTempDir { dir ->
            assertEquals("", textOrEmpty(joinPath(dir, "nope")))
            assertNull(bytesIfExists(joinPath(dir, "nope")))
            joinPath(dir, "a").write("x")
            assertEquals("x", textOrEmpty(joinPath(dir, "a")))
            assertContentEquals("x".encodeToByteArray(), bytesIfExists(joinPath(dir, "a")))
            val err = assertFailsWith<FsException> { textOrEmpty(dir) }
            assertTrue(dir.toString() in err.message!!, err.message)
        }

    @Test
    fun createsAndReplacesWithoutLeftovers() =
        withTempDir { dir ->
            val f = joinPath(dir, "state.json")
            f.write("one")
            assertEquals("one", f.text())
            f.write("two")
            assertEquals("two", f.text())
            assertEquals(listOf("state.json"), entries(dir))
        }

    @Test
    fun missingParentFailsUnlessAskedToCreateIt() =
        withTempDir { dir ->
            val f = joinPath(dir, "x/y/z.toml")
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
            val f = joinPath(dir, "c")
            WriteOptions(durable = false).write(f, "fast".encodeToByteArray())
            assertEquals("fast", f.text())
        }

    @Test
    fun ifChangedCreatesTheParentAndSkipsIdenticalBytes() =
        withTempDir { dir ->
            val f = joinPath(dir, "sub/a.txt")
            assertTrue(ifChanged(f, "x".encodeToByteArray()))
            assertFalse(ifChanged(f, "x".encodeToByteArray()))
            assertTrue(ifChanged(f, "y".encodeToByteArray()))
            assertEquals("y", f.text())
        }

    @Test
    fun anErrorNamesTheFile() =
        withTempDir { dir ->
            val err = assertFailsWith<FsException> { joinPath(dir, "gone/z.toml").write("v") }
            assertEquals(FsException.Kind.NotFound, err.kind)
            assertTrue("z.toml" in err.message!!, err.message)
        }

    @Test
    fun keepsTheOldMode() =
        unixModes {
            withTempDir { dir ->
                val f = joinPath(dir, "secret")
                f.write("old")
                setMode(f, "600".toInt(8))
                f.write("new")
                assertEquals("600".toInt(8), mode(f))
            }
        }

    @Test
    fun modeAppliesToANewFileAndYieldsToAnExistingOne() =
        unixModes {
            withTempDir { dir ->
                val f = joinPath(dir, "key")
                val private = WriteOptions(mode = "600".toInt(8))
                private.write(f, "k".encodeToByteArray())
                assertEquals("600".toInt(8), mode(f))
                setMode(f, "640".toInt(8))
                private.write(f, "k2".encodeToByteArray())
                assertEquals("640".toInt(8), mode(f))
                private.copy(preservePermissions = false).write(f, "k3".encodeToByteArray())
                assertEquals("600".toInt(8), mode(f))
            }
        }

    @Test
    fun writesThroughASymlinkAndKeepsIt() =
        withLinkDir { dir ->
            val real = joinPath(dir, "real.json")
            val linked = joinPath(dir, "link.json")
            real.write("old")
            link("real.json", linked)
            linked.write("new")
            assertEquals(EntryKind.Symlink, entryKind(linked))
            assertEquals("new", real.text())
        }

    @Test
    fun createsTheTargetOfADanglingSymlink() =
        withLinkDir { dir ->
            val linked = joinPath(dir, "link")
            link("missing", linked)
            linked.write("made")
            assertEquals(EntryKind.Symlink, entryKind(linked))
            assertEquals("made", joinPath(dir, "missing").text())
        }

    @Test
    fun notFollowingReplacesTheLinkItself() =
        withLinkDir { dir ->
            val real = joinPath(dir, "real")
            val linked = joinPath(dir, "link")
            real.write("old")
            link(real.toString(), linked)
            WriteOptions(followSymlinks = false).write(linked, "new".encodeToByteArray())
            assertEquals(EntryKind.File, entryKind(linked))
            assertEquals("old", real.text())
        }

    @Test
    fun symlinkCycleEnds() =
        withLinkDir { dir ->
            val a = joinPath(dir, "a")
            val b = joinPath(dir, "b")
            link(b.toString(), a)
            link(a.toString(), b)
            assertFailsWith<FsException> { a.write("v") }
            assertEquals(listOf("a", "b"), entries(dir))
        }

    @Test
    fun linksAreKeptOrFollowedAsAsked() =
        withLinkDir { dir ->
            val src = joinPath(dir, "src")
            createDirAll(joinPath(src, "real"))
            joinPath(src, "real/f").write("f")
            link("real", joinPath(src, "link"))
            copyTree(src, joinPath(dir, "kept"), Links.Keep)
            assertEquals(EntryKind.Symlink, entryKind(joinPath(dir, "kept/link")))
            copyTree(src, joinPath(dir, "followed"), Links.Follow)
            assertEquals(EntryKind.Dir, entryKind(joinPath(dir, "followed/link")))
            assertEquals("f", joinPath(dir, "followed/link/f").text())
            copyTree(src, joinPath(dir, "skipped"), Links.Skip)
            assertNull(entryKind(joinPath(dir, "skipped/link")))
            assertEquals("f", joinPath(dir, "skipped/real/f").text())
        }

    @Test
    fun copyTreeKeepsAFilesMode() =
        unixModes {
            withTempDir { dir ->
                val src = joinPath(dir, "src")
                createDirAll(joinPath(src, "bin"))
                joinPath(src, "bin/tool").write("#!/bin/sh\n")
                setMode(joinPath(src, "bin/tool"), "755".toInt(8))
                copyTree(src, joinPath(dir, "dest"), Links.Follow)
                assertEquals("755".toInt(8), mode(joinPath(dir, "dest/bin/tool")))
                assertEquals("#!/bin/sh\n", joinPath(dir, "dest/bin/tool").text())
            }
        }

    @Test
    fun removeAnyTakesAFileATreeAndNothing() =
        withTempDir { dir ->
            joinPath(dir, "f").write("f")
            createDirAll(joinPath(dir, "d/e"))
            removeAny(joinPath(dir, "f"))
            removeAny(joinPath(dir, "d"))
            removeAny(joinPath(dir, "never"))
            assertNull(entryKind(joinPath(dir, "f")))
            assertNull(entryKind(joinPath(dir, "d")))
        }

    @Test
    fun removeAnyNeverFollowsALinkIntoItsTarget() =
        withLinkDir { dir ->
            createDirAll(joinPath(dir, "keep"))
            joinPath(dir, "keep/f").write("f")
            link(joinPath(dir, "keep").toString(), joinPath(dir, "link"))
            removeAny(joinPath(dir, "link"))
            assertEquals("f", joinPath(dir, "keep/f").text())
        }

    @Test
    fun canonResolvesLinksAndLeavesAMissingPathAlone() =
        withLinkDir { dir ->
            createDirAll(joinPath(dir, "real"))
            link("real", joinPath(dir, "link"))
            assertEquals(canonicalize(joinPath(dir, "real")), canonicalize(joinPath(dir, "link")))
            val missing = joinPath(dir, "missing")
            assertEquals(missing, canon(missing))
            assertEquals(FsException.Kind.NotFound, assertFailsWith<FsException> { canonicalize(missing) }.kind)
        }

    /** fs-kit's Windows test: a replace cannot land on a read-only file, so the read-only bit gives way. */
    @Test
    fun replacesAReadOnlyFile() =
        withTempDir { dir ->
            val f = joinPath(dir, "f")
            f.write("old")
            setMode(f, "444".toInt(8))
            f.write("new")
            assertEquals("new", f.text())
        }

    @Test
    fun windowsReportsReadOnlyAsAModeWithoutWriteBits() {
        if (!onWindows) return
        withTempDir { dir ->
            val f = joinPath(dir, "f")
            f.write("x")
            assertEquals("666".toInt(8), mode(f))
            setMode(f, "444".toInt(8))
            assertEquals("444".toInt(8), mode(f))
            setMode(f, "644".toInt(8))
            assertEquals("666".toInt(8), mode(f))
            assertEquals("777".toInt(8), mode(dir))
        }
    }
}

/** How Rust prints a missing file's OS error: strerror on Unix, FormatMessageW on Windows. */
private val NOT_FOUND =
    if (onWindows) "The system cannot find the file specified. (os error 2)" else "No such file or directory (os error 2)"
