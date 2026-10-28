// Conan installs of the static C libraries a module links into its klib: the module's `conanfile.txt` and `conan.lock`,
// the profiles in the kit's `conan/`. macOS and Linux take ConanCenter's prebuilt binaries only; for Windows it has
// MSVC ones alone, which K/N's mingw link cannot use, so Conan builds those from the same recipes with K/N's own clang
// and mingw sysroot.
//
// The module sets `conanTargets` (the K/N targets to install for) and `kotlinVersion` before applying this; task
// `conanInstall<Target>` deploys each library to `build/conan/<target>/direct_deploy/<package>/{include,lib}`.
import java.util.Properties

val profiles = rootProject.layout.projectDirectory.dir("conan")

@Suppress("UNCHECKED_CAST")
val targets = extra["conanTargets"] as Set<String>
val kotlinVersion = extra["kotlinVersion"] as String
val built = setOf("mingwX64")

/** The clang and the mingw sysroot this K/N links Windows binaries with, as `mingwX64.profile` reads them. */
fun konanMingw(): Map<String, String> {
    val data = System.getenv("KONAN_DATA_DIR") ?: "${System.getProperty("user.home")}/.konan"
    val (dir, host) =
        if (System.getProperty("os.name").startsWith("Mac")) "macos-aarch64" to "macos_arm64" else "linux-x86_64" to "linux_x64"
    val props = Properties()
    file("$data/kotlin-native-prebuilt-$dir-$kotlinVersion/konan/konan.properties").reader().use(props::load)

    // Values point at other keys with a leading `$`, e.g. targetToolchain → $llvmHome.<host> → $llvm.<host>.user.
    fun resolve(key: String): String = props.getProperty(key).trim().let { if (it.startsWith("$")) resolve(it.drop(1)) else it }
    return mapOf(
        "KONAN_DEPENDENCIES" to "$data/dependencies",
        "KONAN_LLVM" to resolve("targetToolchain.$host-mingw_x64"),
        "KONAN_MINGW" to resolve("targetSysRoot.mingw_x64"),
    )
}

for (target in targets) {
    tasks.register<Exec>("conanInstall${target.replaceFirstChar(Char::uppercase)}") {
        val out = layout.buildDirectory.dir("conan/$target")
        val profile = profiles.file("$target.profile")
        inputs.files(file("conanfile.txt"), file("conan.lock"), profile)
        outputs.dir(out)
        if (target in built) environment(konanMingw())
        commandLine(
            "conan",
            "install",
            projectDir.path,
            "--lockfile=${file("conan.lock").path}",
            "-pr:h=${profile.asFile.path}",
            "-pr:b=${profiles.file("macosArm64.profile").asFile.path}",
            if (target in built) "--build=missing" else "--build=never",
            "--deployer=direct_deploy",
            "--deployer-folder=${out.get().asFile.path}",
            "--output-folder=${out.get().asFile.path}/generators",
        )
    }
}
