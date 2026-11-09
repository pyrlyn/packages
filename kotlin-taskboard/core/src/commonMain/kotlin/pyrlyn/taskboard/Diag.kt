package pyrlyn.taskboard

/** What one call noticed and recovered from; each distinct message goes to [warn] once and into the call's warnings. */
internal class Diag(
    private val warn: (String) -> Unit,
) {
    val messages = mutableListOf<String>()

    fun add(message: String) {
        if (message in messages) return
        messages += message
        // A broken sink must not fail the board operation it reports on.
        runCatching { warn(message) }
    }

    /** Reads a state blob with [decode]; one that cannot be read is reported and replaced by [fallback]. */
    fun <T> recover(
        name: String,
        fallback: () -> T,
        decode: () -> T,
    ): T =
        try {
            decode()
        } catch (_: BoardException) {
            add("board state \"$name\" was unreadable and is rebuilt on the next write")
            fallback()
        }

    /** [liveness] that never throws: a failure is reported and counts as "no answer", so lease expiry decides. */
    // Any failure of a host-supplied hook must be survivable, whatever its type.
    @Suppress("TooGenericExceptionCaught")
    fun guard(liveness: ((String) -> Boolean?)?): ((String) -> Boolean?)? =
        liveness?.let { hook ->
            { agent ->
                try {
                    hook(agent)
                } catch (e: Exception) {
                    add("liveness hook failed (${e::class.simpleName}: ${e.message}); lease expiry decides")
                    null
                }
            }
        }
}
