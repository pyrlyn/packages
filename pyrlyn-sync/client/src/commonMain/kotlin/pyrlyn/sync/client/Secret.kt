package pyrlyn.sync.client

/** A token or code that must not reach a log: [toString] hides it, [reveal] hands it out. */
class Secret(
    private val value: String,
) {
    fun reveal(): String = value

    override fun toString(): String = "Secret(***)"

    override fun equals(other: Any?): Boolean = other is Secret && other.value == value

    override fun hashCode(): Int = value.hashCode()
}

/**
 * Where an app keeps its tokens: the OS keychain in an app, [MemorySecretStore] in tests. A failure
 * is any exception; the client reports it as [AuthException.Store].
 */
interface SecretStore {
    suspend fun get(account: String): Secret?

    suspend fun set(
        account: String,
        secret: Secret,
    )

    /** Forgets [account]; forgetting one that is not there is not an error. */
    suspend fun delete(account: String)
}

/** A [SecretStore] in memory, for tests and for apps that log in on every start. */
class MemorySecretStore : SecretStore {
    private val secrets = mutableMapOf<String, Secret>()

    override suspend fun get(account: String): Secret? = secrets[account]

    override suspend fun set(
        account: String,
        secret: Secret,
    ) {
        secrets[account] = secret
    }

    override suspend fun delete(account: String) {
        secrets.remove(account)
    }
}
