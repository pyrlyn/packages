// SQLite's C API behind the driver-neutral db interfaces: what androidx.sqlite's BundledSQLiteDriver does in db-room,
// done here for Windows, which it has no build for. One connection per `Db`, opened with db-room's flags, and nothing
// set on it the caller did not ask for.
@file:OptIn(ExperimentalForeignApi::class)

package db.native

import cnames.structs.sqlite3
import cnames.structs.sqlite3_stmt
import db.Cursor
import db.Db
import db.DbDriver
import db.DbException
import db.Row
import db.native.sqlite.SQLITE_DONE
import db.native.sqlite.SQLITE_MISUSE
import db.native.sqlite.SQLITE_NULL
import db.native.sqlite.SQLITE_OK
import db.native.sqlite.SQLITE_OPEN_CREATE
import db.native.sqlite.SQLITE_OPEN_FULLMUTEX
import db.native.sqlite.SQLITE_OPEN_READWRITE
import db.native.sqlite.SQLITE_OPEN_URI
import db.native.sqlite.SQLITE_ROW
import db.native.sqlite.kit_bind_blob
import db.native.sqlite.kit_bind_text
import db.native.sqlite.sqlite3_bind_double
import db.native.sqlite.sqlite3_bind_int64
import db.native.sqlite.sqlite3_bind_null
import db.native.sqlite.sqlite3_changes
import db.native.sqlite.sqlite3_close_v2
import db.native.sqlite.sqlite3_column_blob
import db.native.sqlite.sqlite3_column_bytes
import db.native.sqlite.sqlite3_column_count
import db.native.sqlite.sqlite3_column_double
import db.native.sqlite.sqlite3_column_int64
import db.native.sqlite.sqlite3_column_name
import db.native.sqlite.sqlite3_column_text
import db.native.sqlite.sqlite3_column_type
import db.native.sqlite.sqlite3_errmsg
import db.native.sqlite.sqlite3_errstr
import db.native.sqlite.sqlite3_exec
import db.native.sqlite.sqlite3_extended_errcode
import db.native.sqlite.sqlite3_finalize
import db.native.sqlite.sqlite3_get_autocommit
import db.native.sqlite.sqlite3_last_insert_rowid
import db.native.sqlite.sqlite3_libversion
import db.native.sqlite.sqlite3_open_v2
import db.native.sqlite.sqlite3_prepare_v2
import db.native.sqlite.sqlite3_step
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.CPointerVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.toKString
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value

/** The driver to install as `Db.driver` on Windows. */
object NativeDbDriver : DbDriver {
    private const val FLAGS = SQLITE_OPEN_READWRITE or SQLITE_OPEN_CREATE or SQLITE_OPEN_FULLMUTEX or SQLITE_OPEN_URI

    override fun open(path: String): Db =
        memScoped {
            val handle = alloc<CPointerVar<sqlite3>>()
            val rc = sqlite3_open_v2(path, handle.ptr, FLAGS, null)
            val conn = handle.value
            if (rc != SQLITE_OK) {
                // SQLite hands back a handle even when the open fails; only it knows why, and it must still be closed.
                val e = DbException(conn?.let(::sqlite3_extended_errcode) ?: rc, conn?.let(::message) ?: errstr(rc))
                sqlite3_close_v2(conn)
                throw e
            }
            NativeDb(conn!!)
        }

    override fun version(): String = sqlite3_libversion()?.toKString() ?: "?"
}

private fun message(conn: CPointer<sqlite3>) = sqlite3_errmsg(conn)?.toKString() ?: "sqlite error"

private fun errstr(rc: Int) = sqlite3_errstr(rc)?.toKString() ?: "sqlite error $rc"

private class StatementCursor(
    private val st: CPointer<sqlite3_stmt>,
    private val fail: (Int) -> Nothing,
) : Cursor {
    override fun next() =
        when (val rc = sqlite3_step(st)) {
            SQLITE_ROW -> true
            SQLITE_DONE -> false
            else -> fail(rc)
        }

    override fun isNull(i: Int) = sqlite3_column_type(st, i) == SQLITE_NULL

    override fun long(i: Int) = sqlite3_column_int64(st, i)

    override fun int(i: Int) = long(i).toInt()

    override fun double(i: Int) = sqlite3_column_double(st, i)

    // The byte count is read after the text, as SQLite asks; the text may hold a NUL a C string would stop at.
    override fun string(i: Int): String {
        val text = sqlite3_column_text(st, i) ?: return ""
        return text.readBytes(sqlite3_column_bytes(st, i)).decodeToString()
    }

    // A zero-length blob comes back as a null pointer too.
    override fun blob(i: Int): ByteArray {
        val bytes = sqlite3_column_blob(st, i) ?: return ByteArray(0)
        return bytes.readBytes(sqlite3_column_bytes(st, i))
    }

    override val columns: Int get() = sqlite3_column_count(st)

    override fun name(i: Int) = sqlite3_column_name(st, i)?.toKString().orEmpty()
}

private class NativeDb(
    private val conn: CPointer<sqlite3>,
) : Db {
    private var closed = false

    /** sqlite3_exec runs the whole script by SQLite's own rules, triggers and all. */
    override fun exec(sql: String) {
        val rc = sqlite3_exec(conn, sql, null, null, null)
        if (rc != SQLITE_OK) fail(rc)
    }

    override fun execute(
        sql: String,
        vararg args: Any?,
    ): Int {
        cursor(sql, *args) { c ->
            while (c.next()) {
                // rows a statement returns are not wanted here
            }
        }
        return changes()
    }

    override fun <T> query(
        sql: String,
        vararg args: Any?,
        map: (Row) -> T,
    ): List<T> =
        cursor(sql, *args) { row ->
            val out = ArrayList<T>()
            while (row.next()) out += map(row)
            out
        }

    override fun <T> cursor(
        sql: String,
        vararg args: Any?,
        use: (Cursor) -> T,
    ): T {
        val st = prepare(sql)
        try {
            args.forEachIndexed { i, a -> bind(st, i + 1, a) }
            return use(StatementCursor(st, ::fail))
        } finally {
            sqlite3_finalize(st)
        }
    }

    override fun lastInsertRowid(): Long = sqlite3_last_insert_rowid(conn)

    override fun changes(): Int = sqlite3_changes(conn)

    override val inTransaction: Boolean get() = sqlite3_get_autocommit(conn) == 0

    override fun close() {
        if (!closed) {
            closed = true
            sqlite3_close_v2(conn)
        }
    }

    private fun prepare(sql: String): CPointer<sqlite3_stmt> =
        memScoped {
            val st = alloc<CPointerVar<sqlite3_stmt>>()
            val rc = sqlite3_prepare_v2(conn, sql, -1, st.ptr, null)
            if (rc != SQLITE_OK) fail(rc)
            // Text with no statement in it, a comment alone, prepares to nothing.
            st.value ?: throw DbException(SQLITE_MISUSE, "no statement in: $sql")
        }

    private fun bind(
        st: CPointer<sqlite3_stmt>,
        i: Int,
        a: Any?,
    ) {
        val rc =
            when (a) {
                null -> sqlite3_bind_null(st, i)
                is Int -> sqlite3_bind_int64(st, i, a.toLong())
                is Long -> sqlite3_bind_int64(st, i, a)
                is UInt -> sqlite3_bind_int64(st, i, a.toLong())
                is ULong -> sqlite3_bind_int64(st, i, a.toLong())
                is Boolean -> sqlite3_bind_int64(st, i, if (a) 1 else 0)
                is Double -> sqlite3_bind_double(st, i, a)
                is Float -> sqlite3_bind_double(st, i, a.toDouble())
                is String -> bytes(a.encodeToByteArray()) { p, n -> kit_bind_text(st, i, p, n) }
                is ByteArray -> bytes(a) { p, n -> kit_bind_blob(st, i, p, n) }
                else -> throw IllegalArgumentException("cannot bind ${a::class}")
            }
        if (rc != SQLITE_OK) fail(rc)
    }

    /** The extended code SQLite keeps for the last call that failed on this connection, with its message. */
    private fun fail(rc: Int): Nothing = throw DbException(sqlite3_extended_errcode(conn).takeIf { it != 0 } ?: rc, message(conn))
}

/** [bytes] pinned for [call]. An empty array has no address, and a null pointer would bind NULL, not `''` or `x''`. */
private inline fun bytes(
    bytes: ByteArray,
    call: (CPointer<ByteVar>, Int) -> Int,
): Int = (if (bytes.isEmpty()) ByteArray(1) else bytes).usePinned { call(it.addressOf(0), bytes.size) }
