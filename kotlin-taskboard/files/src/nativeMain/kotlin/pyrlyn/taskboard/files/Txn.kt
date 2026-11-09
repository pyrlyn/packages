package pyrlyn.taskboard.files

import fs.FsException
import fs.WriteOptions
import fs.bytesIfExists
import fs.removeFile
import fs.syncDir
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import pyrlyn.taskboard.BoardError
import pyrlyn.taskboard.BoardException

/** One step of a commit. Every step can be repeated: a redo after a crash replays them all. */
internal sealed interface Effect {
    /** The file, relative to the board folder, with `/` separators. */
    val path: String

    /** Replace the file with [text], atomically. */
    data class Write(
        override val path: String,
        val text: String,
    ) : Effect

    data class Unlink(
        override val path: String,
    ) : Effect

    /**
     * Keep the first [from] bytes of the file and add [lines] after them. Counting from [from] rather than adding to
     * the end is what stops a replay from adding them twice.
     */
    data class Append(
        override val path: String,
        val from: Int,
        val lines: List<String>,
    ) : Effect
}

/** What a commit will do, written to `.state/txn.json` before anything is touched. */
internal class Txn(
    val effects: List<Effect>,
) {
    fun encode(): String =
        buildJsonObject {
            put(
                "effects",
                JsonArray(
                    effects.map { e ->
                        buildJsonObject {
                            when (e) {
                                is Effect.Write -> {
                                    put("op", "write")
                                    put("path", e.path)
                                    put("text", e.text)
                                }

                                is Effect.Unlink -> {
                                    put("op", "unlink")
                                    put("path", e.path)
                                }

                                is Effect.Append -> {
                                    put("op", "append")
                                    put("path", e.path)
                                    put("from", e.from)
                                    put("lines", JsonArray(e.lines.map(::JsonPrimitive)))
                                }
                            }
                        }
                    },
                ),
            )
        }.toString()

    companion object {
        /** Throws [FormatException] when [text] is not a txn this library wrote. */
        fun decode(text: String): Txn =
            try {
                Txn(
                    Json
                        .parseToJsonElement(text)
                        .jsonObject
                        .getValue("effects")
                        .jsonArray
                        .map { effect(it.jsonObject) },
                )
            } catch (e: IllegalArgumentException) {
                throw FormatException(e.message ?: "unreadable JSON", e)
            } catch (e: NoSuchElementException) {
                throw FormatException(e.message ?: "a field is missing", e)
            }

        private fun effect(o: JsonObject): Effect {
            val path = o.getValue("path").jsonPrimitive.content
            return when (val op = o.getValue("op").jsonPrimitive.contentOrNull) {
                "write" -> Effect.Write(path, o.getValue("text").jsonPrimitive.content)
                "unlink" -> Effect.Unlink(path)
                "append" -> Effect.Append(path, o.getValue("from").jsonPrimitive.int, o.getValue("lines").jsonArray.map { it.jsonPrimitive.content })
                else -> throw FormatException("unknown step \"$op\"")
            }
        }
    }
}

/** What [FileStore]'s test hook throws to stand for the process dying between two effects. */
internal class SimulatedCrash(
    val applied: Int,
) : RuntimeException("simulated crash after $applied effects")

private val SAFE_PATH = Regex("(todo|in-progress|done|closed)/[^/\\\\:]+\\.md|\\.state/[a-z][a-z0-9_-]*\\.jsonl?")

/** Applies and replays [Txn]s on one board folder. */
internal class TxnLog(
    private val dir: BoardDir,
) {
    fun save(txn: Txn) {
        WriteOptions(createParent = true).write(dir.txnPath, txn.encode().encodeToByteArray())
    }

    /** Runs [txn]; [afterEach] gets the number of effects applied so far, 0 included, and may throw. */
    fun apply(
        txn: Txn,
        afterEach: (applied: Int) -> Unit = {},
    ) {
        afterEach(0)
        txn.effects.forEachIndexed { i, effect ->
            apply(effect)
            afterEach(i + 1)
        }
    }

    /** Takes the log away once everything in it has been applied. */
    fun finish() {
        try {
            removeFile(dir.txnPath)
        } catch (e: FsException) {
            if (e.kind != FsException.Kind.NotFound) throw e
        }
        syncDir(dir.pathOf(BoardDir.STATE_DIR))
    }

    /** Finishes a commit a crash interrupted, if there is one. Needs the board lock. */
    // BoardException carries no cause, so the reason goes into its message.
    @Suppress("SwallowedException")
    fun redo() {
        val bytes = bytesIfExists(dir.txnPath) ?: return
        val txn =
            try {
                Txn.decode(bytes.decodeToString())
            } catch (e: FormatException) {
                throw refused("it does not parse: ${e.message}")
            }
        // The file sits in a folder anyone can edit; a replay must never write or delete outside the board's own files.
        txn.effects.firstOrNull { !SAFE_PATH.matches(it.path) }?.let { throw refused("it names ${it.path}") }
        apply(txn)
        finish()
    }

    private fun refused(why: String) = BoardException(BoardError.Invalid("the board has an unfinished write in ${dir.txnPath} that cannot be redone: $why; check the file and delete it"))

    private fun apply(effect: Effect) {
        val path = dir.pathOf(effect.path)
        when (effect) {
            is Effect.Write -> {
                WriteOptions(createParent = true).write(path, effect.text.encodeToByteArray())
            }

            is Effect.Unlink -> {
                unlink(effect)
            }

            is Effect.Append -> {
                val kept = (bytesIfExists(path) ?: ByteArray(0)).let { it.copyOf(minOf(it.size, effect.from)) }
                val added = effect.lines.joinToString("") { it + "\n" }.encodeToByteArray()
                WriteOptions(createParent = true).write(path, kept + added)
            }
        }
    }

    private fun unlink(effect: Effect.Unlink) {
        try {
            removeFile(dir.pathOf(effect.path))
        } catch (e: FsException) {
            if (e.kind != FsException.Kind.NotFound) throw e
        }
    }
}
