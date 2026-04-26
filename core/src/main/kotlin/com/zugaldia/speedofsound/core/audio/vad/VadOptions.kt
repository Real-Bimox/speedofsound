package com.zugaldia.speedofsound.core.audio.vad

import java.nio.file.Path

/**
 * Configuration for [VadEngine]. Defaults align with the Silero VAD project's
 * recommended values for English / multilingual conversational speech.
 *
 * @param modelPath Absolute path to the Silero ONNX file on disk.
 * @param threshold Speech-probability threshold above which a frame counts as speech.
 *                  Range [0.0, 1.0]; lower = more permissive.
 * @param minSilenceMs Silence duration before VAD declares end-of-utterance. Lower
 *                     feels more responsive but cuts speech sooner.
 * @param minSpeechMs Minimum speech duration before VAD declares start-of-utterance.
 *                    Filters out short clicks and microphone noise.
 * @param sampleRate PCM sample rate in Hz. Silero VAD requires 16000 (or 8000).
 */
data class VadOptions(
    val modelPath: Path,
    val threshold: Float = 0.5f,
    val minSilenceMs: Int = 600,
    val minSpeechMs: Int = 250,
    val sampleRate: Int = 16_000,
)
