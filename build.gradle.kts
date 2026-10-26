plugins { `kotlin-dsl` }

dependencies {
    // compileOnly: the consumer's own Kotlin plugin (same version) is used at runtime.
    compileOnly(libs.kotlin.gradle.plugin)
}

gradlePlugin {
    plugins {
        register("native") {
            id = "ci.diffuser.native"
            implementationClass = "ci.diffuser.NativeConventionPlugin"
            description = "Kotlin/Native dev and release build settings for every subproject using Kotlin Multiplatform"
        }
    }
}
