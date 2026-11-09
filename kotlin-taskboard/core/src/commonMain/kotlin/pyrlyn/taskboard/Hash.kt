package pyrlyn.taskboard

import org.kotlincrypto.hash.sha2.SHA256

private const val BYTE_MASK = 0xff
private const val HEX = 16

internal fun sha256(bytes: ByteArray): ByteArray = SHA256().digest(bytes)

internal fun hex(bytes: List<Byte>): String = bytes.joinToString("") { (it.toInt() and BYTE_MASK).toString(HEX).padStart(2, '0') }

internal fun sha256Hex(text: String): String = hex(sha256(text.encodeToByteArray()).asList())
