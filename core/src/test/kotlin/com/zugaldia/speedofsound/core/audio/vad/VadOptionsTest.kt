package com.zugaldia.speedofsound.core.audio.vad

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals

class VadOptionsTest {
    @Test
    fun `defaults match spec`() {
        val opts = VadOptions(modelPath = Path.of("/tmp/silero.onnx"))
        assertEquals(0.5f, opts.threshold)
        assertEquals(600, opts.minSilenceMs)
        assertEquals(250, opts.minSpeechMs)
        assertEquals(16_000, opts.sampleRate)
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
            sampleRate = 8_000,
        )
        assertEquals(0.7f, opts.threshold)
        assertEquals(800, opts.minSilenceMs)
        assertEquals(300, opts.minSpeechMs)
        assertEquals(8_000, opts.sampleRate)
    }
}
