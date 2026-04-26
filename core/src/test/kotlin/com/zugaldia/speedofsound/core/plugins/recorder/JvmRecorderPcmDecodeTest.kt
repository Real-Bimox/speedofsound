package com.zugaldia.speedofsound.core.plugins.recorder

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

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
}
