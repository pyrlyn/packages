package db.sqldelight

import app.cash.sqldelight.TransacterImpl
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.use
import db.room.RoomDbDriver
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails

class KitSqlDriverTest {
    private class Transacter(
        driver: SqlDriver,
    ) : TransacterImpl(driver)

    private fun SqlDriver.rows(): List<List<Any?>> =
        executeQuery(null, "SELECT a, b, c, d, e FROM t ORDER BY rowid", { c ->
            val out = mutableListOf<List<Any?>>()
            while (c.next().value) out += listOf(c.getLong(0), c.getString(1), c.getBytes(2)?.toList(), c.getDouble(3), c.getBoolean(4))
            QueryResult.Value(out)
        }, 0).value

    @Test
    fun parametersBindFromZeroAndEveryTypeRoundTrips() {
        KitSqlDriver(RoomDbDriver.open(":memory:")).use { driver ->
            driver.execute(null, "CREATE TABLE t (a INTEGER, b TEXT, c BLOB, d REAL, e INTEGER)", 0)
            val changed =
                driver
                    .execute(null, "INSERT INTO t VALUES (?, ?, ?, ?, ?)", 5) {
                        bindLong(0, 7)
                        bindString(1, "x'); DROP TABLE t;--")
                        bindBytes(2, byteArrayOf(1, 2))
                        bindDouble(3, 0.5)
                        bindBoolean(4, true)
                    }.value
            assertEquals(1L, changed)
            driver.execute(null, "INSERT INTO t VALUES (?, ?, ?, ?, ?)", 5) {
                bindLong(0, null)
                bindString(1, null)
                bindBytes(2, null)
                bindDouble(3, null)
                bindBoolean(4, false)
            }
            assertEquals(
                listOf(listOf(7L, "x'); DROP TABLE t;--", listOf<Byte>(1, 2), 0.5, true), listOf(null, null, null, null, false)),
                driver.rows(),
            )
        }
    }

    @Test
    fun aFailedInnerTransactionRollsBackTheOuterOne() {
        KitSqlDriver(RoomDbDriver.open(":memory:")).use { driver ->
            val db = Transacter(driver)
            driver.execute(null, "CREATE TABLE t (a INTEGER, b TEXT, c BLOB, d REAL, e INTEGER)", 0)
            db.transaction { driver.execute(null, "INSERT INTO t (a) VALUES (1)", 0) }
            assertFails {
                db.transaction {
                    driver.execute(null, "INSERT INTO t (a) VALUES (2)", 0)
                    db.transaction { error("inner") }
                }
            }
            assertEquals(listOf(1L), driver.rows().map { it[0] })
            assertEquals(null, driver.currentTransaction())
        }
    }
}
