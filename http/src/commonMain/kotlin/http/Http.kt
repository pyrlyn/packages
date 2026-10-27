// Engine-neutral HTTP client interface. One implementation (`http.ktor.KtorHttp`) wraps Ktor;
// callers depend only on this file.
package http

/** Timeouts of one client (Rust: `tls::client` connect 10 s, read `timeout_s`). */
data class HttpSettings(
    val connectTimeoutMs: Long = 10_000,
    val requestTimeoutMs: Long = 60_000,
    val socketTimeoutMs: Long = 60_000,
)

data class HttpRequest(
    val url: String,
    val headers: List<Pair<String, String>> = emptyList(),
    val body: ByteArray = ByteArray(0),
)

data class HttpResponse(
    val status: Int,
    val body: ByteArray,
) {
    val isSuccess get() = status in 200..299

    fun text() = body.decodeToString()
}

/** The status line and headers of a response whose body is still to be read. */
data class HttpHead(
    val status: Int,
    val headers: List<Pair<String, String>>,
) {
    val isSuccess get() = status in 200..299

    /** The first value of [name], matched case-insensitively as header names are. */
    fun header(name: String): String? = headers.firstOrNull { it.first.equals(name, ignoreCase = true) }?.second
}

/** A response body read as it arrives. */
fun interface HttpBody {
    /** Fill the start of [buffer]: the count read, or -1 once the body has ended. Throws [HttpException] when the transfer fails. */
    suspend fun read(buffer: ByteArray): Int
}

/** A request that got no response. */
class HttpException(
    val kind: Kind,
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause) {
    enum class Kind { ConnectTimeout, RequestTimeout, SocketTimeout, Connect, Io }
}

interface HttpClient : AutoCloseable {
    /** POST [req]; a non-2xx status is a response, not an exception. Throws [HttpException] otherwise. */
    suspend fun post(req: HttpRequest): HttpResponse

    /** GET [req] (its body is ignored); errors as for [post]. */
    suspend fun get(req: HttpRequest): HttpResponse = throw HttpException(HttpException.Kind.Io, "GET is not supported by this client")

    /**
     * GET [req] and hand the head and the unread body to [handle], so a caller can refuse a body by its size or stop
     * partway through it; the connection is released when [handle] returns. A non-2xx status reaches [handle] like any
     * other. Throws [HttpException] when no response arrives; whatever [handle] throws passes through unchanged.
     */
    suspend fun <T> stream(
        req: HttpRequest,
        handle: suspend (HttpHead, HttpBody) -> T,
    ): T = throw HttpException(HttpException.Kind.Io, "streaming is not supported by this client")
}

/** Builds clients; the composition root picks the engine adapter. */
fun interface HttpClientFactory {
    fun create(settings: HttpSettings): HttpClient
}
