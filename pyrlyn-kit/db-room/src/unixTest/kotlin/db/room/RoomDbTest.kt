package db.room

import db.testing.DriverContract
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RoomDbTest {
    private val contract = DriverContract(RoomDbDriver)

    @Test fun roundTripsTypesAndFts5() = contract.roundTripsTypesAndFts5()

    @Test fun columnsHaveTheirNamesAndTheLastRowIdComesBack() = contract.columnsHaveTheirNamesAndTheLastRowIdComesBack()

    @Test fun aCursorIsSteppedByItsCaller() = contract.aCursorIsSteppedByItsCaller()

    @Test fun transactionRollsBackAndNests() = contract.transactionRollsBackAndNests()

    @Test fun errorsCarryTheSqliteCode() = contract.errorsCarryTheSqliteCode()

    @Test fun installsOnce() = contract.installsOnce()

    @Test
    fun scriptsSplitOnStatementsButNotInsideTriggersQuotesOrComments() {
        val parts = splitSql(DriverContract.SCRIPT)
        assertEquals(3, parts.size, parts.joinToString("\n---\n"))
        assertTrue("CREATE TRIGGER" in parts[1] && parts[1].endsWith("END;"), parts[1])
        contract.scriptsRunWhole(DriverContract.SCRIPT)
    }
}
