# kotlin-sync

The sync contracts and the Kotlin client for the creator's paid cloud sync, on Kotlin
Multiplatform (Kotlin/Native for macOS arm64 and Linux).

- `pyrlyn.sync:proto`: the gRPC contracts (`pyrlyn.sync.v1`, `pyrlyn.usage.v1` and the product
  payloads) as generated Kotlin code, plus a reader for the rich errors the server sends.
- `pyrlyn.sync:client`: opts a device in, logs the user in with the device flow, pulls then
  pushes in batches, retries what the protocol allows, resyncs when a cursor is refused, and
  holds, rejects or keeps changes as the server's refusals say.
- `pyrlyn.sync:testing`: an in-memory store for tests.

An app implements `LocalStore` over its own database and `SecretStore` over the system keychain:

```kotlin
val login = DeviceLogin(http, "https://api.example.com/api/auth", "rtok", keychain, "sync")
val client = SyncClient("sync.example.com:443", login, SyncConfig(info, scope, "Laptop")) { credentials = tls() }
client.enable(store)
val report = client.sync(store)
```

Include it as a Gradle build, then depend on `pyrlyn.sync:client`. In `settings.gradle.kts`, find it
with the `sharedBuild` helper from `packages/shared-settings/README.md`:

```kotlin
pluginManagement {
    extra["kotlinSyncDir"] = sharedBuild("kotlin-sync", "KOTLIN_SYNC_DIR")
}

includeBuild(extra["kotlinSyncDir"] as String)
```
