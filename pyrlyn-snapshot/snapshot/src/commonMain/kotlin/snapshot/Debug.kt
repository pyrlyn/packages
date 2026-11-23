// A multi-line rendering of `toString()`, the way insta's debug snapshots use `{:#?}`.
package snapshot

private const val INDENT = "    "

/**
 * [value]'s `toString()` with one element per line: data classes, lists, maps and sets are broken
 * after each opening bracket and comma. Commas inside string values are not told apart.
 */
public fun debugFormat(value: Any?): String {
    val src = value.toString()
    val closers = mapOf('(' to ')', '[' to ']', '{' to '}')
    var depth = 0
    return buildString {
        var i = 0
        while (i < src.length) {
            val c = src[i]
            when {
                c in closers && src.getOrNull(i + 1) == closers[c] -> {
                    append(c).append(src[i + 1])
                    i++
                }

                c in closers -> {
                    depth++
                    append(c).append('\n').append(INDENT.repeat(depth))
                }

                c in closers.values && depth > 0 -> {
                    depth--
                    append(",\n").append(INDENT.repeat(depth)).append(c)
                }

                c == ',' && depth > 0 && src.getOrNull(i + 1) == ' ' -> {
                    append(",\n").append(INDENT.repeat(depth))
                    i++
                }

                else -> {
                    append(c)
                }
            }
            i++
        }
    }
}
