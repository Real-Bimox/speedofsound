package com.zugaldia.speedofsound.core.audio.vad

import com.zugaldia.speedofsound.core.plugins.SelectableProvider
import kotlinx.serialization.Serializable

/**
 * Voice Activity Detection providers.
 *
 * Kept distinct from [com.zugaldia.speedofsound.core.plugins.asr.AsrProvider] so VAD
 * models do not flow through ASR-flow routing (`getModelsForProvider`,
 * `pluginIdForProvider`) and cannot be misrouted by future exhaustive when-statements.
 */
@Serializable
enum class VadProvider(override val displayName: String) : SelectableProvider {
    SILERO("Silero VAD"),
}
