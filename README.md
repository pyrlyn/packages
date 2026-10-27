# kotlin-snapshot

Snapshot assertions for Kotlin Multiplatform, written after Rust's
[insta](https://insta.rs). Snapshots use insta's `.snap` format, so a project ported from Rust keeps
its `.snap` files as they are.

## Module

`:snapshot` (`pyrlyn.snapshot:snapshot`, package `snapshot`), with no dependencies:

- `assertSnapshot(name, actual, expression?)`: compares with `<dir>/<prefix>__<name>.snap`.
- `assertDebugSnapshot(name, value)`: the same over a multi-line `toString()` (`debugFormat`).
- `assertInlineSnapshot(actual, expected)`: compares with a literal, ignoring its indentation.
- `withSettings(SnapshotSettings(...)) { }`: the directory, name prefix, header `source`,
  redactions (`redact(regex, replacement)`) and update mode.
- `SnapFile`, `lineDiff`, `normalize`: the file format and the diff, for tools.

## Use

```kotlin
// settings.gradle.kts
includeBuild(System.getenv("KOTLIN_SNAPSHOT_DIR") ?: "../../packages/kotlin-snapshot")

// build.gradle.kts
sourceSets.commonTest.dependencies { implementation("pyrlyn.snapshot:snapshot") }
tasks.withType<org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeTest>().configureEach {
    environment("SNAPSHOT_DIR", file("src/nativeTest/snapshots").absolutePath)
    System.getenv("SNAPSHOT_UPDATE")?.let { environment("SNAPSHOT_UPDATE", it) }
}
```

```kotlin
@Test fun rendersTheReport() =
    withSettings(SnapshotSettings(prefix = "report__html")) {
        assertSnapshot("zero", renderHtml(emptyReport()), expression = "out")
    }

@Test fun rendersTheTable() =
    assertInlineSnapshot(
        toTable(rows),
        """
        path  state
        /w/x  dirty
        """,
    )
```

## Updating

`SNAPSHOT_UPDATE` (or insta's `INSTA_UPDATE`) decides what a new or changed snapshot does:

- `new` (the default, `auto` outside CI): writes `<name>.snap.new` and fails. Review it and rename
  it to `.snap`.
- `always`: writes the `.snap` and passes.
- `no` (the default when `CI` is set): writes nothing and fails.

A failure prints a line diff with old and new line numbers. Inline snapshots cannot rewrite the
call site; a mismatch prints the new literal to paste.

Targets: JVM, macOS arm64, Linux x64 and arm64, Windows x64, and Wasm (`wasmJs`, Node: files go through `fs`; in a browser only inline snapshots work).

## Build

```sh
./gradlew check
```

`CI_DIFFUSER_DIR` points to a checkout of ci-diffuser, which provides ktlint and detekt.

## Where tests live

Packages and libraries contain only tests of the package itself. Tests that verify an app (such as
rtok-kotlin) through a package live in the app, next to its other tests, with their `.snap` files.
This applies to the whole Kotlin port, including snapshot tests.
