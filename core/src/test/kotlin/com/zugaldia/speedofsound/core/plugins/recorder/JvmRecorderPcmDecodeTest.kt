package com.zugaldia.speedofsound.core.plugins.recorder

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class JvmRecorderPcmDecodeTest {
    @Test
    fun `decodes little-endian shorts`() {
        // 0x00 0x01 -> 0x0100 = 256
        // 0xFF 0x7F -> 0x7FFF = 32767
        // 0x00 0x80 -> 0x8000 = -32768
        // 0xFF 0xFF -> 0xFFFF = -1
        val bytes = byteArrayOf(
            0x00, 0x01,
            0xFF.toByte(), 0x7F,
            0x00, 0x80.toByte(),
            0xFF.toByte(), 0xFF.toByte(),
        )
        val shorts = JvmRecorder.decodePcm16LittleEndian(bytes, bytes.size)
        assertContentEquals(shortArrayOf(256, 32767, -32768, -1), shorts)
    }

    @Test
    fun `respects byteCount when buffer is partially filled`() {
        val bytes = byteArrayOf(0x00, 0x01, 0x00, 0x02, 0x00, 0x00)
        val shorts = JvmRecorder.decodePcm16LittleEndian(bytes, byteCount = 4)
        assertContentEquals(shortArrayOf(256, 512), shorts)
    }

    @Test
    fun `odd trailing byte is dropped`() {
        val bytes = byteArrayOf(0x00, 0x01, 0xFF.toByte())
        val shorts = JvmRecorder.decodePcm16LittleEndian(bytes, bytes.size)
        assertEquals(1, shorts.size)
        assertEquals(256, shorts[0].toInt())
    }

    @Test
    fun `empty input returns empty`() {
        val shorts = JvmRecorder.decodePcm16LittleEndian(ByteArray(0), 0)
        assertEquals(0, shorts.size)
    }

    @Test
    fun `decodePcm16LittleEndianInto fills caller buffer and returns sample count`() {
        val bytes = byteArrayOf(0x00, 0x01, 0xFF.toByte(), 0x7F)
        val dest = ShortArray(8)
        val n = JvmRecorder.decodePcm16LittleEndianInto(bytes, bytes.size, dest)
        assertEquals(2, n)
        assertEquals(256.toShort(), dest[0])
        assertEquals(32767.toShort(), dest[1])
    }

    @Test
    fun `decodePcm16LittleEndianInto rejects undersized dest`() {
        val bytes = byteArrayOf(0x00, 0x01, 0x00, 0x02)
        assertFailsWith<IllegalArgumentException> {
            JvmRecorder.decodePcm16LittleEndianInto(bytes, bytes.size, ShortArray(1))
        }
    }
}
