@file:OptIn(ExperimentalCoroutinesApi::class)

package pyrlyn.sync.client

import http.HttpClient
import http.HttpRequest
import http.HttpResponse
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.time.Clock
import kotlin.time.Instant

// Ported from ../backend/crates/sync-client/src/login.rs; a scripted auth server stands in for
// wiremock.

private const val NOW = 1_000_000L

private val FIXED =
    object : Clock {
        override fun now() = Instant.fromEpochSeconds(NOW)
    }

private fun jwt(exp: Long): String {
    val claims =
        Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT).encode(
            buildJsonObject {
                put("sub", "ada")
                put("exp", exp)
            }.toString().encodeToByteArray(),
        )
    return "e30.$claims.sig"
}

private fun json(
    status: Int,
    body: JsonObject,
) = HttpResponse(status, body.toString().encodeToByteArray())

private fun pollError(error: String) = json(400, buildJsonObject { put("error", error) })

/** An auth server answering each request that a rule matches with the rule's next answer; the last answer repeats. */
private class AuthServer : HttpClient {
    private class Rule(
        val method: String,
        val path: String,
        val matches: (HttpRequest) -> Boolean,
        val answers: ArrayDeque<HttpResponse>,
    )

    private val rules = mutableListOf<Rule>()
    val received = mutableListOf<Pair<String, String>>()

    fun on(
        method: String,
        path: String,
        vararg answers: HttpResponse,
        matches: (HttpRequest) -> Boolean = { true },
    ) {
        rules += Rule(method, path, matches, ArrayDeque(answers.toList()))
    }

    private fun answer(
        method: String,
        req: HttpRequest,
    ): HttpResponse {
        val path = req.url.removePrefix("https://api.test")
        received += method to path
        val rule = rules.firstOrNull { it.method == method && it.path == path && it.matches(req) } ?: return HttpResponse(404, ByteArray(0))
        return if (rule.answers.size > 1) rule.answers.removeFirst() else rule.answers.first()
    }

    override suspend fun post(req: HttpRequest) = answer("POST", req)

    override suspend fun get(req: HttpRequest) = answer("GET", req)

    override fun close() = Unit
}

private fun HttpRequest.json() = Json.parseToJsonElement(body.decodeToString()).jsonObject

private fun HttpRequest.has(vararg fields: Pair<String, String>) = fields.all { (k, v) -> json()[k] == JsonPrimitive(v) }

private fun HttpRequest.bearer(session: String) = headers.any { (k, v) -> k.equals("authorization", ignoreCase = true) && v == "Bearer $session" }

private fun AuthServer.code() =
    on(
        "POST",
        "/api/auth/device/code",
        json(
            200,
            buildJsonObject {
                put("device_code", "dev-1")
                put("user_code", "ABCD-EFGH")
                put("verification_uri", "https://app.test/device")
                put("verification_uri_complete", "https://app.test/device?user_code=ABCD-EFGH")
                put("expires_in", 1800)
                put("interval", 0)
            },
        ),
    ) { it.has("client_id" to "rtok") }

private fun AuthServer.poll(vararg answers: HttpResponse) =
    on("POST", "/api/auth/device/token", *answers) {
        it.has("grant_type" to "urn:ietf:params:oauth:grant-type:device_code", "device_code" to "dev-1", "client_id" to "rtok")
    }

private fun AuthServer.mint(
    session: String,
    answer: HttpResponse,
) = on("GET", "/api/auth/token", answer) { it.bearer(session) }

private fun TestScope.login(
    server: AuthServer,
    store: SecretStore = MemorySecretStore(),
) = DeviceLogin(server, "https://api.test/api/auth/", "rtok", store, "sync", FIXED, testScheduler.timeSource)

class DeviceLoginTest {
    @Test
    fun aLoginPollsUntilApprovedThenMintsJwtsFromTheSession() =
        runTest {
            val server = AuthServer()
            server.code()
            val granted =
                json(
                    200,
                    buildJsonObject {
                        put("access_token", "session-1")
                        put("token_type", "Bearer")
                    },
                )
            server.poll(pollError("authorization_pending"), pollError("slow_down"), granted)
            val fresh = jwt(NOW + 900)
            server.mint("session-1", json(200, buildJsonObject { put("token", fresh) }))
            val tokens = login(server)

            val pending = tokens.start()
            pending.userCode shouldBe "ABCD-EFGH"
            pending.verificationUri shouldBe "https://app.test/device"
            pending.verificationUriComplete shouldBe "https://app.test/device?user_code=ABCD-EFGH"
            val started = testScheduler.currentTime
            tokens.finish(pending)
            // slow_down stretched the interval from 0 to 5 s.
            (testScheduler.currentTime - started) shouldBe SLOW_DOWN.inWholeMilliseconds

            tokens.token().reveal() shouldBe fresh
            // A token far from expiry is served from the store without asking the server.
            tokens.token().reveal() shouldBe fresh
            server.received.count { it.second == "/api/auth/token" } shouldBe 1
        }

    @Test
    fun aDeniedOrExpiredLoginEndsWithoutASession() =
        runTest {
            val server = AuthServer()
            server.code()
            server.poll(pollError("access_denied"), pollError("expired_token"))
            val tokens = login(server)

            shouldThrow<AuthException.Denied> { tokens.finish(tokens.start()) }
            shouldThrow<AuthException.Expired> { tokens.finish(tokens.start()) }
            shouldThrow<AuthException.LoginRequired> { tokens.token() }
        }

    @Test
    fun aLoginPastItsExpiryStopsWithoutAskingAgain() =
        runTest {
            val server = AuthServer()
            server.on(
                "POST",
                "/api/auth/device/code",
                json(
                    200,
                    buildJsonObject {
                        put("device_code", "dev-1")
                        put("user_code", "ABCD-EFGH")
                        put("verification_uri", "https://app.test/device")
                        put("expires_in", 10)
                        put("interval", 4)
                    },
                ),
            )
            server.poll(pollError("authorization_pending"))
            val tokens = login(server)

            shouldThrow<AuthException.Expired> { tokens.finish(tokens.start()) }
            // Polls at 4 s and 8 s; at 12 s the code had expired.
            server.received.count { it.second == "/api/auth/device/token" } shouldBe 2
        }

    @Test
    fun anExpiringTokenIsReplacedBeforeUseAndARefreshAlwaysMints() =
        runTest {
            val server = AuthServer()
            val fresh = jwt(NOW + 900)
            server.mint("session-1", json(200, buildJsonObject { put("token", fresh) }))
            val store = MemorySecretStore()
            store.set("sync/session", Secret("session-1"))
            store.set("sync/jwt", Secret(jwt(NOW + 30)))
            val tokens = login(server, store)

            tokens.token().reveal() shouldBe fresh
            tokens.refresh().reveal() shouldBe fresh
            server.received.size shouldBe 2
        }

    @Test
    fun aRefusedSessionIsForgottenAndAsksForALogin() =
        runTest {
            val server = AuthServer()
            server.mint("revoked", HttpResponse(401, ByteArray(0)))
            val store = MemorySecretStore()
            store.set("sync/session", Secret("revoked"))
            store.set("sync/jwt", Secret(jwt(0)))
            val tokens = login(server, store)

            shouldThrow<AuthException.LoginRequired> { tokens.refresh() }
            store.get("sync/session") shouldBe null
            store.get("sync/jwt") shouldBe null
        }

    @Test
    fun aMalformedAnswerIsAServerError() =
        runTest {
            val server = AuthServer()
            server.on("POST", "/api/auth/device/code", HttpResponse(200, "<html>".encodeToByteArray()))
            shouldThrow<AuthException.Server> { login(server).start() }

            val negative = AuthServer()
            negative.on(
                "POST",
                "/api/auth/device/code",
                json(
                    200,
                    buildJsonObject {
                        put("device_code", "dev-1")
                        put("user_code", "ABCD-EFGH")
                        put("verification_uri", "https://app.test/device")
                        put("expires_in", 1800)
                        put("interval", -1)
                    },
                ),
            )
            shouldThrow<AuthException.Server> { login(negative).start() }

            val noToken = AuthServer()
            noToken.mint("session-1", json(200, buildJsonObject { put("token", 42) }))
            val store = MemorySecretStore()
            store.set("sync/session", Secret("session-1"))
            shouldThrow<AuthException.Server> { login(noToken, store).refresh() }
        }

    @Test
    fun expiryIsReadFromTheClaimsAndUnreadableTokensAreSent() {
        expiresSoon(jwt(1_000), 1_000) shouldBe true
        expiresSoon(jwt(1_059), 1_000) shouldBe true
        expiresSoon(jwt(1_061), 1_000) shouldBe false
        expiresSoon("opaque", 1_000) shouldBe false
        expiresSoon("a.!!!.c", 1_000) shouldBe false
        val quoted = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT).encode("""{"exp":"0"}""".encodeToByteArray())
        expiresSoon("e30.$quoted.sig", 1_000) shouldBe false
    }
}
