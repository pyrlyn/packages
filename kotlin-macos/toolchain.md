# Toolchain

| Program | How to install | Why here | Source |
| --- | --- | --- | --- |
| JDK 21 | mise | Build and jvm target | https://openjdk.org |
| Gradle | wrapper | Build | https://github.com/gradle/gradle |

## Gradle

| Package | Where | Source | Why here |
| --- | --- | --- | --- |
| Kotlin Multiplatform plugin | local | https://github.com/JetBrains/kotlin | jvm and wasmJs targets |
| Compose Multiplatform | local | https://github.com/JetBrains/compose-multiplatform | The components' runtime, foundation and ui |
| shared-settings | local | packages/shared-settings | ktlint, detekt, repositories |

## Bundled fonts

| Font | Where | Source | Why here |
| --- | --- | --- | --- |
| Inter | `macos/src/jvmMain/resources` | https://github.com/rsms/inter | The design's text face |
| Roboto Mono | `macos/src/jvmMain/resources` | https://github.com/googlefonts/RobotoMono | The design's code face |
