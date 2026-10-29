package process

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class EnvTest {
    @Test
    fun aVariableSetHereIsInTheEnvironmentUntilUnset() {
        val key = "KIT_ENV_TEST_VALUE"
        setEnv(key, "from the parent")
        try {
            assertEquals("from the parent", envVars().toMap()[key])
        } finally {
            unsetEnv(key)
        }
        assertNull(envVars().toMap()[key])
    }
}
