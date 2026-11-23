package archive

import kotlinx.io.Buffer
import kotlinx.io.RawSource
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.files.SystemTemporaryDirectory
import kotlinx.io.readByteArray
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

fun source(bytes: ByteArray): RawSource = Buffer().apply { write(bytes) }

/** Everything [src] yields, read [chunk] bytes at a time so a decoder's buffer edges are crossed. */
fun readAll(
    src: RawSource,
    chunk: Long = 7,
): ByteArray {
    val out = Buffer()
    val step = Buffer()
    while (true) {
        val n = src.readAtMostTo(step, chunk)
        if (n < 0) break
        check(n in 0..chunk) { "read $n of at most $chunk" }
        out.write(step, n)
    }
    return out.readByteArray()
}

fun assertKind(
    kind: ArchiveException.Kind,
    body: () -> Unit,
) = assertEquals(kind, assertFailsWith<ArchiveException> { body() }.kind)

/** [bytes] as a file in a fresh directory, removed after [body]. */
fun <T> withFile(
    bytes: ByteArray,
    body: (Path) -> T,
): T {
    val dir = Path(SystemTemporaryDirectory, "archive-test-${Random.nextLong().toULong().toString(36)}")
    SystemFileSystem.createDirectories(dir, mustCreate = true)
    val file = Path(dir, "archive")
    SystemFileSystem.sink(file).buffered().use { it.write(bytes) }
    try {
        return body(file)
    } finally {
        SystemFileSystem.delete(file)
        SystemFileSystem.delete(dir)
    }
}

fun octal(text: String) = text.toInt(8)
