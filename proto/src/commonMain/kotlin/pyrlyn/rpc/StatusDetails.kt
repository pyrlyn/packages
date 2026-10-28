@file:OptIn(InternalRpcApi::class, ExperimentalRpcApi::class)

package pyrlyn.rpc

import com.google.protobuf.kotlin.Any
import com.google.protobuf.kotlin.invoke
import com.google.rpc.BadRequest
import com.google.rpc.BadRequestInternal
import com.google.rpc.ErrorInfo
import com.google.rpc.ErrorInfoInternal
import com.google.rpc.QuotaFailure
import com.google.rpc.QuotaFailureInternal
import com.google.rpc.RetryInfo
import com.google.rpc.RetryInfoInternal
import com.google.rpc.Status
import com.google.rpc.StatusInternal
import com.google.rpc.invoke
import kotlinx.io.Buffer
import kotlinx.io.bytestring.ByteString
import kotlinx.io.readByteArray
import kotlinx.rpc.grpc.GrpcMetadata
import kotlinx.rpc.grpc.GrpcStatus
import kotlinx.rpc.grpc.GrpcStatusCode
import kotlinx.rpc.grpc.GrpcStatusException
import kotlinx.rpc.grpc.appendBinary
import kotlinx.rpc.grpc.getBinary
import kotlinx.rpc.grpc.marshaller.GrpcMarshaller
import kotlinx.rpc.grpc.status
import kotlinx.rpc.grpc.trailers
import kotlinx.rpc.internal.utils.ExperimentalRpcApi
import kotlinx.rpc.internal.utils.InternalRpcApi

// The trailer gRPC's rich error model travels in, a serialized google.rpc.Status
// (https://google.aip.dev/193).
private const val STATUS_DETAILS = "grpc-status-details-bin"
private const val TYPE_PREFIX = "type.googleapis.com/"

/**
 * The google.rpc details of an error status, the ones the sync service sends and its clients read:
 * clients branch on [errorInfo]'s reason, never on the message.
 */
data class StatusDetails(
    val errorInfo: ErrorInfo? = null,
    val badRequest: BadRequest? = null,
    val quotaFailure: QuotaFailure? = null,
    val retryInfo: RetryInfo? = null,
)

private class Detail<T>(
    val typeName: String,
    val marshaller: GrpcMarshaller<T>,
)

// The marshallers are kotlinx-rpc's internal API; this file is the only place that names them.
private val ERROR_INFO = Detail("google.rpc.ErrorInfo", ErrorInfoInternal.MARSHALLER)
private val BAD_REQUEST = Detail("google.rpc.BadRequest", BadRequestInternal.MARSHALLER)
private val QUOTA_FAILURE = Detail("google.rpc.QuotaFailure", QuotaFailureInternal.MARSHALLER)
private val RETRY_INFO = Detail("google.rpc.RetryInfo", RetryInfoInternal.MARSHALLER)

private fun <T> GrpcMarshaller<T>.bytes(value: T): ByteArray = encode(value, null).readByteArray()

private fun <T> Detail<T>.pack(value: T): Any =
    Any {
        typeUrl = TYPE_PREFIX + typeName
        this.value = ByteString(marshaller.bytes(value))
    }

private fun <T> Detail<T>.unpack(details: List<Any>): T? = details.firstOrNull { it.typeUrl == TYPE_PREFIX + typeName }?.let { marshaller.decode(Buffer().apply { write(it.value.toByteArray()) }, null) }

/** A status [code] with [message] whose trailers carry [details] as the rich error model sends them. */
fun statusException(
    code: GrpcStatusCode,
    message: String,
    details: StatusDetails,
): GrpcStatusException {
    val status =
        Status {
            this.code = code.value
            this.message = message
            this.details =
                listOfNotNull(
                    details.errorInfo?.let(ERROR_INFO::pack),
                    details.badRequest?.let(BAD_REQUEST::pack),
                    details.quotaFailure?.let(QUOTA_FAILURE::pack),
                    details.retryInfo?.let(RETRY_INFO::pack),
                )
        }
    val trailers = GrpcMetadata { appendBinary(STATUS_DETAILS, StatusInternal.MARSHALLER.bytes(status)) }
    return GrpcStatusException(GrpcStatus(code, message), trailers)
}

/** The details [this] status carries, or null when it has none or they are not a google.rpc.Status. */
fun GrpcStatusException.statusDetails(): StatusDetails? {
    val bytes = trailers?.getBinary(STATUS_DETAILS) ?: return null
    return runCatching {
        val status = StatusInternal.MARSHALLER.decode(Buffer().apply { write(bytes) }, null)
        val details = status.details
        StatusDetails(ERROR_INFO.unpack(details), BAD_REQUEST.unpack(details), QUOTA_FAILURE.unpack(details), RETRY_INFO.unpack(details))
    }.getOrNull()
}
