package pyrlyn.taskboard

/** Longest id prefix accepted; matches rtok's `TASK_PREFIX_MAX` so ids stay interchangeable. */
const val PREFIX_MAX = 8

/** Deepest nesting: `R5.1.2` is a subtask of a subtask. */
const val MAX_DEPTH = 3

/** What an operation points at: an existing task by id, or a task an earlier `Create` of the same batch named. */
sealed interface Target

/** The `ref` an `Op.Create` gave its task; resolved against the batch only, never against the board. */
data class Ref(
    val name: String,
) : Target

/** `<PREFIX><n>[.<n>[.<n>]]`: 1 to 8 ASCII letters, then 1 to 3 numbers counted from 1. */
class TaskId private constructor(
    val prefix: String,
    val path: List<UInt>,
) : Target,
    Comparable<TaskId> {
    fun child(n: UInt): TaskId = of(prefix, path + n)

    fun parent(): TaskId? = if (path.size > 1) TaskId(prefix, path.dropLast(1)) else null

    override fun toString() = prefix + path.joinToString(".")

    override fun equals(other: Any?) = other is TaskId && other.prefix == prefix && other.path == path

    override fun hashCode() = prefix.hashCode() * 31 + path.hashCode()

    override fun compareTo(other: TaskId): Int {
        prefix.compareTo(other.prefix).let { if (it != 0) return it }
        for (i in 0 until minOf(path.size, other.path.size)) path[i].compareTo(other.path[i]).let { if (it != 0) return it }
        return path.size.compareTo(other.path.size)
    }

    companion object {
        fun of(
            prefix: String,
            path: List<UInt>,
        ): TaskId {
            if (prefix.isEmpty() || prefix.length > PREFIX_MAX || !prefix.all { it in 'a'..'z' || it in 'A'..'Z' }) {
                invalid("task prefix \"$prefix\" must be 1 to $PREFIX_MAX ASCII letters")
            }
            if (path.isEmpty() || path.size > MAX_DEPTH) invalid("task id needs 1 to $MAX_DEPTH numbers, got ${path.size}")
            if (0u in path) invalid("task numbers start at 1")
            return TaskId(prefix.uppercase(), path)
        }

        fun top(
            prefix: String,
            n: UInt,
        ) = of(prefix, listOf(n))

        /** Throws `BoardException(invalid)` on anything that is not an id; ids come from files and JSON. */
        fun parse(raw: String): TaskId {
            val s = raw.trim()
            val split = s.indexOfFirst { !(it in 'a'..'z' || it in 'A'..'Z') }.let { if (it < 0) s.length else it }
            val rest = s.substring(split)
            if (rest.isEmpty()) invalid("task id \"$s\" has no number")
            val path =
                rest.split('.').map { part ->
                    if (part.isEmpty() || part.startsWith('0') || !part.all { it in '0'..'9' }) {
                        invalid("task id \"$s\": \"$part\" is not a number from 1")
                    }
                    part.toUIntOrNull() ?: invalid("task id \"$s\": \"$part\" is too large")
                }
            return of(s.substring(0, split), path)
        }

        fun parseOrNull(raw: String): TaskId? =
            try {
                parse(raw)
            } catch (_: BoardException) {
                null
            }
    }
}
