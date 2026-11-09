# kotlin-taskboard

https://github.com/pyrlyn/packages

The engine of rtok's task board as a Kotlin Multiplatform library: model, `Store` SPI, and a `Board` with an API for
parallel clients that never conflict.

## Decisions

- Native targets only (macOS arm64, Linux x64 and arm64, Windows x64), like rtok and `kotlin-kit`: the race tests need
  real threads and no consumer needs a JVM or wasm build yet.
- Times are whole seconds on the wire and in every store (`created_at`, `updated_at`, leases, journal), so what a write
  returns is what the next read returns. The engine truncates the clock to seconds.
- SHA-256 comes from `org.kotlincrypto.hash:sha2`, a small maintained KMP library; none of the packages had one.
- `watch` fails with `invalid` when the journal no longer reaches back to `since`; a client re-reads the board and
  starts over from its `seq`.
