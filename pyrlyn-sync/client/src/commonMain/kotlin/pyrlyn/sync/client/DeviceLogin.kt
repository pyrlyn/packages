package pyrlyn.sync.client

import http.HttpClient
import http.HttpException
import http.HttpRequest
import http.HttpResponse
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlin.io.encoding.Base64
import kotlin.time.Clock
import kotlin.time.ComparableTimeMark
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

private const val DEVICE_GRANT = "urn:ietf:params:oauth:grant-type:device_code"

/** RFC 8628, section 3.5: every `slow_down` adds five seconds to the polling interval. */
internal val SLOW_DOWN = 5.seconds

/** A JWT this close to its `exp` is replaced before it is sent, so it cannot expire in flight. */
private const val EXPIRY_MARGIN_SECS = 60L

/** A hung auth server must not hang a sync round. */
private val HTTP_TIMEOUT = 30.seconds

private const val UNAUTHORIZED = 401

private val JSON_HEADERS = listOf("content-type" to "application/json")

/** A login waiting for the user: show them [userCode] and where to enter it, then await [DeviceLogin.finish]. */
class PendingLogin internal constructor(
    /** The code the user types on the verification page. */
    val userCode: String,
    /** The page where the user signs in and enters the code. */
    val verificationUri: String,
    /** The same page with the code filled in, if the server offers one. */
    val verificationUriComplete: String?,
    internal val deviceCode: Secret,
    internal val interval: Duration,
    internal val expiresAt: ComparableTimeMark,
) {
    override fun toString(): String = "PendingLogin(userCode=$userCode, verificationUri=$verificationUri)"
}

/**
 * Logs the user in with the device authorization flow at [authUrl] (the api's `/api/auth`) as app
 * [clientId], and serves sync JWTs.
 *
 * The session token the login yields is the refresh credential: it stays in [store] under
 * `<account>/session` and mints a 15-minute JWT, kept under `<account>/jwt`, whenever the last one
 * is about to expire or the sync service refuses it. [http] is the app's client: device login does
 * not pick an HTTP engine, as gRPC's TLS and libcurl's clash on Linux.
 */
class DeviceLogin(
    private val http: HttpClient,
    authUrl: String,
    private val clientId: String,
    private val store: SecretStore,
    account: String,
    private val clock: Clock = Clock.System,
    private val time: TimeSource.WithComparableMarks = TimeSource.Monotonic,
) : TokenSource {
    private val authUrl = authUrl.trimEnd('/')
    private val session = "$account/session"
    private val jwt = "$account/jwt"

    /** Asks the server for a user code. Throws [AuthException.Server] when the server cannot be reached or refuses. */
    suspend fun start(): PendingLogin {
        val response = send("device code") { post(HttpRequest(url("/device/code"), JSON_HEADERS, body { put("client_id", clientId) })) }
        val code = response.succeeded("device code").json("device code")
        return PendingLogin(
            userCode = code.text("user_code"),
            verificationUri = code.text("verification_uri"),
            verificationUriComplete = code.optionalText("verification_uri_complete"),
            deviceCode = Secret(code.text("device_code")),
            interval = code.seconds("interval"),
            expiresAt = time.markNow() + code.seconds("expires_in"),
        )
    }

    /**
     * Waits until the user approves [login], polling at the server's interval, and keeps the
     * session. A JWT of an earlier login is dropped: it may be another user's. Throws
     * [AuthException.Denied] or [AuthException.Expired] when the login ends without approval,
     * [AuthException.Server] when the server fails, [AuthException.Store] when the store does.
     */
    suspend fun finish(login: PendingLogin) {
        var interval = login.interval
        while (true) {
            delay(interval)
            if (login.expiresAt.hasPassedNow()) throw AuthException.Expired()
            val body =
                body {
                    put("grant_type", DEVICE_GRANT)
                    put("device_code", login.deviceCode.reveal())
                    put("client_id", clientId)
                }
            val response = send("device token") { post(HttpRequest(url("/device/token"), JSON_HEADERS, body)) }
            val grant = response.json("device token")
            val session = grant.optionalText("access_token")
            if (session != null && response.isSuccess) {
                secrets { store.delete(jwt) }
                secrets { store.set(this.session, Secret(session)) }
                return
            }
            when (val error = grant.optionalText("error")) {
                "authorization_pending" -> {}

                "slow_down" -> {
                    interval += SLOW_DOWN
                }

                else -> {
                    throw refusal(error, response.status)
                }
            }
        }
    }

    /** Forgets the session and the JWT, for logout. Throws [AuthException.Store] when the store fails. */
    suspend fun logout() {
        secrets { store.delete(jwt) }
        secrets { store.delete(session) }
    }

    override suspend fun token(): Secret {
        val current = secrets { store.get(jwt) }
        return if (current != null && !expiresSoon(current.reveal(), clock.now().epochSeconds)) current else mint()
    }

    override suspend fun refresh(): Secret = mint()

    /** Trades the session for a new JWT and keeps it. */
    private suspend fun mint(): Secret {
        val session = secrets { store.get(session) } ?: throw AuthException.LoginRequired()
        val response = send("token") { get(HttpRequest(url("/token"), listOf("authorization" to "Bearer ${session.reveal()}"))) }
        if (response.status == UNAUTHORIZED) {
            // The session expired or was revoked (sign-out, ban): only a new login helps.
            logout()
            throw AuthException.LoginRequired()
        }
        val token = Secret(response.succeeded("token").json("token").text("token"))
        secrets { store.set(jwt, token) }
        return token
    }

    private fun url(path: String) = authUrl + path

    private suspend fun send(
        what: String,
        request: suspend HttpClient.() -> HttpResponse,
    ): HttpResponse =
        try {
            withTimeout(HTTP_TIMEOUT) { http.request() }
        } catch (e: TimeoutCancellationException) {
            throw AuthException.Server("$what: no answer before the deadline", e)
        } catch (e: HttpException) {
            throw AuthException.Server("$what: ${e.message}", e)
        }

    override fun toString(): String = "DeviceLogin(authUrl=$authUrl, clientId=$clientId, session=$session)"
}

/** Why the server ended a device login with [error]. */
private fun refusal(
    error: String?,
    status: Int,
): AuthException =
    when (error) {
        "access_denied" -> AuthException.Denied()
        "expired_token" -> AuthException.Expired()
        else -> AuthException.Server("device token: $status ${error.orEmpty()}")
    }

private fun HttpResponse.succeeded(what: String): HttpResponse = if (isSuccess) this else throw AuthException.Server("$what: $status")

private fun body(fields: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit): ByteArray = buildJsonObject(fields).toString().encodeToByteArray()

private fun HttpResponse.json(what: String): JsonObject =
    try {
        Json.parseToJsonElement(text()).jsonObject
    } catch (e: SerializationException) {
        throw AuthException.Server("$what: the answer is not JSON", e)
    } catch (e: IllegalArgumentException) {
        throw AuthException.Server("$what: the answer is not a JSON object", e)
    }

private fun JsonObject.optionalText(key: String): String? = (get(key) as? JsonPrimitive)?.takeIf { it.isString }?.content

private fun JsonObject.text(key: String): String = optionalText(key) ?: throw AuthException.Server("the answer has no $key")

private fun JsonObject.seconds(key: String): Duration =
    (get(key) as? JsonPrimitive)
        ?.takeIf { !it.isString }
        ?.longOrNull
        ?.takeIf { it >= 0 }
        ?.seconds ?: throw AuthException.Server("the answer has no $key")

/**
 * Whether [jwt] expires within the margin of [now] (seconds). The claims are read, not verified:
 * the sync service verifies, and a token without a readable `exp` is sent so its refusal decides.
 */
internal fun expiresSoon(
    jwt: String,
    now: Long,
): Boolean {
    val exp =
        try {
            val claims =
                jwt.split('.').getOrNull(1)?.let {
                    Base64.UrlSafe
                        .withPadding(Base64.PaddingOption.ABSENT_OPTIONAL)
                        .decode(it)
                        .decodeToString()
                }
            (claims?.let { Json.parseToJsonElement(it).jsonObject["exp"] } as? JsonPrimitive)?.takeIf { !it.isString }?.longOrNull
        } catch (_: IllegalArgumentException) {
            null
        }
    return exp != null && exp >= 0 && exp <= now + EXPIRY_MARGIN_SECS
}
