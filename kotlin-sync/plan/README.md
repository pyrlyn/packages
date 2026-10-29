# kotlin-sync

No GitHub repository yet.

The sync contracts and the apps' Kotlin sync client, ported from `crates/sync-proto` and
`crates/sync-client` of [backend](https://github.com/pyrlyn/backend). Created by task T13 of
`apps/backend-kotlin`, whose `plan/` holds that task.

## Decisions

| # | Decision | Why |
| --- | --- | --- |
| D1 | A package of its own instead of a module of backend-kotlin. | The creator's choice: apps include it without the server's repository, and the server and the apps compile the same contracts. |
| D2 | The `SyncClient` owns its `GrpcClient` and sends the bearer token through an interceptor that reads an atomic reference; a `Mutex` runs the public calls one at a time. | kotlinx-rpc has no per-call metadata besides interceptors; the mutex stands in for Rust's `&mut store`. |
| D3 | Device login speaks HTTP through kotlin-kit's `http` interface only. | gRPC's BoringSSL clashes with libcurl's OpenSSL on Linux; the app picks the engine. |
| D4 | No Windows target. | kotlinx-rpc `0.11.0-grpc-190` does not build gRPC for mingw. |

## Port map

| Rust | Kotlin |
| --- | --- |
| `crates/sync-proto` | `proto` |
| `sync-client/src/error.rs` | `client/.../Errors.kt` |
| `sync-client/src/store.rs` | `client/.../Store.kt` |
| `sync-client/src/token.rs`, `secret.rs` | `client/.../Token.kt`, `Secret.kt` |
| `sync-client/src/client.rs` | `client/.../SyncClient.kt` |
| `sync-client/src/login.rs` | `client/.../DeviceLogin.kt` |
| `sync-client/src/testing.rs` | `testing/.../Mem.kt` |
| `sync-client/tests/converge.rs`, `refusals.rs`, `crates/sync-e2e` | `apps/backend-kotlin` `services/sync` `KotlinClientTest` |
