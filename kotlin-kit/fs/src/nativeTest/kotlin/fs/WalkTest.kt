package fs

import kotlinx.io.files.Path
import kotlinx.io.files.SystemPathSeparator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WalkTest {
    private fun tree(dir: Path) {
        createDirAll(joinPath(dir, "a/b"))
        joinPath(dir, "a/b/f").write("f")
        joinPath(dir, "top").write("t")
        link("a", joinPath(dir, "l"))
    }

    private fun rel(
        root: Path,
        entries: Sequence<WalkEntry>,
    ) = entries
        .map {
            it.path
                .toString()
                .removePrefix(root.toString())
                .replace('\\', '/')
                .removePrefix("/") to it.depth
        }.toList()

    @Test
    fun theRootComesFirstAndEachDirectoryBeforeItsContents(): Unit =
        withLinkDir { dir ->
            tree(dir)
            val got = rel(dir, walk(dir))
            assertEquals("" to 0, got.first())
            assertEquals(setOf("" to 0, "a" to 1, "a/b" to 2, "a/b/f" to 3, "top" to 1, "l" to 1), got.toSet())
            assertTrue(got.indexOf("a" to 1) < got.indexOf("a/b" to 2))
            assertTrue(got.indexOf("a/b" to 2) < got.indexOf("a/b/f" to 3))
        }

    @Test
    fun aLinkBelowTheRootIsReportedAndNotFollowed(): Unit =
        withLinkDir { dir ->
            tree(dir)
            val entries = walk(dir).toList()
            assertEquals(EntryKind.Symlink, entries.single { it.path == joinPath(dir, "l") }.kind)
            assertTrue(entries.none { it.path.toString().startsWith(joinPath(dir, "l").toString() + SystemPathSeparator) })
        }

    @Test
    fun aRootThatIsALinkIsFollowed(): Unit =
        withLinkDir { dir ->
            tree(dir)
            val entries = walk(joinPath(dir, "l")).toList()
            assertEquals(EntryKind.Dir, entries.first().kind)
            assertEquals(setOf("", "b", "b/f"), rel(joinPath(dir, "l"), entries.asSequence()).map { it.first }.toSet())
        }

    @Test
    fun maxDepthStopsTheDescent(): Unit =
        withLinkDir { dir ->
            tree(dir)
            assertEquals(listOf("" to 0), rel(dir, walk(dir, maxDepth = 0)))
            assertEquals(setOf(0, 1), walk(dir, maxDepth = 1).map { it.depth }.toSet())
        }

    @Test
    fun enterFalsePrunesADirectoryAndEverythingInIt(): Unit =
        withLinkDir { dir ->
            tree(dir)
            val got = rel(dir, walk(dir) { it.path.name != "a" }).map { it.first }.toSet()
            assertEquals(setOf("", "top", "l"), got)
        }

    @Test
    fun aMissingRootGoesToOnError(): Unit =
        withLinkDir { dir ->
            val errors = mutableListOf<FsException>()
            assertEquals(emptyList(), walk(joinPath(dir, "missing"), onError = { errors += it }).toList())
            assertEquals(FsException.Kind.NotFound, errors.single().kind)
        }
}
