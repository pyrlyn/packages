@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package fs

import kotlinx.cinterop.cstr
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.toKString
import kotlinx.io.files.Path
import platform.posix.getenv
import platform.posix.mkdtemp
import platform.posix.symlink

/** A fresh directory under `$TMPDIR` for one test, removed afterwards. */
fun <T> withTempDir(body: (Path) -> T): T {
    val base = (getenv("TMPDIR")?.toKString() ?: "/tmp").trimEnd('/')
    val dir =
        memScoped {
            val template = "$base/fs-test-XXXXXX".cstr.ptr
            Path(checkNotNull(mkdtemp(template)) { "mkdtemp" }.toKString())
        }
    try {
        return body(dir)
    } finally {
        removeAny(dir)
    }
}

fun Path.text() = readText(this)

fun Path.write(text: String) = atomic(this, text.encodeToByteArray())

fun link(
    target: String,
    at: Path,
) = check(symlink(target, at.toString()) == 0) { "symlink $at" }

fun entries(dir: Path) = listDir(dir).sorted()
