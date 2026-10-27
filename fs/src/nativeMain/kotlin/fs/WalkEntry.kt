package fs

import kotlinx.io.files.Path

/** One entry [walk] reached: where it is, how far below the root, and what it is without following a link. */
data class WalkEntry(
    val path: Path,
    /** 0 for the root, 1 for its own entries. */
    val depth: Int,
    val kind: EntryKind,
)

/**
 * Rust `walkdir::WalkDir` with `follow_links(false)`: [root] first, then everything below it, each directory before
 * its contents and in the order the system lists it. Only [root] itself is followed when it is a link.
 *
 * [enter] false leaves an entry out and, for a directory, everything inside it (walkdir `filter_entry`). Nothing
 * deeper than [maxDepth] is reached; 0 yields the root alone. An entry or directory that cannot be read goes to
 * [onError] and is skipped, so a caller that wants walkdir's `filter_map(Result::ok)` passes `{}`.
 */
fun walk(
    root: Path,
    maxDepth: Int = Int.MAX_VALUE,
    onError: (FsException) -> Unit = { throw it },
    enter: (WalkEntry) -> Boolean = { true },
): Sequence<WalkEntry> =
    sequence {
        val kind = entryKind(root) ?: return@sequence onError(FsException.ofErrno(root))
        val top = WalkEntry(root, 0, if (kind == EntryKind.Symlink) entryKind(canon(root)) ?: kind else kind)
        if (enter(top)) visit(top, maxDepth, onError, enter)
    }

private suspend fun SequenceScope<WalkEntry>.visit(
    entry: WalkEntry,
    maxDepth: Int,
    onError: (FsException) -> Unit,
    enter: (WalkEntry) -> Boolean,
) {
    yield(entry)
    if (entry.kind != EntryKind.Dir || entry.depth >= maxDepth) return
    val names =
        try {
            listDir(entry.path)
        } catch (e: FsException) {
            return onError(e)
        }
    for (name in names) {
        val path = Path(entry.path, name)
        val kind = entryKind(path)
        if (kind == null) {
            onError(FsException.ofErrno(path))
            continue
        }
        val child = WalkEntry(path, entry.depth + 1, kind)
        if (enter(child)) visit(child, maxDepth, onError, enter)
    }
}
