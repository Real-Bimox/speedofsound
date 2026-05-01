package com.zugaldia.speedofsound.core.models.voice

import com.zugaldia.speedofsound.core.Language
import com.zugaldia.speedofsound.core.models.SelectableModel
import com.zugaldia.speedofsound.core.plugins.SelectableProvider

/**
 * Packaging format of the downloadable archive at [VoiceModelFile.url].
 *
 * - [TAR_BZ2]: a `.tar.bz2` containing a directory named after the model id with the components inside.
 *   This is the format Sherpa-ONNX publishes ASR models in.
 * - [SINGLE_FILE]: the URL points directly at one component file (e.g., `silero_vad.onnx`). The model
 *   must declare exactly one component, whose name matches the file we expect on disk.
 */
enum class ArchiveFormat { TAR_BZ2, SINGLE_FILE }

data class VoiceModelFile(
    val name: String,
    val url: String? = null,
    val sha256sum: String? = null,
    val format: ArchiveFormat = ArchiveFormat.TAR_BZ2,
)

data class VoiceModel(
    override val id: String,
    override val name: String, // User-friendly name
    val provider: SelectableProvider,
    val languages: List<Language> = emptyList(), // Only if language restrictions apply, empty for multilingual models
    val dataSizeMegabytes: Long = 0L, // Model files uncompressed (not the archive file download), 0 for cloud models
    val archiveFile: VoiceModelFile? = null,
    val components: List<VoiceModelFile> = emptyList()
) : SelectableModel
