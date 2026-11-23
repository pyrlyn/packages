// A line diff in insta's layout: old and new line numbers, a gutter, `-` and `+` marks.
package snapshot

private const val CONTEXT = 3
private const val NUMBER_WIDTH = 5
private val GUTTER = " ".repeat(NUMBER_WIDTH * 2 + 2)
private val RULE_LEFT = "─".repeat(NUMBER_WIDTH * 2 + 2)
private val RULE_RIGHT = "─".repeat(NUMBER_WIDTH * 2 * 2)

private sealed interface Op {
    data class Same(
        val old: Int,
        val new: Int,
        val line: String,
    ) : Op

    data class Removed(
        val old: Int,
        val line: String,
    ) : Op

    data class Added(
        val new: Int,
        val line: String,
    ) : Op
}

/** The diff from [old] to [new], with [CONTEXT] unchanged lines around each change. */
public fun lineDiff(
    old: String,
    new: String,
): String {
    val ops = ops(old.split('\n'), new.split('\n'))
    val changed = ops.indices.filter { ops[it] !is Op.Same }
    return buildString {
        append("-old snapshot\n+new results\n")
        append(RULE_LEFT).append("┬").append(RULE_RIGHT).append('\n')
        var lastShown = -1
        for (i in ops.indices) {
            if (changed.none { kotlin.math.abs(it - i) <= CONTEXT }) continue
            if (lastShown >= 0 && i > lastShown + 1) append(GUTTER).append("┆\n")
            append(row(ops[i]))
            lastShown = i
        }
        append(RULE_LEFT).append("┴").append(RULE_RIGHT).append('\n')
    }
}

private fun num(n: Int?): String = (n?.plus(1)?.toString() ?: "").padStart(NUMBER_WIDTH)

private fun row(op: Op): String =
    when (op) {
        is Op.Same -> "${num(op.old)} ${num(op.new)} │ ${op.line}\n"
        is Op.Removed -> "${num(op.old)} ${num(null)} │-${op.line}\n"
        is Op.Added -> "${num(null)} ${num(op.new)} │+${op.line}\n"
    }

/** A longest-common-subsequence diff; snapshots are small, so the quadratic table is fine. */
private fun ops(
    a: List<String>,
    b: List<String>,
): List<Op> {
    val lcs = Array(a.size + 1) { IntArray(b.size + 1) }
    for (i in a.indices.reversed()) {
        for (j in b.indices.reversed()) {
            lcs[i][j] = if (a[i] == b[j]) lcs[i + 1][j + 1] + 1 else maxOf(lcs[i + 1][j], lcs[i][j + 1])
        }
    }
    val out = mutableListOf<Op>()
    var i = 0
    var j = 0
    while (i < a.size || j < b.size) {
        when {
            i < a.size && j < b.size && a[i] == b[j] -> out += Op.Same(i++, j++, a[i - 1])
            i < a.size && (j == b.size || lcs[i + 1][j] >= lcs[i][j + 1]) -> out += Op.Removed(i++, a[i - 1])
            else -> out += Op.Added(j++, b[j - 1])
        }
    }
    return out
}
