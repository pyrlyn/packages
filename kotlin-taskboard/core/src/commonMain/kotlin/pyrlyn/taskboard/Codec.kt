package pyrlyn.taskboard

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * The one wire schema of the CLI `--json`, the MCP tools and JSON-RPC: snake_case names, task fields as rtok's task
 * JSON has them (defaults left out, times as unix seconds), plus `rev`, `lease`, `report` and `remote`. Everything
 * read from JSON fails with `BoardException(invalid)`.
 */
object Codec {
    val json: Json = Json { ignoreUnknownKeys = true }

    fun parse(text: String): JsonElement =
        try {
            json.parseToJsonElement(text)
        } catch (e: SerializationException) {
            invalid("not JSON: ${e.message}")
        } catch (e: IllegalArgumentException) {
            invalid("not JSON: ${e.message}")
        }

    fun parseObject(text: String): JsonObject = parse(text) as? JsonObject ?: invalid("expected a JSON object")

    // ---- tasks

    /** The wire form of a task. A store passes [withExtra] to keep the keys it does not know. */
    fun taskToJson(
        task: Task,
        withExtra: Boolean = false,
    ): JsonObject =
        buildJsonObject {
            put("id", task.id.toString())
            put("title", task.title)
            if (task.description.isNotEmpty()) put("description", task.description)
            put("status", task.status.word)
            task.parent?.let { put("parent", it.toString()) }
            put("created_at", task.created.epochSeconds)
            put("updated_at", task.updated.epochSeconds)
            if (task.priority != DEFAULT_PRIORITY) put("priority", task.priority)
            task.assignee?.let { put("assignee", it) }
            if (task.blockedBy.isNotEmpty()) putIds("blocked_by", task.blockedBy)
            if (task.conflictsWith.isNotEmpty()) putIds("conflicts_with", task.conflictsWith)
            if (task.parallel) put("parallel", true)
            task.report?.let { put("report", it) }
            task.remote?.let { put("remote", it) }
            if (withExtra) task.extra.forEach { (k, v) -> put(k, v) }
        }

    /** The task and its rev (and lease) in one object, the shape of every task a front end returns. */
    fun versionedToJson(
        task: Versioned<Task>,
        lease: Lease? = null,
    ): JsonObject =
        JsonObject(
            taskToJson(task.value) +
                buildMap {
                    put("rev", JsonPrimitive(task.rev.value))
                    lease?.let { put("lease", leaseToJson(it)) }
                },
        )

    private val known =
        setOf(
            "id",
            "title",
            "description",
            "status",
            "parent",
            "created_at",
            "updated_at",
            "priority",
            "assignee",
            "blocked_by",
            "conflicts_with",
            "parallel",
            "report",
            "remote",
        )

    /** Reads what [taskToJson] wrote. Unknown keys become `extra`, except those in [ignore] (`rev`, `lease`). */
    fun taskFromJson(
        o: JsonObject,
        ignore: Set<String> = emptySet(),
    ): Task {
        val created = Instant.fromEpochSeconds(o.long("created_at"))
        return Task(
            id = TaskId.parse(o.string("id")),
            title = o.string("title"),
            description = o.optString("description") ?: "",
            status = Status.parse(o.string("status")),
            priority = o.optInt("priority") ?: DEFAULT_PRIORITY,
            parent = o.optString("parent")?.let(TaskId::parse),
            assignee = o.optString("assignee"),
            blockedBy = o.optIds("blocked_by"),
            conflictsWith = o.optIds("conflicts_with"),
            parallel = o.optBool("parallel") ?: false,
            report = o.optString("report"),
            remote = o.optString("remote"),
            created = created,
            updated = o.optLong("updated_at")?.let(Instant::fromEpochSeconds) ?: created,
            extra = o.filterKeys { it !in known && it !in ignore },
        )
    }

    // ---- views

    fun leaseToJson(l: Lease): JsonObject =
        buildJsonObject {
            put("agent", l.agent)
            put("acquired", l.acquired.epochSeconds)
            put("expires", l.expires.epochSeconds)
        }

    fun leaseFromJson(o: JsonObject) = Lease(o.string("agent"), Instant.fromEpochSeconds(o.long("acquired")), Instant.fromEpochSeconds(o.long("expires")))

    fun cardToJson(c: Card): JsonObject =
        buildJsonObject {
            put("id", c.id.toString())
            put("title", c.title)
            put("status", c.status.word)
            put("priority", c.priority)
            put("parent", c.parent?.toString())
            put("assignee", c.assignee)
            putIds("blocked_by", c.blockedBy)
            putIds("conflicts_with", c.conflictsWith)
            put("parallel", c.parallel)
            put("ready", c.ready)
            put("stale", c.stale)
            put("lease", c.lease?.let(::leaseToJson) ?: JsonNull)
            put("rev", c.rev.value)
            put("remote", c.remote)
        }

    fun viewToJson(v: BoardView): JsonObject =
        buildJsonObject {
            put("seq", v.seq)
            putJsonArray("columns") {
                v.columns.forEach { col ->
                    add(
                        buildJsonObject {
                            put("status", col.status.word)
                            put("tasks", JsonArray(col.cards.map(::cardToJson)))
                        },
                    )
                }
            }
            putJsonArray("warnings") { v.warnings.forEach { add(JsonPrimitive(it)) } }
        }

    fun changeToJson(c: Change): JsonObject =
        buildJsonObject {
            put("seq", c.seq)
            put("at", c.at.epochSeconds)
            put("actor", c.actor)
            put("op", c.op)
            put("id", c.id.toString())
            put("rev", c.rev?.value)
            put("fields", JsonObject(c.fields.mapValues { (_, f) -> JsonArray(listOf(f.old, f.new)) }))
            if (c.external) put("external", true)
        }

    fun changeFromJson(o: JsonObject): Change =
        Change(
            seq = o.long("seq"),
            at = Instant.fromEpochSeconds(o.long("at")),
            actor = o.optString("actor"),
            op = o.string("op"),
            id = TaskId.parse(o.string("id")),
            rev = o.optString("rev")?.let(::Rev),
            fields =
                (o["fields"] as? JsonObject ?: JsonObject(emptyMap())).mapValues { (name, v) ->
                    val pair = v as? JsonArray
                    if (pair == null || pair.size != 2) invalid("change field \"$name\" is not [old, new]")
                    FieldChange(pair[0], pair[1])
                },
            external = o.optBool("external") ?: false,
        )

    fun pageToJson(p: ChangePage): JsonObject =
        buildJsonObject {
            put("seq", p.seq)
            put("changes", JsonArray(p.changes.map(::changeToJson)))
            put("reset", p.reset)
        }

    fun resultToJson(r: OpResult): JsonObject =
        JsonObject(
            versionedToJson(r.task, r.lease) +
                buildMap {
                    put("changed", JsonPrimitive(r.changed))
                    r.tookOverFrom?.let { put("took_over_from", JsonPrimitive(it)) }
                    if (r.conflictsInProgress.isNotEmpty()) put("conflicts_in_progress", JsonArray(r.conflictsInProgress.map { JsonPrimitive(it.toString()) }))
                },
        )

    fun resultFromJson(o: JsonObject): OpResult =
        OpResult(
            task = Versioned(taskFromJson(o, setOf("rev", "lease", "changed", "took_over_from", "conflicts_in_progress")), Rev(o.string("rev"))),
            changed = o.optBool("changed") ?: true,
            lease = (o["lease"] as? JsonObject)?.let(::leaseFromJson),
            tookOverFrom = o.optString("took_over_from"),
            conflictsInProgress = o.optIds("conflicts_in_progress"),
        )

    fun writtenToJson(w: Written): JsonObject =
        buildJsonObject {
            put("results", JsonArray(w.results.map(::resultToJson)))
            put("seq", w.seq)
            if (w.replayed) put("replayed", true)
        }

    fun writtenFromJson(o: JsonObject): Written =
        Written(
            results = (o["results"] as? JsonArray ?: invalid("\"results\" is required")).map { resultFromJson(it as? JsonObject ?: invalid("a result is an object")) },
            seq = o.long("seq"),
            replayed = o.optBool("replayed") ?: false,
        )

    fun errorToJson(
        e: BoardError,
        opIndex: Int? = null,
    ): JsonObject =
        buildJsonObject {
            put("code", e.code)
            put("message", e.message)
            put(
                "data",
                when (e) {
                    is BoardError.Conflict -> {
                        buildJsonObject {
                            put("current", versionedToJson(e.current))
                            put(
                                "fields",
                                JsonArray(
                                    e.fields.map {
                                        buildJsonObject {
                                            put("field", it.field)
                                            put("base", it.base)
                                            put("current", it.current)
                                            put("requested", it.requested)
                                        }
                                    },
                                ),
                            )
                        }
                    }

                    is BoardError.Claimed -> {
                        buildJsonObject {
                            put("id", e.id.toString())
                            put("holder", e.holder)
                            put("until", e.until?.epochSeconds)
                        }
                    }

                    is BoardError.NotFound -> {
                        buildJsonObject { put("what", e.what) }
                    }

                    is BoardError.KeyReused -> {
                        buildJsonObject { put("key", e.key) }
                    }

                    else -> {
                        if (opIndex == null) JsonNull else buildJsonObject {}
                    }
                }.let { data -> if (opIndex != null && data is JsonObject) JsonObject(data + ("op" to JsonPrimitive(opIndex))) else data },
            )
        }

    // ---- requests

    fun writeFromJson(o: JsonObject) = Write(ifRev = o.optString("if_rev")?.let(::Rev), baseRev = o.optString("base_rev")?.let(::Rev), key = o.optString("key"))

    /** One step of `task_batch`: `{"op":"create|update|move|claim|release", "id"|"ref", ...}`. */
    fun opFromJson(o: JsonObject): Op {
        val ifRev = o.optString("if_rev")?.let(::Rev)
        val baseRev = o.optString("base_rev")?.let(::Rev)
        return when (val kind = o.string("op")) {
            "create" -> {
                Op.Create(
                    task =
                        NewTask(
                            title = o.string("title"),
                            description = o.optString("description") ?: "",
                            parent = o.optString("parent")?.let(TaskId::parse) ?: o.optString("parent_ref")?.let(::Ref),
                            priority = o.optInt("priority") ?: DEFAULT_PRIORITY,
                            blockedBy = o.optIds("blocked_by"),
                            conflictsWith = o.optIds("conflicts_with"),
                            parallel = o.optBool("parallel") ?: false,
                            remote = o.optString("remote"),
                        ),
                    ref = o.optString("ref"),
                    ifRev = ifRev,
                    baseRev = baseRev,
                )
            }

            "update" -> {
                Op.Update(o.target(), patchFromJson(o), ifRev, baseRev)
            }

            "move" -> {
                Op.Move(o.target(), Status.parse(o.string("status")), o.optString("report"), o.optBool("force") ?: false, ifRev, baseRev)
            }

            "claim" -> {
                Op.Claim(o.optTarget(), o.string("agent"), o.optLong("lease_s")?.seconds, ifRev, baseRev)
            }

            "release" -> {
                Op.Release(o.target(), o.string("agent"), o.optBool("force") ?: false, ifRev, baseRev)
            }

            else -> {
                invalid("unknown op \"$kind\": use create, update, move, claim or release")
            }
        }
    }

    fun patchFromJson(o: JsonObject) =
        Patch(
            title = o.optString("title"),
            description = o.optString("description"),
            priority = o.optInt("priority"),
            parent = o.optString("parent")?.let(TaskId::parse),
            clearParent = o["parent"] is JsonNull,
            parallel = o.optBool("parallel"),
            remote = o.optString("remote"),
            addBlockedBy = o.optIds("add_blocked_by").toSet(),
            removeBlockedBy = o.optIds("remove_blocked_by").toSet(),
            addConflictsWith = o.optIds("add_conflicts").toSet(),
            removeConflictsWith = o.optIds("remove_conflicts").toSet(),
        )

    fun opToJson(op: Op): JsonObject =
        buildJsonObject {
            when (op) {
                is Op.Create -> {
                    put("op", "create")
                    put("ref", op.ref)
                    put("title", op.task.title)
                    put("description", op.task.description)
                    when (val p = op.task.parent) {
                        is TaskId -> put("parent", p.toString())
                        is Ref -> put("parent_ref", p.name)
                        null -> Unit
                    }
                    put("priority", op.task.priority)
                    putIds("blocked_by", op.task.blockedBy)
                    putIds("conflicts_with", op.task.conflictsWith)
                    put("parallel", op.task.parallel)
                    put("remote", op.task.remote)
                }

                is Op.Update -> {
                    put("op", "update")
                    putTarget(op.target)
                    val p = op.patch
                    put("title", p.title)
                    put("description", p.description)
                    put("priority", p.priority)
                    if (p.clearParent) put("parent", JsonNull) else put("parent", p.parent?.toString())
                    put("parallel", p.parallel)
                    put("remote", p.remote)
                    putIds("add_blocked_by", p.addBlockedBy.sorted())
                    putIds("remove_blocked_by", p.removeBlockedBy.sorted())
                    putIds("add_conflicts", p.addConflictsWith.sorted())
                    putIds("remove_conflicts", p.removeConflictsWith.sorted())
                }

                is Op.Move -> {
                    put("op", "move")
                    putTarget(op.target)
                    put("status", op.status.word)
                    put("report", op.report)
                    put("force", op.force)
                }

                is Op.Claim -> {
                    put("op", "claim")
                    op.target?.let { putTarget(it) }
                    put("agent", op.agent)
                    put("lease_s", op.lease?.inWholeSeconds)
                }

                is Op.Release -> {
                    put("op", "release")
                    putTarget(op.target)
                    put("agent", op.agent)
                    put("force", op.force)
                }
            }
            put("if_rev", op.ifRev?.value)
            put("base_rev", op.baseRev?.value)
        }

    /** The text an idempotency key is checked against: the same request always encodes to the same string. */
    internal fun requestText(ops: List<Op>): String = JsonArray(ops.map(::opToJson)).toString()

    // ---- helpers

    private fun kotlinx.serialization.json.JsonObjectBuilder.putIds(
        name: String,
        ids: Collection<TaskId>,
    ) = put(name, JsonArray(ids.map { JsonPrimitive(it.toString()) }))

    private fun kotlinx.serialization.json.JsonObjectBuilder.putTarget(t: Target) =
        when (t) {
            is TaskId -> put("id", t.toString())
            is Ref -> put("ref", t.name)
        }

    private fun JsonObject.target(): Target = optTarget() ?: invalid("an op needs an \"id\" or a \"ref\"")

    private fun JsonObject.optTarget(): Target? = optString("id")?.let(TaskId::parse) ?: optString("ref")?.let(::Ref)

    private fun JsonObject.prim(name: String): JsonPrimitive? =
        when (val v = this[name]) {
            null, JsonNull -> null
            is JsonPrimitive -> v
            else -> invalid("\"$name\" must be a plain value")
        }

    private fun JsonObject.optString(name: String): String? = prim(name)?.let { if (it.isString) it.content else invalid("\"$name\" must be a string") }

    private fun JsonObject.string(name: String) = optString(name) ?: invalid("\"$name\" is required")

    private fun JsonObject.optLong(name: String): Long? = prim(name)?.let { it.longOrNull ?: invalid("\"$name\" must be an integer") }

    private fun JsonObject.long(name: String) = optLong(name) ?: invalid("\"$name\" is required")

    private fun JsonObject.optInt(name: String): Int? = prim(name)?.let { it.intOrNull ?: invalid("\"$name\" must be an integer") }

    private fun JsonObject.optBool(name: String): Boolean? = prim(name)?.let { it.booleanOrNull ?: invalid("\"$name\" must be true or false") }

    private fun JsonObject.optIds(name: String): List<TaskId> =
        when (val v = this[name]) {
            null, JsonNull -> emptyList()
            is JsonArray -> v.map { TaskId.parse((it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content ?: invalid("\"$name\" must list task ids")) }
            else -> invalid("\"$name\" must be a list of task ids")
        }
}
