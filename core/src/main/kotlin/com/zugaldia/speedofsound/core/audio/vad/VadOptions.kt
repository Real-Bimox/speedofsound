package com.zugaldia.speedofsound.core.audio.vad

import com.zugaldia.speedofsound.core.audio.AudioConstants
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
 * @param maxSpeechMs Hard cap on a single utterance before Sherpa force-flushes it,
 *                    in milliseconds. Default 30s — matches Sherpa Whisper's hard
 *                    30-second per-chunk limit (longer audio is silently truncated
 *                    by the recognizer). Sherpa's own default of 5s cut speech
 *                    mid-clause; 15s still felt short for full thoughts.
 * @param sampleRate PCM sample rate in Hz. Silero VAD requires 16000 (or 8000).
 */
data class VadOptions(
    val modelPath: Path,
    val threshold: Float = 0.5f,
    val minSilenceMs: Int = 600,
    val minSpeechMs: Int = 250,
    val maxSpeechMs: Int = 30_000,
    val sampleRate: Int = SAMPLE_RATE_16K,
) {
    init {
        require(threshold in 0.0f..1.0f) {
            "threshold must be in [0.0, 1.0]; got $threshold"
        }
        require(sampleRate == SAMPLE_RATE_8K || sampleRate == SAMPLE_RATE_16K) {
            "Silero VAD supports sample rates 8000 and 16000 Hz; got $sampleRate"
        }
        require(minSilenceMs > 0) { "minSilenceMs must be > 0; got $minSilenceMs" }
        require(minSpeechMs > 0) { "minSpeechMs must be > 0; got $minSpeechMs" }
        require(maxSpeechMs >= minSpeechMs) {
            "maxSpeechMs ($maxSpeechMs) must be >= minSpeechMs ($minSpeechMs)"
        }
    }

    companion object {
        /** Silero-only sample rate; not represented in [AudioConstants]. */
        const val SAMPLE_RATE_8K: Int = 8_000

        /**
         * Aliases [AudioConstants.AUDIO_SAMPLE_RATE_16KHZ] for use as a `VadOptions` default.
         * Kept as `const val` so the value inlines at every call site.
         */
        const val SAMPLE_RATE_16K: Int = AudioConstants.AUDIO_SAMPLE_RATE_16KHZ
    }
}
