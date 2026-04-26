# VAD-Driven Endpointing + CPU/GPU Compute Selector

**Status:** Approved (auto mode)
**Date:** 2026-04-27
**Project:** VoiceStream (forked from Speed of Sound)
**Related:** Latency-optimization track for the rebranded fork

## Summary

Reduce perceived voice-typing latency by replacing the manual "press stop" gesture with Silero VAD-based endpointing, while keeping Whisper Turbo as the active offline ASR. Add a Voice-preferences toggle for CPU vs GPU inference; the GPU path becomes functional automatically once a GPU-enabled Sherpa JAR is present.

## Goals

1. Auto-stop recording on configurable silence (default 600 ms) using Sherpa's bundled `SileroVadModelConfig`.
2. Preserve Whisper Turbo as the default ASR; no accuracy regression vs the current offline pipeline.
3. Surface a CPU/GPU compute switch in Voice preferences. Probe GPU at startup; gracefully disable the option when the bundled Sherpa runtime is CPU-only.
4. Default the new behavior on (`vadEndpointing = true`) so users get the latency win without configuration.

## Non-goals (deferred to follow-up specs)

- Live partial transcription (would require a streaming model family — Zipformer or Paraformer — separate spec).
- A custom Sherpa build with `-DSHERPA_ONNX_ENABLE_GPU=ON`. The UI switch is wired now; activating GPU is a runtime concern handled by swapping the JAR in `core/libs/`.
- Pipeline parallelism between ASR / LLM / typing stages.
- Clipboard-paste fast path for typing.

## Architecture

### New components

| File | Purpose |
|---|---|
| `core/src/main/kotlin/com/zugaldia/speedofsound/core/audio/vad/VadEngine.kt` | Wraps Sherpa `Vad` + `SileroVadModelConfig`. Stateful: ingests PCM chunks, emits `SpeechStarted` / `SpeechEnded` via a `SharedFlow`. |
| `core/src/main/kotlin/com/zugaldia/speedofsound/core/audio/vad/VadOptions.kt` | Data class: `modelPath: Path`, `threshold: Float = 0.5f`, `minSilenceMs: Int = 600`, `minSpeechMs: Int = 250`. |
| `core/src/main/kotlin/com/zugaldia/speedofsound/core/audio/vad/SileroVadModels.kt` | Catalog entry for Silero VAD model (~2 MB). Mirrors the existing `SUPPORTED_LOCAL_ASR_MODELS` shape. |
| `core/src/test/kotlin/com/zugaldia/speedofsound/core/audio/vad/VadEngineTest.kt` | State-machine tests (silence → speech → silence). |

### Modified components

| File | Change |
|---|---|
| `core/.../plugins/recorder/RecorderPlugin.kt` | Add `SpeechStarted`, `SpeechEnded` to the existing `RecorderEvent` sealed hierarchy. |
| `core/.../plugins/recorder/JvmRecorder.kt` | Optional `vadEngine` constructor param. When present, push each captured chunk through VAD; emit speech events; on `SpeechEnded` close the buffer. |
| `app/.../plugins/recorder/GStreamerRecorder.kt` | Same treatment as JvmRecorder. |
| `core/.../plugins/director/DefaultDirector.kt` | Subscribe to `RecorderEvent.SpeechEnded` while recording; auto-call `stop()` when `currentOptions.vadEndpointing == true`. |
| `core/.../plugins/director/DirectorOptions.kt` | Add `vadEndpointing: Boolean = true`, `vadOptions: VadOptions?`. |
| `core/.../plugins/asr/SherpaOfflineAsr.kt` | Drop hardcoded `const val PROVIDER = "cpu"`. Read `currentOptions.computeProvider` (a `ComputeProvider` enum) and pass `computeProvider.name.lowercase()` to Sherpa's `setProvider`. On `ComputeProvider.CUDA` recognizer-creation failure: catch, log at INFO, set a per-process `gpuFallback` flag, retry with `ComputeProvider.CPU`. |
| `core/.../plugins/asr/AsrPluginOptions.kt` | Introduce `enum class ComputeProvider { CPU, CUDA }` (mirroring the existing `AsrProvider` pattern). Add `computeProvider: ComputeProvider = ComputeProvider.CPU` to the interface, with the default routed through a single `DEFAULT_COMPUTE_PROVIDER` constant. |
| `core/.../desktop/settings/SettingsClient.kt` + `SettingsConstants.kt` | Persist three new keys: `vad-endpointing` (bool), `vad-min-silence-ms` (int), `compute-provider` (string enum: `"cpu"` \| `"cuda"`). |
| `data/io.voicestream.VoiceStream.gschema.xml` | Schema entries for the three new keys. |
| `app/.../screens/preferences/voice/VoiceModelsPage.kt` | Two new rows: **Auto-stop on silence** (`AdwSwitchRow`) with a collapsible **Silence threshold (ms)** `AdwSpinRow` (range 200–2000, step 50); and **Compute device** (`AdwComboRow`) with options CPU/GPU. GPU option grayed-out + tooltip when probe fails. |
| `app/.../settings/AsrProviderManager.kt` | On startup, run a one-shot GPU probe in a coroutine (try creating a recognizer with `provider="cuda"`, immediately close). Cache the result. |

### Data flow (VAD on)

```
user trigger
  → Director.start
      → emit RecordingStarted
      → Recorder.startRecording(vad=engine)
      → VAD ingests chunks
          on SpeechStarted   → emit RecorderEvent.SpeechStarted (UI cue)
          on SpeechEnded     → Director.stop()
  → Director.stop
      → audioData = Recorder.stopRecording()
      → emit TranscriptionStarted
      → SherpaOfflineAsr.transcribe(audioData)   [CPU or GPU per settings]
      → optional LLM polish
      → typeText
      → emit PipelineCompleted
```

### Data flow (VAD off — unchanged)

The existing manual-stop path is preserved verbatim. `VadEngine` is never constructed; `Recorder.startRecording(vad=null)` runs the legacy code path.

## Error handling

| Failure | Response |
|---|---|
| Silero VAD model missing on disk | Auto-fallback to manual stop, in-app toast "VAD model not downloaded — using manual stop". User can download from Library page. |
| GPU recognizer creation throws | Log at INFO, set process-local `gpuFallback = true`, recreate with `"cpu"`. UI compute combo reverts selection to CPU and shows tooltip "GPU unavailable in current Sherpa build". |
| VAD never fires `SpeechEnded` | Existing `maxRecordingMs` auto-stop timer (already present in `DefaultDirector`) takes over as backstop. |
| Recorder receives PCM at unexpected sample rate | VadEngine asserts `audioInfo.sampleRate == 16000` (Silero requirement); on mismatch, log error and disable VAD for that session. |

## Settings & defaults

| Key | Type | Default | Notes |
|---|---|---|---|
| `vad-endpointing` | bool | `true` | New default; users opt out via toggle. |
| `vad-min-silence-ms` | int | `600` | Range 200–2000 in UI. |
| `compute-provider` | string | `"CPU"` | Persisted as the `ComputeProvider` enum's `name` (`"CPU"` or `"CUDA"`). UI greys out non-functional options. |

The Silero VAD model is auto-downloaded on first launch when `vad-endpointing` is true and the file is missing. ~2 MB; pulled from the Sherpa ONNX model zoo using the existing `ModelDownloader` + `ChecksumVerifier`.

## Testing

- **Unit:** `VadEngine` transitions (silence-only chunks, speech-only chunks, mixed); `DirectorOptions` default propagation; `SherpaOfflineAsr` GPU-fallback path with a mock recognizer factory.
- **Manual:** record a short utterance — verify auto-stop fires within ~600 ms of silence; toggle VAD off — verify manual-stop unchanged; toggle compute combo on a CPU-only build — verify GPU is disabled with tooltip.

## Out-of-scope follow-ups

1. **GPU-enabled Sherpa JAR** — separate workstream. Build Sherpa from source with `-DSHERPA_ONNX_ENABLE_GPU=ON`, drop into `core/libs/`. UI switch then becomes functional with no further changes.
2. **Streaming ASR** — add `SherpaOnlineAsr` plugin using Zipformer/Paraformer for live partial text. Coexists with Whisper Turbo as a separate model family in the existing catalog.
3. **Pipeline parallelism** — depends on streaming ASR; LLM polish on segment boundaries; typing on already-polished prefix.

## Estimated scope

~13 files touched, ~600–800 net lines of Kotlin (excluding tests).
