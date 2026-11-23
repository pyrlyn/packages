@file:OptIn(ExperimentalForeignApi::class)

package snapshot

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.convert

private const val MODE = 493 // 0755

internal actual fun makeDir(path: String) {
    platform.posix.mkdir(path, MODE.convert())
}
