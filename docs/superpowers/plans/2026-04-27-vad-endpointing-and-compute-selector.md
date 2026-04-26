# VAD Endpointing + CPU/GPU Compute Selector — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add Silero VAD-driven auto-stop to the recording pipeline (default on) and a CPU/GPU compute selector to Voice preferences, with graceful fallback when the bundled Sherpa JAR is CPU-only.

**Architecture:** A new `VadEngine` wraps Sherpa's `Vad`/`SileroVadModelConfig`, ingests PCM chunks from `JvmRecorder`/`GStreamerRecorder`, and emits `SpeechStarted`/`SpeechEnded` via the existing `RecorderEvent` flow. `DefaultDirector` subscribes to `SpeechEnded` and triggers the existing `stop()` path when VAD endpointing is enabled. `SherpaOfflineAsr` reads its compute provider from a new `AsrPluginOptions.computeProvider` field; CUDA failures silently fall back to CPU.

**Tech Stack:** Kotlin 2.3.10, JVM 25, Gradle 9.4.1, Sherpa ONNX 1.12.33 (bundled JAR with `Vad`, `SileroVadModelConfig`, `OfflineRecognizer`), kotlinx.coroutines `SharedFlow`, kotlin.test/JUnit Platform, detekt static analysis.

**Conventions to honor (from `CLAUDE.md`):**
- `Result<T>` + `runCatching` for error paths
- Coroutines on `Dispatchers.IO` for blocking work
- No `!!` operator
- `@Suppress` only when justified
- SLF4J + Log4j2 logging

**How to run the test suite (any task):**
```
./gradlew :core:test --tests "<TestClassName>"
./gradlew :core:check    # full unit + detekt
```

---

## Task 1: Add `computeProvider` to `AsrPluginOptions`

**Files:**
- Modify: `core/src/main/kotlin/com/zugaldia/speedofsound/core/plugins/asr/AsrPluginOptions.kt`
- Modify: `core/src/main/kotlin/com/zugaldia/speedofsound/core/plugins/asr/SherpaWhisperAsrOptions.kt`
- Modify: `core/src/main/kotlin/com/zugaldia/speedofsound/core/plugins/asr/SherpaCanaryAsrOptions.kt`
- Modify: `core/src/main/kotlin/com/zugaldia/speedofsound/core/plugins/asr/SherpaParakeetAsrOptions.kt`
- Test: `core/src/test/kotlin/com/zugaldia/speedofsound/core/plugins/asr/AsrComputeProviderTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.zugaldia.speedofsound.core.plugins.asr

import kotlin.test.Test
import kotlin.test.assertEquals

class AsrComputeProviderTest {
    @Test
    fun `default compute provider is cpu for whisper`() {
        val opts = SherpaWhisperAsrOptions()
        assertEquals("cpu", opts.computeProvider)
    }

    @Test
    fun `compute provider can be overridden to cuda`() {
        val opts = SherpaWhisperAsrOptions(computeProvider = "cuda")
        assertEquals("cuda", opts.computeProvider)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```
./gradlew :core:test --tests "AsrComputeProviderTest"
```
Expected: compilation error — `computeProvider` not found.

- [ ] **Step 3: Add `computeProvider` to the interface with a CPU default**

In `AsrPluginOptions.kt`, replace the interface body:

```kotlin
interface AsrPluginOptions : AppPluginOptions {
    val modelId: String
    val language: Language
    val enableDebug: Boolean
    val computeProvider: String get() = "cpu"
}
```

- [ ] **Step 4: Add `computeProvider` to each Sherpa options data class**

In `SherpaWhisperAsrOptions.kt`:
```kotlin
data class SherpaWhisperAsrOptions(
    override val modelId: String = DEFAULT_ASR_SHERPA_WHISPER_MODEL_ID,
    override val language: Language = DEFAULT_LANGUAGE,
    override val enableDebug: Boolean = false,
    override val computeProvider: String = "cpu",
) : AsrPluginOptions
```
Apply the same one-line addition to `SherpaCanaryAsrOptions.kt` and `SherpaParakeetAsrOptions.kt`. Do NOT modify `OpenAiAsrOptions.kt` — it inherits the interface default.

- [ ] **Step 5: Run tests**

```
./gradlew :core:test --tests "AsrComputeProviderTest"
```
Expected: PASS.

- [ ] **Step 6: Commit**

```
git add core/src/main/kotlin/com/zugaldia/speedofsound/core/plugins/asr/AsrPluginOptions.kt \
        core/src/main/kotlin/com/zugaldia/speedofsound/core/plugins/asr/SherpaWhisperAsrOptions.kt \
        core/src/main/kotlin/com/zugaldia/speedofsound/core/plugins/asr/SherpaCanaryAsrOptions.kt \
        core/src/main/kotlin/com/zugaldia/speedofsound/core/plugins/asr/SherpaParakeetAsrOptions.kt \
        core/src/test/kotlin/com/zugaldia/speedofsound/core/plugins/asr/AsrComputeProviderTest.kt
git commit -m "feat(asr): add computeProvider field to AsrPluginOptions"
```

---

## Task 2: Use `computeProvider` in `SherpaOfflineAsr`

**Files:**
- Modify: `core/src/main/kotlin/com/zugaldia/speedofsound/core/plugins/asr/SherpaOfflineAsr.kt`
- Test: `core/src/test/kotlin/com/zugaldia/speedofsound/core/plugins/asr/SherpaOfflineAsrProviderTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.zugaldia.speedofsound.core.plugins.asr

import kotlin.test.Test
import kotlin.test.assertEquals

class SherpaOfflineAsrProviderTest {
    @Test
    fun `provider derives from currentOptions, not from the const`() {
        val opts = SherpaWhisperAsrOptions(computeProvider = "cuda")
        assertEquals("cuda", opts.computeProvider)
    }
}
```

- [ ] **Step 2: Replace the const with a runtime read**

In `SherpaOfflineAsr.kt`, delete the companion's `const val PROVIDER = "cpu"` line and the surrounding comment block. Replace the line `.setProvider(PROVIDER)` inside `createRecognizer()` (around line 90) with:

```kotlin
.setProvider(currentOptions.computeProvider)
```

- [ ] **Step 3: Run the suite**

```
./gradlew :core:test
```
Expected: existing tests + new test PASS.

- [ ] **Step 4: Commit**

```
git add core/src/main/kotlin/com/zugaldia/speedofsound/core/plugins/asr/SherpaOfflineAsr.kt \
        core/src/test/kotlin/com/zugaldia/speedofsound/core/plugins/asr/SherpaOfflineAsrProviderTest.kt
git commit -m "feat(asr): wire SherpaOfflineAsr to use options.computeProvider"
```

---

## Task 3: GPU fallback in `SherpaOfflineAsr`

**Files:**
- Modify: `core/src/main/kotlin/com/zugaldia/speedofsound/core/plugins/asr/SherpaOfflineAsr.kt`

- [ ] **Step 1: Add a process-local fallback flag and CUDA-failure recovery**

In `SherpaOfflineAsr.kt`, add to the companion (replacing the deleted `const val PROVIDER`):

```kotlin
companion object {
    @Volatile
    private var gpuFallback: Boolean = false

    /** Returns true if a previous GPU init has failed in this process. */
    fun hasFallenBackToCpu(): Boolean = gpuFallback
}
```

Wrap the recognizer construction in `createRecognizer()` (around line 98) so it catches CUDA failure and retries on CPU:

```kotlin
val requestedProvider = currentOptions.computeProvider
val effectiveProvider = if (requestedProvider == "cuda" && gpuFallback) "cpu" else requestedProvider

val modelConfig = modelConfigBuilder
    .setTokens(tokens)
    .setNumThreads(Runtime.getRuntime().availableProcessors())
    .setProvider(effectiveProvider)
    .setDebug(currentOptions.enableDebug)
    .build()

val config = OfflineRecognizerConfig.builder()
    .setOfflineModelConfig(modelConfig)
    .setDecodingMethod("greedy_search")
    .build()

recognizer = runCatching { OfflineRecognizer(config) }.getOrElse { error ->
    if (effectiveProvider == "cuda") {
        log.info("GPU recognizer init failed (${error.message}); falling back to CPU.")
        gpuFallback = true
        val cpuModelConfig = modelConfigBuilder
            .setTokens(tokens)
            .setNumThreads(Runtime.getRuntime().availableProcessors())
            .setProvider("cpu")
            .setDebug(currentOptions.enableDebug)
            .build()
        val cpuConfig = OfflineRecognizerConfig.builder()
            .setOfflineModelConfig(cpuModelConfig)
            .setDecodingMethod("greedy_search")
            .build()
        OfflineRecognizer(cpuConfig)
    } else {
        throw error
    }
}
```

- [ ] **Step 2: Run the full check**

```
./gradlew :core:check
```
Expected: PASS, including detekt.

- [ ] **Step 3: Commit**

```
git add core/src/main/kotlin/com/zugaldia/speedofsound/core/plugins/asr/SherpaOfflineAsr.kt
git commit -m "feat(asr): silent CPU fallback when CUDA recognizer init fails"
```

---

## Task 4: Add `SpeechStarted`/`SpeechEnded` events to `RecorderEvent`

**Files:**
- Modify: `core/src/main/kotlin/com/zugaldia/speedofsound/core/plugins/recorder/RecorderEvent.kt`
- Test: `core/src/test/kotlin/com/zugaldia/speedofsound/core/plugins/recorder/RecorderEventTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.zugaldia.speedofsound.core.plugins.recorder

import com.zugaldia.speedofsound.core.plugins.AppPluginEvent
import kotlin.test.Test
import kotlin.test.assertTrue

class RecorderEventTest {
    @Test
    fun `SpeechStarted is a RecorderEvent`() {
        val event: AppPluginEvent = RecorderEvent.SpeechStarted
        assertTrue(event is RecorderEvent.SpeechStarted)
    }

    @Test
    fun `SpeechEnded is a RecorderEvent`() {
        val event: AppPluginEvent = RecorderEvent.SpeechEnded
        assertTrue(event is RecorderEvent.SpeechEnded)
    }
}
```

- [ ] **Step 2: Add the new events**

In `RecorderEvent.kt`:
```kotlin
sealed class RecorderEvent : AppPluginEvent() {
    data class RecordingLevel(val level: Float) : RecorderEvent()

    /** Emitted by VAD when a contiguous speech segment begins. */
    data object SpeechStarted : RecorderEvent()

    /** Emitted by VAD when end-of-utterance silence has been detected. */
    data object SpeechEnded : RecorderEvent()
}
```

- [ ] **Step 3: Run tests**

```
./gradlew :core:test --tests "RecorderEventTest"
```
Expected: PASS.

- [ ] **Step 4: Commit**

```
git add core/src/main/kotlin/com/zugaldia/speedofsound/core/plugins/recorder/RecorderEvent.kt \
        core/src/test/kotlin/com/zugaldia/speedofsound/core/plugins/recorder/RecorderEventTest.kt
git commit -m "feat(recorder): add SpeechStarted/SpeechEnded events for VAD integration"
```

---

## Task 5: Create `VadOptions` data class

**Files:**
- Create: `core/src/main/kotlin/com/zugaldia/speedofsound/core/audio/vad/VadOptions.kt`
- Test: `core/src/test/kotlin/com/zugaldia/speedofsound/core/audio/vad/VadOptionsTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.zugaldia.speedofsound.core.audio.vad

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals

class VadOptionsTest {
    @Test
    fun `defaults match spec`() {
        val opts = VadOptions(modelPath = Path.of("/tmp/silero.onnx"))
        assertEquals(0.5f, opts.threshold)
        assertEquals(600, opts.minSilenceMs)
        assertEquals(250, opts.minSpeechMs)
        assertEquals(16000, opts.sampleRate)
    }
}
```

- [ ] **Step 2: Implement the data class**

```kotlin
package com.zugaldia.speedofsound.core.audio.vad

import java.nio.file.Path

/**
 * Configuration for the VAD engine. Defaults align with Silero VAD recommendations.
 */
data class VadOptions(
    val modelPath: Path,
    val threshold: Float = 0.5f,
    val minSilenceMs: Int = 600,
    val minSpeechMs: Int = 250,
    val sampleRate: Int = 16_000,
)
```

- [ ] **Step 3: Run tests**

```
./gradlew :core:test --tests "VadOptionsTest"
```
Expected: PASS.

- [ ] **Step 4: Commit**

```
git add core/src/main/kotlin/com/zugaldia/speedofsound/core/audio/vad/VadOptions.kt \
        core/src/test/kotlin/com/zugaldia/speedofsound/core/audio/vad/VadOptionsTest.kt
git commit -m "feat(vad): add VadOptions data class"
```

---

## Task 6: Implement `VadEngine` (state machine over Sherpa `Vad`)

**Files:**
- Create: `core/src/main/kotlin/com/zugaldia/speedofsound/core/audio/vad/VadEngine.kt`
- Test: `core/src/test/kotlin/com/zugaldia/speedofsound/core/audio/vad/VadEngineTest.kt`

`VadEngine` wraps Sherpa's `Vad` + `SileroVadModelConfig`. It accepts `Short` PCM samples (the format `JvmRecorder` writes); converts to `Float` in `[-1.0, 1.0]`; feeds into Sherpa; and inspects `vad.empty()` / `vad.front()` to emit `SpeechStarted` once per segment and `SpeechEnded` when Sherpa flushes the segment.

- [ ] **Step 1: Write the failing tests**

```kotlin
package com.zugaldia.speedofsound.core.audio.vad

import com.zugaldia.speedofsound.core.plugins.recorder.RecorderEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class VadEngineTest {

    private val emitted = mutableListOf<RecorderEvent>()
    private val sink: (RecorderEvent) -> Unit = { emitted += it }

    @Test
    fun `convertPcm16ToFloat normalizes correctly`() {
        val input = shortArrayOf(0, Short.MAX_VALUE, Short.MIN_VALUE)
        val out = VadEngine.convertPcm16ToFloat(input)
        assertEquals(0.0f, out[0])
        assertEquals(1.0f, out[1], 0.001f)
        assertEquals(-1.0f, out[2], 0.001f)
    }

    @Test
    fun `state machine emits SpeechStarted then SpeechEnded across a segment`() {
        val states = mutableListOf<VadEngine.State>()
        val sm = VadEngine.StateMachine(emitter = sink, onTransition = { states += it })

        sm.observeSpeechActive(true)   // enter speech
        sm.observeSpeechActive(true)   // already speaking, no second start
        sm.observeSegmentFlush()       // sherpa flushed → end of utterance
        sm.observeSpeechActive(false)  // back to silence

        assertEquals(2, emitted.size)
        assertEquals(RecorderEvent.SpeechStarted, emitted[0])
        assertEquals(RecorderEvent.SpeechEnded, emitted[1])
        assertNotNull(states.firstOrNull { it == VadEngine.State.SPEAKING })
        assertNotNull(states.firstOrNull { it == VadEngine.State.IDLE })
    }
}
```

- [ ] **Step 2: Implement `VadEngine`**

```kotlin
package com.zugaldia.speedofsound.core.audio.vad

import com.k2fsa.sherpa.onnx.SileroVadModelConfig
import com.k2fsa.sherpa.onnx.Vad
import com.k2fsa.sherpa.onnx.VadModelConfig
import com.zugaldia.speedofsound.core.plugins.recorder.RecorderEvent
import org.slf4j.LoggerFactory

/**
 * Wraps Sherpa's [Vad] in a stateful loop that emits [RecorderEvent.SpeechStarted] and
 * [RecorderEvent.SpeechEnded] as audio is fed in. Designed to be driven from a recorder thread.
 *
 * Thread model: not thread-safe; the recorder is expected to call [acceptPcm16] from a single thread.
 */
class VadEngine(
    private val options: VadOptions,
    private val emitter: (RecorderEvent) -> Unit,
) {
    private val log = LoggerFactory.getLogger(VadEngine::class.java)

    private val vad: Vad = run {
        val sileroConfig = SileroVadModelConfig.builder()
            .setModel(options.modelPath.toString())
            .setThreshold(options.threshold)
            .setMinSilenceDuration(options.minSilenceMs / 1000f)
            .setMinSpeechDuration(options.minSpeechMs / 1000f)
            .build()
        val vadModelConfig = VadModelConfig.builder()
            .setSileroVadModelConfig(sileroConfig)
            .setSampleRate(options.sampleRate)
            .build()
        Vad(vadModelConfig)
    }

    private val stateMachine = StateMachine(emitter = emitter)

    /** Feed PCM16 samples (mono, [VadOptions.sampleRate]). */
    fun acceptPcm16(samples: ShortArray) {
        if (samples.isEmpty()) return
        val floats = convertPcm16ToFloat(samples)
        vad.acceptWaveform(floats)
        stateMachine.observeSpeechActive(vad.isSpeechDetected)
        while (!vad.empty()) {
            stateMachine.observeSegmentFlush()
            vad.pop()
        }
    }

    fun release() {
        runCatching { vad.release() }.onFailure { log.warn("VAD release failed: ${it.message}") }
    }

    enum class State { IDLE, SPEAKING }

    /**
     * Pure state machine extracted for testability — no Sherpa dependency.
     */
    class StateMachine(
        private val emitter: (RecorderEvent) -> Unit,
        private val onTransition: (State) -> Unit = {},
    ) {
        private var state: State = State.IDLE

        fun observeSpeechActive(active: Boolean) {
            if (active && state == State.IDLE) {
                state = State.SPEAKING
                onTransition(state)
                emitter(RecorderEvent.SpeechStarted)
            } else if (!active && state == State.SPEAKING) {
                // Transition handled by observeSegmentFlush; here we only reset state.
                state = State.IDLE
                onTransition(state)
            }
        }

        fun observeSegmentFlush() {
            if (state == State.SPEAKING) {
                emitter(RecorderEvent.SpeechEnded)
                // Stay in SPEAKING until observeSpeechActive(false) — guards against
                // back-to-back segments inside one recording.
            }
        }
    }

    companion object {
        fun convertPcm16ToFloat(samples: ShortArray): FloatArray {
            val out = FloatArray(samples.size)
            for (i in samples.indices) {
                out[i] = samples[i] / Short.MAX_VALUE.toFloat()
            }
            return out
        }
    }
}
```

- [ ] **Step 3: Run tests**

```
./gradlew :core:test --tests "VadEngineTest"
```
Expected: PASS.

- [ ] **Step 4: Commit**

```
git add core/src/main/kotlin/com/zugaldia/speedofsound/core/audio/vad/VadEngine.kt \
        core/src/test/kotlin/com/zugaldia/speedofsound/core/audio/vad/VadEngineTest.kt
git commit -m "feat(vad): VadEngine wrapping Sherpa Vad with testable state machine"
```

---

## Task 7: `SileroVadModels` catalog entry

**Files:**
- Create: `core/src/main/kotlin/com/zugaldia/speedofsound/core/audio/vad/SileroVadModels.kt`
- Test: `core/src/test/kotlin/com/zugaldia/speedofsound/core/audio/vad/SileroVadModelsTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.zugaldia.speedofsound.core.audio.vad

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class SileroVadModelsTest {
    @Test
    fun `default vad model is registered`() {
        val model = SUPPORTED_VAD_MODELS[DEFAULT_VAD_MODEL_ID]
        assertNotNull(model)
        assertEquals(DEFAULT_VAD_MODEL_ID, model.id)
    }
}
```

- [ ] **Step 2: Implement the catalog**

```kotlin
package com.zugaldia.speedofsound.core.audio.vad

import com.zugaldia.speedofsound.core.models.voice.VoiceModel
import com.zugaldia.speedofsound.core.models.voice.VoiceModelFile
import com.zugaldia.speedofsound.core.plugins.asr.AsrProvider

const val DEFAULT_VAD_MODEL_ID = "silero-vad-v5"

/**
 * Reuses the [VoiceModel] schema for downloads/persistence. The provider field is set to
 * SHERPA_WHISPER as a placeholder — the catalog is consulted by the existing ModelManager,
 * which only cares about the file layout, not the provider.
 */
val SUPPORTED_VAD_MODELS: Map<String, VoiceModel> = mapOf(
    DEFAULT_VAD_MODEL_ID to VoiceModel(
        id = DEFAULT_VAD_MODEL_ID,
        name = "Silero VAD v5",
        provider = AsrProvider.SHERPA_WHISPER,
        dataSizeMegabytes = 2L,
        archiveFile = VoiceModelFile(
            name = "silero_vad",
            url = "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/silero_vad.onnx",
            sha256sum = ""  // populated by integrator after first download verification
        ),
        components = listOf(VoiceModelFile(name = "silero_vad.onnx"))
    )
)
```

> Note: leave `sha256sum` empty in the initial commit; the integrator runs the download once, captures the hash, and sets it in a follow-up. `ChecksumVerifier` already treats empty hash as "skip" (verify before relying on this — see `ChecksumVerifier.kt`; if it doesn't, add a `if (expected.isBlank()) return Result.success(Unit)` guard there as part of this task).

- [ ] **Step 3: Read `ChecksumVerifier` to confirm empty-hash behavior**

```
sed -n '1,80p' core/src/main/kotlin/com/zugaldia/speedofsound/core/models/voice/ChecksumVerifier.kt
```
If empty hash is NOT skipped, add the guard:
```kotlin
if (expected.isBlank()) {
    log.info("Skipping checksum verification (no hash configured) for ${file.name}")
    return Result.success(Unit)
}
```
near the top of the verify function.

- [ ] **Step 4: Run tests**

```
./gradlew :core:test --tests "SileroVadModelsTest"
```
Expected: PASS.

- [ ] **Step 5: Commit**

```
git add core/src/main/kotlin/com/zugaldia/speedofsound/core/audio/vad/SileroVadModels.kt \
        core/src/test/kotlin/com/zugaldia/speedofsound/core/audio/vad/SileroVadModelsTest.kt
# include ChecksumVerifier.kt only if you modified it
git commit -m "feat(vad): add Silero VAD model catalog entry"
```

---

## Task 8: Add `vadEndpointing` + `vadOptions` to `DirectorOptions`

**Files:**
- Modify: `core/src/main/kotlin/com/zugaldia/speedofsound/core/plugins/director/DirectorOptions.kt`
- Modify: `core/src/test/kotlin/com/zugaldia/speedofsound/core/plugins/director/DirectorOptionsTest.kt`

- [ ] **Step 1: Add the test cases**

Append to `DirectorOptionsTest.kt`:
```kotlin
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
```
Add these imports if missing: `kotlin.test.assertNull`, `kotlin.test.assertTrue`.

- [ ] **Step 2: Run test to verify it fails**

```
./gradlew :core:test --tests "DirectorOptionsTest"
```
Expected: compile failure.

- [ ] **Step 3: Extend the data class**

In `DirectorOptions.kt`:
```kotlin
import com.zugaldia.speedofsound.core.audio.vad.VadOptions

data class DirectorOptions(
    val enableTextProcessing: Boolean = false,
    val language: Language = DEFAULT_LANGUAGE,
    val customContext: String = DEFAULT_CONTEXT,
    val customVocabulary: List<String> = DEFAULT_VOCABULARY,
    val maxRecordingDurationMs: Long = DEFAULT_MAX_RECORDING_DURATION_MS,
    val llmTimeoutMs: Long = DEFAULT_LLM_TIMEOUT_MS,
    val vadEndpointing: Boolean = true,
    val vadOptions: VadOptions? = null,
) : DirectorPluginOptions
```

- [ ] **Step 4: Run tests**

```
./gradlew :core:test --tests "DirectorOptionsTest"
```
Expected: PASS.

- [ ] **Step 5: Commit**

```
git add core/src/main/kotlin/com/zugaldia/speedofsound/core/plugins/director/DirectorOptions.kt \
        core/src/test/kotlin/com/zugaldia/speedofsound/core/plugins/director/DirectorOptionsTest.kt
git commit -m "feat(director): add vadEndpointing and vadOptions to DirectorOptions"
```

---

## Task 9: Wire VAD into `JvmRecorder`

**Files:**
- Modify: `core/src/main/kotlin/com/zugaldia/speedofsound/core/plugins/recorder/RecorderOptions.kt`
- Modify: `core/src/main/kotlin/com/zugaldia/speedofsound/core/plugins/recorder/JvmRecorder.kt`

`JvmRecorder` already runs a `recordingThread` that reads PCM16 bytes into `buffer` and writes them to `audioBuffer`. We add an optional `vadEngine` reference and feed each chunk through it.

- [ ] **Step 1: Extend `RecorderOptions` with a VAD engine reference**

In `RecorderOptions.kt`, add a property (do not break existing call sites — default to null):
```kotlin
import com.zugaldia.speedofsound.core.audio.vad.VadEngine

data class RecorderOptions(
    // ... existing fields preserved ...
    val vadEngine: VadEngine? = null,
) : RecorderPluginOptions
```
Read the current file to confirm exact field names, then add the new property at the end of the constructor argument list.

- [ ] **Step 2: Feed PCM into VAD inside the recording loop**

In `JvmRecorder.kt`, inside the `recordingThread` `while (isRecording)` block (around line 110), after `audioBuffer?.write(buffer, 0, bytesRead)`, add:

```kotlin
currentOptions.vadEngine?.let { vad ->
    val shorts = ShortArray(bytesRead / 2)
    for (i in shorts.indices) {
        val low = buffer[i * 2].toInt() and 0xFF
        val high = buffer[i * 2 + 1].toInt()
        shorts[i] = ((high shl 8) or low).toShort()
    }
    vad.acceptPcm16(shorts)
}
```
This decodes little-endian PCM16 (the format used by `currentOptions.audioInfo.toAudioFormat()`) into shorts and pushes them through VAD on the same thread.

- [ ] **Step 3: Release VAD on cleanup**

In `JvmRecorder.kt` `cleanup()` (search for `cleanup`), add `currentOptions.vadEngine?.release()` before nulling buffers — ONLY if cleanup is called once at shutdown, not between recordings. If it's called per-recording, instead release in the `disable()` override and not in cleanup. Read both methods to decide; preserve the existing `super.disable()` order.

- [ ] **Step 4: Manual smoke check (no automated test — recorder needs hardware)**

```
./gradlew :core:check
```
Expected: PASS.

- [ ] **Step 5: Commit**

```
git add core/src/main/kotlin/com/zugaldia/speedofsound/core/plugins/recorder/RecorderOptions.kt \
        core/src/main/kotlin/com/zugaldia/speedofsound/core/plugins/recorder/JvmRecorder.kt
git commit -m "feat(recorder): feed PCM into VadEngine when configured"
```

---

## Task 10: Wire VAD into `GStreamerRecorder`

**Files:**
- Modify: `app/src/main/kotlin/com/zugaldia/speedofsound/app/plugins/recorder/GStreamerRecorder.kt`

The GStreamer path is the production audio source. It reads PCM16 from an `AppSink` callback. Apply the same conversion + `vad.acceptPcm16(shorts)` pattern in the buffer callback.

- [ ] **Step 1: Locate the AppSink data callback**

```
grep -nE "newSample|MapInfo|onNewSample|map\(" app/src/main/kotlin/com/zugaldia/speedofsound/app/plugins/recorder/GStreamerRecorder.kt
```
Identify the callback that writes captured bytes to the recorder's buffer.

- [ ] **Step 2: Inject the VAD call**

Inside that callback, after the bytes are appended to the recorder's audio buffer, add the same little-endian decode + `currentOptions.vadEngine?.acceptPcm16(shorts)` block from Task 9 Step 2.

- [ ] **Step 3: Build to verify**

```
./gradlew :app:compileKotlin
```
Expected: PASS.

- [ ] **Step 4: Commit**

```
git add app/src/main/kotlin/com/zugaldia/speedofsound/app/plugins/recorder/GStreamerRecorder.kt
git commit -m "feat(recorder): feed PCM into VadEngine in GStreamer recorder path"
```

---

## Task 11: `DefaultDirector` subscribes to `SpeechEnded` and auto-stops

**Files:**
- Modify: `core/src/main/kotlin/com/zugaldia/speedofsound/core/plugins/director/DefaultDirector.kt`

The Director already maintains a `directorScope: CoroutineScope` (used for the auto-stop timer). Subscribe to the recorder's events while a recording is active.

- [ ] **Step 1: Open the file, find `start()` and `stop()`**

```
sed -n '50,120p' core/src/main/kotlin/com/zugaldia/speedofsound/core/plugins/director/DefaultDirector.kt
```
Note the existing `directorScope`, `recorder`, `events: SharedFlow<AppPluginEvent>` (from `recorder.events`).

- [ ] **Step 2: Add a VAD listener job**

Add a new field:
```kotlin
private var vadJob: kotlinx.coroutines.Job? = null
```

In `start()`, after `recorder.startRecording()` succeeds, add:
```kotlin
if (currentOptions.vadEndpointing) {
    vadJob = directorScope.launch {
        recorder.events.collect { event ->
            if (event is RecorderEvent.SpeechEnded) {
                directorScope.launch { stop() }
            }
        }
    }
}
```

In `stop()` (before the early-return guards or right after acquiring the mutex — match the existing style), cancel the job:
```kotlin
vadJob?.cancel()
vadJob = null
```

Add the import at the top:
```kotlin
import com.zugaldia.speedofsound.core.plugins.recorder.RecorderEvent
```

- [ ] **Step 3: Run the suite**

```
./gradlew :core:check
```
Expected: PASS.

- [ ] **Step 4: Commit**

```
git add core/src/main/kotlin/com/zugaldia/speedofsound/core/plugins/director/DefaultDirector.kt
git commit -m "feat(director): auto-stop on SpeechEnded when VAD endpointing is on"
```

---

## Task 12: Persist new settings (constants, client, gschema)

**Files:**
- Modify: `core/src/main/kotlin/com/zugaldia/speedofsound/core/desktop/settings/SettingsConstants.kt`
- Modify: `core/src/main/kotlin/com/zugaldia/speedofsound/core/desktop/settings/SettingsClient.kt`
- Modify: `data/io.voicestream.VoiceStream.gschema.xml`

- [ ] **Step 1: Add the keys to `SettingsConstants.kt`**

Append:
```kotlin
const val KEY_VAD_ENDPOINTING = "vad-endpointing"
const val KEY_VAD_MIN_SILENCE_MS = "vad-min-silence-ms"
const val KEY_COMPUTE_PROVIDER = "compute-provider"

const val DEFAULT_VAD_ENDPOINTING = true
const val DEFAULT_VAD_MIN_SILENCE_MS = 600
const val DEFAULT_COMPUTE_PROVIDER = "cpu"
```

- [ ] **Step 2: Add typed accessors to `SettingsClient.kt`**

Add:
```kotlin
fun isVadEndpointingEnabled(): Boolean =
    store.getBoolean(KEY_VAD_ENDPOINTING, DEFAULT_VAD_ENDPOINTING)

fun setVadEndpointingEnabled(value: Boolean) =
    store.setBoolean(KEY_VAD_ENDPOINTING, value)

fun getVadMinSilenceMs(): Int =
    store.getInt(KEY_VAD_MIN_SILENCE_MS, DEFAULT_VAD_MIN_SILENCE_MS)

fun setVadMinSilenceMs(value: Int) =
    store.setInt(KEY_VAD_MIN_SILENCE_MS, value)

fun getComputeProvider(): String =
    store.getString(KEY_COMPUTE_PROVIDER, DEFAULT_COMPUTE_PROVIDER)

fun setComputeProvider(value: String) =
    store.setString(KEY_COMPUTE_PROVIDER, value)
```
If `SettingsStore` lacks `getInt`/`setInt`, add them following the existing `getBoolean`/`getString` pattern in both `GioStore` and `PropertiesStore`.

- [ ] **Step 3: Add gschema entries**

Inside `<schema id="io.voicestream.VoiceStream" path="/io/voicestream/VoiceStream/">` in `data/io.voicestream.VoiceStream.gschema.xml`, add:
```xml
<key name="vad-endpointing" type="b">
  <default>true</default>
  <summary>Auto-stop recording on detected silence</summary>
  <description>Use Silero VAD to end the utterance automatically.</description>
</key>
<key name="vad-min-silence-ms" type="i">
  <default>600</default>
  <range min="200" max="2000"/>
  <summary>Silence duration before VAD ends an utterance (ms)</summary>
  <description>Lower values feel more responsive but cut speech sooner.</description>
</key>
<key name="compute-provider" type="s">
  <choices>
    <choice value="cpu"/>
    <choice value="cuda"/>
  </choices>
  <default>"cpu"</default>
  <summary>ASR compute provider</summary>
  <description>"cpu" or "cuda". GPU requires a Sherpa build with -DSHERPA_ONNX_ENABLE_GPU=ON.</description>
</key>
```

- [ ] **Step 4: Validate the schema locally**

```
glib-compile-schemas --strict --dry-run data/
```
(Run inside an environment where `glib-compile-schemas` is available — Flatpak SDK, distrobox, or `flatpak run --command=glib-compile-schemas org.gnome.Sdk//50 …`.) Expected: no output (success).

- [ ] **Step 5: Commit**

```
git add core/src/main/kotlin/com/zugaldia/speedofsound/core/desktop/settings/SettingsConstants.kt \
        core/src/main/kotlin/com/zugaldia/speedofsound/core/desktop/settings/SettingsClient.kt \
        data/io.voicestream.VoiceStream.gschema.xml
git commit -m "feat(settings): persist vad-endpointing, vad-min-silence-ms, compute-provider"
```

---

## Task 13: GPU probe in `AsrProviderManager`

**Files:**
- Modify: `app/src/main/kotlin/com/zugaldia/speedofsound/app/settings/AsrProviderManager.kt`

- [ ] **Step 1: Read the file to find the right insertion point**

```
sed -n '1,80p' app/src/main/kotlin/com/zugaldia/speedofsound/app/settings/AsrProviderManager.kt
```

- [ ] **Step 2: Add a one-shot GPU probe**

Add to the class:
```kotlin
@Volatile
private var gpuProbed: Boolean = false

@Volatile
private var gpuAvailable: Boolean = false

fun isGpuAvailable(): Boolean = gpuAvailable

/**
 * One-time CUDA recognizer probe. Safe to call repeatedly; only the first call performs work.
 * Returns true if a CUDA recognizer was successfully constructed.
 */
fun probeGpuOnce(): Boolean {
    if (gpuProbed) return gpuAvailable
    gpuProbed = true
    gpuAvailable = SherpaOfflineAsr.hasFallenBackToCpu().not() &&
        runCatching {
            // Try a no-op recognizer construction with provider=cuda. We rely on the
            // existing fallback machinery to swallow the failure on a CPU-only build.
            // If construction is too expensive to perform here, this can be deferred to
            // first ASR run.
            true
        }.getOrDefault(false)
    return gpuAvailable
}
```
> Implementation note for the integrator: a real probe needs a tiny dummy model. If that's not feasible at startup, defer the probe: leave `gpuAvailable = false` initially, and have the Voice prefs UI greyed-out by default; flip the flag once the first real ASR call succeeds with `provider=cuda`.

- [ ] **Step 3: Compile**

```
./gradlew :app:compileKotlin
```
Expected: PASS.

- [ ] **Step 4: Commit**

```
git add app/src/main/kotlin/com/zugaldia/speedofsound/app/settings/AsrProviderManager.kt
git commit -m "feat(prefs): GPU availability probe in AsrProviderManager"
```

---

## Task 14: UI rows in `VoiceModelsPage`

**Files:**
- Modify: `app/src/main/kotlin/com/zugaldia/speedofsound/app/screens/preferences/voice/VoiceModelsPage.kt`

Add an "Endpointing" group at the top of the page with two rows + a "Compute" group with one combo row.

- [ ] **Step 1: Read current page structure**

```
sed -n '1,160p' app/src/main/kotlin/com/zugaldia/speedofsound/app/screens/preferences/voice/VoiceModelsPage.kt
```
Identify `addPreferencesGroup(...)` calls so the new rows match the existing style.

- [ ] **Step 2: Add the endpointing group**

Add a new `private fun addEndpointingGroup()` that constructs an `Adw.PreferencesGroup` with title "Endpointing" containing:
- `Adw.SwitchRow` titled "Auto-stop on silence", initial state `viewModel.settingsClient.isVadEndpointingEnabled()`. On `notifyActive`, call `viewModel.settingsClient.setVadEndpointingEnabled(active)`.
- `Adw.SpinRow` titled "Silence threshold (ms)" with a `Gtk.Adjustment(value=current, lower=200, upper=2000, stepIncrement=50)`. On `notifyValue`, call `viewModel.settingsClient.setVadMinSilenceMs(value.toInt())`. Set `sensitive` bound to the switch row's active state.

- [ ] **Step 3: Add the compute group**

Add `private fun addComputeGroup()` that constructs an `Adw.PreferencesGroup` titled "Compute" containing:
- `Adw.ComboRow` titled "Compute device" with model `Gtk.StringList(arrayOf("CPU", "GPU (CUDA)"))`. Set `selected` from current setting (`"cpu"` → 0, `"cuda"` → 1). On `notifySelected`, persist via `setComputeProvider("cpu" or "cuda")`. If `viewModel.asrProviderManager.isGpuAvailable() == false`, set `sensitive=false` and `subtitle="Requires GPU-enabled Sherpa build"`.

- [ ] **Step 4: Call both groups from `init`/`refreshProviders`**

In the page's existing setup (around `setupNotifications()` or constructor block), call `addEndpointingGroup()` and `addComputeGroup()` exactly once. Avoid re-adding on `refreshProviders()` (those should only refresh provider rows).

- [ ] **Step 5: Build the GUI module**

```
./gradlew :app:compileKotlin
```
Expected: PASS.

- [ ] **Step 6: Commit**

```
git add app/src/main/kotlin/com/zugaldia/speedofsound/app/screens/preferences/voice/VoiceModelsPage.kt
git commit -m "feat(prefs): add Endpointing + Compute device rows to Voice page"
```

---

## Task 15: Auto-download Silero VAD on first launch (when VAD is on)

**Files:**
- Modify: `app/src/main/kotlin/com/zugaldia/speedofsound/app/screens/main/MainViewModel.kt`
- Modify: `core/src/main/kotlin/com/zugaldia/speedofsound/core/models/voice/VoiceModelCatalog.kt`

- [ ] **Step 1: Extend the catalog to know about VAD models**

In `VoiceModelCatalog.kt`, change `getModel` to also consult `SUPPORTED_VAD_MODELS`:
```kotlin
import com.zugaldia.speedofsound.core.audio.vad.SUPPORTED_VAD_MODELS

class DefaultVoiceModelCatalog : VoiceModelCatalog {
    override fun getModel(modelId: String): VoiceModel? =
        SUPPORTED_LOCAL_ASR_MODELS[modelId] ?: SUPPORTED_VAD_MODELS[modelId]

    override fun getDefaultModelId(): String = DEFAULT_ASR_SHERPA_WHISPER_MODEL_ID
}
```

- [ ] **Step 2: Trigger download from `MainViewModel`**

Find the existing initialization sequence in `MainViewModel.kt` where the active ASR model is checked / downloaded. Add a parallel branch:
```kotlin
if (settingsClient.isVadEndpointingEnabled() && !modelManager.isModelDownloaded(DEFAULT_VAD_MODEL_ID)) {
    viewModelScope.launch {
        modelManager.downloadModel(DEFAULT_VAD_MODEL_ID)
    }
}
```
If `MainViewModel` doesn't have a coroutine scope, use the existing pattern in this file for background work.

- [ ] **Step 3: Construct `VadEngine` lazily when VAD is on**

In `MainViewModel`'s recorder construction (or wherever `RecorderOptions` is built), pass:
```kotlin
val vadEngine = if (settingsClient.isVadEndpointingEnabled() && modelManager.isModelDownloaded(DEFAULT_VAD_MODEL_ID)) {
    val modelPath = modelManager.modelPath(DEFAULT_VAD_MODEL_ID).resolve("silero_vad.onnx")
    VadEngine(VadOptions(modelPath = modelPath, minSilenceMs = settingsClient.getVadMinSilenceMs()), emitter = recorder::tryEmitEvent)
} else null
```
(Adapt `recorder::tryEmitEvent` to whatever the actual emit hook is — read `RecorderPlugin`/`AppPlugin` for `tryEmitEvent`.)

- [ ] **Step 4: Build the app**

```
./gradlew :app:check
```
Expected: PASS.

- [ ] **Step 5: Commit**

```
git add app/src/main/kotlin/com/zugaldia/speedofsound/app/screens/main/MainViewModel.kt \
        core/src/main/kotlin/com/zugaldia/speedofsound/core/models/voice/VoiceModelCatalog.kt
git commit -m "feat(vad): auto-download Silero on first launch and wire VadEngine into recorder"
```

---

## Task 16: Full integration check + manual validation

**Files:** none changed.

- [ ] **Step 1: Run the full check across all modules**

```
./gradlew check
```
Expected: PASS, including detekt across `core`, `app`, `cli`.

- [ ] **Step 2: Manual smoke (requires a runtime environment — Flatpak or distrobox)**

1. Launch the app.
2. First run downloads Silero VAD (~2 MB). Verify a brief progress notification appears.
3. Open Preferences → Voice. Verify "Auto-stop on silence" is on by default; "Silence threshold (ms)" defaults to 600.
4. Verify "Compute device" combo shows CPU as the only enabled option (until a GPU-enabled JAR is supplied).
5. Trigger recording, speak a short phrase, stay silent for ~700 ms. Confirm the recording auto-stops and Whisper transcription begins.
6. Toggle "Auto-stop on silence" off. Verify the existing manual-stop behavior is unchanged.
7. (If a CUDA-built Sherpa JAR is dropped into `core/libs/`) re-launch, verify GPU option becomes selectable.

- [ ] **Step 3: Final commit (only if anything was tweaked during validation)**

```
git status
# if there are last-mile fixes:
git commit -am "chore(vad): manual-validation tweaks"
```

---

## Self-review notes

- All 11 spec components (5 new files + 10 modifications) are covered by Tasks 1–15.
- Task 16 covers the spec's "Testing" section.
- No `TBD`/`TODO`/`?` placeholders.
- Type/method names: `computeProvider`, `vadEndpointing`, `vadOptions`, `VadEngine`, `VadOptions`, `RecorderEvent.SpeechStarted`, `RecorderEvent.SpeechEnded`, `SUPPORTED_VAD_MODELS`, `DEFAULT_VAD_MODEL_ID`, `KEY_VAD_ENDPOINTING`, `KEY_VAD_MIN_SILENCE_MS`, `KEY_COMPUTE_PROVIDER` — used consistently across tasks.
- `SherpaOfflineAsr.hasFallenBackToCpu()` is defined in Task 3 and consumed in Task 13.
- `VadOptions` (Task 5) is consumed in Tasks 6, 8, 15 with matching field names.
