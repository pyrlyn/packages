package http.ktor

import http.HttpException
import http.HttpRequest
import http.HttpSettings
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import kotlin.test.*

private fun client(
    settings: HttpSettings = HttpSettings(),
    handler: MockRequestHandler,
) = KtorHttp(settings, MockEngine(handler))

class KtorHttpTest {
    @Test fun headersIncludingAuthAndTheBodyReachTheServerUnchanged() =
        runBlocking {
            var seen: io.ktor.client.request.HttpRequestData? = null
            var body = ""
            val json = """{"model":"m","messages":[{"role":"user","content":"hi"}]}"""
            client { req ->
                seen = req
                body = (req.body as io.ktor.http.content.OutgoingContent.ByteArrayContent).bytes().decodeToString()
                respond("""{"content":[{"type":"text","text":"ok"}]}""", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
            }.use { c ->
                val r = c.post(HttpRequest("http://127.0.0.1:1/v1/messages", listOf("content-type" to "application/json", "x-api-key" to "k-test", "authorization" to "Bearer t", "x-rtok-lane" to "internal"), json.encodeToByteArray()))
                assertTrue(r.isSuccess)
                assertEquals(
                    "ok",
                    Json
                        .parseToJsonElement(r.text())
                        .jsonObject["content"]!!
                        .jsonArray[0]
                        .jsonObject["text"]!!
                        .jsonPrimitive.content,
                )
            }
            val h = seen!!.headers
            assertEquals(HttpMethod.Post, seen.method)
            assertEquals("/v1/messages", seen.url.encodedPath)
            assertEquals("k-test", h["x-api-key"])
            assertEquals("Bearer t", h["Authorization"])
            assertEquals("internal", h["x-rtok-lane"])
            assertEquals(json, body)
        }

    @Test fun aGetSendsItsHeadersAndReturnsTheBytes() =
        runBlocking {
            var seen: io.ktor.client.request.HttpRequestData? = null
            client { req ->
                seen = req
                respond(byteArrayOf(1, 2, 3), HttpStatusCode.OK)
            }.use { c ->
                val r = c.get(HttpRequest("http://x/crate/serde/1.0.0/json.zst", listOf("user-agent" to "rtok/t")))
                assertEquals(200, r.status)
                assertContentEquals(byteArrayOf(1, 2, 3), r.body)
            }
            assertEquals(HttpMethod.Get, seen!!.method)
            assertEquals("rtok/t", seen.headers["User-Agent"])
        }

    @Test fun aPutAndAPatchSendTheirMethodAndBody() =
        runBlocking {
            val seen = mutableListOf<Pair<HttpMethod, String>>()
            client { req ->
                seen += req.method to (req.body as io.ktor.http.content.OutgoingContent.ByteArrayContent).bytes().decodeToString()
                respond("{}", HttpStatusCode.OK)
            }.use { c ->
                assertEquals(200, c.put(HttpRequest("http://x/contents/a", body = "put".encodeToByteArray())).status)
                assertEquals(200, c.patch(HttpRequest("http://x/refs/heads/b", body = "patch".encodeToByteArray())).status)
            }
            assertEquals(listOf(HttpMethod.Put to "put", HttpMethod.Patch to "patch"), seen)
        }

    @Test fun aNonSuccessStatusIsAResponseNotAnException() =
        runBlocking {
            client { respond("overloaded", HttpStatusCode.ServiceUnavailable) }.use { c ->
                val r = c.post(HttpRequest("http://x/v1/messages"))
                assertEquals(503, r.status)
                assertFalse(r.isSuccess)
                assertEquals("overloaded", r.text())
            }
        }

    @Test fun aSlowServerMapsToRequestTimeout() =
        runBlocking {
            client(HttpSettings(requestTimeoutMs = 50)) {
                delay(2_000)
                respond("late")
            }.use { c ->
                val e = assertFailsWith<HttpException> { c.post(HttpRequest("http://x/")) }
                assertEquals(HttpException.Kind.RequestTimeout, e.kind)
            }
        }

    @Test fun anEngineFailureMapsToIo() =
        runBlocking {
            client { throw IllegalStateException("connection refused") }.use { c ->
                val e = assertFailsWith<HttpException> { c.post(HttpRequest("http://x/")) }
                assertEquals(HttpException.Kind.Io, e.kind)
                assertTrue("connection refused" in e.message!!)
            }
        }

    @Test fun aStreamHandsOverTheHeadBeforeTheBodyAndThenTheBodyInChunks() =
        runBlocking {
            val payload = ByteArray(10_000) { (it % 251).toByte() }
            client { respond(payload, HttpStatusCode.OK, headersOf(HttpHeaders.ContentLength, payload.size.toString())) }.use { c ->
                val got =
                    c.stream(HttpRequest("http://x/asset")) { head, body ->
                        assertEquals(200, head.status)
                        assertEquals(payload.size.toString(), head.header("content-length"))
                        val out = mutableListOf<Byte>()
                        val buf = ByteArray(1024)
                        while (true) {
                            val n = body.read(buf)
                            if (n < 0) break
                            for (i in 0 until n) out += buf[i]
                        }
                        out.toByteArray()
                    }
                assertContentEquals(payload, got)
            }
        }

    @Test fun aStreamGivesANonSuccessStatusToTheHandler() =
        runBlocking {
            client { respond("""{"message":"Not Found"}""", HttpStatusCode.NotFound) }.use { c ->
                assertEquals(404, c.stream(HttpRequest("http://x/")) { head, _ -> head.status })
            }
        }

    @Test fun whatAStreamHandlerThrowsPassesThroughUnchanged() =
        runBlocking {
            client { respond("body") }.use { c ->
                val e = assertFailsWith<IllegalStateException> { c.stream(HttpRequest("http://x/")) { _, _ -> error("too big") } }
                assertEquals("too big", e.message)
            }
        }

    @Test fun aStreamThatGetsNoResponseMapsToIo() =
        runBlocking {
            client { throw IllegalStateException("connection refused") }.use { c ->
                val e = assertFailsWith<HttpException> { c.stream(HttpRequest("http://x/")) { _, _ -> } }
                assertEquals(HttpException.Kind.Io, e.kind)
            }
        }

    /** GitHub answers an asset download with a redirect to a CDN, which must not receive the token. */
    @Test fun aRedirectToAnotherHostDropsTheAuthorizationHeader() =
        runBlocking {
            val seen = mutableMapOf<String, String?>()
            client { req ->
                seen[req.url.host + req.url.encodedPath] = req.headers[HttpHeaders.Authorization]
                when (req.url.host) {
                    "api.x" -> respond("", HttpStatusCode.Found, headersOf(HttpHeaders.Location, "http://cdn.y/blob"))
                    else -> respond("bytes")
                }
            }.use { c ->
                c.stream(HttpRequest("http://api.x/asset", listOf("authorization" to "Bearer t"))) { head, _ -> assertEquals(200, head.status) }
            }
            assertEquals(mapOf("api.x/asset" to "Bearer t", "cdn.y/blob" to null), seen)
        }
}
