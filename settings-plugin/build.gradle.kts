plugins { `kotlin-dsl` }

dependencies {
    // compileOnly: the consumer declares com.github.burrunan.s3-build-cache (with version) in the same settings plugins block.
    compileOnly(libs.s3cache.gradle.plugin)
}

gradlePlugin {
    plugins {
        register("settings") {
            id = "ci.diffuser.settings"
            implementationClass = "ci.diffuser.SettingsConventionPlugin"
            description = "Settings plugin: shared repositories and the optional S3/R2 remote build cache"
        }
    }
}
