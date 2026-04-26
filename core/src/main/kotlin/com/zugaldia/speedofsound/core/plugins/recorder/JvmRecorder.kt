package com.zugaldia.speedofsound.core.plugins.recorder

import com.zugaldia.speedofsound.core.audio.AudioInputDevice
import com.zugaldia.speedofsound.core.audio.AudioManager
import java.io.ByteArrayOutputStream
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.LineUnavailableException
import javax.sound.sampled.TargetDataLine

/**
 * JVM-based audio recorder plugin.
 */
class JvmRecorder(
    options: RecorderOptions = RecorderOptions(),
) : RecorderPlugin<RecorderOptions>(initialOptions = options) {
    override val id: String = ID

    private var targetDataLine: TargetDataLine? = null
    private var recordingThread: Thread? = null
    private var audioBuffer: ByteArrayOutputStream? = null

    @Volatile
    private var isRecording = false

    companion object {
        const val ID = "RECORDER_JVM"
        private const val DEFAULT_BUFFER_SIZE = 1024
        private const val THREAD_JOIN_TIMEOUT_MS = 1000L

        /** Number of bytes per PCM16 sample. */
        private const val PCM16_BYTES_PER_SAMPLE = 2

        /** Bit shift to combine low and high bytes of a 16-bit sample. */
        private const val PCM16_HIGH_BYTE_SHIFT = 8

        /** Mask to treat a signed byte as an unsigned 8-bit value. */
        private const val BYTE_UNSIGNED_MASK = 0xFF

        /**
         * Decode a little-endian PCM16 byte buffer into shorts. Public for unit-test access.
         * @param bytes source buffer
         * @param byteCount number of valid bytes (must be even; last byte is silently dropped if odd)
         */
        fun decodePcm16LittleEndian(bytes: ByteArray, byteCount: Int): ShortArray {
            val sampleCount = byteCount / PCM16_BYTES_PER_SAMPLE
            val out = ShortArray(sampleCount)
            for (i in 0 until sampleCount) {
                val low = bytes[i * PCM16_BYTES_PER_SAMPLE].toInt() and BYTE_UNSIGNED_MASK
                val high = bytes[i * PCM16_BYTES_PER_SAMPLE + 1].toInt()
                out[i] = ((high shl PCM16_HIGH_BYTE_SHIFT) or low).toShort()
            }
            return out
        }

        /**
         * Decode a little-endian PCM16 byte buffer into a caller-supplied [dest] array (zero-copy
         * path for the recording loop). Public for unit-test access.
         * @param bytes source buffer
         * @param byteCount number of valid bytes (must be even; last byte is silently dropped if odd)
         * @param dest pre-allocated destination; must have at least [byteCount]/2 elements
         * @return number of samples written
         */
        fun decodePcm16LittleEndianInto(bytes: ByteArray, byteCount: Int, dest: ShortArray): Int {
            val sampleCount = byteCount / PCM16_BYTES_PER_SAMPLE
            require(dest.size >= sampleCount) {
                "dest must be at least $sampleCount; got ${dest.size}"
            }
            for (i in 0 until sampleCount) {
                val low = bytes[i * PCM16_BYTES_PER_SAMPLE].toInt() and BYTE_UNSIGNED_MASK
                val high = bytes[i * PCM16_BYTES_PER_SAMPLE + 1].toInt()
                dest[i] = ((high shl PCM16_HIGH_BYTE_SHIFT) or low).toShort()
            }
            return sampleCount
        }
    }

    /**
     * Returns whether a recording is currently in progress.
     */
    override fun isCurrentlyRecording(): Boolean = isRecording

    /**
     * Returns a list of all available audio input devices (microphones) in the system.
     * Only devices that support audio capture (TargetDataLine) are included.
     */
    override fun getAvailableDevices(): List<AudioInputDevice> {
        return AudioSystem.getMixerInfo()
            .filter { mixerInfo ->
                val mixer = AudioSystem.getMixer(mixerInfo)
                mixer.targetLineInfo.any { lineInfo ->
                    TargetDataLine::class.java.isAssignableFrom(lineInfo.lineClass)
                }
            }
            .map { mixerInfo ->
                AudioInputDevice(
                    deviceId = mixerInfo.name,
                    name = mixerInfo.name,
                    vendor = mixerInfo.vendor,
                    description = mixerInfo.description,
                    version = mixerInfo.version,
                )
            }
    }

    override fun enable() {
        super.enable()
        val version = System.getProperty("java.version")
        log.info("JVM recorder initialized (Java v$version).")
        if (currentOptions.enableDebug) {
            val devices = getAvailableDevices()
            devices.forEach { device ->
                log.info(
                    "Found audio input device: id=${device.deviceId}, " +
                        "name=${device.name}, description=${device.description}"
                )
            }
        }
    }

    /**
     * Starts recording audio from the default input device.
     * Audio is captured using the format specified in RecorderOptions.
     */
    override fun startRecording() {
        if (isRecording) {
            log.warn("Recording is already in progress.")
            return
        }

        val audioInfo = currentOptions.audioInfo
        val audioFormat = audioInfo.toAudioFormat()
        val dataLineInfo = DataLine.Info(TargetDataLine::class.java, audioFormat)
        if (!AudioSystem.isLineSupported(dataLineInfo)) {
            log.error("Audio line not supported for format: $audioFormat")
            return
        }

        try {
            targetDataLine = AudioSystem.getLine(dataLineInfo) as TargetDataLine
            targetDataLine?.open(audioFormat)
            targetDataLine?.start()

            audioBuffer = ByteArrayOutputStream()
            isRecording = true

            val bufferSize = targetDataLine?.bufferSize ?: DEFAULT_BUFFER_SIZE
            val readSize = minOf(DEFAULT_BUFFER_SIZE, bufferSize)
            val buffer = ByteArray(readSize)
            val vadShortScratch: ShortArray? =
                currentOptions.vadEngine?.let { ShortArray(readSize / PCM16_BYTES_PER_SAMPLE) }

            recordingThread = Thread {
                while (isRecording) {
                    val bytesRead = targetDataLine?.read(buffer, 0, buffer.size) ?: 0
                    if (bytesRead > 0) {
                        audioBuffer?.write(buffer, 0, bytesRead)
                        if (currentOptions.computeVolumeLevel) {
                            val level = AudioManager.computeRmsLevel(buffer.copyOf(bytesRead))
                            tryEmitEvent(RecorderEvent.RecordingLevel(level))
                        }
                        val vad = currentOptions.vadEngine
                        if (vad != null && vadShortScratch != null) {
                            val sampleCount = decodePcm16LittleEndianInto(buffer, bytesRead, vadShortScratch)
                            vad.acceptPcm16(vadShortScratch.copyOf(sampleCount))
                        }
                    }
                }
            }

            recordingThread?.start()
            log.info("Recording started.")
        } catch (e: LineUnavailableException) {
            log.error("Failed to start recording: ${e.message}")
            cleanup()
        }
    }

    /**
     * Stops recording and returns the captured audio data.
     * @return Result containing RecorderResponse with the captured audio data, or an error.
     */
    override fun stopRecording(): Result<RecorderResponse> = runCatching {
        if (!isRecording) {
            throw IllegalStateException("No recording in progress.")
        }

        isRecording = false

        try {
            recordingThread?.join(THREAD_JOIN_TIMEOUT_MS)
        } catch (e: InterruptedException) {
            log.warn("Recording thread interrupted: ${e.message}")
        }

        targetDataLine?.stop()
        targetDataLine?.close()

        val audioData = audioBuffer?.toByteArray()
        cleanup()

        if (audioData == null || audioData.isEmpty()) {
            throw IllegalStateException("No audio data captured.")
        }

        log.info("Recording stopped. Captured ${audioData.size} bytes.")
        RecorderResponse(audioData)
    }

    private fun cleanup() {
        isRecording = false
        targetDataLine = null
        recordingThread = null
        audioBuffer = null
    }

    override fun disable() {
        if (isRecording) {
            // Joins the capture thread before returning.
            stopRecording().onFailure { log.warn("stopRecording failed during disable: ${it.message}") }
        }
        runCatching { currentOptions.vadEngine?.release() }
            .onFailure { log.warn("VAD release failed in disable: ${it.message}") }
        super.disable()
    }
}
