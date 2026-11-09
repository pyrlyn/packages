package pyrlyn.taskboard

import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class CodecTest {
    private val t0 = Instant.fromEpochSeconds(1_700_000_000)

    private fun sample() =
        Task(
            id = id("T1"),
            title = "Port the parser",
            description = "why",
            status = Status.InProgress,
            priority = 1,
            parent = null,
            assignee = "a1",
            blockedBy = listOf(id("T2")),
            conflictsWith = listOf(id("T3")),
            parallel = true,
            report = null,
            remote = "https://example.com/1",
            created = t0,
            updated = t0,
        )

    @Test
    fun taskJsonIsPinned() {
        val rev = Rev("0123456789abcdef")
        val lease = Lease("a1", t0, Instant.fromEpochSeconds(1_700_001_800))
        assertEquals(
            """{"id":"T1","title":"Port the parser","description":"why","status":"in-progress","created_at":1700000000,"updated_at":1700000000,""" +
                """"priority":1,"assignee":"a1","blocked_by":["T2"],"conflicts_with":["T3"],"parallel":true,"remote":"https://example.com/1",""" +
                """"rev":"0123456789abcdef","lease":{"agent":"a1","acquired":1700000000,"expires":1700001800}}""",
            Codec.versionedToJson(Versioned(sample(), rev), lease).toString(),
        )
    }

    @Test
    fun defaultsAreLeftOut() {
        val min = Task(id("T1"), "x", created = t0, updated = t0)
        assertEquals("""{"id":"T1","title":"x","status":"todo","created_at":1700000000,"updated_at":1700000000}""", Codec.taskToJson(min).toString())
    }

    @Test
    fun taskRoundTripsWithExtraKeys() {
        val task = sample().copy(report = "done it", extra = mapOf("sprint" to kotlinx.serialization.json.JsonPrimitive(7)))
        assertEquals(task, Codec.taskFromJson(Codec.taskToJson(task, withExtra = true)))
    }

    @Test
    fun cardJsonIsPinned() {
        val card = Card(id("T1"), "x", Status.Todo, 2, null, null, emptyList(), emptyList(), false, ready = true, stale = false, lease = null, rev = Rev("0123456789abcdef"), remote = null)
        assertEquals(
            """{"id":"T1","title":"x","status":"todo","priority":2,"parent":null,"assignee":null,"blocked_by":[],"conflicts_with":[],"parallel":false,""" +
                """"ready":true,"stale":false,"lease":null,"rev":"0123456789abcdef","remote":null}""",
            Codec.cardToJson(card).toString(),
        )
    }

    @Test
    fun changeJsonIsPinnedAndRoundTrips() {
        val change =
            Change(
                3,
                t0,
                "a1",
                "update",
                id("T1"),
                Rev("0123456789abcdef"),
                mapOf("title" to FieldChange(kotlinx.serialization.json.JsonPrimitive("a"), kotlinx.serialization.json.JsonPrimitive("b")), "assignee" to FieldChange(JsonNull, kotlinx.serialization.json.JsonPrimitive("x"))),
                external = true,
            )
        val text = Codec.changeToJson(change).toString()
        assertEquals(
            """{"seq":3,"at":1700000000,"actor":"a1","op":"update","id":"T1","rev":"0123456789abcdef","fields":{"title":["a","b"],"assignee":[null,"x"]},"external":true}""",
            text,
        )
        assertEquals(change, Codec.changeFromJson(Codec.parseObject(text)))
    }

    @Test
    fun errorJsonCarriesTheStableCode() {
        val e = BoardError.Claimed(id("T1"), "a1", t0)
        assertEquals("""{"code":"claimed","message":"T1 is claimed by a1 until 2023-11-14T22:13:20Z","data":{"id":"T1","holder":"a1","until":1700000000}}""", Codec.errorToJson(BoardException(e)).toString())
        assertEquals("""{"code":"invalid","message":"x","data":null}""", Codec.errorToJson(BoardException(BoardError.Invalid("x"))).toString())
        assertEquals("""{"code":"invalid","message":"x","data":{"op":2}}""", Codec.errorToJson(BoardException(BoardError.Invalid("x"), 2)).toString())
        assertEquals(listOf("not_found", "conflict", "claimed", "invalid", "key_reused", "busy", "remote"), listOf(BoardError.NotFound("x"), conflict(), e, BoardError.Invalid("x"), BoardError.KeyReused("k"), BoardError.Busy(), BoardError.Remote("x")).map { it.code })
    }

    private fun conflict() = BoardError.Conflict(Versioned(Task(id("T1"), "x", created = t0, updated = t0), Rev("0123456789abcdef")), emptyList())

    @Test
    fun opsRoundTripThroughJson() {
        val ops =
            listOf(
                Op.Create(NewTask("a", parent = Ref("p"), priority = 1, blockedBy = listOf(id("T2"))), ref = "c"),
                Op.Update(id("T1"), Patch(title = "x", clearParent = true, addBlockedBy = setOf(id("T2"))), baseRev = Rev("0123456789abcdef")),
                Op.Move(Ref("c"), Status.Done, report = "ok", force = true),
                Op.Claim(null, "a1"),
                Op.Release(id("T1"), "a1", force = true, ifRev = Rev("0123456789abcdef")),
            )
        val back = ops.map { Codec.opFromJson(Codec.opToJson(it)) }
        assertEquals(ops, back)
    }

    @Test
    fun badJsonIsInvalid() {
        assertCode("invalid") { Codec.parse("{nope") }
        assertCode("invalid") { Codec.parseObject("[1]") }
        assertCode("invalid") { Codec.opFromJson(Codec.parseObject("""{"op":"explode"}""")) }
        assertCode("invalid") { Codec.opFromJson(Codec.parseObject("""{"op":"move","id":"T1"}""")) }
        assertCode("invalid") { Codec.opFromJson(Codec.parseObject("""{"op":"update","id":"T1","priority":"high"}""")) }
        assertCode("invalid") { Codec.taskFromJson(Codec.parseObject("""{"id":"T1","title":"x","status":"todo"}""")) }
        assertCode("invalid") { Codec.taskFromJson(JsonObject(emptyMap())) }
    }
}
