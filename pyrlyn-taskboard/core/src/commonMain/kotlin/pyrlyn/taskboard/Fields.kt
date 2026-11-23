package pyrlyn.taskboard

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive

/**
 * The fields of a task that journal diffs and base-rev merges compare, by wire name. A field at its default is left
 * out, so "absent" and "default" are the same thing and a diff never reports `""` becoming nothing.
 */
internal fun Task.fieldValues(): Map<String, JsonElement> =
    buildMap {
        put("title", JsonPrimitive(title))
        if (description.isNotEmpty()) put("description", JsonPrimitive(description))
        put("status", JsonPrimitive(status.word))
        put("priority", JsonPrimitive(priority))
        parent?.let { put("parent", JsonPrimitive(it.toString())) }
        assignee?.let { put("assignee", JsonPrimitive(it)) }
        if (blockedBy.isNotEmpty()) put("blocked_by", ids(blockedBy))
        if (conflictsWith.isNotEmpty()) put("conflicts_with", ids(conflictsWith))
        if (parallel) put("parallel", JsonPrimitive(true))
        report?.let { put("report", JsonPrimitive(it)) }
        remote?.let { put("remote", JsonPrimitive(it)) }
        putAll(extra)
    }

private fun ids(list: List<TaskId>) = JsonArray(list.map { JsonPrimitive(it.toString()) })

/** `name -> [old, new]` for every field that differs; a missing side is JSON null. */
internal fun diff(
    old: Map<String, JsonElement>,
    new: Map<String, JsonElement>,
): Map<String, FieldChange> =
    buildMap {
        for (name in (old.keys + new.keys)) {
            val before = old[name] ?: JsonNull
            val after = new[name] ?: JsonNull
            if (before != after) put(name, FieldChange(before, after))
        }
    }

/** Everything but the clock fields: two tasks equal here are the same write. */
internal fun Task.sameContent(other: Task) = copy(updated = other.updated) == other
