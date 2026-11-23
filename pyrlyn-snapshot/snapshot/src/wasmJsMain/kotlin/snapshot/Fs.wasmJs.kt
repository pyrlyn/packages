// Node's `fs` on Kotlin/Wasm; in a browser there is no file system, so only inline snapshots work.
@file:OptIn(ExperimentalWasmJsInterop::class)
@file:Suppress("UnusedParameter") // the parameters are read inside js()

package snapshot

private fun nodeRead(path: String): String? =
    js(
        """{
            const fs = globalThis.process?.getBuiltinModule?.('fs');
            try { return fs ? fs.readFileSync(path, 'utf8') : null; } catch (e) { return null; }
        }""",
    )

private fun nodeWrite(
    path: String,
    text: String,
): Boolean =
    js(
        """{
            const fs = globalThis.process?.getBuiltinModule?.('fs');
            if (!fs) return false;
            fs.writeFileSync(path, text);
            return true;
        }""",
    )

private fun nodeRemove(path: String): Unit =
    js(
        """{
            const fs = globalThis.process?.getBuiltinModule?.('fs');
            if (fs) fs.rmSync(path, { force: true });
        }""",
    )

private fun nodeMkdirs(path: String): Unit =
    js(
        """{
            const fs = globalThis.process?.getBuiltinModule?.('fs');
            if (fs) fs.mkdirSync(path, { recursive: true });
        }""",
    )

private fun nodeEnv(name: String): String? =
    js("{ const v = globalThis.process?.env?.[name]; return v === undefined ? null : v; }")

internal actual fun readText(path: String): String? = nodeRead(path)

internal actual fun writeText(
    path: String,
    text: String,
) = check(nodeWrite(path, text)) { "no file system to write $path; use assertInlineSnapshot here" }

internal actual fun removeFile(path: String) = nodeRemove(path)

internal actual fun makeDirs(path: String) = nodeMkdirs(path)

internal actual fun env(name: String): String? = nodeEnv(name)
