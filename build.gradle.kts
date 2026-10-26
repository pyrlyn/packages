plugins { `kotlin-dsl` }

dependencies {
    // compileOnly: the consumer's own Kotlin plugin (same version) is used at runtime.
    compileOnly(libs.kotlin.gradle.plugin)
    implementation(libs.ktlint.gradle.plugin)
    implementation(libs.detekt.gradle.plugin)
    // compileOnly: the consumer declares these plugins (with versions) itself; only their API types are compiled against.
    compileOnly(libs.kover.gradle.plugin)
}

// The ktlint engine version reaches the plugin code as a generated constant.
val ktlintVersion = libs.versions.ktlintEngine.get()
val kotestVersion = libs.versions.kotest.get()
val jsonVersion = libs.versions.serializationJson.get()
val genDir = layout.buildDirectory.dir("generated/versions")
val genVersions = tasks.register("genVersions") {
    inputs.property("ktlint", ktlintVersion)
    inputs.property("kotest", kotestVersion)
    inputs.property("json", jsonVersion)
    outputs.dir(genDir)
    doLast {
        val f = genDir.get().file("ci/diffuser/Versions.kt").asFile
        f.parentFile.mkdirs()
        f.writeText("package ci.diffuser\n\ninternal object Versions {\n    const val KTLINT = \"$ktlintVersion\"\n    const val KOTEST = \"$kotestVersion\"\n    const val SERIALIZATION_JSON = \"$jsonVersion\"\n}\n")
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
        register("detekt") {
            id = "ci.diffuser.detekt"
            implementationClass = "ci.diffuser.DetektConventionPlugin"
            description = "detekt static analysis with the shared ci-diffuser config; detekt runs before every test task"
        }
        register("kmp") {
            id = "ci.diffuser.kmp"
            implementationClass = "ci.diffuser.KmpConventionPlugin"
            description = "Root plugin: KMP + serialization + Dokka + KSP/Kotest for every subproject, aggregated Dokka, opt-in Kover coverage"
        }
        register("cstatic") {
            id = "ci.diffuser.cstatic"
            implementationClass = "ci.diffuser.CStaticConventionPlugin"
            description = "Compile a module's own C helpers into a static library linked via cinterop staticLibraries"
        }
        register("ktlint") {
            id = "ci.diffuser.ktlint"
            implementationClass = "ci.diffuser.KtlintConventionPlugin"
            description = "ktlint check/format for a Kotlin module; ktlintCheck runs before every test task"
        }
    }
}
