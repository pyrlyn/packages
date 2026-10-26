// Adapter: Ktor client (Darwin engine on Apple, Curl on Linux) behind the http interface.
val ktor = "3.6.0"
configure<org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension> {
    sourceSets.commonMain.dependencies {
        api(project(":http"))
        implementation("io.ktor:ktor-client-core:$ktor")
    }
    sourceSets.appleMain.dependencies { implementation("io.ktor:ktor-client-darwin:$ktor") }
    sourceSets.linuxMain.dependencies { implementation("io.ktor:ktor-client-curl:$ktor") }
    sourceSets.commonTest.dependencies {
        implementation("io.ktor:ktor-client-mock:$ktor")
        implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.12.0")
    }
}
