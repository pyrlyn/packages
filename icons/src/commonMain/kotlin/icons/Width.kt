package icons

/**
 * The columns a terminal gives [text], as the unicode-width crate counts a `str`: two for East Asian Wide and
 * Fullwidth code points and for a narrow one followed by the emoji selector U+FE0F, none for combining marks,
 * zero-width characters and selectors, one for everything else.
 */
fun displayWidth(text: String): Int {
    var total = 0
    var i = 0
    while (i < text.length) {
        val point = codePointAt(text, i)
        val next = i + Character.charCount(point)
        val selected = next < text.length && text[next] == EMOJI_SELECTOR
        total +=
            when {
                inRanges(point, ZERO) -> 0
                inRanges(point, WIDE) || selected -> 2
                else -> 1
            }
        i = next
    }
    return total
}

private const val EMOJI_SELECTOR = '️'

private fun codePointAt(
    text: String,
    i: Int,
): Int {
    val high = text[i]
    if (high.isHighSurrogate() && i + 1 < text.length && text[i + 1].isLowSurrogate()) {
        return Character.toCodePoint(high, text[i + 1])
    }
    return high.code
}

/** Rust's `char::len_utf16` and `char::from_u32` pairing, which common Kotlin has no public form of. */
private object Character {
    private const val SUPPLEMENTARY = 0x10000
    private const val HIGH_BASE = 0xD800
    private const val LOW_BASE = 0xDC00
    private const val BITS = 10

    fun charCount(point: Int) = if (point >= SUPPLEMENTARY) 2 else 1

    fun toCodePoint(
        high: Char,
        low: Char,
    ) = ((high.code - HIGH_BASE) shl BITS) + (low.code - LOW_BASE) + SUPPLEMENTARY
}

/** `1100-115F 2705 …`: hex code points and inclusive ranges, as pairs of first and last. */
private fun ranges(table: String): IntArray =
    table
        .trimMargin()
        .split(' ', '\n')
        .filter { it.isNotEmpty() }
        .flatMap { item ->
            val bounds = item.split('-').map { it.toInt(HEX) }
            listOf(bounds.first(), bounds.last())
        }.toIntArray()

private const val HEX = 16

private fun inRanges(
    point: Int,
    ranges: IntArray,
): Boolean {
    var lo = 0
    var hi = ranges.size / 2 - 1
    while (lo <= hi) {
        val mid = (lo + hi) / 2
        when {
            point < ranges[2 * mid] -> hi = mid - 1
            point > ranges[2 * mid + 1] -> lo = mid + 1
            else -> return true
        }
    }
    return false
}

// Pairs of first and last code point, sorted. Combining marks, zero-width spaces and joiners, bidi controls,
// variation selectors and tags.
private val ZERO =
    ranges(
        """
        |300-36F 483-489 591-5BD 610-61A 64B-65F 200B-200F 202A-202E 2060-2064 20D0-20F0 FE00-FE0F FE20-FE2F
        |E0000-E007F E0100-E01EF
        """,
    )

// East Asian Wide and Fullwidth, from Unicode 16's EastAsianWidth.txt.
private val WIDE =
    ranges(
        """
        |1100-115F 231A-231B 2329-232A 23E9-23EC 23F0 23F3 25FD-25FE 2614-2615 2630-2637 2648-2653 267F
        |268A-268F 2693 26A1 26AA-26AB 26BD-26BE 26C4-26C5 26CE 26D4 26EA 26F2-26F3 26F5 26FA 26FD 2705
        |270A-270B 2728 274C 274E 2753-2755 2757 2795-2797 27B0 27BF 2B1B-2B1C 2B50 2B55 2E80-303E 3041-33FF
        |3400-4DBF 4E00-A4CF A960-A97F AC00-D7A3 F900-FAFF FE10-FE19 FE30-FE6F FF00-FF60 FFE0-FFE6
        |16FE0-16FE4 17000-18CFF 1B000-1B2FF 1F004 1F0CF 1F18E 1F191-1F19A 1F200-1F202 1F210-1F23B
        |1F240-1F248 1F250-1F251 1F260-1F265 1F300-1F320 1F32D-1F335 1F337-1F37C 1F37E-1F393 1F3A0-1F3CA
        |1F3CF-1F3D3 1F3E0-1F3F0 1F3F4 1F3F8-1F43E 1F440 1F442-1F4FC 1F4FF-1F53D 1F54B-1F54E 1F550-1F567
        |1F57A 1F595-1F596 1F5A4 1F5FB-1F64F 1F680-1F6C5 1F6CC 1F6D0-1F6D2 1F6D5-1F6D7 1F6DC-1F6DF
        |1F6EB-1F6EC 1F6F4-1F6FC 1F7E0-1F7EB 1F7F0 1F90C-1F93A 1F93C-1F945 1F947-1F9FF 1FA70-1FAFF
        |20000-2FFFD 30000-3FFFD
        """,
    )
