package com.zugaldia.speedofsound.core.plugins.recorder

import com.zugaldia.speedofsound.core.plugins.AppPluginEvent
import kotlin.test.Test
import kotlin.test.assertSame
import kotlin.test.assertTrue

class RecorderSpeechEventsTest {
    @Test
    fun `SpeechStarted is a RecorderEvent`() {
        val event: AppPluginEvent = RecorderEvent.SpeechStarted
        assertTrue(event is RecorderEvent.SpeechStarted)
    }

    @Test
    fun `SpeechEnded is a RecorderEvent`() {
        val event: AppPluginEvent = RecorderEvent.SpeechEnded
        assertTrue(event is RecorderEvent.SpeechEnded)
    }

    @Test
    fun `SpeechStarted is a singleton`() {
        assertSame(RecorderEvent.SpeechStarted, RecorderEvent.SpeechStarted)
    }

    @Test
    fun `SpeechEnded is a singleton`() {
        assertSame(RecorderEvent.SpeechEnded, RecorderEvent.SpeechEnded)
    }
}
