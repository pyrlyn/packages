@file:OptIn(ExperimentalForeignApi::class)

package cli

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.convert
import platform.posix.TIOCGWINSZ

internal actual val WINSIZE_REQUEST: ULong = TIOCGWINSZ.convert()
