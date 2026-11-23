// SQLDelight's own native driver links the system libsqlite3; this one runs its generated code on a kit `Db`, whose
// SQLite is whatever driver the application installed.
package db.sqldelight

import app.cash.sqldelight.Query
import app.cash.sqldelight.Transacter
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlPreparedStatement
import db.Cursor
import db.Db

/** A [SqlDriver] over [db], which it closes when closed. */
class KitSqlDriver(
    private val db: Db,
) : SqlDriver {
    private var current: Transaction? = null
    private val listeners = mutableMapOf<String, MutableSet<Query.Listener>>()

    override fun <R> executeQuery(
        identifier: Int?,
        sql: String,
        mapper: (SqlCursor) -> QueryResult<R>,
        parameters: Int,
        binders: (SqlPreparedStatement.() -> Unit)?,
    ): QueryResult<R> = db.cursor(sql, *Arguments(parameters).apply { binders?.invoke(this) }.values) { mapper(KitCursor(it)) }

    override fun execute(
        identifier: Int?,
        sql: String,
        parameters: Int,
        binders: (SqlPreparedStatement.() -> Unit)?,
    ): QueryResult<Long> = QueryResult.Value(db.execute(sql, *Arguments(parameters).apply { binders?.invoke(this) }.values).toLong())

    override fun newTransaction(): QueryResult<Transacter.Transaction> {
        val enclosing = current
        // SQLDelight rolls the outermost transaction back when any inner one fails, so only the outermost needs SQL.
        if (enclosing == null) db.exec("BEGIN")
        return QueryResult.Value(Transaction(enclosing).also { current = it })
    }

    override fun currentTransaction(): Transacter.Transaction? = current

    override fun addListener(
        vararg queryKeys: String,
        listener: Query.Listener,
    ) {
        queryKeys.forEach { listeners.getOrPut(it) { mutableSetOf() } += listener }
    }

    override fun removeListener(
        vararg queryKeys: String,
        listener: Query.Listener,
    ) {
        queryKeys.forEach { listeners[it]?.remove(listener) }
    }

    override fun notifyListeners(vararg queryKeys: String) {
        queryKeys.flatMap { listeners[it].orEmpty() }.toSet().forEach { it.queryResultsChanged() }
    }

    override fun close() = db.close()

    private inner class Transaction(
        override val enclosingTransaction: Transaction?,
    ) : Transacter.Transaction() {
        override fun endTransaction(successful: Boolean): QueryResult<Unit> {
            if (enclosingTransaction == null) db.exec(if (successful) "COMMIT" else "ROLLBACK")
            current = enclosingTransaction
            return QueryResult.Unit
        }
    }
}

/** SQLDelight numbers parameters from 0. */
private class Arguments(
    count: Int,
) : SqlPreparedStatement {
    val values = arrayOfNulls<Any?>(count)

    override fun bindBytes(
        index: Int,
        bytes: ByteArray?,
    ) {
        values[index] = bytes
    }

    override fun bindLong(
        index: Int,
        long: Long?,
    ) {
        values[index] = long
    }

    override fun bindDouble(
        index: Int,
        double: Double?,
    ) {
        values[index] = double
    }

    override fun bindString(
        index: Int,
        string: String?,
    ) {
        values[index] = string
    }

    override fun bindBoolean(
        index: Int,
        boolean: Boolean?,
    ) {
        values[index] = boolean
    }
}

private class KitCursor(
    private val cursor: Cursor,
) : SqlCursor {
    override fun next(): QueryResult<Boolean> = QueryResult.Value(cursor.next())

    override fun getString(index: Int) = cursor.stringOrNull(index)

    override fun getLong(index: Int) = cursor.longOrNull(index)

    override fun getBytes(index: Int) = cursor.blobOrNull(index)

    override fun getDouble(index: Int) = cursor.doubleOrNull(index)

    override fun getBoolean(index: Int) = cursor.longOrNull(index)?.let { it != 0L }
}
