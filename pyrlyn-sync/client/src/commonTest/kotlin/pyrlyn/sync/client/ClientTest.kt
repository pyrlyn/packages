package pyrlyn.sync.client

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlinx.io.bytestring.ByteString
import kotlinx.io.bytestring.encodeToByteString
import kotlinx.io.bytestring.isEmpty
import kotlinx.rpc.grpc.GrpcStatusException
import pyrlyn.sync.v1.MutationOutcome
import pyrlyn.sync.v1.MutationResult
import pyrlyn.sync.v1.Record
import pyrlyn.sync.v1.copy
import pyrlyn.sync.v1.invoke
import kotlin.random.Random
import kotlin.test.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

// Ported from ../backend/crates/sync-client/src/client.rs.

class ClientTest {
    @Test
    fun aBatchStopsAtTheLimitARepeatedRecordAndTheSizeCap() {
        fun small(id: String) = newMutation("note", id, 0uL, ByteString(ByteArray(10)))
        val queue = listOf(small("a"), small("b"), small("c"))
        batchLen(queue, 500) shouldBe 3
        batchLen(queue, 2) shouldBe 2

        batchLen(listOf(small("a"), small("b"), small("a")), 500) shouldBe 2

        fun big(id: String) = newMutation("note", id, 0uL, ByteString(ByteArray(1024 * 1024)))
        batchLen(listOf(big("a"), big("b"), big("c"), big("d")), 500) shouldBe 2
        // A lone record always goes, so the queue keeps moving.
        batchLen(listOf(big("a")), 0) shouldBe 1
    }

    @Test
    fun backoffGrowsAndStaysUnderTheCap() {
        val retry = Retry(attempts = 10, base = 100.milliseconds, max = 1.seconds)
        for (random in listOf(Random(1), Random(2), Random(3))) {
            val first = retry.delay(0, random)
            (first >= 50.milliseconds && first <= 100.milliseconds) shouldBe true
            val late = retry.delay(9, random)
            (late >= 500.milliseconds && late <= 1.seconds) shouldBe true
        }
        // Past the doubling limit the wait stays at the cap instead of overflowing.
        (retry.delay(1_000) <= 1.seconds) shouldBe true
    }

    @Test
    fun aReplyMustAnswerEveryMutationInOrder() {
        val m = newMutation("note", "a", 0uL, "x".encodeToByteString())
        val record =
            Record {
                kind = "note"
                id = "a"
                version = 1uL
            }
        val ok =
            MutationResult {
                mutationId = m.mutationId
                outcome = MutationOutcome.APPLIED
                this.record = record
            }
        pushed(listOf(m), listOf(ok))[0].outcome shouldBe Outcome.APPLIED

        shouldThrow<GrpcStatusException> { pushed(listOf(m), emptyList()) }
        shouldThrow<GrpcStatusException> { pushed(listOf(m), listOf(ok.copy { outcome = MutationOutcome.UNSPECIFIED })) }
        shouldThrow<GrpcStatusException> { pushed(listOf(m), listOf(ok.copy { mutationId = "other" })) }
        shouldThrow<GrpcStatusException> { pushed(listOf(m), listOf(ok.copy { clearRecord() })) }
    }

    @Test
    fun aNewMutationHasAUniqueIdAndAPositiveTime() {
        // Ported from store.rs.
        val a = newMutation("note", "n", 0uL, "x".encodeToByteString())
        val b = newMutation("note", "n", 0uL, "x".encodeToByteString())
        (a.mutationId != b.mutationId) shouldBe true
        (a.modifiedAtUnixMs > 0) shouldBe true
        a.deleted shouldBe false

        val delete = newMutation("note", "n", 3uL, null)
        (delete.deleted && delete.payload.isEmpty()) shouldBe true
        delete.baseVersion shouldBe 3uL
    }
}
