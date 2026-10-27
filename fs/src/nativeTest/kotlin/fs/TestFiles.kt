@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package fs

import kotlinx.io.files.Path
import platform.posix.symlink

/** A fresh directory under `$TMPDIR` for one test, removed afterwards. */
fun <T> withTempDir(body: (Path) -> T): T = TempDir.create("fs-test-").use { body(it.path) }

fun Path.text() = readText(this)

fun Path.write(text: String) = atomic(this, text.encodeToByteArray())

fun link(
    target: String,
    at: Path,
) = check(symlink(target, at.toString()) == 0) { "symlink $at" }

fun entries(dir: Path) = listDir(dir).sorted()
