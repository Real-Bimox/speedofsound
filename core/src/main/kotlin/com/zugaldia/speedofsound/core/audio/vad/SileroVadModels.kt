package com.zugaldia.speedofsound.core.audio.vad

import com.zugaldia.speedofsound.core.models.voice.ArchiveFormat
import com.zugaldia.speedofsound.core.models.voice.VoiceModel
import com.zugaldia.speedofsound.core.models.voice.VoiceModelFile

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
 * (no archive), so the entry uses [ArchiveFormat.SINGLE_FILE] to opt out of the default
 * tar.bz2 extract step. An empty [VoiceModelFile.sha256sum] is treated as "skip
 * verification" by [ChecksumVerifier]; the integrator should confirm the hash by
 * downloading once before relying on the entry.
 */
val SUPPORTED_VAD_MODELS: Map<String, VoiceModel> = mapOf(
    DEFAULT_VAD_MODEL_ID to VoiceModel(
        id = DEFAULT_VAD_MODEL_ID,
        name = "Silero VAD v5",
        provider = VadProvider.SILERO,
        dataSizeMegabytes = 2L,
        archiveFile = VoiceModelFile(
            name = "silero_vad",
            url = "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/silero_vad.onnx",
            sha256sum = "",
            format = ArchiveFormat.SINGLE_FILE,
        ),
        components = listOf(VoiceModelFile(name = "silero_vad.onnx")),
    ),
)
