package cli

/** clap offers a candidate only when it is at least this similar. */
private const val CONFIDENCE = 0.7

/** Jaro averages three ratios. */
private const val PARTS = 3.0

private const val SURROGATE_BITS = 10
private const val SUPPLEMENTARY = 0x10000

/** strsim's Jaro similarity over code points, which clap ranks its suggestions by. */
internal fun jaro(
    a: String,
    b: String,
): Double {
    val x = codePoints(a)
    val y = codePoints(b)
    return when {
        x.isEmpty() && y.isEmpty() -> 1.0
        x.isEmpty() || y.isEmpty() -> 0.0
        else -> matched(x, y)
    }
}

private fun matched(
    x: List<Int>,
    y: List<Int>,
): Double {
    val range = maxOf(maxOf(x.size, y.size) / 2 - 1, 0)
    val xFlags = BooleanArray(x.size)
    val yFlags = BooleanArray(y.size)
    var matches = 0
    for (i in x.indices) {
        val lo = maxOf(i - range, 0)
        val j = (lo until minOf(y.size, i + range + 1)).firstOrNull { x[i] == y[it] && !yFlags[it] } ?: continue
        xFlags[i] = true
        yFlags[j] = true
        matches++
    }
    if (matches == 0) return 0.0
    val xs = x.indices.filter { xFlags[it] }.map { x[it] }
    val ys = y.indices.filter { yFlags[it] }.map { y[it] }
    val transpositions = xs.indices.count { xs[it] != ys[it] } / 2
    val m = matches.toDouble()
    return (m / x.size + m / y.size + (matches - transpositions) / m) / PARTS
}

private fun codePoints(s: String): List<Int> {
    val points = mutableListOf<Int>()
    var i = 0
    while (i < s.length) {
        val c = s[i]
        if (c.isHighSurrogate() && i + 1 < s.length && s[i + 1].isLowSurrogate()) {
            points += ((c - Char.MIN_HIGH_SURROGATE) shl SURROGATE_BITS) + (s[i + 1] - Char.MIN_LOW_SURROGATE) + SUPPLEMENTARY
            i += 2
        } else {
            points += c.code
            i++
        }
    }
    return points
}

/** clap's `did_you_mean`: the similar candidates, least similar first, so the best one is last. */
internal fun didYouMean(
    value: String,
    candidates: List<String>,
): List<String> =
    candidates
        .map { jaro(value, it) to it }
        .filter { it.first > CONFIDENCE }
        .sortedBy { it.first }
        .map { it.second }
