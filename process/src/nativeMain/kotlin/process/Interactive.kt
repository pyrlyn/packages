// A child that shares the terminal. Kept apart from `runBounded` because the guards there (no stdin, captured
// output, a deadline) are exactly what a program asking for a password must not have.
package process

/**
 * Run the command with this process's stdin, stdout and stderr, in its process group, and wait however long it takes
 * (Rust `Command::status`). For a program the person drives — a package manager that may ask for a password — and so
 * only for a program the caller chose, never one a file supplied. Throws [ProcessException] when it cannot be started
 * or waited on.
 */
fun Command.status(): ExitStatus {
    val child = spawnInherited(this)
    try {
        return child.waitFor()
    } finally {
        child.close()
    }
}
