package com.zugaldia.speedofsound.core.audio.vad

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SileroVadModelsTest {
    @Test
    fun `default VAD model id is registered`() {
        val model = SUPPORTED_VAD_MODELS[DEFAULT_VAD_MODEL_ID]
        assertNotNull(model)
        assertEquals(DEFAULT_VAD_MODEL_ID, model.id)
    }

    @Test
    fun `default VAD model has a non-empty name and download URL`() {
        val model = SUPPORTED_VAD_MODELS.getValue(DEFAULT_VAD_MODEL_ID)
        assertTrue(model.name.isNotBlank())
        val archive = model.archiveFile
        assertNotNull(archive)
        val url = archive.url
        assertNotNull(url)
        assertTrue(url.startsWith("https://"))
    }

    @Test
    fun `default VAD model declares the silero_vad onnx component`() {
        val model = SUPPORTED_VAD_MODELS.getValue(DEFAULT_VAD_MODEL_ID)
        val componentNames = model.components.map { it.name }
        assertTrue(
            componentNames.any { it.endsWith(".onnx") },
            "expected at least one .onnx component, got $componentNames"
        )
    }

    @Test
    fun `unknown id returns null`() {
        assertEquals(null, SUPPORTED_VAD_MODELS["nope"])
    }
}
