// The assertions: file snapshots, debug snapshots and inline snapshots, named after insta's macros.
package snapshot

/**
 * Asserts that [actual] matches the snapshot [name] (`<dir>/<prefix>__<name>.snap`), like insta's
 * `assert_snapshot!`. A new or changed value is written per [SnapshotSettings.update]; a match
 * removes a stale `.snap.new`. [expression] goes into the header of a written file.
 */
public fun assertSnapshot(
    name: String,
    actual: String,
    expression: String? = null,
) {
    val settings = currentSettings()
    val value = normalize(settings.apply(actual))
    val path = "${settings.snapshotDir}/${settings.fileName(name)}"
    val pending = "$path.new"
    val old = readText(path)?.let { SnapFile.parse(it) }
    if (old != null && old.contents == value) {
        removeFile(pending)
        return
    }
    val header = LinkedHashMap(old?.header.orEmpty())
    settings.source?.let { header["source"] = SnapFile.quote(it) }
    expression?.let { header["expression"] = SnapFile.quote(it) }
    val file = SnapFile(header, value).render()
    val written =
        when (settings.update) {
            UpdateMode.Always -> {
                write(settings.snapshotDir, path, file)
                removeFile(pending)
                return
            }

            UpdateMode.New -> {
                pending.also { write(settings.snapshotDir, it, file) }
            }

            UpdateMode.No -> {
                null
            }
        }
    throw AssertionError(failure(name, path, old?.contents, value, written))
}

/** [assertSnapshot] of [debugFormat] of [value], like insta's `assert_debug_snapshot!`. */
public fun assertDebugSnapshot(
    name: String,
    value: Any?,
    expression: String? = null,
): Unit = assertSnapshot(name, debugFormat(value), expression)

/**
 * Asserts that [actual] equals the inline literal [expected], compared the way insta compares
 * `@"..."`: indentation, a leading blank line and trailing whitespace are ignored. Kotlin cannot
 * rewrite the call site, so a mismatch fails with the diff and the new literal to paste.
 */
public fun assertInlineSnapshot(
    actual: String,
    expected: String,
) {
    val value = normalize(currentSettings().apply(actual))
    val want = normalizeInline(expected)
    if (value == want) return
    val literal = value.lines().joinToString("\n") { if (it.isEmpty()) it else "    $it" }
    val quotes = "\"\"\""
    throw AssertionError(
        "Inline snapshot does not match.\n${lineDiff(want, value)}New literal:\n$quotes\n$literal\n$quotes",
    )
}

private fun write(
    dir: String,
    path: String,
    text: String,
) {
    makeDirs(dir)
    writeText(path, text)
}

private fun failure(
    name: String,
    path: String,
    old: String?,
    value: String,
    written: String?,
): String =
    buildString {
        if (old == null) {
            append("Snapshot '$name' is new: $path does not exist.\n")
            append(value).append('\n')
        } else {
            append("Snapshot '$name' does not match $path.\n")
            append(lineDiff(old, value))
        }
        if (written != null) append("Pending snapshot written to $written; review it and rename it to .snap,\n")
        append("or rerun with SNAPSHOT_UPDATE=always to accept.")
    }
