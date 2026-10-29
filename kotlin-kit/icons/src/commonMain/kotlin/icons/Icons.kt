// Icons for what a status-line verb is doing, and the gutter they sit in: the port of the operation-icons crate.
//
// Tone-agnostic: a caller that has its own fallback icon per line kind (success, warning, ...) keeps that table and
// uses this one first.
package icons

/**
 * Icons for what a verb is doing, looked up before a line kind's own icon.
 *
 * Matched as substrings of the lowercased verb, in order, so `installing`, `installed` and `already installed` share
 * one row. `uninstall` comes before `install` because it contains it.
 */
val OPERATION_ICONS: List<Pair<String, String>> =
    listOf(
        "uninstall" to "🧹",
        "remov" to "🧹",
        "prun" to "🧹",
        "install" to "📦",
        "upgrad" to "⏫",
        "updat" to "⏫",
        "download" to "⏬",
        "fetch" to "⏬",
        "link" to "🔗",
        "roll" to "⏪",
        "search" to "🔍",
        "doctor" to "🩺",
    )

/**
 * Columns every icon is padded to. Every icon is one code point that is wide by default: a narrow symbol made wide
 * by U+FE0F (`⬆️`, `⚠️`) is two columns to [displayWidth] but one to a terminal that ignores the selector, which then
 * draws the glyph over the space after it and shifts the verb left. Each icon is still measured rather than assumed,
 * so a narrower one added later pads out instead of pulling its line left.
 */
const val ICON_WIDTH: Int = 2

/** The icon of the first row of [OPERATION_ICONS] whose key [verb] contains. */
fun icon(verb: String): String? = iconIn(OPERATION_ICONS, verb)

/** Like [icon] over a caller's own rows, so a program can add verbs of its own after the shared ones. */
fun iconIn(
    rows: List<Pair<String, String>>,
    verb: String,
): String? {
    val lower = verb.lowercase()
    return rows.firstOrNull { (key, _) -> key in lower }?.second
}

/** Blanks that bring [icon] up to [ICON_WIDTH] columns. */
fun padding(icon: String): Int = (ICON_WIDTH - displayWidth(icon)).coerceAtLeast(0)

/**
 * The gutter in front of a verb column: the icon, its padding and one space, or as many blanks when the line has
 * none, so lines with and without one align.
 */
fun gutter(icon: String?): String {
    val shown = icon.orEmpty()
    return shown + " ".repeat(padding(shown)) + " "
}
