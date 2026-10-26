# ci-diffuser

Shared Gradle build settings, used as a local included build (no remote, no publishing).

Consumer `settings.gradle.kts`:

```kotlin
pluginManagement { includeBuild("../ci-diffuser") }
```

Consumer root `build.gradle.kts` (same `plugins {}` block as the Kotlin Multiplatform plugin):

```kotlin
plugins { id("ci.diffuser.native") }
```

## `ci.diffuser.native`

For every subproject that applies `org.jetbrains.kotlin.multiplatform`:

| Setting | Default | Property (`diffuser.*`, legacy `rtok.*`) |
|---|---|---|
| Targets macosArm64 (+ linuxX64, linuxArm64) | macosArm64 | `allTargets=true` |
| `linkRelease*` outputs never cached | on | — |
| Release size: `-Xbinary=smallBinary=true`, `-Xbinary=latin1Strings=true`, strip (`-dead_strip -x -S` on macOS, `--gc-sections -s` on Linux) | none | `releaseSize=none\|all\|small,latin1,strip` |
| Fast release: K/N release binary cache (`enableReleaseBinaryCache`), no whole-program LTO; never ship | off | `release.fast=true` |

Release options apply to RELEASE non-test binaries only; debug and test binaries are untouched.
Versions live in `gradle/libs.versions.toml`; KGP is `compileOnly`, the consumer's Kotlin plugin is used at runtime.

Rename: `rootProject.name`, plugin id in `build.gradle.kts`, package `ci.diffuser`.
