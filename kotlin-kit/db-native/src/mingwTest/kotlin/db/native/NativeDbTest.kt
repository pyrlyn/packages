package db.native

import db.testing.DriverContract
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toKString
import platform.posix.getenv
import platform.windows.DeleteFileW
import kotlin.test.Test
import kotlin.test.assertEquals

class NativeDbTest {
    private val contract = DriverContract(NativeDbDriver)

    @Test fun roundTripsTypesAndFts5() = contract.roundTripsTypesAndFts5()

    @Test fun columnsHaveTheirNamesAndTheLastRowIdComesBack() = contract.columnsHaveTheirNamesAndTheLastRowIdComesBack()

    @Test fun aCursorIsSteppedByItsCaller() = contract.aCursorIsSteppedByItsCaller()

    @Test fun transactionRollsBackAndNests() = contract.transactionRollsBackAndNests()

    @Test fun errorsCarryTheSqliteCode() = contract.errorsCarryTheSqliteCode()

    @Test fun scriptsRunWhole() = contract.scriptsRunWhole(DriverContract.SCRIPT)

    @Test fun installsOnce() = contract.installsOnce()

    /** SQLite takes a UTF-8 file name; its Windows VFS must turn it into the wide path the file system needs. */
    @OptIn(ExperimentalForeignApi::class)
    @Test
    fun aFileWithANonAsciiNameIsCreatedAndReadBack() {
        val path = "${getenv("TEMP")!!.toKString()}\\kit-db-é-${kotlin.random.Random.nextInt(1_000_000)}.db"
        try {
            NativeDbDriver.open(path).use { it.exec("CREATE TABLE t (a TEXT); INSERT INTO t VALUES ('kept')") }
            assertEquals("kept", NativeDbDriver.open(path).use { it.stringOf("SELECT a FROM t") })
        } finally {
            DeleteFileW(path)
        }
    }
}
