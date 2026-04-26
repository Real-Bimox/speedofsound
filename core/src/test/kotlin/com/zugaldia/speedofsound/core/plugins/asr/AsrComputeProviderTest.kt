package com.zugaldia.speedofsound.core.plugins.asr

import kotlin.test.Test
import kotlin.test.assertEquals

class AsrComputeProviderTest {
    @Test
    fun `default compute provider is cpu for whisper`() {
        val opts = SherpaWhisperAsrOptions()
        assertEquals("cpu", opts.computeProvider)
    }

    @Test
    fun `compute provider can be overridden to cuda`() {
        val opts = SherpaWhisperAsrOptions(computeProvider = "cuda")
        assertEquals("cuda", opts.computeProvider)
    }
}
