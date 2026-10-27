// The Ktor implementation of http.HttpClient.
package http.ktor

import http.HttpBody
import http.HttpClient
import http.HttpClientFactory
import http.HttpException
import http.HttpHead
import http.HttpRequest
import http.HttpResponse
import http.HttpSettings
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.prepareGet
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpMethod
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.CancellationException
import io.ktor.client.HttpClient as Ktor

/** What a [KtorHttp.stream] handler threw, carried past the transport mapping. */
private class HandlerFailed(
    val error: Exception,
) : Exception(error.message, error)

/** The platform engine (Darwin / Curl). */
internal expect fun platformEngine(): HttpClientEngine

class KtorHttp(
    settings: HttpSettings,
    engine: HttpClientEngine = platformEngine(),
) : HttpClient {
    private val client =
        Ktor(engine) {
            expectSuccess = false
            followRedirects = true
            install(HttpTimeout) {
                connectTimeoutMillis = settings.connectTimeoutMs
                requestTimeoutMillis = settings.requestTimeoutMs
                socketTimeoutMillis = settings.socketTimeoutMs
            }
        }

    override suspend fun post(req: HttpRequest): HttpResponse = withBody(req, HttpMethod.Post)

    override suspend fun put(req: HttpRequest): HttpResponse = withBody(req, HttpMethod.Put)

    override suspend fun patch(req: HttpRequest): HttpResponse = withBody(req, HttpMethod.Patch)

    private suspend fun withBody(
        req: HttpRequest,
        verb: HttpMethod,
    ): HttpResponse =
        send(req) {
            client.request(req.url) {
                method = verb
                // Raw headers: Ktor must not merge or re-case what the caller set (auth included).
                req.headers.forEach { (k, v) -> headers.append(k, v) }
                setBody(req.body)
            }
        }

    override suspend fun get(req: HttpRequest): HttpResponse = send(req) { client.get(req.url) { req.headers.forEach { (k, v) -> headers.append(k, v) } } }

    override suspend fun <T> stream(
        req: HttpRequest,
        handle: suspend (HttpHead, HttpBody) -> T,
    ): T =
        try {
            mapped(req) {
                client.prepareGet(req.url) { req.headers.forEach { (k, v) -> headers.append(k, v) } }.execute { resp ->
                    val head = HttpHead(resp.status.value, resp.headers.entries().flatMap { (k, vs) -> vs.map { k to it } })
                    val channel = resp.bodyAsChannel()
                    val body = HttpBody { buffer -> mapped(req) { channel.readAvailable(buffer, 0, buffer.size) } }
                    try {
                        handle(head, body)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        // Kept apart so the mapping below does not turn the caller's own refusal into a transport error.
                        throw HandlerFailed(e)
                    }
                }
            }
        } catch (e: HandlerFailed) {
            throw e.error
        }

    /** One request whose whole body is read, with every transport failure mapped to an [HttpException] kind. */
    private suspend fun send(
        req: HttpRequest,
        call: suspend () -> io.ktor.client.statement.HttpResponse,
    ): HttpResponse =
        mapped(req) {
            val resp = call()
            HttpResponse(resp.status.value, resp.bodyAsBytes())
        }

    private suspend fun <T> mapped(
        req: HttpRequest,
        call: suspend () -> T,
    ): T =
        try {
            call()
        } catch (e: HttpRequestTimeoutException) {
            throw HttpException(HttpException.Kind.RequestTimeout, "request timeout: ${req.url}", e)
        } catch (e: CancellationException) {
            throw e
        } catch (e: ConnectTimeoutException) {
            throw HttpException(HttpException.Kind.ConnectTimeout, "connect timeout: ${req.url}", e)
        } catch (e: SocketTimeoutException) {
            throw HttpException(HttpException.Kind.SocketTimeout, "socket timeout: ${req.url}", e)
        } catch (e: HttpException) {
            throw e
        } catch (e: HandlerFailed) {
            throw e
        } catch (e: Exception) {
            throw HttpException(HttpException.Kind.Io, "${req.url}: ${e.message}", e)
        }

    override fun close() = client.close()

    companion object {
        val FACTORY = HttpClientFactory { KtorHttp(it) }
    }
}
