package pyrlyn.taskboard

import kotlin.time.Instant

/** One field a base-rev merge could not apply: what the caller saw, what is there now, what the caller wants. */
data class FieldClash(
    val field: String,
    val base: kotlinx.serialization.json.JsonElement,
    val current: kotlinx.serialization.json.JsonElement,
    val requested: kotlinx.serialization.json.JsonElement,
)

/** The failures every front end reports, with the same stable [code]. */
sealed class BoardError(
    val code: String,
    val message: String,
) {
    /** No such task (or, in a batch, no such `ref`). */
    class NotFound(
        val what: String,
    ) : BoardError("not_found", "no task $what")

    /** `if_rev` did not match, or a `base_rev` merge clashed; [fields] is empty for a strict `if_rev` miss. */
    class Conflict(
        val current: Versioned<Task>,
        val fields: List<FieldClash>,
    ) : BoardError(
            "conflict",
            if (fields.isEmpty()) "${current.value.id} changed: its rev is ${current.rev}" else "${current.value.id} changed in " + fields.joinToString { it.field },
        )

    /** Somebody else holds a live lease; [until] is when it ends (null: an assignee with no lease). */
    class Claimed(
        val id: TaskId,
        val holder: String,
        val until: Instant?,
    ) : BoardError("claimed", "$id is claimed by $holder" + (until?.let { " until $it" } ?: ""))

    /** The request is malformed or breaks a rule; the message says which. */
    class Invalid(
        message: String,
    ) : BoardError("invalid", message)

    /** An idempotency key was sent again with a different request. */
    class KeyReused(
        val key: String,
    ) : BoardError("key_reused", "idempotency key \"$key\" was used for a different request")

    /** The board lock was not free within the timeout; retry. */
    class Busy(
        message: String = "the board is busy; retry",
    ) : BoardError("busy", message)

    /** A tracker call failed. Local writes warn instead of failing with it. */
    class Remote(
        message: String,
    ) : BoardError("remote", message)
}

/** Thrown by every [Board] call that fails. [opIndex] names the failing operation of a batch. */
class BoardException(
    val error: BoardError,
    val opIndex: Int? = null,
) : Exception(error.message.let { if (opIndex != null) "op $opIndex: $it" else it }) {
    val code get() = error.code
}

internal fun invalid(message: String): Nothing = throw BoardException(BoardError.Invalid(message))
