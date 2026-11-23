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

/**
 * Start the command and leave it running past this process, apart from its console: no stdin, its output discarded, a
 * process group of its own (Rust `spawn` with null stdio and `CREATE_NEW_PROCESS_GROUP`). For a helper that finishes a
 * job once this process has exited. Returns its pid; throws [ProcessException] when it cannot be started.
 */
fun Command.spawnDetached(): Int = spawnDetached(this)
