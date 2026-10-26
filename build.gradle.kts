plugins { `kotlin-dsl` }

dependencies {
    // compileOnly: the consumer's own Kotlin plugin (same version) is used at runtime.
    compileOnly(libs.kotlin.gradle.plugin)
    implementation(libs.ktlint.gradle.plugin)
}

// The ktlint engine version reaches the plugin code as a generated constant.
val ktlintVersion = libs.versions.ktlintEngine.get()
val genDir = layout.buildDirectory.dir("generated/versions")
val genVersions = tasks.register("genVersions") {
    inputs.property("ktlint", ktlintVersion)
    outputs.dir(genDir)
    doLast {
        val f = genDir.get().file("ci/diffuser/Versions.kt").asFile
        f.parentFile.mkdirs()
        f.writeText("package ci.diffuser\n\ninternal object Versions {\n    const val KTLINT = \"$ktlintVersion\"\n}\n")
    }
}
kotlin.sourceSets.main { kotlin.srcDir(genVersions) }
dependencies {
}

gradlePlugin {
    plugins {
        register("native") {
            id = "ci.diffuser.native"
            implementationClass = "ci.diffuser.NativeConventionPlugin"
            description = "Kotlin/Native dev and release build settings for every subproject using Kotlin Multiplatform"
        }
        register("ktlint") {
            id = "ci.diffuser.ktlint"
            implementationClass = "ci.diffuser.KtlintConventionPlugin"
            description = "ktlint check/format for a Kotlin module; ktlintCheck runs before every test task"
        }
    }
}
