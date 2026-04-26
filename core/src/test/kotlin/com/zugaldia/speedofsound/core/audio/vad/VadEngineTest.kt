package com.zugaldia.speedofsound.core.audio.vad

import com.zugaldia.speedofsound.core.plugins.recorder.RecorderEvent
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VadEngineTest {

    @Test
    fun `convertPcm16ToFloat normalizes endpoints to plus-minus one`() {
        val input = shortArrayOf(0, Short.MAX_VALUE, Short.MIN_VALUE)
        val out = VadEngine.convertPcm16ToFloat(input)
        assertEquals(0.0f, out[0])
        assertEquals(1.0f, out[1], 0.001f)
        assertTrue(out[2] in -1.001f..-0.999f)
    }

    @Test
    fun `convertPcm16ToFloat preserves length`() {
        val out = VadEngine.convertPcm16ToFloat(ShortArray(64) { it.toShort() })
        assertEquals(64, out.size)
    }

    @Test
    fun `convertPcm16ToFloat empty input returns empty`() {
        assertContentEquals(FloatArray(0), VadEngine.convertPcm16ToFloat(ShortArray(0)))
    }

    @Test
    fun `state machine emits SpeechStarted on first speech-active observation`() {
        val emitted = mutableListOf<RecorderEvent>()
        val sm = VadEngine.StateMachine(emitter = { emitted += it })

        sm.observeSpeechActive(true)

        assertEquals(listOf<RecorderEvent>(RecorderEvent.SpeechStarted), emitted)
    }

    @Test
    fun `state machine does not re-emit SpeechStarted while speech continues`() {
        val emitted = mutableListOf<RecorderEvent>()
        val sm = VadEngine.StateMachine(emitter = { emitted += it })

        sm.observeSpeechActive(true)
        sm.observeSpeechActive(true)
        sm.observeSpeechActive(true)

        assertEquals(listOf<RecorderEvent>(RecorderEvent.SpeechStarted), emitted)
    }

    @Test
    fun `state machine emits SpeechEnded on segment flush`() {
        val emitted = mutableListOf<RecorderEvent>()
        val sm = VadEngine.StateMachine(emitter = { emitted += it })

        sm.observeSpeechActive(true)
        sm.observeSegmentFlush()

        assertEquals(listOf(RecorderEvent.SpeechStarted, RecorderEvent.SpeechEnded), emitted)
    }

    @Test
    fun `state machine ignores segment flush while idle`() {
        val emitted = mutableListOf<RecorderEvent>()
        val sm = VadEngine.StateMachine(emitter = { emitted += it })

        sm.observeSegmentFlush()

        assertEquals(emptyList<RecorderEvent>(), emitted)
    }

    @Test
    fun `state machine resets to idle on speech-active false`() {
        val emitted = mutableListOf<RecorderEvent>()
        val sm = VadEngine.StateMachine(emitter = { emitted += it })

        sm.observeSpeechActive(true)
        sm.observeSpeechActive(false)
        // back to idle — a new speech segment must emit SpeechStarted again
        sm.observeSpeechActive(true)

        assertEquals(
            listOf<RecorderEvent>(RecorderEvent.SpeechStarted, RecorderEvent.SpeechStarted),
            emitted,
        )
    }

    @Test
    fun `state machine handles full segment cycle without spurious events`() {
        val emitted = mutableListOf<RecorderEvent>()
        val sm = VadEngine.StateMachine(emitter = { emitted += it })

        // Simulate: silence -> speech -> sherpa flushes the segment -> back to silence
        sm.observeSpeechActive(false)         // still idle
        sm.observeSpeechActive(true)          // -> SpeechStarted
        sm.observeSpeechActive(true)
        sm.observeSegmentFlush()              // -> SpeechEnded
        sm.observeSpeechActive(false)         // back to idle
        sm.observeSpeechActive(false)

        assertEquals(
            listOf(RecorderEvent.SpeechStarted, RecorderEvent.SpeechEnded),
            emitted,
        )
    }
}
