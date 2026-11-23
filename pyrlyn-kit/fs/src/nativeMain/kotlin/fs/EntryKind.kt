package fs

/** What is at a path, as `lstat(2)` sees it: a link is a link, never its target. */
enum class EntryKind { File, Dir, Symlink, Other }
