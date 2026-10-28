package fs

import kotlinx.io.files.Path
import kotlin.random.Random

/** Rust `env::temp_dir`: `TMPDIR` or `/tmp` on Unix, GetTempPathW on Windows. Nothing is created. */
fun systemTempDir(): Path = tempRoot()

/** Rust `tempfile::TempDir`: a fresh directory under the system's temp directory, removed with everything in it on [close]. */
class TempDir private constructor(
    val path: Path,
) : AutoCloseable {
    // Best effort, as Rust's drop is: a failed cleanup must not hide the error that ended the work.
    override fun close() {
        runCatching { removeAny(path) }
    }

    companion object {
        /** Create one named [prefix] and six random characters; fails as [FsException]. */
        fun create(prefix: String = ".tmp"): TempDir = createIn(tempRoot(), prefix)

        /**
         * Rust `tempfile::tempdir_in`: the same, inside [dir]. A directory staged next to where its contents end up
         * can be renamed into place, which one under `$TMPDIR` on another filesystem cannot.
         */
        fun createIn(
            dir: Path,
            prefix: String = ".tmp",
        ): TempDir = freshName(dir, prefix) { path -> if (makeDir(path)) TempDir(path) else null }
    }
}

/**
 * What [make] creates at the first free name in [dir], [prefix] and six random characters (Rust tempfile's names).
 * A taken name is tried again; any other failure is an error about [dir].
 */
internal inline fun <T : Any> freshName(
    dir: Path,
    prefix: String,
    make: (Path) -> T?,
): T {
    repeat(NAME_ATTEMPTS) {
        val name = prefix + (1..NAME_LENGTH).map { NAME_CHARS[Random.nextInt(NAME_CHARS.length)] }.joinToString("")
        make(joinPath(dir, name))?.let { return it }
        val code = lastOsError()
        if (osKind(code) != FsException.Kind.AlreadyExists) throw FsException.ofErrno(dir, code)
    }
    throw FsException.other(dir, "no free temp file name")
}

private const val NAME_ATTEMPTS = 8
private const val NAME_CHARS = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
private const val NAME_LENGTH = 6
