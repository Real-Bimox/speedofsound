package com.zugaldia.speedofsound.core.plugins.recorder

import com.zugaldia.speedofsound.core.audio.AudioInfo
import com.zugaldia.speedofsound.core.audio.vad.VadEngine

/**
 * Options for configuring the JVM audio recorder.
 *
 * @param audioInfo Audio format configuration
 * @param computeVolumeLevel When true, the recorder will compute and emit volume level events
 *                           during recording. Default is false for performance reasons.
 * @param enableDebug When true, enables additional debug logging during recording. Default is false.
 * @param vadEngine When non-null, captured PCM is fed through this VAD engine in real time; the
 *                  engine emits SpeechStarted/SpeechEnded events via the recorder's emitEvent flow.
 */
data class RecorderOptions(
    val audioInfo: AudioInfo = AudioInfo.Default,
    val computeVolumeLevel: Boolean = false,
    val enableDebug: Boolean = false,
    val vadEngine: VadEngine? = null,
) : RecorderPluginOptions
