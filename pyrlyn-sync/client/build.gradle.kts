// The app side of sync: opt-in, device login, pull then push, retries and full resyncs.
kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":proto"))
            // Only the interface: gRPC links BoringSSL, which clashes with the OpenSSL libcurl links
            // on Linux, so the app picks the HTTP engine.
            api("pyrlyn.kit:http")
            implementation(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(libs.kotest.assertions.core)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
