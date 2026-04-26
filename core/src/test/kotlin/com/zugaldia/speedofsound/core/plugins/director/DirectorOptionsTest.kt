package com.zugaldia.speedofsound.core.plugins.director

import com.zugaldia.speedofsound.core.Language
import com.zugaldia.speedofsound.core.audio.vad.VadOptions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DirectorOptionsTest {

    @Test
    fun `default DirectorOptions has expected values`() {
        val options = DirectorOptions()
        assertFalse(options.enableTextProcessing)
        assertEquals(Language.ENGLISH, options.language)
        assertEquals(DEFAULT_CONTEXT, options.customContext)
        assertEquals(DEFAULT_VOCABULARY, options.customVocabulary)
        assertEquals(DEFAULT_MAX_RECORDING_DURATION_MS, options.maxRecordingDurationMs)
    }

    @Test
    fun `creates DirectorOptions with text processing enabled`() {
        val options = DirectorOptions(enableTextProcessing = true)
        assertTrue(options.enableTextProcessing)
    }

    @Test
    fun `creates DirectorOptions with custom language`() {
        val options = DirectorOptions(language = Language.SPANISH)
        assertEquals(Language.SPANISH, options.language)
    }

    @Test
    fun `creates DirectorOptions with custom context`() {
        val customContext = "Custom context for testing"
        val options = DirectorOptions(customContext = customContext)
        assertEquals(customContext, options.customContext)
    }

    @Test
    fun `creates DirectorOptions with custom vocabulary`() {
        val customVocab = listOf("Kotlin", "Gradle", "Testing")
        val options = DirectorOptions(customVocabulary = customVocab)
        assertEquals(customVocab, options.customVocabulary)
    }

    @Test
    fun `creates DirectorOptions with empty vocabulary`() {
        val options = DirectorOptions(customVocabulary = emptyList())
        assertTrue(options.customVocabulary.isEmpty())
    }

    @Test
    fun `creates DirectorOptions with custom max recording duration`() {
        val customDuration = 60_000L // 60 seconds
        val options = DirectorOptions(maxRecordingDurationMs = customDuration)
        assertEquals(customDuration, options.maxRecordingDurationMs)
    }

    @Test
    fun `creates DirectorOptions with all custom values`() {
        val customVocab = listOf("Custom", "Terms")
        val customContext = "Custom context"
        val customDuration = 45_000L

        val options = DirectorOptions(
            enableTextProcessing = true,
            language = Language.FRENCH,
            customContext = customContext,
            customVocabulary = customVocab,
            maxRecordingDurationMs = customDuration
        )

        assertTrue(options.enableTextProcessing)
        assertEquals(Language.FRENCH, options.language)
        assertEquals(customContext, options.customContext)
        assertEquals(customVocab, options.customVocabulary)
        assertEquals(customDuration, options.maxRecordingDurationMs)
    }

    @Test
    fun `data class equality works correctly`() {
        val options1 = DirectorOptions(enableTextProcessing = true)
        val options2 = DirectorOptions(enableTextProcessing = true)
        assertEquals(options1, options2)
        assertEquals(options1.hashCode(), options2.hashCode())
    }

    @Test
    fun `data class copy works correctly`() {
        val original = DirectorOptions(enableTextProcessing = false)
        val modified = original.copy(enableTextProcessing = true)
        assertFalse(original.enableTextProcessing)
        assertTrue(modified.enableTextProcessing)
        assertEquals(original.language, modified.language)
        assertEquals(original.customContext, modified.customContext)
        assertEquals(original.customVocabulary, modified.customVocabulary)
        assertEquals(original.maxRecordingDurationMs, modified.maxRecordingDurationMs)
    }

    @Test
    fun `data class copy can modify language`() {
        val original = DirectorOptions(language = Language.ENGLISH)
        val modified = original.copy(language = Language.GERMAN)
        assertEquals(Language.ENGLISH, original.language)
        assertEquals(Language.GERMAN, modified.language)
    }

    @Test
    fun `DEFAULT_MAX_RECORDING_DURATION_MS is 30 seconds`() {
        assertEquals(30_000L, DEFAULT_MAX_RECORDING_DURATION_MS)
    }

    @Test
    fun `DEFAULT_VOCABULARY contains expected terms`() {
        assertTrue(DEFAULT_VOCABULARY.contains("VoiceStream"))
        assertTrue(DEFAULT_VOCABULARY.contains("Linux"))
        assertTrue(DEFAULT_VOCABULARY.contains("GNOME"))
        assertEquals(3, DEFAULT_VOCABULARY.size)
    }

    @Test
    fun `DEFAULT_CONTEXT mentions Linux desktop`() {
        assertTrue(DEFAULT_CONTEXT.contains("Linux desktop"))
        assertTrue(DEFAULT_CONTEXT.contains("raw transcription"))
    }

    @Test
    fun `PROMPT_TEMPLATE contains expected keys`() {
        assertTrue(PROMPT_TEMPLATE.contains(PROMPT_KEY_INPUT))
        assertTrue(PROMPT_TEMPLATE.contains(PROMPT_KEY_LANGUAGE))
        assertTrue(PROMPT_TEMPLATE.contains(PROMPT_KEY_CONTEXT))
        assertTrue(PROMPT_TEMPLATE.contains(PROMPT_KEY_VOCABULARY))
    }

    @Test
    fun `PROMPT_TEMPLATE contains instructions`() {
        assertTrue(PROMPT_TEMPLATE.contains("grammar"))
        assertTrue(PROMPT_TEMPLATE.contains("spelling"))
        assertTrue(PROMPT_TEMPLATE.contains("capitalization"))
        assertTrue(PROMPT_TEMPLATE.contains("punctuation"))
    }

    @Test
    fun `prompt keys are defined correctly`() {
        assertEquals("{INPUT}", PROMPT_KEY_INPUT)
        assertEquals("{LANGUAGE}", PROMPT_KEY_LANGUAGE)
        assertEquals("{CONTEXT}", PROMPT_KEY_CONTEXT)
        assertEquals("{VOCABULARY}", PROMPT_KEY_VOCABULARY)
    }

    @Test
    fun `vadEndpointing default is true`() {
        val opts = DirectorOptions()
        assertTrue(opts.vadEndpointing)
    }

    @Test
    fun `vadOptions default is null`() {
        val opts = DirectorOptions()
        assertNull(opts.vadOptions)
    }

    @Test
    fun `vadOptions can be set`() {
        val v = VadOptions(modelPath = java.nio.file.Path.of("/tmp/m.onnx"))
        val opts = DirectorOptions(vadOptions = v)
        assertEquals(v, opts.vadOptions)
    }

    @Test
    fun `vadEndpointing can be disabled`() {
        val opts = DirectorOptions(vadEndpointing = false)
        assertFalse(opts.vadEndpointing)
    }
}
