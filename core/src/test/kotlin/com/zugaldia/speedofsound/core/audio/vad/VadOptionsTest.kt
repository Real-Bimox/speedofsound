package com.zugaldia.speedofsound.core.audio.vad

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class VadOptionsTest {
    @Test
    fun `defaults match spec`() {
        val opts = VadOptions(modelPath = Path.of("/tmp/silero.onnx"))
        assertEquals(0.5f, opts.threshold)
        assertEquals(600, opts.minSilenceMs)
        assertEquals(250, opts.minSpeechMs)
        assertEquals(VadOptions.SAMPLE_RATE_16K, opts.sampleRate)
    }

    @Test
    fun `modelPath is required (no default)`() {
        // Compile-time guarantee: VadOptions() without modelPath would not compile.
        // This test exists to document the contract for future readers.
        val opts = VadOptions(modelPath = Path.of("/some/path"))
        assertEquals(Path.of("/some/path"), opts.modelPath)
    }

    @Test
    fun `data class equality works on identical inputs`() {
        val a = VadOptions(modelPath = Path.of("/m"))
        val b = VadOptions(modelPath = Path.of("/m"))
        assertEquals(a, b)
    }

    @Test
    fun `non-default values are preserved`() {
        val opts = VadOptions(
            modelPath = Path.of("/m"),
            threshold = 0.7f,
            minSilenceMs = 800,
            minSpeechMs = 300,
            sampleRate = VadOptions.SAMPLE_RATE_8K,
        )
        assertEquals(0.7f, opts.threshold)
        assertEquals(800, opts.minSilenceMs)
        assertEquals(300, opts.minSpeechMs)
        assertEquals(VadOptions.SAMPLE_RATE_8K, opts.sampleRate)
    }

    @Test
    fun `threshold above 1 is rejected`() {
        assertFailsWith<IllegalArgumentException> {
            VadOptions(modelPath = Path.of("/m"), threshold = 1.5f)
        }
    }

    @Test
    fun `threshold below 0 is rejected`() {
        assertFailsWith<IllegalArgumentException> {
            VadOptions(modelPath = Path.of("/m"), threshold = -0.1f)
        }
    }

    @Test
    fun `unsupported sample rate is rejected`() {
        assertFailsWith<IllegalArgumentException> {
            VadOptions(modelPath = Path.of("/m"), sampleRate = 44_100)
        }
    }

    @Test
    fun `non-positive minSilenceMs is rejected`() {
        assertFailsWith<IllegalArgumentException> {
            VadOptions(modelPath = Path.of("/m"), minSilenceMs = 0)
        }
    }

    @Test
    fun `non-positive minSpeechMs is rejected`() {
        assertFailsWith<IllegalArgumentException> {
            VadOptions(modelPath = Path.of("/m"), minSpeechMs = -1)
        }
    }
}
