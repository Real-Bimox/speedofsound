package com.zugaldia.speedofsound.core.models.voice

import com.zugaldia.speedofsound.core.audio.vad.SUPPORTED_VAD_MODELS
import com.zugaldia.speedofsound.core.desktop.settings.SUPPORTED_LOCAL_ASR_MODELS
import com.zugaldia.speedofsound.core.plugins.asr.DEFAULT_ASR_SHERPA_WHISPER_MODEL_ID

/**
 * Catalog for looking up voice models.
 */
interface VoiceModelCatalog {
    fun getModel(modelId: String): VoiceModel?
    fun getDefaultModelId(): String
}

/**
 * Production implementation that uses the global SUPPORTED_LOCAL_ASR_MODELS and SUPPORTED_VAD_MODELS.
 * VAD models are resolved as a fallback so ModelManager's download/verify/extract pipeline works
 * for Silero VAD without any additional plumbing.
 */
class DefaultVoiceModelCatalog : VoiceModelCatalog {
    override fun getModel(modelId: String): VoiceModel? =
        SUPPORTED_LOCAL_ASR_MODELS[modelId] ?: SUPPORTED_VAD_MODELS[modelId]

    override fun getDefaultModelId(): String {
        return DEFAULT_ASR_SHERPA_WHISPER_MODEL_ID
    }
}
