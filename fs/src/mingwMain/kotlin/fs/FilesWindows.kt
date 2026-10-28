@file:OptIn(ExperimentalForeignApi::class)

package fs

import fs.win.kit_CreateFileW
import fs.win.kit_CreateHardLinkW
import fs.win.kit_CreateSymbolicLinkW
import fs.win.kit_DeleteFileW
import fs.win.kit_FlushFileBuffers
import fs.win.kit_GetFileAttributesW
import fs.win.kit_GetFileTime
import fs.win.kit_MoveFileExW
import fs.win.kit_ReadFile
import fs.win.kit_RemoveDirectoryW
import fs.win.kit_SetFileAttributesW
import fs.win.kit_SetFileTime
import fs.win.kit_WriteFile
import fs.win.kit_last_error
import fs.win.kit_rename_posix
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.toKStringFromUtf16
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import kotlinx.io.files.Path
import platform.windows.CREATE_ALWAYS
import platform.windows.CREATE_NEW
import platform.windows.DWORDVar
import platform.windows.ERROR_ACCESS_DENIED
import platform.windows.ERROR_DIR_NOT_EMPTY
import platform.windows.FILETIME
import platform.windows.FILE_ATTRIBUTE_DIRECTORY
import platform.windows.FILE_ATTRIBUTE_NORMAL
import platform.windows.FILE_ATTRIBUTE_READONLY
import platform.windows.FILE_ATTRIBUTE_REPARSE_POINT
import platform.windows.FILE_READ_ATTRIBUTES
import platform.windows.FILE_WRITE_ATTRIBUTES
import platform.windows.GENERIC_READ
import platform.windows.GENERIC_WRITE
import platform.windows.GetCurrentProcessId
import platform.windows.GetSystemTimePreciseAsFileTime
import platform.windows.GetTempPathW
import platform.windows.HANDLE
import platform.windows.INVALID_FILE_ATTRIBUTES
import platform.windows.MOVEFILE_REPLACE_EXISTING
import platform.windows.OPEN_EXISTING
import platform.windows.WCHARVar

internal actual fun readWhole(path: Path): ByteArray? {
    val h = opened(kit_CreateFileW(path.win, GENERIC_READ, SHARE_ALL, OPEN_EXISTING.convert(), FILE_ATTRIBUTE_NORMAL.convert()))
    return h?.use {
        memScoped {
            val got = alloc<DWORDVar>()
            readChunks { buf ->
                val ok = buf.usePinned { kit_ReadFile(h, it.addressOf(0), buf.size.convert(), got.ptr) }
                if (ok == 0) -1 else got.value.toInt()
            } ?: failed()
        }
    }
}

/** The new file is opened sharing everything, as Rust's `File` is, so it can be renamed and removed while open. */
internal actual fun openWrite(
    path: Path,
    create: Create,
    mode: Int,
): WriteHandle? {
    val how = if (create == Create.New) CREATE_NEW else CREATE_ALWAYS
    val h = opened(kit_CreateFileW(path.win, GENERIC_WRITE.convert(), SHARE_ALL, how.convert(), FILE_ATTRIBUTE_NORMAL.convert()))
    return h?.let { FileHandle(it, path) }
}

private class FileHandle(
    private val handle: HANDLE,
    private val path: Path,
) : WriteHandle {
    override fun write(
        bytes: ByteArray,
        at: Int,
        len: Int,
    ): Int =
        memScoped {
            val put = alloc<DWORDVar>()
            val ok = bytes.usePinned { kit_WriteFile(handle, it.addressOf(at), len.convert(), put.ptr) }
            if (ok != 0 || failedFalse()) put.value.toInt() else -1
        }

    override fun sync() = kit_FlushFileBuffers(handle) != 0 || failedFalse()

    // The read-only attribute is the only part of a mode Windows keeps, and it is set by name.
    override fun setMode(bits: Int) = chmodEntry(path, bits)

    override fun close() {
        platform.windows.CloseHandle(handle)
    }
}

/**
 * Rust `fs::rename`: a file at [to] is replaced. MoveFileExW refuses while another process has [to] open; the POSIX
 * rename Rust then tries does not.
 */
internal actual fun renameEntry(
    from: Path,
    to: Path,
): Boolean {
    if (kit_MoveFileExW(from.win, to.win, MOVEFILE_REPLACE_EXISTING.convert()) != 0) return true
    val code = kit_last_error().toInt()
    return if (code == ERROR_ACCESS_DENIED) renamePosix(from, to, code) else failedFalse(code)
}

/** The retry's own failure is reported only for a full directory, as in Rust; otherwise MoveFileExW's [moveError]. */
private fun renamePosix(
    from: Path,
    to: Path,
    moveError: Int,
): Boolean = kit_rename_posix(from.win, to.win) != 0 || failedFalse(kit_last_error().toInt().takeIf { it == ERROR_DIR_NOT_EMPTY } ?: moveError)

/** A link to a directory is a directory entry on Windows; it goes with RemoveDirectoryW, its target untouched. */
internal actual fun unlinkEntry(path: Path): Boolean {
    if (kit_DeleteFileW(path.win) != 0) return true
    val code = kit_last_error().toInt()
    val attrs = kit_GetFileAttributesW(path.win)
    val dirLink = FILE_ATTRIBUTE_DIRECTORY or FILE_ATTRIBUTE_REPARSE_POINT
    val isDirLink = attrs != INVALID_FILE_ATTRIBUTES && attrs.toInt() and dirLink == dirLink
    return if (isDirLink) removeDirEntry(path) else failedFalse(code)
}

internal actual fun removeDirEntry(path: Path) = kit_RemoveDirectoryW(path.win) != 0 || failedFalse()

/** Rust `set_permissions` on Windows: read-only exactly when no owner write bit is set. */
internal actual fun chmodEntry(
    path: Path,
    bits: Int,
): Boolean {
    val attrs = kit_GetFileAttributesW(path.win)
    if (attrs == INVALID_FILE_ATTRIBUTES) return failedFalse()
    val readOnly = bits and OWNER_WRITE == 0
    val kept = attrs.toInt() and FILE_ATTRIBUTE_READONLY.inv()
    val next = (if (readOnly) kept or FILE_ATTRIBUTE_READONLY else kept).takeIf { it != 0 } ?: FILE_ATTRIBUTE_NORMAL
    return kit_SetFileAttributesW(path.win, next.convert()) != 0 || failedFalse()
}

internal actual fun hardLinkEntry(
    original: Path,
    link: Path,
) = kit_CreateHardLinkW(link.win, original.win) != 0 || failedFalse()

/**
 * Rust `symlink_file` and `symlink_dir`: unprivileged where Developer Mode allows it, and retried without that flag on
 * a Windows too old to know it. A relative target takes backslashes, which Windows resolves and slashes it does not.
 */
internal actual fun symlinkEntry(
    target: String,
    link: Path,
    dir: Boolean,
): Boolean {
    val written = if (Path(target).isAbsolute) Path(target).win else target.replace('/', '\\')
    val kind = if (dir) SYMBOLIC_LINK_FLAG_DIRECTORY else 0

    fun make(flags: Int) = kit_CreateSymbolicLinkW(link.win, written, flags.convert()).toInt() != 0
    val made =
        make(kind or SYMBOLIC_LINK_FLAG_ALLOW_UNPRIVILEGED_CREATE) ||
            (kit_last_error().toInt() == ERROR_INVALID_PARAMETER && make(kind))
    return made || failedFalse()
}

/** Rust cannot open a directory as a `File` on Windows, so its `sync_dir` never succeeds there; nothing to do. */
internal actual fun syncDirEntry(dir: Path) = Unit

internal actual fun modifiedAgoNanos(path: Path): Long? =
    openMeta(path, follow = true, access = FILE_READ_ATTRIBUTES.convert())?.use { h ->
        memScoped {
            val modified = alloc<FILETIME>()
            if (kit_GetFileTime(h, modified.ptr) == 0) return failed()
            ((now() - ticks(modified)) * NANOS_PER_TICK).coerceAtLeast(0)
        }
    }

internal actual fun setModifiedAgoNanos(
    path: Path,
    nanos: Long,
): Boolean = setFileTimes(path, now() - nanos / NANOS_PER_TICK, access = false)

internal actual fun setTimesSeconds(
    path: Path,
    seconds: Long,
): Boolean = setFileTimes(path, (seconds + UNIX_EPOCH_SECONDS) * TICKS_PER_SECOND, access = true)

/** Set the modification time, and the access time when [access], to [ticks] of 100 ns since 1601. */
private fun setFileTimes(
    path: Path,
    ticks: Long,
    access: Boolean,
): Boolean =
    openMeta(path, follow = true, access = FILE_WRITE_ATTRIBUTES.convert())?.use { h ->
        memScoped {
            val time = alloc<FILETIME>()
            time.dwLowDateTime = (ticks and LOW_32).convert()
            time.dwHighDateTime = (ticks ushr HIGH_SHIFT).convert()
            kit_SetFileTime(h, if (access) time.ptr else null, time.ptr) != 0 || failedFalse()
        }
    } ?: false

private fun now(): Long =
    memScoped {
        val time = alloc<FILETIME>()
        GetSystemTimePreciseAsFileTime(time.ptr)
        ticks(time)
    }

private fun ticks(time: FILETIME) = (time.dwHighDateTime.toLong() shl HIGH_SHIFT) or time.dwLowDateTime.toLong()

/** Rust `env::temp_dir`: GetTempPathW, without its trailing backslash. */
internal actual fun tempRoot(): Path =
    memScoped {
        val buf = allocArray<WCHARVar>(TEMP_CHARS)
        val n = GetTempPathW(TEMP_CHARS.convert(), buf).toInt()
        Path(if (n in 1 until TEMP_CHARS) buf.toKStringFromUtf16().trimEnd('\\') else ".")
    }

internal actual fun processId(): Int = GetCurrentProcessId().toInt()

internal actual val keepsModeOnReplace = false

internal actual val linksAreOptional = true

private const val OWNER_WRITE = 0b010_000_000
private const val SYMBOLIC_LINK_FLAG_DIRECTORY = 0x1
private const val SYMBOLIC_LINK_FLAG_ALLOW_UNPRIVILEGED_CREATE = 0x2
private const val NANOS_PER_TICK = 100L
private const val TICKS_PER_SECOND = 10_000_000L

/** Seconds from 1601-01-01, where FILETIME counts from, to 1970-01-01. */
private const val UNIX_EPOCH_SECONDS = 11_644_473_600L
private const val LOW_32 = 0xFFFF_FFFFL
private const val HIGH_SHIFT = 32
private const val TEMP_CHARS = 1024

internal actual fun simplified(path: String) = withoutVerbatim(path)
