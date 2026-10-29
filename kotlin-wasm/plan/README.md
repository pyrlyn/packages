# kotlin-wasm

GitHub: https://github.com/pyrlyn/kotlin-wasm

An engine-neutral WebAssembly host interface for Kotlin Multiplatform, with a Chasm adapter.

## Decisions

- Extracted from rtok-kotlin (`libs/wasm`, `libs/wasm-chasm` at rtok `d45eefb`) for Octo's
  plugins (Octo T16). The packages were renamed from `rtok.wasm` to the neutral `wasm`.
- Consumers use a composite build (`includeBuild`) rather than a published artifact, the
  creator's choice for this extraction.
