@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package toml

import kotlinx.cinterop.toKString
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readByteArray
import kotlinx.serialization.json.*
import platform.posix.getenv
import kotlin.test.Test
import kotlin.test.assertEquals

/** Every TOML 1.1.0 file of toml-test: the valid ones read to the expected values, the invalid ones are refused. */
class TomlTestSuiteTest {
    private val dir = getenv("TOML_TEST_DIR")?.toKString() ?: error("TOML_TEST_DIR is not set")

    private fun text(name: String): String? =
        SystemFileSystem
            .source(Path(dir, name))
            .buffered()
            .use { it.readByteArray() }
            .decodeToStringOrNull()

    private fun ByteArray.decodeToStringOrNull() = runCatching { decodeToString(throwOnInvalidSequence = true) }.getOrNull()

    private val files = text("files")!!.lines().filter { it.endsWith(".toml") }

    @Test
    fun validFilesReadToTheExpectedValues() {
        val wrong =
            files.filter { it.startsWith("valid/") }.mapNotNull { name ->
                val got = runCatching { TomlParser.parse(text(name)!!) }
                val want = Json.parseToJsonElement(text(name.removeSuffix(".toml") + ".json")!!)
                when {
                    got.isFailure -> "$name: ${got.exceptionOrNull()?.message}"
                    !same(want, got.getOrThrow()) -> "$name: read ${got.getOrThrow()}, expected $want"
                    else -> null
                }
            }
        assertEquals(emptyList(), wrong)
    }

    @Test
    fun invalidFilesAreRefused() {
        // A file that is not UTF-8 never reaches the parser, which takes a String.
        val accepted = files.filter { it.startsWith("invalid/") }.filter { name -> text(name)?.let { t -> runCatching { TomlParser.parse(t) }.isSuccess } ?: false }
        assertEquals(emptyList(), accepted)
    }

    /** toml-test's tagged JSON (`{"type": …, "value": …}` for each scalar) against a parsed tree. */
    private fun same(
        want: JsonElement,
        got: JsonElement,
    ): Boolean =
        when {
            want is JsonArray -> got is JsonArray && want.size == got.size && want.indices.all { same(want[it], got[it]) }
            want is JsonObject && want.keys == setOf("type", "value") && want["type"] is JsonPrimitive -> scalar(want, got)
            want is JsonObject -> got is JsonObject && want.keys == got.keys && want.keys.all { same(want[it]!!, got[it]!!) }
            else -> false
        }

    private fun scalar(
        want: JsonObject,
        got: JsonElement,
    ): Boolean {
        val type = want["type"]!!.jsonPrimitive.content
        val value = want["value"]!!.jsonPrimitive.content
        val p = got as? JsonPrimitive ?: return false
        return when (type) {
            "string" -> p.isString && p.content == value

            "integer" -> TomlValues.isInteger(p) && p.long == value.toLong()

            "float" -> TomlValues.isFloat(p) && sameFloat(p.double, value)

            "bool" -> TomlValues.isBool(p) && p.content == value

            // The reader gives a datetime Rust's normalized text; toml-test's value is the same instant, written its way.
            else -> p.isString && seconds(p.content) == TomlScalars.of(value) { error(it) }?.content?.let(::seconds)
        }
    }

    // Rust writes `07:32` for a time read without seconds where toml-test writes `07:32:00`; both are the same time.
    private fun seconds(datetime: String) = datetime.replace(NO_SECONDS) { it.groupValues[1] + ":00" }

    private fun sameFloat(
        d: Double,
        value: String,
    ): Boolean =
        when (value.trimStart('+', '-')) {
            "nan" -> d.isNaN()
            "inf" -> d == if (value.startsWith('-')) Double.NEGATIVE_INFINITY else Double.POSITIVE_INFINITY
            else -> d == value.toDouble()
        }

    private companion object {
        val NO_SECONDS = Regex("((?:^|T)[0-9]{2}:[0-9]{2})(?=$|[Z+-])")
    }
}
