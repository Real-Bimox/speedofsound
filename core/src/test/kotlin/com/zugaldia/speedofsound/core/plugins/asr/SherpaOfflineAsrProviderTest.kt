package com.zugaldia.speedofsound.core.plugins.asr

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Boundary unit-test for the enum→Sherpa string conversion. Sherpa's
 * OfflineModelConfig.Builder.setProvider(...) takes lowercase "cpu" / "cuda".
 */
class SherpaOfflineAsrProviderTest {
    @Test
    fun `CPU enum maps to lowercase cpu`() {
        assertEquals("cpu", ComputeProvider.CPU.toSherpaProviderString())
    }

    @Test
    fun `CUDA enum maps to lowercase cuda`() {
        assertEquals("cuda", ComputeProvider.CUDA.toSherpaProviderString())
    }
}
