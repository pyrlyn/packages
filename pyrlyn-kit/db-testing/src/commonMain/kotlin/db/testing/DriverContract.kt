// What `Db` promises whatever SQLite binding is behind it. Plain functions rather than an abstract test class, so
// each adapter's test names them with `@Test` itself and the native test runner needs nothing inherited.
// The numbers are test data, as in the test source sets detekt already lets them be.
@file:Suppress("MagicNumber")

package db.testing

import db.Db
import db.DbDriver
import db.DbException
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class DriverContract(
    private val driver: DbDriver,
) {
    private fun open() = driver.open(":memory:")

    fun roundTripsTypesAndFts5() {
        open().use { db ->
            db.exec("CREATE TABLE t (a INTEGER, b TEXT, c BLOB, d REAL); CREATE VIRTUAL TABLE f USING fts5(x)")
            assertEquals(1, db.execute("INSERT INTO t VALUES (?, ?, ?, ?)", 5L, "héllo", byteArrayOf(0, 1, 2), 1.5))
            db.execute("INSERT INTO t VALUES (?, ?, ?, ?)", null, "", ByteArray(0), null)
            val rows =
                db.query("SELECT a, b, c, d FROM t ORDER BY rowid") {
                    listOf(it.longOrNull(0), it.string(1), it.blob(2).toList(), it.doubleOrNull(3))
                }
            assertEquals(listOf(listOf(5L, "héllo", listOf<Byte>(0, 1, 2), 1.5), listOf(null, "", emptyList<Byte>(), null)), rows)
            db.execute("INSERT INTO f(x) VALUES (?)", "token reduction")
            assertEquals(1L, db.longOf("SELECT count(*) FROM f WHERE f MATCH ?", "reduction"))
            assertEquals(2L, db.longOf("SELECT count(*) FROM t"))
        }
    }

    fun columnsHaveTheirNamesAndTheLastRowIdComesBack() {
        open().use { db ->
            db.exec("CREATE TABLE t (id INTEGER PRIMARY KEY, name TEXT)")
            db.execute("INSERT INTO t (name) VALUES (?)", "a")
            assertEquals(1L, db.lastInsertRowid())
            assertEquals(1, db.changes())
            assertEquals(listOf("id", "name"), db.queryOne("SELECT id, name FROM t") { row -> (0 until row.columns).map(row::name) })
        }
    }

    fun aCursorIsSteppedByItsCaller() {
        open().use { db ->
            db.exec("CREATE TABLE t (a INTEGER); INSERT INTO t VALUES (1), (2), (3)")
            val seen =
                db.cursor("SELECT a FROM t WHERE a > ? ORDER BY a", 1) { c ->
                    assertTrue(c.next())
                    val first = c.long(0)
                    assertTrue(c.next())
                    first to c.long(0)
                }
            assertEquals(2L to 3L, seen)
            assertFalse(db.cursor("SELECT a FROM t WHERE a > 9") { it.next() })
        }
    }

    fun transactionRollsBackAndNests() {
        open().use { db ->
            db.exec("CREATE TABLE t (a INTEGER)")
            assertFails {
                db.transaction {
                    db.execute("INSERT INTO t VALUES (1)")
                    error("boom")
                }
            }
            db.transaction {
                db.execute("INSERT INTO t VALUES (2)")
                runCatching {
                    db.transaction {
                        db.execute("INSERT INTO t VALUES (3)")
                        error("inner")
                    }
                }
            }
            assertEquals(listOf(2L), db.query("SELECT a FROM t") { it.long(0) })
            assertFalse(db.inTransaction)
        }
    }

    fun errorsCarryTheSqliteCode() {
        open().use { db ->
            val e = assertFailsWith<DbException> { db.exec("SELECT * FROM missing") }
            assertEquals(1, e.code)
            assertTrue("no such table" in e.message!!, e.message)
        }
    }

    /** A script's `;` inside a trigger body, a quote or a comment does not end a statement. */
    fun scriptsRunWhole(script: String) {
        open().use { db ->
            db.exec("CREATE TABLE b (x TEXT, y INTEGER); $script")
            db.execute("INSERT INTO a VALUES ('v')")
            assertEquals(1L, db.longOf("SELECT y FROM b"))
        }
    }

    fun installsOnce() {
        Db.installIfAbsent(driver)
        Db.installIfAbsent(
            object : DbDriver {
                override fun open(path: String) = error("x")

                override fun version() = "x"
            },
        )
        assertSame(driver, Db.driver)
        assertTrue(driver.version().startsWith("3."))
    }

    companion object {
        val SCRIPT =
            """
            -- a; comment
            CREATE TABLE a (x TEXT DEFAULT 'semi;colon'); /* block; */
            CREATE TRIGGER tr AFTER INSERT ON a BEGIN
              INSERT INTO b(x) VALUES (new.x); UPDATE b SET y = 1;
            END;
            CREATE TABLE "b;c" (y INTEGER)
            """.trimIndent()
    }
}
