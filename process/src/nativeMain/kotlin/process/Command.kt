package process

/** A program to run, with its arguments as given and no shell in between (Rust `std::process::Command`). */
class Command(
    val program: String,
    val args: List<String> = emptyList(),
) {
    private val extraEnv = LinkedHashMap<String, String>()
    private val unset = LinkedHashSet<String>()

    /** The child's working directory; null keeps this process's. */
    var dir: String? = null
        private set

    /** The file `runBounded` gives the child as stdin; null gives it an empty one. */
    var stdin: String? = null
        private set

    /** Variables set in the child's environment on top of this process's. */
    val env: Map<String, String> get() = extraEnv

    /** Variables of this process's environment the child does not get. */
    val removedEnv: Set<String> get() = unset

    /** Set [key] to [value] in the child's environment. */
    fun env(
        key: String,
        value: String,
    ) = apply {
        unset -= key
        extraEnv[key] = value
    }

    /** Leave [key] out of the child's environment (Rust `Command::env_remove`). */
    fun envRemove(key: String) =
        apply {
            extraEnv -= key
            unset += key
        }

    /** Read the child's stdin from [path] when it runs under `runBounded`. */
    fun stdinFrom(path: String) = apply { stdin = path }

    /** Start the child in [dir]. */
    fun currentDir(dir: String) = apply { this.dir = dir }
}
