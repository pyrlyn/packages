package pyrlyn.taskboard.files

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import pyrlyn.taskboard.Status
import pyrlyn.taskboard.Task
import pyrlyn.taskboard.TaskId
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TaskFileTest {
    private fun parse(
        text: String,
        column: Status = Status.Todo,
    ) = TaskFile.parse(text, column) { 42L }

    private val titles =
        listOf(
            "Port the parser",
            "Исправить ошибку",
            "a: b",
            "\"quoted\"",
            "it's",
            "#hash",
            "- dash",
            "  spaces  ",
            "emoji \uD83D\uDE80 launch",
            "tab\there",
            "{[curly]}",
            "123",
            "true",
            "null",
            "~",
            "back\\slash",
            "line\u2028sep",
            "ключ: значение",
            "\u00e9\u0301",
            "x",
        )
    private val descriptions =
        listOf(
            "",
            "one line",
            "multi\nline\n\nwith blank",
            "## Heading\ntext",
            "---\nfake front matter\n---",
            "code:\n```\n---\n```",
            "## report\nlower",
            "тело описания",
            "- list\n- items",
            "key: value\nother: 1",
        )
    private val reports = listOf(null, "short", "two\nlines", "## Not the heading\nbody", "отчёт")

    private fun extraValues(): List<JsonElement> =
        listOf(
            JsonPrimitive("x"),
            JsonPrimitive(3),
            JsonPrimitive(true),
            JsonNull,
            JsonPrimitive(1.5),
            JsonPrimitive("true"),
            JsonPrimitive("123"),
            JsonPrimitive("quote \" and \\ and \n newline and \u00e9"),
            JsonPrimitive(""),
            JsonArray(listOf(JsonPrimitive(1), JsonPrimitive("a b"), JsonNull)),
            JsonArray(emptyList()),
            JsonObject(emptyMap()),
            JsonObject(mapOf("k" to JsonPrimitive("v"), "n" to JsonArray(listOf(JsonObject(mapOf("deep" to JsonPrimitive(false))))))),
        )

    private fun varied(r: Random): Task {
        val ids = listOf("T1", "T2", "R12", "R12.3", "ABC7.1.2", "T100")
        val extras = extraValues()
        val keys = listOf("estimate", "labels", "my key", "ключ", "x-y", "a.b", "0", "true")
        val chosenKeys = keys.shuffled(r).take(r.nextInt(0, 4))
        return Task(
            id = TaskId.parse(ids.random(r)),
            title = titles.random(r),
            description = descriptions.random(r),
            status = Status.entries.random(r),
            priority = r.nextInt(0, 5),
            parent = if (r.nextBoolean()) TaskId.parse("T${r.nextInt(1, 9)}") else null,
            assignee = if (r.nextBoolean()) listOf("agent-1", "Иван", "a b", "x: y").random(r) else null,
            blockedBy =
                ids
                    .shuffled(r)
                    .take(r.nextInt(0, 3))
                    .map(TaskId::parse)
                    .distinct()
                    .sorted(),
            conflictsWith =
                ids
                    .shuffled(r)
                    .take(r.nextInt(0, 3))
                    .map(TaskId::parse)
                    .distinct()
                    .sorted(),
            parallel = r.nextBoolean(),
            report = reports.random(r),
            remote = if (r.nextBoolean()) "https://example.com/issues/${r.nextInt()}" else null,
            created = at(r.nextLong(0, 4_000_000_000)),
            updated = at(r.nextLong(0, 4_000_000_000)),
            extra = chosenKeys.associateWith { extras.random(r) },
        )
    }

    @Test
    fun parseOfRenderGivesBackTheTask() {
        val r = Random(7)
        repeat(500) { n ->
            val t = varied(r)
            val parsed = parse(TaskFile.render(t), t.status).task
            assertEquals(t, parsed, "task $n:\n${TaskFile.render(t)}")
            assertEquals(t.extra.keys.toList(), parsed.extra.keys.toList())
        }
    }

    @Test
    fun aTaskWithNothingSetIsJustTheRequiredKeys() {
        val t = task("T1", "plain")
        assertEquals("---\nid: T1\ntitle: plain\ncreated: 2023-11-14T22:13:20Z\nupdated: 2023-11-14T22:13:20Z\n---\n", TaskFile.render(t))
        assertEquals(t, parse(TaskFile.render(t)).task)
    }

    @Test
    fun everyFieldIsWrittenInTheDocumentedOrder() {
        val t =
            task("T2", "all").copy(
                parent = TaskId.parse("T1"),
                priority = 0,
                assignee = "a1",
                blockedBy = listOf(TaskId.parse("T3")),
                conflictsWith = listOf(TaskId.parse("T4")),
                parallel = true,
                remote = "https://x.test/1",
                extra = mapOf("estimate" to JsonPrimitive(3)),
            )
        val keys =
            TaskFile
                .render(t)
                .lines()
                .takeWhile { it != "" }
                .filter { it.isNotEmpty() && !it.startsWith(' ') && it != "---" }
                .map { it.substringBefore(':') }
        assertEquals(listOf("id", "title", "created", "updated", "parent", "priority", "assignee", "blocked_by", "conflicts_with", "parallel", "remote", "estimate"), keys)
    }

    // The texts below are what rtok's renderBoard writes today.
    private val rtokFull =
        """
        ---
        id: R12
        title: "Port the parser"
        created: 2023-11-14T22:13:20Z
        updated: 2023-11-14T22:14:20Z
        priority: 1
        assignee: agent-1
        blocked_by:
          - R3
          - R7
        parallel: true
        ---

        Make it fast.

        Second paragraph.

        ## Report

        Done in PR 5
        """.trimIndent() + "\n"

    @Test
    fun aFileRtokWroteReadsBackAndRendersIdentically() {
        val t = parse(rtokFull, Status.Done).task
        assertEquals(TaskId.parse("R12"), t.id)
        assertEquals("Port the parser", t.title)
        assertEquals("Make it fast.\n\nSecond paragraph.", t.description)
        assertEquals("Done in PR 5", t.report)
        assertEquals(1, t.priority)
        assertEquals("agent-1", t.assignee)
        assertEquals(listOf("R3", "R7"), t.blockedBy.map { it.toString() })
        assertTrue(t.parallel)
        assertEquals(at(1_700_000_000), t.created)
        assertEquals(at(1_700_000_060), t.updated)
        assertEquals(rtokFull, TaskFile.render(t))
    }

    @Test
    fun aFileWithNoBodyAndNoOptionalKeysRendersIdentically() {
        val text = "---\nid: T5\ntitle: \"Исправить ошибку\"\ncreated: 2023-11-14T22:13:20Z\nupdated: 2023-11-14T22:13:20Z\n---\n"
        assertEquals(text, TaskFile.render(parse(text).task))
    }

    @Test
    fun aReportAloneIsKeptWithoutADescription() {
        val text = "---\nid: T5\ntitle: x\ncreated: 2023-11-14T22:13:20Z\nupdated: 2023-11-14T22:13:20Z\n---\n\n## Report\n\nall done\n"
        val t = parse(text, Status.Done).task
        assertEquals("", t.description)
        assertEquals("all done", t.report)
        assertEquals(text, TaskFile.render(t))
    }

    @Test
    fun unixSecondTimesAndUnknownKeysAreRead() {
        val text =
            """
            ---
            id: R2
            title: Fix it
            created_at: 1700000000
            updated_at: 1700000100
            estimate: 3
            labels:
              - a
              - b
            owner:
              name: Ann
              team: core
            ---

            body
            """.trimIndent() + "\n"
        val t = parse(text).task
        assertEquals(at(1_700_000_000), t.created)
        assertEquals(at(1_700_000_100), t.updated)
        assertEquals(listOf("estimate", "labels", "owner"), t.extra.keys.toList())
        assertEquals(JsonPrimitive(3), t.extra["estimate"])
        assertEquals(JsonArray(listOf(JsonPrimitive("a"), JsonPrimitive("b"))), t.extra["labels"])
        assertEquals(JsonObject(mapOf("name" to JsonPrimitive("Ann"), "team" to JsonPrimitive("core"))), t.extra["owner"])
        // Rewriting keeps the keys, in flow style: comments inside them are the one thing that is lost.
        assertEquals(t, parse(TaskFile.render(t)).task)
    }

    @Test
    fun aMissingTimeFallsBackToTheFilesTime() {
        val t = parse("---\nid: T1\ntitle: x\n---\n").task
        assertEquals(at(42), t.created)
        assertEquals(at(42), t.updated)
    }

    @Test
    fun theStatusKeyIsIgnoredAndOnlyWarnsWhenItDisagrees() {
        val text = "---\nid: T1\ntitle: x\nstatus: done\n---\n"
        val agree = parse(text, Status.Done)
        assertEquals(Status.Done, agree.task.status)
        assertEquals(emptyList(), agree.warnings)
        val disagree = parse(text, Status.Todo)
        assertEquals(Status.Todo, disagree.task.status)
        assertEquals(1, disagree.warnings.size)
        assertTrue("status" !in disagree.task.extra)
    }

    @Test
    fun theOldNameWithSpacesAndTheNewOneBothCarryAnId() {
        assertEquals(TaskId.parse("R12"), TaskFile.fileId("R12 - port-the-parser.md"))
        assertEquals(TaskId.parse("R12.1"), TaskFile.fileId("R12.1-port.md"))
        assertNull(TaskFile.fileId("README.md"))
        assertNull(TaskFile.fileId("R12-port.txt"))
        assertEquals("T3-исправить-ошибку.md", TaskFile.fileName(task("T3", "Исправить ошибку!")))
        assertEquals("T3-task.md", TaskFile.fileName(task("T3", "!!!")))
    }

    @Test
    fun brokenFilesAreFormatErrors() {
        for (text in listOf("", "no front matter", "---\nid: T1\n", "---\nid: T1\n---\n", "---\ntitle: x\n---\n", "---\nid: nope\ntitle: x\n---\n", "---\nid: T1\ntitle: x\npriority: 9\n---\n", "---\nid: T1\ntitle: x\ncreated: yesterday\n---\n", "---\n- a\n---\n")) {
            assertFailsWith<FormatException>("\"$text\"") { parse(text) }
        }
    }

    @Test
    fun aReportHeadingInTheTextCannotBeWritten() {
        assertTrue(TaskFile.unwritable(task("T1").copy(description = "a\n## Report\nb")) != null)
        assertTrue(TaskFile.unwritable(task("T1").copy(description = "a\n## Report  \nb")) != null)
        assertTrue(TaskFile.unwritable(task("T1").copy(report = "## Report")) != null)
        assertNull(TaskFile.unwritable(task("T1").copy(description = "## Reports\n### Report", report = "x")))
    }
}
