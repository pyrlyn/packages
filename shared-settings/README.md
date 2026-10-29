# shared-settings

Shared Gradle build settings, used as an included build from a checkout of [pyrlyn/packages](https://github.com/pyrlyn/packages) (not published to a plugin repository).

It lives in the workspace's `packages/`. Consumer `settings.gradle.kts` looks it up in every
ancestor directory, so the build works from the main checkout and from a worktree nested deeper
(for example `.rtok/worktrees/`); `SHARED_SETTINGS_DIR` points elsewhere. Other shared builds in
`packages/` (such as `kotlin-wasm`) are found the same way:

```kotlin
pluginManagement {
    // Defined here because pluginManagement cannot see the rest of the script.
    fun sharedBuild(name: String, env: String): String =
        System.getenv(env)?.let { settingsDir.resolve(it).path }
            ?: generateSequence(settingsDir.parentFile) { it.parentFile }
                .map { it.resolve("packages/$name") }
                .firstOrNull { it.isDirectory }
                ?.path
            ?: error("packages/$name not found above $settingsDir; set $env")
    val sharedSettings = sharedBuild("shared-settings", "SHARED_SETTINGS_DIR")
    includeBuild(sharedSettings)
    includeBuild("$sharedSettings/settings-plugin")
    extra["kotlinWasmDir"] = sharedBuild("kotlin-wasm", "KOTLIN_WASM_DIR")
}

plugins { id("shared.settings") }

includeBuild(extra["kotlinWasmDir"] as String)
```

`includeBuild` inside `pluginManagement` takes a `String`: a `File` silently resolves to the outer
`Settings.includeBuild` and the plugins are not found.

Consumer root `build.gradle.kts` (same `plugins {}` block as the Kotlin Multiplatform plugin):

```kotlin
plugins { id("shared.settings.native") }
```

## `shared.settings.native`

For every subproject that applies `org.jetbrains.kotlin.multiplatform`:

| Setting | Default | Property (`diffuser.*`, legacy `rtok.*`) |
|---|---|---|
| Targets macosArm64 (+ linuxX64, linuxArm64) | macosArm64 | `allTargets=true` |
| `linkRelease*` outputs never cached | on | — |
| Release size: `-Xbinary=smallBinary=true`, `-Xbinary=latin1Strings=true`, strip (`-dead_strip -x -S` on macOS, `--gc-sections -s` on Linux) | none | `releaseSize=none\|all\|small,latin1,strip` |
| Fast release: K/N release binary cache (`enableReleaseBinaryCache`), no whole-program LTO; never ship | off | `release.fast=true` |

Release options apply to RELEASE non-test binaries only; debug and test binaries are untouched.
Versions live in `gradle/libs.versions.toml`; KGP is `compileOnly`, the consumer's Kotlin plugin is used at runtime.

Rename: `rootProject.name`, plugin id in `build.gradle.kts`, package `shared.settings`.

## `shared.settings.ktlint`

Applied automatically by `shared.settings.native` to every subproject with `org.jetbrains.kotlin.multiplatform`
or `org.jetbrains.kotlin.jvm` (can also be applied directly per module). Uses `org.jlleitschuh.gradle.ktlint`
(version in `gradle/libs.versions.toml`, ktlint engine pinned there too).

- `./gradlew ktlintCheck`: lint; part of `check`.
- `./gradlew ktlintFormat`: auto-format.
- Every test task (`AbstractTestTask`: K/N test runs, `jvmTest`, `allTests`, custom `KotlinNativeHostTest`
  tasks) depends on the module's `ktlintCheck`, so style errors fail before tests, locally and in CI.
- Files under `build/` and `generated/` are excluded. Rule tweaks go in the consumer's `.editorconfig`.

## shared.settings.cstatic

Per-module plugin: compiles `src/nativeInterop/c/<name>.c` into `build/native/macosArm64/lib<name>.a` (xcrun clang, macOS arm64) and links it via cinterop `staticLibraries`:

```kotlin
plugins { id("shared.settings.cstatic") }
configure<shared.settings.CStaticExtension> { library(name = "rtok_spawn", cinterop = "spawn") }
```
