package pyrlyn.sync.client

import com.google.protobuf.kotlin.Duration
import com.google.protobuf.kotlin.invoke
import com.google.rpc.BadRequest
import com.google.rpc.ErrorInfo
import com.google.rpc.RetryInfo
import com.google.rpc.invoke
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.rpc.grpc.GrpcStatusCode
import kotlinx.rpc.grpc.GrpcStatusException
import pyrlyn.rpc.StatusDetails
import pyrlyn.rpc.statusException
import kotlin.test.Test
import kotlin.time.Duration.Companion.milliseconds

// Ported from ../backend/crates/sync-client/src/error.rs.

private fun info(
    reason: String,
    domain: String = DOMAIN,
    vararg meta: Pair<String, String>,
) = ErrorInfo {
    this.reason = reason
    this.domain = domain
    metadata = meta.toMap()
}

internal fun syncStatus(
    code: GrpcStatusCode,
    reason: String,
    vararg meta: Pair<String, String>,
): GrpcStatusException = statusException(code, "refused", StatusDetails(errorInfo = info(reason, DOMAIN, *meta)))

private fun mapped(status: GrpcStatusException) = status.toSyncException()

private fun badRequest(field: String) =
    BadRequest {
        fieldViolations =
            listOf(
                BadRequest.FieldViolation {
                    this.field = field
                    description = "unknown"
                },
            )
    }

class ErrorsTest {
    @Test
    fun eachStopReasonMapsToItsOwnSubclass() {
        val quota = mapped(syncStatus(GrpcStatusCode.RESOURCE_EXHAUSTED, "QUOTA_EXCEEDED", "quota" to "records", "limit" to "2", "used" to "2"))
        quota.shouldBeInstanceOf<SyncException.QuotaExceeded>()
        Triple(quota.quota, quota.limit, quota.used) shouldBe Triple("records", "2", "2")
        val old = mapped(syncStatus(GrpcStatusCode.FAILED_PRECONDITION, "CLIENT_TOO_OLD", "min_version" to "2.0.0"))
        old.shouldBeInstanceOf<SyncException.ClientTooOld>().minVersion shouldBe "2.0.0"
        mapped(syncStatus(GrpcStatusCode.PERMISSION_DENIED, "SCOPE_MISMATCH")).shouldBeInstanceOf<SyncException.ScopeMismatch>()
        mapped(syncStatus(GrpcStatusCode.PERMISSION_DENIED, "ORGANIZATION_MISMATCH", "organization_id" to "acme"))
            .shouldBeInstanceOf<SyncException.OrganizationMismatch>()
            .organizationId shouldBe "acme"
        mapped(syncStatus(GrpcStatusCode.PERMISSION_DENIED, "PRODUCT_NOT_ENTITLED", "product" to "rtok"))
            .shouldBeInstanceOf<SyncException.NotEntitled>()
            .product shouldBe "rtok"
        mapped(syncStatus(GrpcStatusCode.FAILED_PRECONDITION, "DEVICE_NOT_ENABLED")).shouldBeInstanceOf<SyncException.DeviceNotEnabled>()
    }

    @Test
    fun transportErrorsAreTransientAndClientBugsAreNot() {
        mapped(statusException(GrpcStatusCode.UNAVAILABLE, "down")).shouldBeInstanceOf<SyncException.Unavailable>()
        mapped(statusException(GrpcStatusCode.DEADLINE_EXCEEDED, "slow")).shouldBeInstanceOf<SyncException.Unavailable>()
        mapped(statusException(GrpcStatusCode.INTERNAL, "bug")).shouldBeInstanceOf<SyncException.Unavailable>()
        mapped(statusException(GrpcStatusCode.UNAUTHENTICATED, "expired"))
            .shouldBeInstanceOf<SyncException.Auth>()
            .error
            .shouldBeInstanceOf<AuthException.LoginRequired>()
        val bug = syncStatus(GrpcStatusCode.INVALID_ARGUMENT, "INVALID_REQUEST")
        bug.transient shouldBe false
        mapped(bug).shouldBeInstanceOf<SyncException.Refused>()
        syncStatus(GrpcStatusCode.RESOURCE_EXHAUSTED, "RATE_LIMITED").transient shouldBe true
    }

    @Test
    fun theOffendingMutationIsReadFromBadRequest() {
        val status = statusException(GrpcStatusCode.INVALID_ARGUMENT, "bad", StatusDetails(info("UNKNOWN_KIND"), badRequest("mutations[12].kind")))
        status.reason shouldBe "UNKNOWN_KIND"
        status.badMutation shouldBe 12
        statusException(GrpcStatusCode.INTERNAL, "x").badMutation shouldBe null
        for (field in listOf("mutations[-1].kind", "mutations[+1].kind", "mutations[].kind", "mutations[1", "client.device_id", "mutations[99999999999].kind")) {
            statusException(GrpcStatusCode.INVALID_ARGUMENT, "bad", StatusDetails(badRequest = badRequest(field))).badMutation shouldBe null
        }
    }

    @Test
    fun aReasonFromAnotherDomainIsIgnored() {
        val status = statusException(GrpcStatusCode.FAILED_PRECONDITION, "x", StatusDetails(info("CURSOR_EXPIRED", "example.com")))
        status.reason shouldBe ""
    }

    @Test
    fun theWaitRetryInfoAsksForIsReadAndANegativeOneIsIgnored() {
        fun retry(
            seconds: Long,
            nanos: Int,
        ) = statusException(
            GrpcStatusCode.UNAVAILABLE,
            "busy",
            StatusDetails(
                retryInfo =
                    RetryInfo {
                        retryDelay =
                            Duration {
                                this.seconds = seconds
                                this.nanos = nanos
                            }
                    },
            ),
        )
        retry(1, 500_000_000).retryDelay shouldBe 1500.milliseconds
        retry(-1, 0).retryDelay shouldBe null
        statusException(GrpcStatusCode.UNAVAILABLE, "busy", StatusDetails(retryInfo = RetryInfo {})).retryDelay shouldBe null
        statusException(GrpcStatusCode.UNAVAILABLE, "busy").retryDelay shouldBe null
    }
}
