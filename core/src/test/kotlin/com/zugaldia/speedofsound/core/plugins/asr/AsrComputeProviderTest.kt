package com.zugaldia.speedofsound.core.plugins.asr

import kotlin.test.Test
import kotlin.test.assertEquals

class AsrComputeProviderTest {
    @Test
    fun `default compute provider is CPU for whisper`() {
        val opts = SherpaWhisperAsrOptions()
        assertEquals(ComputeProvider.CPU, opts.computeProvider)
    }

    @Test
    fun `default compute provider is CPU for canary`() {
        val opts = SherpaCanaryAsrOptions()
        assertEquals(ComputeProvider.CPU, opts.computeProvider)
    }

    @Test
    fun `default compute provider is CPU for parakeet`() {
        val opts = SherpaParakeetAsrOptions()
        assertEquals(ComputeProvider.CPU, opts.computeProvider)
    }

    @Test
    fun `compute provider can be overridden to CUDA`() {
        val opts = SherpaWhisperAsrOptions(computeProvider = ComputeProvider.CUDA)
        assertEquals(ComputeProvider.CUDA, opts.computeProvider)
    }

    @Test
    fun `default constant matches CPU`() {
        assertEquals(ComputeProvider.CPU, DEFAULT_COMPUTE_PROVIDER)
    }
}
