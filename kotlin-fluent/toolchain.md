# Toolchain

| Program | How to install | Why here | Source |
| --- | --- | --- | --- |
| JDK 21 | mise | Build and jvm target | https://openjdk.org |
| Gradle | wrapper | Build | https://github.com/gradle/gradle |

## Gradle

| Package | Where | Source | Why here |
| --- | --- | --- | --- |
| Kotlin Multiplatform plugin | local | https://github.com/JetBrains/kotlin | jvm and wasmJs targets |
| Compose Multiplatform | local | https://github.com/JetBrains/compose-multiplatform | The components' runtime, foundation, ui and material3 |
| kotlin-glass | local | packages/kotlin-glass | Glyphs, the auto-fit grid, keyboard focus |
| shared-settings | local | packages/shared-settings | ktlint, detekt, repositories |
| net.java.dev.jna:jna 5.19.1 | local (jvm) | https://github.com/java-native-access/jna | Win32 and DWM calls for `WindowsFrame` |
