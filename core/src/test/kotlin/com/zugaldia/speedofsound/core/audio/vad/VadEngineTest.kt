package com.zugaldia.speedofsound.core.audio.vad

import com.zugaldia.speedofsound.core.plugins.recorder.RecorderEvent
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VadEngineTest {

    @Test
    fun `convertPcm16ToFloat normalizes endpoints into bounded range`() {
        val input = shortArrayOf(0, Short.MAX_VALUE, Short.MIN_VALUE)
        val out = VadEngine.convertPcm16ToFloat(input)
        assertEquals(0.0f, out[0])
        assertEquals(1.0f, out[1], 0.001f)
        assertEquals(-1.0f, out[2])  // exact, no fuzz
        // Both poles must lie in [-1.0, 1.0]
        assertTrue(out[1] in -1.0f..1.0f)
        assertTrue(out[2] in -1.0f..1.0f)
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
    fun `state machine emits SpeechEnded on speech-active false then SpeechStarted on next active`() {
        val emitted = mutableListOf<RecorderEvent>()
        val sm = VadEngine.StateMachine(emitter = { emitted += it })

        sm.observeSpeechActive(true)   // -> SpeechStarted
        sm.observeSpeechActive(false)  // -> SpeechEnded (was previously silent)
        sm.observeSpeechActive(true)   // -> SpeechStarted again

        assertEquals(
            listOf<RecorderEvent>(
                RecorderEvent.SpeechStarted,
                RecorderEvent.SpeechEnded,
                RecorderEvent.SpeechStarted,
            ),
            emitted,
        )
    }

    @Test
    fun `state machine does not double-emit SpeechEnded when both flush and active-false fire`() {
        val emitted = mutableListOf<RecorderEvent>()
        val sm = VadEngine.StateMachine(emitter = { emitted += it })

        sm.observeSpeechActive(true)   // -> SpeechStarted
        sm.observeSegmentFlush()       // -> SpeechEnded
        sm.observeSpeechActive(false)  // dedup: no second SpeechEnded

        assertEquals(
            listOf<RecorderEvent>(
                RecorderEvent.SpeechStarted,
                RecorderEvent.SpeechEnded,
            ),
            emitted,
        )
    }

    @Test
    fun `state machine does not double-emit SpeechEnded when both active-false and flush fire`() {
        val emitted = mutableListOf<RecorderEvent>()
        val sm = VadEngine.StateMachine(emitter = { emitted += it })

        sm.observeSpeechActive(true)   // -> SpeechStarted
        sm.observeSpeechActive(false)  // -> SpeechEnded
        sm.observeSegmentFlush()       // dedup: no second SpeechEnded

        assertEquals(
            listOf<RecorderEvent>(
                RecorderEvent.SpeechStarted,
                RecorderEvent.SpeechEnded,
            ),
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
