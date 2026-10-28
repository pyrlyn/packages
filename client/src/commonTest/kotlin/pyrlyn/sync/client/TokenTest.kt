package pyrlyn.sync.client

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

// Ported from ../backend/crates/sync-client/src/token.rs.

class TokenTest {
    @Test
    fun aSavedTokenIsServedUntilCleared() =
        runTest {
            val tokens = StoredToken(MemorySecretStore(), "rtok")
            shouldThrow<AuthException.LoginRequired> { tokens.token() }

            tokens.save(Secret("jwt"))
            tokens.token().reveal() shouldBe "jwt"

            tokens.clear()
            shouldThrow<AuthException.LoginRequired> { tokens.token() }
        }

    @Test
    fun aRefusedStoredTokenAsksForALogin() =
        runTest {
            val tokens = StoredToken(MemorySecretStore(), "rtok")
            tokens.save(Secret("jwt"))
            shouldThrow<AuthException.LoginRequired> { tokens.refresh() }
        }

    @Test
    fun aFailingStoreIsAStoreError() =
        runTest {
            val broken =
                object : SecretStore by MemorySecretStore() {
                    override suspend fun get(account: String): Secret? = error("keychain locked")
                }
            shouldThrow<AuthException.Store> { StoredToken(broken, "rtok").token() }
        }

    @Test
    fun aSecretIsNotPrinted() {
        Secret("jwt").toString() shouldBe "Secret(***)"
    }
}
