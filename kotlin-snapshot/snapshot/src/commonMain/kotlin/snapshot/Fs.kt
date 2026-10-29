// The file and environment access the assertions need, per platform.
package snapshot

/** The file's text, or null when it cannot be read. */
internal expect fun readText(path: String): String?

/** Writes [text] to [path], replacing it; throws when it cannot. */
internal expect fun writeText(
    path: String,
    text: String,
)

/** Removes [path] when it exists. */
internal expect fun removeFile(path: String)

/** Creates [path] and its parents. */
internal expect fun makeDirs(path: String)

/** An environment variable, or null. */
internal expect fun env(name: String): String?
