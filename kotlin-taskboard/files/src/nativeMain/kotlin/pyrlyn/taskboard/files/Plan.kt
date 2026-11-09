package pyrlyn.taskboard.files

import fs.bytesIfExists
import pyrlyn.taskboard.BoardError
import pyrlyn.taskboard.BoardException
import pyrlyn.taskboard.Commit
import pyrlyn.taskboard.Rev
import pyrlyn.taskboard.TaskId

internal fun invalid(message: String): Nothing = throw BoardException(BoardError.Invalid(message))

/** Every step of a commit, in the order they are applied, and the revs its tasks will have. */
internal class Planned(
    val txn: Txn,
    val revs: Map<TaskId, Rev>,
)

/**
 * Works out what [commit] does to the files seen in [scan], failing with `invalid` before anything is touched.
 * Task files go first, then the unlinks of their old copies (so a task is never absent), then the state blobs,
 * then the appended lines.
 */
internal fun planCommit(
    board: BoardDir,
    scan: Scan,
    commit: Commit,
): Planned {
    val writes = mutableListOf<Effect.Write>()
    val unlinks = mutableListOf<String>()
    val revs = LinkedHashMap<TaskId, Rev>()
    for (task in commit.put) {
        TaskFile.unwritable(task)?.let { invalid(it) }
        refuseUnreadable(board, scan, task.id)
        val text = TaskFile.render(task)
        writes += Effect.Write(board.taskPath(task), text)
        revs[task.id] = BoardDir.revOf(task.status, text.encodeToByteArray())
        unlinks += scan.copies[task.id].orEmpty()
    }
    for (id in commit.remove) {
        refuseUnreadable(board, scan, id)
        unlinks += scan.copies[id].orEmpty()
    }
    // Names are compared case-insensitively: on a case-insensitive file system the old and the new spelling are one
    // file, and unlinking it would delete what was just written.
    val doomed = unlinks.distinct().filter { gone -> writes.none { it.path.equals(gone, ignoreCase = true) } }
    return Planned(Txn(writes + doomed.map { Effect.Unlink(it) } + stateEffects(board, commit)), revs)
}

private fun refuseUnreadable(
    board: BoardDir,
    scan: Scan,
    id: TaskId,
) {
    val file = scan.unreadable[id]?.firstOrNull() ?: return
    invalid("$id cannot be written: ${board.pathOf(file)} carries its id but cannot be read; fix or delete it first")
}

private fun stateEffects(
    board: BoardDir,
    commit: Commit,
): List<Effect> {
    val blobs = commit.state.map { (name, text) -> board.statePath(name).let { if (text == null) Effect.Unlink(it) else Effect.Write(it, text) } }
    val appends =
        commit.append.map { (name, lines) ->
            if (lines.any { '\n' in it }) invalid("a line appended to $name contains a newline")
            val rel = board.statePath(name)
            // An appended blob starts from what the commit's own `state` leaves, so the replay point is exact.
            val from = if (name in commit.state) commit.state[name]?.encodeToByteArray()?.size ?: 0 else bytesIfExists(board.pathOf(rel))?.size ?: 0
            Effect.Append(rel, from, lines)
        }
    return blobs + appends
}
