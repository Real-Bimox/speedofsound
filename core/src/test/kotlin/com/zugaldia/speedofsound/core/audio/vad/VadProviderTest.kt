package com.zugaldia.speedofsound.core.audio.vad

import com.zugaldia.speedofsound.core.plugins.SelectableProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VadProviderTest {
    @Test
    fun `SILERO is a SelectableProvider with a non-blank display name`() {
        val provider: SelectableProvider = VadProvider.SILERO
        assertEquals("SILERO", provider.name)
        assertTrue(provider.displayName.isNotBlank())
    }

    @Test
    fun `SILERO is the only entry today`() {
        assertEquals(listOf(VadProvider.SILERO), VadProvider.entries)
    }
}
