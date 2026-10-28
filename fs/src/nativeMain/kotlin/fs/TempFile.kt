package fs

import kotlinx.io.files.Path

/**
 * Rust `tempfile::NamedTempFile`: a fresh file only its owner can read, written piece by piece and then renamed into
 * place with [persist]. Closed without that, it is removed, so a download that fails half way leaves nothing behind.
 */
class TempFile private constructor(
    val path: Path,
    private var file: WriteHandle?,
) : AutoCloseable {
    private var persisted = false

    /** Append the first [size] bytes of [bytes]. */
    fun write(
        bytes: ByteArray,
        size: Int = bytes.size,
    ) {
        writeAll(checkNotNull(file) { "temp file is closed" }, bytes, path, size)
    }

    /** Rename the file to [dest], replacing what is there; it is no longer removed on [close]. */
    fun persist(dest: Path) {
        closeFile()
        ensure(renameEntry(path, dest), dest)
        persisted = true
    }

    // Best effort, as Rust's drop is: a failed cleanup must not hide the error that ended the work.
    override fun close() {
        closeFile()
        if (!persisted) unlinkEntry(path)
    }

    private fun closeFile() {
        file?.close()
        file = null
    }

    companion object {
        private const val OWNER_ONLY = 0b110_000_000

        /** Create one in [dir] named `.tmp` and six random characters; fails as [FsException]. */
        fun createIn(dir: Path): TempFile = freshName(dir, ".tmp") { path -> openWrite(path, Create.New, OWNER_ONLY)?.let { TempFile(path, it) } }
    }
}
