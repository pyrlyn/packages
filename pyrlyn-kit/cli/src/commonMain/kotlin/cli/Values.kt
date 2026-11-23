package cli

private const val RADIX = 10uL

/** Rust's `i64::MIN` without its sign, which no positive `Long` holds. */
private const val I64_MIN_MAGNITUDE = 9_223_372_036_854_775_808uL

/** Why Rust's integer `from_str` refuses a value, in its own words. */
private enum class IntError(
    val message: String,
) {
    Empty("cannot parse integer from empty string"),
    InvalidDigit("invalid digit found in string"),
    PosOverflow("number too large to fit in target type"),
    NegOverflow("number too small to fit in target type"),
}

/** A number Rust read, or why it would not. */
private class IntParse(
    val magnitude: ULong,
    val negative: Boolean,
    val error: IntError?,
)

/**
 * Rust's `from_str_radix(s, 10)`: an optional sign (`-` only when [signed]), then digits, checked one by one so the
 * first fault wins, exactly as the standard library reports it.
 */
private fun parseInteger(
    s: String,
    signed: Boolean,
): IntParse {
    val sign = s.firstOrNull()
    val negative = signed && sign == '-'
    val digits = if (sign == '+' || negative) s.substring(1) else s
    val limit =
        when {
            negative -> I64_MIN_MAGNITUDE
            signed -> Long.MAX_VALUE.toULong()
            else -> ULong.MAX_VALUE
        }
    val error =
        when {
            s.isEmpty() -> IntError.Empty
            digits.isEmpty() -> IntError.InvalidDigit
            else -> null
        }
    return error?.let { IntParse(0uL, negative, it) } ?: scan(digits, limit, if (negative) IntError.NegOverflow else IntError.PosOverflow)
        .let { (value, fault) -> IntParse(value, negative, fault) }
}

/** The digits' value, or the first fault: a non-digit, or crossing [limit]. */
private fun scan(
    digits: String,
    limit: ULong,
    overflow: IntError,
): Pair<ULong, IntError?> {
    var value = 0uL
    for (c in digits) {
        val digit = (c - '0').toULong()
        val fault =
            when {
                c !in '0'..'9' -> IntError.InvalidDigit
                value > (limit - digit) / RADIX -> overflow
                else -> null
            }
        if (fault != null) return value to fault
        value = value * RADIX + digit
    }
    return value to null
}

/** Check a raw value against [arg]'s kind, as clap's value parser would, and return it as [Matches] keeps it. */
internal fun convert(
    errors: Errors,
    arg: Arg,
    raw: String,
): String =
    when (val kind = arg.value) {
        ValueKind.Text -> {
            raw
        }

        ValueKind.NonEmpty -> {
            raw.ifEmpty { throw errors.emptyValue(arg) }
        }

        is ValueKind.Ranged -> {
            ranged(errors, arg, raw, kind)
        }

        ValueKind.Size -> {
            val parsed = parseInteger(raw, signed = false)
            parsed.error?.let { throw errors.badValue(arg, raw, it.message) }
            parsed.magnitude.toString()
        }

        is ValueKind.OneOf -> {
            kind.values.firstOrNull { it.name == raw }?.name ?: throw errors.invalidValue(arg, raw)
        }
    }

private fun ranged(
    errors: Errors,
    arg: Arg,
    raw: String,
    kind: ValueKind.Ranged,
): String {
    val parsed = parseInteger(raw, signed = true)
    parsed.error?.let { throw errors.badValue(arg, raw, it.message) }
    val value = if (parsed.negative) (0uL - parsed.magnitude).toLong() else parsed.magnitude.toLong()
    if (value !in kind.min..kind.max) throw errors.badValue(arg, raw, "$value is not in ${kind.min}..=${kind.max}")
    return value.toString()
}
