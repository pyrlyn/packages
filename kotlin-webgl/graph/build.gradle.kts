import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

// Generic 2D graph and graph-cloud scene: model, cluster layout, camera (pan, zoom, fly-to), level of detail and
// picking in common code (tested on the JVM); the WebGL2 renderer on wasmJs.
configure<KotlinMultiplatformExtension> {
    jvm()
    sourceSets.getByName("wasmJsMain").dependencies { api(project(":webgl")) }
}
