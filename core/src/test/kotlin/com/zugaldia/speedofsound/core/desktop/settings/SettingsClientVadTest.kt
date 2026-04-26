package com.zugaldia.speedofsound.core.desktop.settings

import com.zugaldia.speedofsound.core.plugins.asr.ComputeProvider
import kotlin.io.path.createTempFile
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SettingsClientVadTest {

    // PropertiesStore(filename) resolves getDataDir().resolve(filename); passing an absolute path
    // causes Path.resolve to return the absolute path directly, so the temp file is used as-is.
    private val tempPath = createTempFile(prefix = "voicestream-vad-test", suffix = ".properties")
    private val client = SettingsClient(PropertiesStore(tempPath.toString()))

    @AfterTest
    fun cleanup() {
        runCatching { tempPath.toFile().delete() }
    }

    @Test
    fun `vad endpointing default is true`() {
        assertTrue(client.getVadEndpointing())
    }

    @Test
    fun `vad endpointing roundtrips`() {
        client.setVadEndpointing(false)
        assertFalse(client.getVadEndpointing())
        client.setVadEndpointing(true)
        assertTrue(client.getVadEndpointing())
    }

    @Test
    fun `vad min silence default is 600`() {
        assertEquals(600, client.getVadMinSilenceMs())
    }

    @Test
    fun `vad min silence roundtrips`() {
        client.setVadMinSilenceMs(800)
        assertEquals(800, client.getVadMinSilenceMs())
    }

    @Test
    fun `compute provider default is CPU`() {
        assertEquals(ComputeProvider.CPU, client.getComputeProvider())
    }

    @Test
    fun `compute provider roundtrips through enum name`() {
        client.setComputeProvider(ComputeProvider.CUDA)
        assertEquals(ComputeProvider.CUDA, client.getComputeProvider())
    }

    @Test
    fun `corrupt compute provider falls back to default`() {
        // Directly write an invalid enum name to the underlying store.
        val store = PropertiesStore(tempPath.toString())
        store.setString(KEY_COMPUTE_PROVIDER, "bogus")
        val freshClient = SettingsClient(store)
        assertEquals(ComputeProvider.CPU, freshClient.getComputeProvider())
    }
}
