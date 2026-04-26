package com.zugaldia.speedofsound.core.plugins.recorder

import com.zugaldia.speedofsound.core.audio.AudioInputDevice
import com.zugaldia.speedofsound.core.plugins.AppPlugin

/**
 * Base class for recorder plugins.
 *
 * Provides a common interface for different recorder implementations.
 */
abstract class RecorderPlugin<Options : RecorderPluginOptions>(initialOptions: Options) :
    AppPlugin<Options>(initialOptions) {

    /**
     * Returns whether a recording is currently in progress.
     */
    abstract fun isCurrentlyRecording(): Boolean

    /**
     * Starts recording audio.
     */
    abstract fun startRecording()

    /**
     * Stops recording and returns the captured audio data.
     * @return Result containing RecorderResponse with the captured audio data, or an error.
     */
    abstract fun stopRecording(): Result<RecorderResponse>

    /**
     * Returns a list of all available audio input devices (microphones) in the system.
     * Only devices that support audio capture are included.
     */
    abstract fun getAvailableDevices(): List<AudioInputDevice>

    /**
     * Public bridge so external callers (e.g. MainViewModel) can supply a [VadEngine] emitter
     * that fires events through this recorder's event flow without needing access to the
     * protected [tryEmitEvent] on [AppPlugin].
     */
    fun emitVadEvent(event: RecorderEvent): Boolean = tryEmitEvent(event)
}
