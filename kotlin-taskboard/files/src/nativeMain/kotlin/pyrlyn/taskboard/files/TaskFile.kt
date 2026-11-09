package pyrlyn.taskboard.files

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import pyrlyn.taskboard.BoardException
import pyrlyn.taskboard.DEFAULT_PRIORITY
import pyrlyn.taskboard.PRIORITY_MAX
import pyrlyn.taskboard.Status
import pyrlyn.taskboard.Task
import pyrlyn.taskboard.TaskId
import yamlaot.Yaml
import yamlaot.YamlAotException
import yamlaot.YamlText
import yamlaot.YamlValue
import kotlin.time.Instant

/** The heading that opens a task's closing report; the last one in a file is the boundary. */
internal const val REPORT_HEADING = "## Report"

/** Longest slug in a file name; the title itself lives in the front matter. */
private const val SLUG_MAX = 48

/** The line that closes a front matter, with its newlines. */
private const val FENCE = "\n---\n"

/** The keys the format writes itself; any other key in a file is kept as it is on rewrite. */
private val KNOWN_KEYS =
    setOf(
        "id",
        "title",
        "created",
        "updated",
        "created_at",
        "updated_at",
        "status",
        "parent",
        "priority",
        "assignee",
        "blocked_by",
        "conflicts_with",
        "parallel",
        "remote",
    )

/** A file that is not a task file; [message] says why, for a warning. */
internal class FormatException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

/** A task read from a file, and what was odd about the file without making it unreadable. */
internal class ParsedTask(
    val task: Task,
    val warnings: List<String>,
)

/**
 * A task as a Markdown file: YAML front matter (no `status`: the folder is the status), the description as the body,
 * and a closing report under a final `## Report` heading. rtok's board writes the same format.
 */
internal object TaskFile {
    fun fileName(task: Task) = "${task.id}-${slug(task.title)}.md"

    /** The id a file name starts with; also reads the old `<ID> - <slug>.md`. */
    fun fileId(name: String): TaskId? =
        name
            .takeIf { it.endsWith(".md") }
            ?.removeSuffix(".md")
            ?.takeWhile { it != '-' && it != ' ' }
            ?.let(TaskId::parseOrNull)

    /** Every Unicode letter and digit is kept, so a Cyrillic title still names its file; `task` when nothing is left. */
    private fun slug(title: String): String {
        val out = StringBuilder()
        for (c in title) {
            if (c.isLetterOrDigit()) {
                out.append(c.lowercaseChar())
            } else if (out.isNotEmpty() && !out.endsWith('-')) {
                out.append('-')
            }
            if (out.length >= SLUG_MAX) break
        }
        return out.toString().trimEnd('-').ifEmpty { "task" }
    }

    /** The reason [task] cannot be written and read back, or null. */
    fun unwritable(task: Task): String? {
        val inDescription = task.description.lines().any { it.trimEnd() == REPORT_HEADING }
        val inReport = task.report?.lines()?.any { it.trimEnd() == REPORT_HEADING } == true
        return if (inDescription || inReport) {
            "${task.id}: a line that is exactly \"$REPORT_HEADING\" would be read as the start of the closing report"
        } else {
            null
        }
    }

    fun render(task: Task): String {
        val yaml =
            buildString {
                append("id: ${YamlText.scalar(task.id.toString())}\n")
                append("title: ${YamlText.scalar(task.title)}\n")
                append("created: ${iso(task.created)}\n")
                append("updated: ${iso(task.updated)}\n")
                task.parent?.let { append("parent: ${YamlText.scalar(it.toString())}\n") }
                if (task.priority != DEFAULT_PRIORITY) append("priority: ${task.priority}\n")
                task.assignee?.let { append("assignee: ${YamlText.scalar(it)}\n") }
                list("blocked_by", task.blockedBy)
                list("conflicts_with", task.conflictsWith)
                if (task.parallel) append("parallel: true\n")
                task.remote?.let { append("remote: ${YamlText.scalar(it)}\n") }
                for ((key, value) in task.extra) append("${YamlText.scalar(key)}: ${yamlValue(value)}\n")
            }
        val report = task.report?.trim()?.takeIf { it.isNotEmpty() }
        val body =
            buildString {
                append(task.description.trim())
                if (report != null) {
                    if (isNotEmpty()) append("\n\n")
                    append("$REPORT_HEADING\n\n$report")
                }
            }
        return if (body.isEmpty()) "---\n$yaml---\n" else "---\n$yaml---\n\n$body\n"
    }

    private fun iso(at: Instant) = Instant.fromEpochSeconds(at.epochSeconds).toString()

    private fun StringBuilder.list(
        key: String,
        ids: List<TaskId>,
    ) {
        if (ids.isEmpty()) return
        append("$key:\n")
        ids.forEach { append("  - ").append(YamlText.scalar(it.toString())).append('\n') }
    }

    /** Collections go in flow style on one line: JSON is YAML there, and the reader takes it back exactly. */
    private fun yamlValue(value: JsonElement): String =
        when {
            value is JsonPrimitive && value.isString -> YamlText.scalar(value.content)
            value is JsonPrimitive -> value.content
            else -> value.toString()
        }

    /**
     * Parse the file [raw] found in the column [column]. A `status:` key in the file is ignored: the folder wins.
     * [fallbackSeconds] supplies the time of a file that names none, only when it is needed.
     */
    fun parse(
        raw: String,
        column: Status,
        fallbackSeconds: () -> Long,
    ): ParsedTask =
        try {
            read(raw, column, fallbackSeconds)
        } catch (e: YamlAotException) {
            throw FormatException(e.message ?: "unreadable YAML", e)
        } catch (e: BoardException) {
            throw FormatException(e.message ?: "invalid field", e)
        }

    private fun read(
        raw: String,
        column: Status,
        fallbackSeconds: () -> Long,
    ): ParsedTask {
        val (yaml, body) = splitFrontMatter(raw)
        val head = Yaml.parse(yaml) as? YamlValue.Map ?: fail("the front matter is not a mapping")
        val (description, report) = splitReport(body.trim())
        val warnings = mutableListOf<String>()
        head["status"]?.let { Status.parseOrNull(it.text("status")) }?.let {
            if (it != column) warnings += "the file says status ${it.word} but it is in the ${column.word} folder; the folder wins"
        }
        val task =
            Task(
                id = TaskId.parse(head["id"]?.text("id") ?: fail("missing field `id`")),
                title = head["title"]?.text("title") ?: fail("missing field `title`"),
                description = description.trim(),
                status = column,
                priority = priority(head),
                parent = head.optText("parent")?.let(TaskId::parse),
                assignee = head.optText("assignee")?.takeIf { it.isNotEmpty() },
                blockedBy = head.ids("blocked_by"),
                conflictsWith = head.ids("conflicts_with"),
                parallel = parallel(head),
                report = report,
                remote = head.optText("remote")?.takeIf { it.isNotEmpty() },
                created = time(head, fallbackSeconds, "created", "created_at"),
                updated = time(head, fallbackSeconds, "updated", "updated_at"),
                extra = head.entries.filterKeys { it !in KNOWN_KEYS }.mapValues { json(it.value) },
            )
        return ParsedTask(task, warnings)
    }

    /** `(front matter text, body)`; CRLF is read as LF. */
    private fun splitFrontMatter(raw: String): Pair<String, String> {
        val text = raw.replace("\r\n", "\n")
        if (!text.startsWith("---\n")) fail("no front matter (the file must start with ---)")
        val rest = text.removePrefix("---\n")
        val at = rest.indexOf(FENCE)
        return when {
            at >= 0 -> rest.substring(0, at) to rest.substring(at + FENCE.length)
            rest.endsWith("\n---") -> rest.removeSuffix("\n---") to ""
            else -> fail("front matter is not closed with ---")
        }
    }

    /** `(description, report)` of a body: the report is what follows the last `## Report` line. */
    private fun splitReport(body: String): Pair<String, String?> {
        val lines = body.lines()
        val at = lines.indexOfLast { it.trimEnd() == REPORT_HEADING }
        if (at < 0) return body to null
        val report = lines.drop(at + 1).joinToString("\n").trim()
        return lines.take(at).joinToString("\n") to report.ifEmpty { null }
    }

    private fun fail(message: String): Nothing = throw FormatException(message)

    private fun YamlValue.text(key: String): String =
        when (this) {
            is YamlValue.Str -> value
            is YamlValue.Int -> value.toString()
            is YamlValue.Float -> text
            is YamlValue.Bool -> value.toString()
            else -> fail("$key: want a string")
        }

    private fun YamlValue.Map.optText(key: String): String? = this[key]?.takeIf { it != YamlValue.Null }?.text(key)

    private fun YamlValue.Map.ids(key: String): List<TaskId> =
        when (val v = this[key]) {
            null, YamlValue.Null -> emptyList()
            is YamlValue.Seq -> v.items.map { TaskId.parse(it.text(key)) }
            else -> fail("$key: want a list")
        }.distinct().sorted()

    private fun priority(head: YamlValue.Map): Int =
        when (val p = head["priority"]) {
            null -> DEFAULT_PRIORITY
            is YamlValue.Int -> p.value.takeIf { it in 0..PRIORITY_MAX }?.toInt() ?: fail("priority: want 0 to $PRIORITY_MAX")
            else -> fail("priority: want an integer")
        }

    private fun parallel(head: YamlValue.Map): Boolean =
        when (val p = head["parallel"]) {
            null -> false
            is YamlValue.Bool -> p.value
            else -> fail("parallel: want a boolean")
        }

    /** `created`/`updated` as ISO 8601 text or unix seconds, whichever key the file has. */
    private fun time(
        head: YamlValue.Map,
        fallbackSeconds: () -> Long,
        vararg keys: String,
    ): Instant {
        val (key, value) = keys.firstNotNullOfOrNull { k -> head[k]?.takeIf { it is YamlValue.Int || it is YamlValue.Str }?.let { k to it } } ?: return Instant.fromEpochSeconds(fallbackSeconds())
        val instant =
            runCatching {
                when (value) {
                    is YamlValue.Int -> Instant.fromEpochSeconds(value.value)
                    else -> Instant.parse(value.text(key))
                }
            }.getOrNull()
        return instant ?: fail("$key: want an ISO 8601 time or unix seconds, got ${value.text(key)}")
    }

    private fun json(value: YamlValue): JsonElement =
        when (value) {
            YamlValue.Null -> {
                JsonNull
            }

            is YamlValue.Bool -> {
                JsonPrimitive(value.value)
            }

            is YamlValue.Int -> {
                JsonPrimitive(value.value)
            }

            is YamlValue.Float -> {
                value.text
                    .toDoubleOrNull()
                    ?.takeIf { it.isFinite() }
                    ?.let(::JsonPrimitive) ?: JsonPrimitive(value.text)
            }

            is YamlValue.Str -> {
                JsonPrimitive(value.value)
            }

            is YamlValue.Seq -> {
                JsonArray(value.items.map(::json))
            }

            is YamlValue.Map -> {
                JsonObject(value.entries.mapValues { json(it.value) })
            }
        }
}
