package wasm

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PackedTest {
    private class Memory(
        private val data: ByteArray,
    ) : WasmMemory {
        override val size: Int get() = data.size

        override fun read(
            offset: Int,
            length: Int,
        ): ByteArray = guestSlice(data, offset, length)

        override fun write(
            offset: Int,
            bytes: ByteArray,
        ) {
            bytes.copyInto(data, offset)
        }
    }

    private fun packed(
        ptr: Int,
        len: Int,
    ) = (len.toLong() shl 32) or ptr.toLong()

    @Test
    fun unpacksPointerLowAndLengthHigh() {
        assertEquals(16 to 4, unpackI64(packed(16, 4)))
        assertEquals(-1 to 0, unpackI64(0xffff_ffffL))
    }

    @Test
    fun readsTheRangeTheGuestNamed() {
        val memory = Memory("..hello..".encodeToByteArray())
        assertContentEquals("hello".encodeToByteArray(), readPacked(memory, packed(2, 5)))
    }

    @Test
    fun refusesALengthOverTheCap() {
        val memory = Memory(ByteArray(8))
        assertFailsWith<WasmException> { readPacked(memory, packed(0, 8), maxLength = 4) }
    }

    @Test
    fun refusesAnOutOfBoundsRangeAndAMissingMemory() {
        assertFailsWith<WasmException> { readPacked(Memory(ByteArray(4)), packed(2, 4)) }
        assertFailsWith<WasmException> { readPacked(null, packed(0, 0)) }
    }
}
