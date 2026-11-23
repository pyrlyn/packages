package pyrlyn.sync.client

import kotlin.coroutines.cancellation.CancellationException

/** Why no token could be had. */
sealed class AuthException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause) {
    /** There is no token, or the server refused it even after a refresh. */
    class LoginRequired : AuthException("no valid token: the user must log in")

    /** The user denied the login on the verification page. */
    class Denied : AuthException("the login was denied")

    /** The login code expired before the user approved it: start a new login. */
    class Expired : AuthException("the login code expired before it was approved")

    /** The auth server could not be reached or gave an answer the client cannot use. */
    class Server(
        message: String,
        cause: Throwable? = null,
    ) : AuthException("auth server: $message", cause)

    /** The secret store failed. */
    class Store(
        cause: Throwable,
    ) : AuthException("secret store: ${cause.message}", cause)
}

/** Supplies bearer tokens to [SyncClient]; both calls throw [AuthException]. */
interface TokenSource {
    /** The token to send now. */
    suspend fun token(): Secret

    /**
     * A new token after the server refused the current one: it expired, or it names another
     * organization than the scope. Called at most once per request, so a source that cannot do
     * better throws [AuthException.LoginRequired].
     */
    suspend fun refresh(): Secret
}

/** Runs a [SecretStore] call, reporting its failure as [AuthException.Store]. */
internal suspend fun <T> secrets(call: suspend () -> T): T =
    try {
        call()
    } catch (e: CancellationException) {
        throw e
    } catch (
        // Whatever the app's store throws is a store failure.
        @Suppress("TooGenericExceptionCaught") e: Exception,
    ) {
        throw AuthException.Store(e)
    }

/** A token another step saved under [account] in [store]. */
class StoredToken(
    private val store: SecretStore,
    private val account: String,
) : TokenSource {
    /** Keeps [token] for the next run. */
    suspend fun save(token: Secret) = secrets { store.set(account, token) }

    /** Forgets the token, for logout. */
    suspend fun clear() = secrets { store.delete(account) }

    override suspend fun token(): Secret = secrets { store.get(account) } ?: throw AuthException.LoginRequired()

    // Nothing here can mint a token, so a refused one means a new login.
    override suspend fun refresh(): Secret = throw AuthException.LoginRequired()

    override fun toString(): String = "StoredToken(account=$account)"
}
