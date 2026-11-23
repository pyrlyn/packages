import kotlinx.rpc.protoc.proto

// The protobuf contracts as Kotlin: messages and gRPC service interfaces for the sync service and
// its clients, the standard gRPC health service, and the google.rpc rich error model.
plugins {
    alias(libs.plugins.kotlinx.rpc)
}

rpc { protoc() }

kotlin {
    sourceSets {
        commonMain {
            proto {
                srcDir("contracts")
                srcDir("health")
                srcDir("googleapis")
            }
            dependencies {
                api(libs.kotlinx.rpc.grpc.core)
                api(libs.kotlinx.rpc.grpc.server)
                api(libs.kotlinx.rpc.grpc.client)
                api(libs.kotlinx.rpc.protobuf)
            }
        }
    }
}

// grpc-190 ships its compiler plugin up to Kotlin 2.4.20, and 2.4.21 is a patch release with the
// same compiler API; drop this once a gRPC build for our Kotlin is published.
configurations.configureEach {
    if (name.contains("CompilerPluginClasspath", ignoreCase = true)) {
        resolutionStrategy.eachDependency {
            if (requested.group == "org.jetbrains.kotlinx" && requested.name.startsWith("kotlinx-rpc-compiler-plugin")) {
                useVersion("2.4.20-${libs.versions.kotlinx.rpc.get()}")
            }
        }
    }
}
