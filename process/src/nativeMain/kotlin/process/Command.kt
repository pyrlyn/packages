package process

/** A program to run, with its arguments as given and no shell in between (Rust `std::process::Command`). */
class Command(
    val program: String,
    val args: List<String> = emptyList(),
) {
    private val extraEnv = LinkedHashMap<String, String>()

    /** The child's working directory; null keeps this process's. */
    var dir: String? = null
        private set

    /** Variables set in the child's environment on top of this process's. */
    val env: Map<String, String> get() = extraEnv

    /** Set [key] to [value] in the child's environment. */
    fun env(
        key: String,
        value: String,
    ) = apply { extraEnv[key] = value }

    /** Start the child in [dir]. */
    fun currentDir(dir: String) = apply { this.dir = dir }
}
