package fs

import kotlinx.io.files.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class BackupTest {
    @Test
    fun backupSkipsAMissingFile(): Unit =
        withTempDir { dir ->
            assertNull(backup(Path(dir, "nope.toml")))
        }

    @Test
    fun backupSkipsTakenNamesWithoutClobbering(): Unit =
        withTempDir { dir ->
            val path = Path(dir, "settings.json")
            val ts = 1_700_000_000uL
            path.write("v0")
            val first = assertNotNull(backupAt(path, ts), "first copy")
            assertEquals(Path(dir, "settings.json.bak-$ts"), first)
            for (n in 1 until 100) Path(dir, "settings.json.bak-$ts-$n").write("slot-$n")
            path.write("v2")
            val second = assertNotNull(backupAt(path, ts), "second copy")
            assertEquals(Path(dir, "settings.json.bak-$ts-100"), second)
            assertEquals("v2", second.text())
            assertEquals("v0", first.text())
            assertEquals("slot-7", Path(dir, "settings.json.bak-$ts-7").text())
        }

    @Test
    fun backupSkipsWhenAnIdenticalCopyExistsUnderAnyName(): Unit =
        withTempDir { dir ->
            val path = Path(dir, "settings.json")
            path.write("same")
            val first = assertNotNull(backupAt(path, 1u), "first copy")
            assertNull(backupAt(path, 2u), "same bytes, no copy")
            rename(first, Path(dir, "settings.json.bak-9-7"))
            assertNull(backupAt(path, 3u))
            path.write("diff")
            val third = assertNotNull(backupAt(path, 4u), "changed content is copied")
            assertEquals("diff", third.text())
            Path(dir, "other.json.bak-1").write("back")
            path.write("back")
            assertNotNull(backupAt(path, 5u), "prefix is per file")
        }

    @Test
    fun aPlantedLinkAtTheBackupNameIsSkippedNotFollowed(): Unit =
        withTempDir { dir ->
            val path = Path(dir, "settings.json")
            path.write("secret")
            val victim = Path(dir, "victim")
            victim.write("keep")
            link(victim.toString(), Path(dir, "settings.json.bak-5"))
            val bak = assertNotNull(backupAt(path, 5u), "copy under the next name")
            assertEquals(Path(dir, "settings.json.bak-5-1"), bak)
            assertEquals("keep", victim.text())
        }
}
