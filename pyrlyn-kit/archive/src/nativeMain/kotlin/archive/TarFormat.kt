// The tar header layout and its number fields, shared by the reader and the writer.
package archive

internal const val BLOCK = 512

internal const val NAME_LEN = 100
internal const val MODE_AT = 100
internal const val UID_AT = 108
internal const val GID_AT = 116
internal const val SIZE_AT = 124
internal const val MTIME_AT = 136
internal const val CKSUM_AT = 148
internal const val TYPE_AT = 156
internal const val LINK_AT = 157
internal const val MAGIC_AT = 257
internal const val VERSION_AT = 263
internal const val PREFIX_AT = 345
internal const val PREFIX_LEN = 155

/** GNU's sparse map: four (offset, length) pairs, then the extension flag and the real size. */
internal const val SPARSE_AT = 386
internal const val SPARSE_IN_HEADER = 4
internal const val IS_EXTENDED_AT = 482
internal const val REAL_SIZE_AT = 483

/** An extension block holds 21 pairs, then its own extension flag. */
internal const val SPARSE_IN_EXTENSION = 21
internal const val EXTENSION_IS_EXTENDED_AT = 504
internal const val SPARSE_PAIR = 24
internal const val NUMBER_LEN = 12
internal const val SHORT_NUMBER_LEN = 8

private const val BASE_256 = 0x80
private const val OCTAL = 8
private const val BYTE_MASK = 0xFF
private const val BITS = 8
private const val LONG_BYTES = 8

/** The bytes up to the first NUL, as tar fields end. */
internal fun ByteArray.field(
    at: Int,
    len: Int,
): ByteArray {
    var end = at
    while (end < at + len && this[end] != 0.toByte()) end++
    return copyOfRange(at, end)
}

/** tar-rs `octal_from`: ASCII octal up to the first NUL, surrounding spaces ignored. */
internal fun ByteArray.octal(
    at: Int,
    len: Int,
): Long? {
    val text = field(at, len).decodeToString().trim()
    if (text.isEmpty() || text.any { it !in '0'..'7' }) return null
    return text.toLongOrNull(OCTAL)
}

/**
 * tar-rs `num_field_wrapper_from`: octal, or GNU's base-256 when the top bit of the first byte is set. tar-rs reads
 * the last eight bytes of a base-256 field; a value that does not fit a non-negative Long is refused here.
 */
internal fun ByteArray.number(
    at: Int,
    len: Int,
): Long? {
    if (this[at].toInt() and BASE_256 == 0) return octal(at, len)
    val start = at + len - LONG_BYTES
    var value = 0L
    for (i in start until at + len) value = (value shl BITS) or (this[i].toLong() and BYTE_MASK.toLong())
    return value.takeIf { it >= 0 }
}

/** Write [value] as zero-padded octal ending in NUL, or as base-256 when octal does not fit, as tar-rs does. */
internal fun ByteArray.putNumber(
    at: Int,
    len: Int,
    value: Long,
) {
    val digits = value.toString(OCTAL)
    if (digits.length <= len - 1) {
        digits.padStart(len - 1, '0').encodeToByteArray().copyInto(this, at)
        this[at + len - 1] = 0
        return
    }
    fill(0, at, at + len)
    var rest = value
    for (i in at + len - 1 downTo at + len - LONG_BYTES) {
        this[i] = (rest and BYTE_MASK.toLong()).toByte()
        rest = rest ushr BITS
    }
    this[at] = BASE_256.toByte()
}

/** The header checksum: every byte summed unsigned, the checksum field counted as spaces. */
internal fun checksum(header: ByteArray): Long {
    var sum = 0L
    for (i in 0 until BLOCK) {
        sum += if (i in CKSUM_AT until CKSUM_AT + SHORT_NUMBER_LEN) ' '.code else header[i].toInt() and BYTE_MASK
    }
    return sum
}

internal fun ByteArray.magicIs(magic: String): Boolean = magic.encodeToByteArray().withIndex().all { (i, b) -> this[MAGIC_AT + i] == b }

/** tar-rs `as_gnu`: magic `ustar ` and version ` \0`. */
internal val ByteArray.isGnu get() = magicIs("ustar  \u0000")

/** tar-rs `as_ustar`: magic `ustar\0` and version `00`. */
internal val ByteArray.isUstar get() = magicIs("ustar\u000000")

/** The bytes it takes to pad [size] out to whole blocks. */
internal fun padding(size: Long): Long = (BLOCK - size % BLOCK) % BLOCK
