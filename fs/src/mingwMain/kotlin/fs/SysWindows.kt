// Win32 for fs: wide-character calls, so a path is never squeezed through the ANSI code page, and the codes and
// words of GetLastError, as Rust's std reports them on Windows.
@file:OptIn(ExperimentalForeignApi::class)

package fs

import fs.win.kit_CreateDirectoryW
import fs.win.kit_CreateFileW
import fs.win.kit_DeviceIoControl
import fs.win.kit_FindFirstFileW
import fs.win.kit_FindNextFileW
import fs.win.kit_GetFileInformationByHandle
import fs.win.kit_GetFileInformationByHandleEx
import fs.win.kit_GetFinalPathNameByHandleW
import fs.win.kit_last_error
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.UShortVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.convert
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.sizeOf
import kotlinx.cinterop.toKStringFromUtf16
import kotlinx.cinterop.value
import kotlinx.io.files.Path
import platform.windows.BY_HANDLE_FILE_INFORMATION
import platform.windows.CloseHandle
import platform.windows.DWORD
import platform.windows.DWORDVar
import platform.windows.FILE_ATTRIBUTE_DIRECTORY
import platform.windows.FILE_ATTRIBUTE_READONLY
import platform.windows.FILE_ATTRIBUTE_REPARSE_POINT
import platform.windows.FILE_ATTRIBUTE_TAG_INFO
import platform.windows.FILE_FLAG_BACKUP_SEMANTICS
import platform.windows.FILE_FLAG_OPEN_REPARSE_POINT
import platform.windows.FILE_SHARE_DELETE
import platform.windows.FILE_SHARE_READ
import platform.windows.FILE_SHARE_WRITE
import platform.windows.FORMAT_MESSAGE_FROM_SYSTEM
import platform.windows.FORMAT_MESSAGE_IGNORE_INSERTS
import platform.windows.FindClose
import platform.windows.FormatMessageW
import platform.windows.HANDLE
import platform.windows.INVALID_HANDLE_VALUE
import platform.windows.OPEN_EXISTING
import platform.windows.WCHARVar
import platform.windows.WIN32_FIND_DATAW
import platform.windows._FILE_INFO_BY_HANDLE_CLASS

/** The code of the last call here that failed, as the shim took it; later calls, the runtime's too, leave it alone. */
@kotlin.native.concurrent.ThreadLocal
private var lastCode = 0

internal actual fun lastOsError(): Int = lastCode

/** Record why the call just made failed; null, so a caller can `return failed()`. */
internal fun failed(code: Int = kit_last_error().toInt()): Nothing? {
    lastCode = code
    return null
}

internal fun failedFalse(code: Int = kit_last_error().toInt()): Boolean {
    lastCode = code
    return false
}

internal actual fun osMessage(code: Int): String =
    memScoped {
        val buf = allocArray<WCHARVar>(MESSAGE_CHARS)
        val flags = FORMAT_MESSAGE_FROM_SYSTEM or FORMAT_MESSAGE_IGNORE_INSERTS
        val n = FormatMessageW(flags.convert(), null, code.convert(), 0u, buf, MESSAGE_CHARS.convert(), null).toInt()
        // FormatMessageW ends the text with CRLF; Rust trims it too.
        if (n == 0) "Unknown error $code" else buf.toKStringFromUtf16().trimEnd()
    }

/** Rust std's `decode_error_kind` for the codes fs tells apart. */
internal actual fun osKind(code: Int): FsException.Kind =
    when (code) {
        ERROR_FILE_NOT_FOUND, ERROR_PATH_NOT_FOUND -> FsException.Kind.NotFound
        ERROR_FILE_EXISTS, ERROR_ALREADY_EXISTS -> FsException.Kind.AlreadyExists
        ERROR_ACCESS_DENIED -> FsException.Kind.PermissionDenied
        ERROR_DIRECTORY -> FsException.Kind.NotADirectory
        ERROR_INVALID_PARAMETER -> FsException.Kind.InvalidInput
        else -> FsException.Kind.Other
    }

/**
 * [path] as Win32 takes it. kotlinx-io joins with `/` even on Windows, and a verbatim `\\?\` path (what [realPath]
 * keeps for a long path or a reserved name) is passed through without normalising, so there a `/` would be read as
 * part of a name.
 */
internal val Path.win: String
    get() = toString().let { if (it.startsWith(VERBATIM_PREFIX)) it.replace('/', '\\') else it }

/** [path] opened for its metadata alone; a link itself unless [follow]. */
internal fun openMeta(
    path: Path,
    follow: Boolean,
    access: DWORD = 0u,
): HANDLE? {
    val flags = FILE_FLAG_BACKUP_SEMANTICS or if (follow) 0 else FILE_FLAG_OPEN_REPARSE_POINT
    return opened(kit_CreateFileW(path.win, access, SHARE_ALL, OPEN_EXISTING.convert(), flags.convert()))
}

/** CreateFileW's result, or null with the failure recorded. */
internal fun opened(h: HANDLE?): HANDLE? = if (h == null || h == INVALID_HANDLE_VALUE) failed() else h

internal inline fun <T> HANDLE.use(body: (HANDLE) -> T): T =
    try {
        body(this)
    } finally {
        CloseHandle(this)
    }

/** As Rust's `symlink_metadata` and `metadata`: a link is a name-surrogate reparse point (a symlink or a junction). */
internal actual fun stat(
    path: Path,
    follow: Boolean,
): Stat? =
    openMeta(path, follow)?.use { h ->
        memScoped {
            val info = alloc<BY_HANDLE_FILE_INFORMATION>()
            if (kit_GetFileInformationByHandle(h, info.ptr) == 0) return failed()
            val attrs = info.dwFileAttributes.toInt()
            val link = !follow && attrs and FILE_ATTRIBUTE_REPARSE_POINT != 0 && isNameSurrogate(h) ?: return failed()
            val dir = attrs and FILE_ATTRIBUTE_DIRECTORY != 0
            val kind =
                when {
                    link -> EntryKind.Symlink
                    dir -> EntryKind.Dir
                    else -> EntryKind.File
                }
            // Windows has only the read-only attribute; it stands for a mode without any write bit.
            val all = if (dir) DIR_MODE else NEW_FILE_MODE
            val mode = if (attrs and FILE_ATTRIBUTE_READONLY != 0) all and WRITE_BITS.inv() else all
            val size = if (link) 0L else (info.nFileSizeHigh.toLong() shl HIGH_SHIFT) or info.nFileSizeLow.toLong()
            Stat(kind, mode, size)
        }
    }

private fun isNameSurrogate(h: HANDLE): Boolean? =
    memScoped {
        val tag = alloc<FILE_ATTRIBUTE_TAG_INFO>()
        val ok =
            kit_GetFileInformationByHandleEx(h, _FILE_INFO_BY_HANDLE_CLASS.FileAttributeTagInfo, tag.ptr, sizeOf<FILE_ATTRIBUTE_TAG_INFO>().convert())
        if (ok == 0) null else tag.ReparseTag.toInt() and NAME_SURROGATE != 0
    }

/** Rust `fs::read_link`: a symlink's or a junction's substitute name, with the NT `\??\` prefix made a user path. */
internal actual fun readLinkText(path: Path): String? =
    openMeta(path, follow = false)?.use { h ->
        memScoped {
            val buf = allocArray<ByteVar>(REPARSE_BUFFER)
            val got = alloc<DWORDVar>()
            if (kit_DeviceIoControl(h, FSCTL_GET_REPARSE_POINT.convert(), buf, REPARSE_BUFFER.convert(), got.ptr) == 0) {
                return failed()
            }
            reparseTarget(buf.readBytes(got.value.toInt())) ?: failed(ERROR_NOT_A_REPARSE_POINT)
        }
    }

/** The target in a REPARSE_DATA_BUFFER, whose layout ntifs.h has and windows.h does not; null for another kind. */
internal fun reparseTarget(data: ByteArray): String? {
    val tag = if (data.size >= SYMLINK_PATHS) u32(data, 0) else 0
    val paths =
        when (tag) {
            IO_REPARSE_TAG_SYMLINK -> SYMLINK_PATHS
            IO_REPARSE_TAG_MOUNT_POINT -> MOUNT_POINT_PATHS
            else -> null
        }
    val name = paths?.let { substituteName(data, it) } ?: return null
    val relative = tag == IO_REPARSE_TAG_SYMLINK && u32(data, SYMLINK_FLAGS) and SYMLINK_FLAG_RELATIVE != 0
    return if (relative) name else userPath(name)
}

/** The UTF-16 substitute name, whose offset counts from the path buffer at [paths]; null when it runs past the end. */
private fun substituteName(
    data: ByteArray,
    paths: Int,
): String? {
    val offset = paths + u16(data, SUBSTITUTE_OFFSET)
    val end = offset + u16(data, SUBSTITUTE_LENGTH)
    return if (end > data.size) null else CharArray((end - offset) / 2) { u16(data, offset + it * 2).toChar() }.concatToString()
}

/** `\??\C:\x` as `C:\x` and `\??\UNC\host\share` as `\\host\share`, as Rust's read_link gives them; others verbatim. */
private fun userPath(nt: String): String {
    if (!nt.startsWith(NT_PREFIX)) return nt
    val rest = nt.substring(NT_PREFIX.length)
    return when {
        rest.length >= 2 && rest[1] == ':' && rest[0].isLetter() -> rest
        rest.startsWith("UNC\\") -> "\\\\" + rest.substring("UNC\\".length)
        else -> VERBATIM_PREFIX + rest
    }
}

private fun u16(
    data: ByteArray,
    at: Int,
) = (data[at].toInt() and BYTE) or ((data[at + 1].toInt() and BYTE) shl BYTE_BITS)

private fun u32(
    data: ByteArray,
    at: Int,
) = u16(data, at) or (u16(data, at + 2) shl (2 * BYTE_BITS))

internal actual fun makeDir(path: Path): Boolean = kit_CreateDirectoryW(path.win) != 0 || failedFalse()

internal actual fun dirNames(dir: Path): List<String>? =
    memScoped {
        val found = alloc<WIN32_FIND_DATAW>()
        val find = kit_FindFirstFileW(Path(dir, "*").win, found.ptr)
        if (find == INVALID_HANDLE_VALUE) return failed()
        try {
            buildList {
                do {
                    val name = wide(found.cFileName)
                    if (name != "." && name != "..") add(name)
                } while (kit_FindNextFileW(find, found.ptr) != 0)
                val code = kit_last_error().toInt()
                if (code != ERROR_NO_MORE_FILES) return failed(code)
            }
        } finally {
            FindClose(find)
        }
    }

private fun wide(chars: CPointer<UShortVar>): String =
    buildString {
        var i = 0
        while (chars[i].toInt() != 0) append(chars[i++].toInt().toChar())
    }

/** fs-kit `canonicalize` on Windows: the final path, its `\\?\` prefix dropped where [simplified] allows. */
internal actual fun realPath(path: Path): String? =
    openMeta(path, follow = true)?.use { h ->
        memScoped {
            var size = PATH_CHARS
            repeat(2) {
                val buf = allocArray<WCHARVar>(size)
                val n = kit_GetFinalPathNameByHandleW(h, buf, size.convert()).toInt()
                if (n == 0) return failed()
                if (n < size) return simplified(buf.toKStringFromUtf16())
                size = n + 1
            }
            failed()
        }
    }

internal val SHARE_ALL: DWORD = (FILE_SHARE_READ or FILE_SHARE_WRITE or FILE_SHARE_DELETE).convert()

private const val MESSAGE_CHARS = 512
private const val PATH_CHARS = 512
private const val HIGH_SHIFT = 32
private const val WRITE_BITS = 0b010_010_010
private const val BYTE = 0xFF
private const val BYTE_BITS = 8
private const val NT_PREFIX = "\\??\\"

// winerror.h, as Rust's std names them.
internal const val ERROR_FILE_NOT_FOUND = 2
internal const val ERROR_PATH_NOT_FOUND = 3
internal const val ERROR_ACCESS_DENIED = 5
private const val ERROR_NO_MORE_FILES = 18
internal const val ERROR_FILE_EXISTS = 80
internal const val ERROR_INVALID_PARAMETER = 87
internal const val ERROR_ALREADY_EXISTS = 183
private const val ERROR_DIRECTORY = 267
private const val ERROR_NOT_A_REPARSE_POINT = 4390

// winioctl.h and ntifs.h: the reparse point query and the REPARSE_DATA_BUFFER layout.
private const val FSCTL_GET_REPARSE_POINT = 0x000900A8
private const val REPARSE_BUFFER = 16 * 1024
private const val NAME_SURROGATE = 0x20000000
private const val IO_REPARSE_TAG_SYMLINK = 0xA000000C.toInt()
private const val IO_REPARSE_TAG_MOUNT_POINT = 0xA0000003.toInt()
private const val SUBSTITUTE_OFFSET = 8
private const val SUBSTITUTE_LENGTH = 10
private const val SYMLINK_FLAGS = 16
private const val SYMLINK_PATHS = 20
private const val MOUNT_POINT_PATHS = 16
private const val SYMLINK_FLAG_RELATIVE = 1
