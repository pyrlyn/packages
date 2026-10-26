# kotlin-wasm

An engine-neutral WebAssembly host interface for Kotlin Multiplatform, with a
[Chasm](https://github.com/CharlieTap/chasm) adapter. It started as rtok-kotlin's `libs/wasm` and
`libs/wasm-chasm` and is shared so that several projects run guest modules the same way.

## Modules

- `:wasm` (`pyrlyn.wasm:wasm`, package `wasm`):
  - the values: `WasmValue` and `WasmType`;
  - the parts of a guest: `WasmMemory`, `HostFunction` and `WasmInstance`;
  - the engine: `WasmRuntime`;
  - helpers: `WasmException` and the bounds-checked `guestSlice`.
- `:wasm-chasm` (`pyrlyn.wasm:wasm-chasm`, package `wasm.chasm`): `ChasmRuntime`. It validates
  modules before instantiating them and can meter calls with fuel.

## Use

```kotlin
// settings.gradle.kts
includeBuild(System.getenv("KOTLIN_WASM_DIR") ?: "../../packages/kotlin-wasm")

// build.gradle.kts
dependencies { implementation("pyrlyn.wasm:wasm-chasm") }
```

Targets: JVM, macOS arm64, Linux x64 and arm64, Windows x64.

## Build

`./gradlew check` runs the tests, ktlint and detekt. The lint settings come from a checkout of
ci-diffuser at `../../apps/ci-diffuser`, or `CI_DIFFUSER_DIR`.
