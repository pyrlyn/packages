// Numbers and datetimes of TOML 1.1 from their text; a datetime becomes the text Rust's `toml_datetime` Display gives it.
// Regex group positions and calendar numbers read better as the literals they are.
@file:Suppress("MagicNumber")

package toml

import kotlinx.serialization.json.JsonPrimitive

internal object TomlScalars {
    private val DEC = Regex("[+-]?(?:0|[1-9](?:_?[0-9])*)")
    private val HEX = Regex("0x[0-9A-Fa-f](?:_?[0-9A-Fa-f])*")
    private val OCT = Regex("0o[0-7](?:_?[0-7])*")
    private val BIN = Regex("0b[01](?:_?[01])*")
    private val FLOAT = Regex("[+-]?(?:0|[1-9](?:_?[0-9])*)(?:\\.[0-9](?:_?[0-9])*)?(?:[eE][+-]?[0-9](?:_?[0-9])*)?")
    private val SPECIAL = Regex("[+-]?(?:inf|nan)")
    private const val TIME = "([0-9]{2}):([0-9]{2})(?::([0-9]{2})(?:\\.([0-9]+))?)?"
    private val DATETIME = Regex("([0-9]{4})-([0-9]{2})-([0-9]{2})(?:[Tt ]$TIME(?:([Zz])|([+-])([0-9]{2}):([0-9]{2}))?)?")
    private val LOCAL_TIME = Regex(TIME)
    val DATE_ONLY = Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}")

    private const val RADIX_HEX = 16
    private const val RADIX_OCT = 8
    private const val NANO_DIGITS = 9
    private const val MAX_HOUR = 23
    private const val MAX_MINUTE = 59
    private const val MAX_SECOND = 60
    private const val MONTHS = 12
    private const val MINUTES_PER_HOUR = 60

    /** The value [token] spells, or null when it is not a number or datetime; [fail] reports one out of range. */
    fun of(
        token: String,
        fail: (String) -> Nothing,
    ): JsonPrimitive? =
        when {
            DEC.matches(token) -> JsonPrimitive(token.replace("_", "").toLongOrNull() ?: fail("integer `$token` is out of range"))
            HEX.matches(token) -> radix(token, RADIX_HEX, fail)
            OCT.matches(token) -> radix(token, RADIX_OCT, fail)
            BIN.matches(token) -> radix(token, 2, fail)
            FLOAT.matches(token) || SPECIAL.matches(token) -> JsonPrimitive(float(token))
            else -> datetime(token, fail)?.let(::JsonPrimitive)
        }

    private fun radix(
        token: String,
        radix: Int,
        fail: (String) -> Nothing,
    ) = JsonPrimitive(token.substring(2).replace("_", "").toLongOrNull(radix) ?: fail("integer `$token` is out of range"))

    private fun float(token: String): Double =
        when (token.trimStart('+', '-')) {
            "inf" -> if (token.startsWith('-')) Double.NEGATIVE_INFINITY else Double.POSITIVE_INFINITY
            "nan" -> Double.NaN
            else -> token.replace("_", "").toDouble()
        }

    private fun datetime(
        token: String,
        fail: (String) -> Nothing,
    ): String? {
        DATETIME.matchEntire(token)?.let { return dateTime(it.groupValues, fail) }
        return LOCAL_TIME.matchEntire(token)?.let { time(it.groupValues.drop(1), fail) }
    }

    private fun dateTime(
        g: List<String>,
        fail: (String) -> Nothing,
    ): String {
        val (year, month, day) = g.subList(1, 4).map { it.toInt() }
        if (month !in 1..MONTHS || day !in 1..daysIn(year, month)) fail("`${g[0]}` is not a valid date")
        val date = g[1] + "-" + g[2] + "-" + g[3]
        if (g[4].isEmpty()) return date
        return date + "T" + time(g.subList(4, 8), fail) + offset(g.subList(8, 12), fail)
    }

    /** Hour, minute, second and fraction groups; the fraction keeps nine digits at most, as Rust's nanoseconds do. */
    private fun time(
        g: List<String>,
        fail: (String) -> Nothing,
    ): String {
        val (hour, minute) = g.subList(0, 2).map { it.toInt() }
        val second = g[2].ifEmpty { "0" }.toInt()
        if (hour > MAX_HOUR || minute > MAX_MINUTE || second > MAX_SECOND) fail("`${g.joinToString(":")}` is not a valid time")
        val seconds = if (g[2].isEmpty()) "" else ":" + g[2]
        val fraction =
            g[3].takeIf { it.isNotEmpty() }?.let {
                "." +
                    it
                        .take(NANO_DIGITS)
                        .padEnd(NANO_DIGITS, '0')
                        .trimEnd('0')
                        .ifEmpty { "0" }
            } ?: ""
        return g[0] + ":" + g[1] + seconds + fraction
    }

    private fun offset(
        g: List<String>,
        fail: (String) -> Nothing,
    ): String {
        if (g[1].isEmpty()) return if (g[0].isEmpty()) "" else "Z"
        val (hour, minute) = g.subList(2, 4).map { it.toInt() }
        if (hour > MAX_HOUR || minute > MAX_MINUTE) fail("`${g.joinToString("")}` is not a valid offset")
        // Rust keeps the offset as signed minutes, so `-00:00` reads back as `+00:00`.
        val sign = if (g[1] == "-" && hour * MINUTES_PER_HOUR + minute != 0) "-" else "+"
        return sign + g[2] + ":" + g[3]
    }

    private fun daysIn(
        year: Int,
        month: Int,
    ): Int =
        when (month) {
            2 -> if (year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)) 29 else 28
            4, 6, 9, 11 -> 30
            else -> 31
        }
}
