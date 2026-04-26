package com.zugaldia.speedofsound.core.plugins.recorder

import com.zugaldia.speedofsound.core.plugins.AppPluginEvent

/**
 * Events emitted by the recorder plugins.
 */
sealed class RecorderEvent : AppPluginEvent() {
    /**
     * Emitted periodically during recording with the current audio level.
     * @param level Normalized volume level in range [0.0, 1.0]
     */
    data class RecordingLevel(val level: Float) : RecorderEvent()

    /**
     * Emitted by the recorder's VAD when a contiguous speech segment begins
     * (transition from silence to detected speech).
     */
    data object SpeechStarted : RecorderEvent()

    /**
     * Emitted by the recorder's VAD when an end-of-utterance silence has been
     * detected. `DefaultDirector` (when `vadEndpointing` is enabled) treats
     * this as the cue to stop recording and proceed to transcription.
     */
    data object SpeechEnded : RecorderEvent()
}
