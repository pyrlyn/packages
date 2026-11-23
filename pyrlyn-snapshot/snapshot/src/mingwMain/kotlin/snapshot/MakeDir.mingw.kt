package snapshot

internal actual fun makeDir(path: String) {
    platform.posix.mkdir(path)
}
