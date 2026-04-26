package com.zugaldia.speedofsound.core.audio.vad

import com.zugaldia.speedofsound.core.models.voice.VoiceModel
import com.zugaldia.speedofsound.core.models.voice.VoiceModelFile
import com.zugaldia.speedofsound.core.plugins.asr.AsrProvider

const val DEFAULT_VAD_MODEL_ID: String = "silero-vad-v5"

/**
 * Catalog of downloadable VAD models. Reuses the [VoiceModel] schema so the existing
 * [com.zugaldia.speedofsound.core.models.voice.ModelManager] download/verify/extract
 * pipeline works without modification.
 *
 * The provider field is set to [AsrProvider.SHERPA_WHISPER] as a placeholder — the
 * model manager only consults provider for filesystem layout; it does not constrain VAD
 * model usage. (A future "VAD" provider could be added if/when the catalog grows.)
 *
 * The Silero VAD model is distributed by the Sherpa ONNX project as a single .onnx file
 * (no archive). The download URL is therefore not a tar.bz2; the integrator should
 * confirm the hash by downloading once before relying on the entry. An empty
 * [VoiceModelFile.sha256sum] is treated as "skip verification" by [ChecksumVerifier].
 */
val SUPPORTED_VAD_MODELS: Map<String, VoiceModel> = mapOf(
    DEFAULT_VAD_MODEL_ID to VoiceModel(
        id = DEFAULT_VAD_MODEL_ID,
        name = "Silero VAD v5",
        // TODO(VAD-15): introduce a dedicated VAD provider (or non-ASR-typed catalog) and stop
        // borrowing AsrProvider.SHERPA_WHISPER. Currently safe because no code path
        // exhaustively switches on VoiceModel.provider — but a future when(provider) addition
        // would silently misroute the VAD model into Whisper-specific logic.
        provider = AsrProvider.SHERPA_WHISPER,
        dataSizeMegabytes = 2L,
        archiveFile = VoiceModelFile(
            name = "silero_vad",
            url = "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/silero_vad.onnx",
            sha256sum = "",
        ),
        components = listOf(VoiceModelFile(name = "silero_vad.onnx")),
    ),
)
