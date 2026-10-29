// Where snapshots live and how a new or changed one is handled, in the spirit of insta's settings.
package snapshot

/** What a failing or new snapshot does, read from `SNAPSHOT_UPDATE` (or insta's `INSTA_UPDATE`). */
public enum class UpdateMode {
    /** Write the `.snap` file and pass: accepting every change. */
    Always,

    /** Write a pending `<name>.snap.new` next to the `.snap` and fail. The local default. */
    New,

    /** Write nothing and fail. The default when `CI` is set. */
    No,
    ;

    public companion object {
        /** Parses an insta-style value: `always`/`1`, `new`, `no`/`0`; anything else is `auto`. */
        public fun parse(
            value: String?,
            ci: Boolean,
        ): UpdateMode =
            when (value?.trim()?.lowercase()) {
                "always", "1", "true", "force" -> Always
                "new" -> New
                "no", "0", "false" -> No
                else -> if (ci) No else New
            }

        /** The mode the environment asks for. */
        public fun fromEnv(): UpdateMode {
            val ci = env("CI")?.let { it.isNotEmpty() && it != "false" && it != "0" } ?: false
            return parse(env("SNAPSHOT_UPDATE") ?: env("INSTA_UPDATE"), ci)
        }
    }
}

/** Replaces every match of [pattern] before a value is compared or written, like insta's redactions. */
public data class Redaction(
    val pattern: Regex,
    val replacement: String,
)

/**
 * The settings an assertion uses. [snapshotDir] defaults to `SNAPSHOT_DIR` or `snapshots` under the
 * working directory; [prefix] is put before each name as `<prefix>__<name>.snap`, the way insta
 * prefixes the module path; [source] is written to the header.
 */
public data class SnapshotSettings(
    val snapshotDir: String = env("SNAPSHOT_DIR") ?: "snapshots",
    val prefix: String = "",
    val source: String? = null,
    val redactions: List<Redaction> = emptyList(),
    val update: UpdateMode = UpdateMode.fromEnv(),
) {
    /** These settings plus one more redaction. */
    public fun redact(
        pattern: String,
        replacement: String,
    ): SnapshotSettings = copy(redactions = redactions + Redaction(Regex(pattern), replacement))

    internal fun apply(text: String): String = redactions.fold(text) { acc, r -> acc.replace(r.pattern, r.replacement) }

    internal fun fileName(name: String): String = if (prefix.isEmpty()) "$name.snap" else "${prefix}__$name.snap"
}

private var current: SnapshotSettings? = null

/** The settings in effect: the innermost [withSettings], or the defaults. */
public fun currentSettings(): SnapshotSettings = current ?: SnapshotSettings()

/** Runs [block] with [settings] in effect, like insta's `with_settings!`. */
public fun <T> withSettings(
    settings: SnapshotSettings,
    block: () -> T,
): T {
    val previous = current
    current = settings
    try {
        return block()
    } finally {
        current = previous
    }
}
