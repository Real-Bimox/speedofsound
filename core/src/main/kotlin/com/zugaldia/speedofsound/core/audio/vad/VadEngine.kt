package com.zugaldia.speedofsound.core.audio.vad

import com.k2fsa.sherpa.onnx.SileroVadModelConfig
import com.k2fsa.sherpa.onnx.Vad
import com.k2fsa.sherpa.onnx.VadModelConfig
import com.zugaldia.speedofsound.core.plugins.recorder.RecorderEvent
import org.slf4j.LoggerFactory

/**
 * Wraps Sherpa's [Vad] in a per-recording stateful loop that emits
 * [RecorderEvent.SpeechStarted] / [RecorderEvent.SpeechEnded] as audio is fed in.
 *
 * Thread model: NOT thread-safe. The recorder is expected to call [acceptPcm16] from a
 * single capture thread; [release] from the same thread (or after the capture thread has
 * joined).
 *
 * @param options VAD configuration (model path, threshold, min-silence, min-speech, sample rate).
 * @param emitter Callback that receives speech-segment boundary events. Typically wired to the
 *   recorder's `tryEmitEvent`.
 */
class VadEngine(
    private val options: VadOptions,
    private val emitter: (RecorderEvent) -> Unit,
) {
    private val log = LoggerFactory.getLogger(VadEngine::class.java)

    private val vad: Vad = run {
        val sileroConfig = SileroVadModelConfig.builder()
            .setModel(options.modelPath.toString())
            .setThreshold(options.threshold)
            .setMinSilenceDuration(options.minSilenceMs / MS_PER_SECOND)
            .setMinSpeechDuration(options.minSpeechMs / MS_PER_SECOND)
            .build()
        val vadConfig = VadModelConfig.builder()
            .setSileroVadModelConfig(sileroConfig)
            .setSampleRate(options.sampleRate)
            .build()
        Vad(vadConfig)
    }

    private val stateMachine = StateMachine(emitter = emitter)

    /** Feed PCM16 mono samples (sample rate = [VadOptions.sampleRate]). */
    fun acceptPcm16(samples: ShortArray) {
        if (samples.isEmpty()) return
        val floats = convertPcm16ToFloat(samples)
        vad.acceptWaveform(floats)
        stateMachine.observeSpeechActive(vad.isSpeechDetected)
        while (!vad.empty()) {
            stateMachine.observeSegmentFlush()
            vad.pop()
        }
    }

    fun release() {
        runCatching { vad.release() }.onFailure {
            log.warn("VAD release failed: ${it.message}")
        }
    }

    enum class State { IDLE, SPEAKING }

    /**
     * Pure state machine — no Sherpa dependency, fully unit-testable. The engine drives this
     * with two observations: [observeSpeechActive] (every chunk) and [observeSegmentFlush]
     * (whenever Sherpa emits a finalised segment via `pop()`).
     */
    class StateMachine(
        private val emitter: (RecorderEvent) -> Unit,
    ) {
        private var state: State = State.IDLE

        fun observeSpeechActive(active: Boolean) {
            if (active && state == State.IDLE) {
                state = State.SPEAKING
                emitter(RecorderEvent.SpeechStarted)
            } else if (!active && state == State.SPEAKING) {
                state = State.IDLE
            }
        }

        fun observeSegmentFlush() {
            if (state == State.SPEAKING) {
                emitter(RecorderEvent.SpeechEnded)
                // Stay SPEAKING until observeSpeechActive(false). Guards against
                // back-to-back segments inside one recording emitting only one start.
            }
        }
    }

    companion object {
        private const val MS_PER_SECOND: Float = 1000.0f

        /** Public for testability. Normalises PCM16 to `[-1.0, 1.0]` floats. */
        fun convertPcm16ToFloat(samples: ShortArray): FloatArray {
            val out = FloatArray(samples.size)
            for (i in samples.indices) {
                out[i] = samples[i] / Short.MAX_VALUE.toFloat()
            }
            return out
        }
    }
}
