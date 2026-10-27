// java.io on the JVM.
package snapshot

import java.io.File

internal actual fun readText(path: String): String? = File(path).takeIf { it.isFile }?.readText()

internal actual fun writeText(
    path: String,
    text: String,
) = File(path).writeText(text)

internal actual fun removeFile(path: String) {
    File(path).delete()
}

internal actual fun makeDirs(path: String) {
    File(path).mkdirs()
}

internal actual fun env(name: String): String? = System.getenv(name)
