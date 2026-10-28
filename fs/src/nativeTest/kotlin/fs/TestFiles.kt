package fs

import kotlinx.io.files.Path
import kotlin.experimental.ExperimentalNativeApi

/** A fresh directory under the system's temp directory for one test, removed afterwards. */
fun <T> withTempDir(body: (Path) -> T): T = TempDir.create("fs-test-").use { body(it.path) }

@OptIn(ExperimentalNativeApi::class)
val onWindows = Platform.osFamily == OsFamily.WINDOWS

/** Unix can always make a symlink; Windows only with Developer Mode on or for an administrator. */
val symlinksWork: Boolean by lazy { !onWindows || withTempDir { runCatching { symlink(Path("x"), Path(it, "l")) }.isSuccess } }

/** [body] in a fresh directory where this system makes symlinks; elsewhere the test says it skipped. */
fun withLinkDir(body: (Path) -> Unit) = if (symlinksWork) withTempDir(body) else println("skipped: this Windows makes no symlinks")

/** [body] where files carry Unix permission bits; Windows keeps only read-only, and fs-kit tests modes on Unix alone. */
inline fun unixModes(body: () -> Unit) {
    if (!onWindows) body()
}

fun Path.text() = readText(this)

fun Path.write(text: String) = atomic(this, text.encodeToByteArray())

fun link(
    target: String,
    at: Path,
) = symlink(Path(target), at)

fun entries(dir: Path) = listDir(dir).sorted()
