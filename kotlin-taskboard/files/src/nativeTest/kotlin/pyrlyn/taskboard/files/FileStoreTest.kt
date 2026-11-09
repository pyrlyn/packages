package pyrlyn.taskboard.files

import fs.entryKind
import fs.joinPath
import pyrlyn.taskboard.Commit
import pyrlyn.taskboard.StateNames
import pyrlyn.taskboard.Status
import pyrlyn.taskboard.Task
import pyrlyn.taskboard.TaskId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

private val LOCK = 1.seconds

private fun FileStore.commit(commit: Commit) = locked(LOCK) { it.commit(commit) }

private fun FileStore.put(vararg tasks: Task) = commit(Commit(put = tasks.toList()))

class FileStoreTest {
    @Test
    fun aMissingFolderIsAnEmptyBoardAndReadingCreatesNothing() =
        withBoard { dir ->
            val r = FileStore(dir).snapshot()
            assertEquals(emptyList(), r.tasks())
            assertEquals(emptyList(), r.warnings())
            assertNull(r.state(StateNames.INDEX))
            assertNull(r.state(StateNames.JOURNAL))
            assertNull(entryKind(joinPath(dir)))
        }

    @Test
    fun theRevOfATaskIsTheRevReportedAfterCommitAndSeenByReaders() =
        withBoard { dir ->
            val store = FileStore(dir)
            val t = task("T1", "first").copy(description = "body", report = "r", status = Status.Done)
            val (expected, reported) = store.locked(LOCK) { s -> s.revOf(t) to s.commit(Commit(put = listOf(t))).getValue(t.id) }
            assertEquals(expected, reported)
            val seen = store.snapshot().tasks().single()
            assertEquals(reported, seen.rev)
            assertEquals(t, seen.value)
        }

    @Test
    fun aMoveChangesTheRevEvenInTheSameSecond() =
        withBoard { dir ->
            val store = FileStore(dir)
            val t = task("T1")
            val before = store.put(t).getValue(t.id)
            val after = store.put(t.copy(status = Status.InProgress, assignee = "a")).getValue(t.id)
            val moved = store.put(t.copy(status = Status.InProgress)).getValue(t.id)
            assertTrue(before != after)
            assertTrue(after != moved)
            assertEquals(emptyList(), files(dir, "todo"))
            assertEquals(1, files(dir, "in-progress").size)
        }

    @Test
    fun theColumnAloneChangesTheRevOfIdenticalBytes() {
        val bytes = "same".encodeToByteArray()
        assertTrue(BoardDir.revOf(Status.Todo, bytes) != BoardDir.revOf(Status.Done, bytes))
    }

    @Test
    fun aTitleChangeRenamesTheFileAndLeavesNoOldCopy() =
        withBoard { dir ->
            val store = FileStore(dir)
            val t = task("T1", "Old title")
            store.put(t)
            assertEquals(listOf("T1-old-title.md"), files(dir, "todo"))
            store.put(t.copy(title = "New title"))
            assertEquals(listOf("T1-new-title.md"), files(dir, "todo"))
            assertEquals(
                "New title",
                store
                    .snapshot()
                    .tasks()
                    .single()
                    .value.title,
            )
        }

    @Test
    fun aCaseOnlyDifferenceInTheFileNameKeepsTheFileOnACaseInsensitiveSystem() =
        withBoard { dir ->
            val store = FileStore(dir)
            put(dir, "todo/probe.md", "x")
            val insensitive = exists(dir, "todo/PROBE.md")
            fs.removeFile(joinPath(dir, "todo/probe.md"))
            if (!insensitive) {
                println("skipped: this file system tells names apart by case")
                return@withBoard
            }
            val t = task("T1", "Same")
            put(dir, "todo/T1-Same.md", TaskFile.render(t))
            store.put(t.copy(description = "edited"))
            assertEquals(1, files(dir, "todo").size)
            assertEquals(
                "edited",
                store
                    .snapshot()
                    .tasks()
                    .single()
                    .value.description,
            )
        }

    @Test
    fun aRemoveDeletesTheFile() =
        withBoard { dir ->
            val store = FileStore(dir)
            store.put(task("T1"), task("T2"))
            store.commit(Commit(remove = listOf(TaskId.parse("T1"))))
            assertEquals(listOf("T2"), store.snapshot().tasks().map { it.value.id.toString() })
            assertEquals(1, files(dir, "todo").size)
        }

    @Test
    fun stateBlobsAreFilesUnderTheStateFolder() =
        withBoard { dir ->
            val store = FileStore(dir)
            store.commit(Commit(state = mapOf("ids" to "{\"T\":1}", "leases" to "{}"), append = mapOf("journal" to listOf("{\"a\":1}", "{\"a\":2}"))))
            assertEquals("{\"T\":1}", read(dir, ".state/ids.json"))
            assertEquals("{\"a\":1}\n{\"a\":2}\n", read(dir, ".state/journal.jsonl"))
            store.commit(Commit(state = mapOf("ids" to null), append = mapOf("journal" to listOf("{\"a\":3}"))))
            assertNull(store.snapshot().state("ids"))
            assertEquals("{\"a\":1}\n{\"a\":2}\n{\"a\":3}\n", store.snapshot().state(StateNames.JOURNAL))
            assertTrue(!exists(dir, ".state/txn.json"))
        }

    @Test
    fun anAppendAfterAStateWriteOfTheSameBlobStartsFromTheNewText() =
        withBoard { dir ->
            val store = FileStore(dir)
            store.commit(Commit(append = mapOf("journal" to listOf("old"))))
            store.commit(Commit(state = mapOf("journal" to "fresh\n"), append = mapOf("journal" to listOf("next"))))
            assertEquals("fresh\nnext\n", read(dir, ".state/journal.jsonl"))
        }

    @Test
    fun stateNamesThatCouldLeaveTheStateFolderAreRefused() =
        withBoard { dir ->
            val store = FileStore(dir)
            for (bad in listOf("", "../x", "A", "a/b", "1a", "a.b", "txn")) {
                assertCode("invalid") { store.snapshot().state(bad) }
                assertCode("invalid") { store.commit(Commit(state = mapOf(bad to "x"))) }
            }
            assertEquals(emptyList(), files(dir, "todo"))
        }

    @Test
    fun aDescriptionWithAReportHeadingIsRefusedBeforeAnythingIsWritten() =
        withBoard { dir ->
            val store = FileStore(dir)
            store.put(task("T1"))
            assertCode("invalid") { store.put(task("T2"), task("T3").copy(description = "a\n## Report\nb")) }
            assertEquals(listOf("T1"), store.snapshot().tasks().map { it.value.id.toString() })
            assertTrue(!exists(dir, ".state/txn.json"))
        }

    @Test
    fun aStatusKeyInAFileIsIgnoredAndTheFolderWins() =
        withBoard { dir ->
            put(dir, "done/T1-x.md", "---\nid: T1\ntitle: x\nstatus: todo\ncreated: 2023-11-14T22:13:20Z\nupdated: 2023-11-14T22:13:20Z\n---\n")
            val r = FileStore(dir).snapshot()
            assertEquals(
                Status.Done,
                r
                    .tasks()
                    .single()
                    .value.status,
            )
            assertEquals(1, r.warnings().size)
        }

    @Test
    fun strayFoldersLooseFilesBrokenFilesAndMismatchedNamesAreSkippedWithWarnings() =
        withBoard { dir ->
            val ok = TaskFile.render(task("T1"))
            put(dir, "todo/T1-ok.md", ok)
            put(dir, "notes/readme.txt", "x")
            put(dir, "T9-loose.md", ok.replace("T1", "T9"))
            put(dir, "todo/T2-broken.md", "not a task")
            put(dir, "todo/T3-mismatch.md", ok.replace("id: T1", "id: T4"))
            put(dir, "todo/README.md", "# not a task file")
            put(dir, ".hidden/T5-x.md", ok)
            val r = FileStore(dir).snapshot()
            assertEquals(listOf("T1"), r.tasks().map { it.value.id.toString() })
            val warnings = r.warnings()
            assertEquals(4, warnings.size, warnings.toString())
            assertTrue(warnings.any { "notes" in it && "folder" in it })
            assertTrue(warnings.any { "T9-loose.md" in it })
            assertTrue(warnings.any { "T2-broken.md" in it })
            assertTrue(warnings.any { "T3-mismatch.md" in it && "T4" in it })
        }

    @Test
    fun twoFilesOfOneIdKeepTheNewerAndATieGoesToTheLaterColumn() =
        withBoard { dir ->
            val t = task("T1", "dup")
            put(dir, "todo/T1-dup.md", TaskFile.render(t.copy(updated = at(1_700_000_100), description = "newer")))
            put(dir, "in-progress/T1-dup.md", TaskFile.render(t.copy(description = "older")))
            val r = FileStore(dir).snapshot()
            val won = r.tasks().single().value
            assertEquals("newer", won.description)
            assertEquals(Status.Todo, won.status)
            assertEquals(1, r.warnings().size)

            put(dir, "done/T2-dup.md", TaskFile.render(task("T2", "dup").copy(description = "d")))
            put(dir, "closed/T2-dup.md", TaskFile.render(task("T2", "dup").copy(description = "c")))
            val tie =
                FileStore(dir)
                    .snapshot()
                    .tasks()
                    .single { it.value.id.toString() == "T2" }
                    .value
            assertEquals(Status.Closed, tie.status)
        }

    @Test
    fun aCommitUnlinksEveryOtherCopyOfTheIdItWrites() =
        withBoard { dir ->
            val t = task("T1", "dup")
            put(dir, "todo/T1-dup.md", TaskFile.render(t))
            put(dir, "done/T1 - dup.md", TaskFile.render(t.copy(updated = at(1_700_000_100))))
            FileStore(dir).put(t.copy(status = Status.InProgress, updated = at(1_700_000_200)))
            assertEquals(emptyList(), files(dir, "todo"))
            assertEquals(emptyList(), files(dir, "done"))
            assertEquals(listOf("T1-dup.md"), files(dir, "in-progress"))
        }

    @Test
    fun aCommitNeverTouchesAnUnreadableFileOfTheSameId() =
        withBoard { dir ->
            put(dir, "todo/T2-broken.md", "not a task")
            val store = FileStore(dir)
            assertCode("invalid") { store.put(task("T1"), task("T2")) }
            assertCode("invalid") { store.commit(Commit(remove = listOf(TaskId.parse("T2")))) }
            assertEquals("not a task", read(dir, "todo/T2-broken.md"))
            assertEquals(emptyList(), store.snapshot().tasks())
            assertTrue(!exists(dir, ".state/txn.json"))
        }

    @Test
    fun theGitignoreIsWrittenOnlyWhenMissingOrRtoksOldOne() {
        withBoard { dir ->
            FileStore(dir).put(task("T1"))
            assertEquals(".lock\n.state/\n.sync/\n", read(dir, ".gitignore"))
        }
        withBoard { dir ->
            put(dir, ".gitignore", ".lock\n.sync/\n")
            FileStore(dir).put(task("T1"))
            assertEquals(".lock\n.state/\n.sync/\n", read(dir, ".gitignore"))
        }
        withBoard { dir ->
            put(dir, ".gitignore", "mine\n")
            FileStore(dir).put(task("T1"))
            assertEquals("mine\n", read(dir, ".gitignore"))
        }
    }

    @Test
    fun readingDoesNotWriteAGitignore() =
        withBoard { dir ->
            put(dir, "todo/T1-x.md", TaskFile.render(task("T1")))
            FileStore(dir).snapshot().tasks()
            assertTrue(!exists(dir, ".gitignore"))
            assertTrue(!exists(dir, ".lock"))
        }

    @Test
    fun aTaskWrittenByRtokKeepsItsUnknownKeysWhenTheBoardRewritesIt() =
        withBoard { dir ->
            put(dir, "todo/T1-x.md", "---\nid: T1\ntitle: x\ncreated: 2023-11-14T22:13:20Z\nupdated: 2023-11-14T22:13:20Z\nestimate: 3\nlabels:\n  - a\n---\n\nbody\n")
            val store = FileStore(dir)
            val t =
                store
                    .snapshot()
                    .tasks()
                    .single()
                    .value
            store.put(t.copy(description = "edited"))
            val again =
                store
                    .snapshot()
                    .tasks()
                    .single()
                    .value
            assertEquals(t.extra, again.extra)
            assertEquals(listOf("estimate", "labels"), again.extra.keys.toList())
        }

    @Test
    fun aTaskFileWithNoTimeUsesTheFilesTime() =
        withBoard { dir ->
            put(dir, "todo/T1-x.md", "---\nid: T1\ntitle: x\n---\n")
            val t =
                FileStore(dir)
                    .snapshot()
                    .tasks()
                    .single()
                    .value
            assertTrue(t.created.epochSeconds > 1_700_000_000)
        }
}
