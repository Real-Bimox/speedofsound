# VoiceStream — Session Resume Guide

**Last updated:** 2026-05-01. **Current branch:** `main`. **Current version:** `0.15.0`. **Last verified working commit:** `82f20c5` (v0.15.0: VAD-15 + GPU JAR + AppImage; `:core:check :cli:check :app:compileKotlin :app:detekt` green for both CPU and `-Pvoicestream.gpu=true`; both `make appimage` and `make appimage-gpu` produce self-contained AppImages, 201 MB and 423 MB respectively).

This document captures everything needed to pick up VoiceStream development after a context loss. Read this first; then drill into the spec/plan docs and `git log` for detail.

---

## 1 — What this fork is

- **Forked from:** [zugaldia/speedofsound](https://github.com/zugaldia/speedofsound) at commit `0b26a64` (v0.13.0 + Gradle 9.4.1 upgrade)
- **Display name:** VoiceStream
- **Application ID:** `io.voicestream.VoiceStream`
- **Source package:** `com.zugaldia.speedofsound.*` — **intentionally preserved** so future upstream merges work cleanly
- **Repo location:** `/var/home/bahram/local-repos/speedofsound` (kept the original directory name)
- **License:** MIT (unchanged)

---

## 2 — What's been done

Three coherent groups of commits since the upstream baseline (`0b26a64`):

### A. Rebrand (3 commits)
- `46aeb3a` fork: rebrand Speed of Sound → VoiceStream — text + file renames + helper scripts (`scripts/rebrand.sh`, `scripts/setup-distrobox.sh`)
- `25cd12c` fix(director): drop raw transcription from LLM error log — PII leak found in pre-build security audit
- `c0ba4ca` docs: add VAD endpointing + CPU/GPU compute-selector spec and plan

### B. VAD endpointing + CPU/GPU compute selector (30 commits, tasks VAD-1 through VAD-16)
Implements: silent CPU fallback when CUDA recognizer fails; `ComputeProvider { CPU, CUDA }` enum; Silero VAD-driven auto-stop; UI toggle in Voice prefs. Spec + plan committed under `docs/superpowers/`.

Read `git log --oneline 0b26a64..HEAD` for the full list. Each VAD-N task is one or more commits with `feat(...)`, `refactor(...)`, `fix(...)` prefixes and the convention `Addresses code-review feedback on commit <SHA>` for fix-up commits.

### C. Distrobox setup polish (1 commit)
- `f1010dd` chore(distrobox): rename setup container to `voicestream-dev`

### Verification
- `:core:check` and `:cli:check` are green under JDK 25
- `:app:compileKotlin` and `:app:detekt` are green
- `:app:test` has a pre-existing `TextUtilsTest` `NoClassDefFoundError` (native library) that was failing before any of our work — not something we broke
- **End-to-end smoke test passed** on 2026-04-27 in distrobox: spoke "Hello, can you hear me? 1 2 3 3", Whisper Tiny transcribed correctly in ~360 ms, text was typed into the focused window via XDG Remote Desktop portal

---

## 3 — How to resume the runtime (if distrobox still exists)

Run the GUI:
```
distrobox enter voicestream-dev -- bash -lc 'cd /var/home/bahram/local-repos/speedofsound && make run'
```

Or drop into a dev shell:
```
distrobox enter voicestream-dev
cd /var/home/bahram/local-repos/speedofsound
make run                 # full GUI
make check               # full ./gradlew check
./gradlew :core:check    # core only
./cli.sh asr <wav>       # CLI ASR
```

The XDG Remote Desktop portal restore token is persisted in the GSettings/Properties store, so you won't be re-prompted for keyboard-injection permission.

---

## 4 — How to recreate everything from scratch

If the distrobox is gone, the host has been wiped, or you're on a different bazzite box:

```
# 1. Clone (with LFS — bundled Whisper Tiny ONNX is in LFS!)
cd /var/home/bahram/local-repos
git clone https://github.com/<your-fork>/voicestream.git speedofsound  # or pull this fork
cd speedofsound

# 2. One-shot distrobox setup (idempotent — safe to re-run)
bash scripts/setup-distrobox.sh

# 3. Pull LFS objects from inside the distrobox
distrobox enter voicestream-dev -- bash -lc 'cd /var/home/bahram/local-repos/speedofsound && git lfs install --local && git lfs pull'

# 4. Build & run
distrobox enter voicestream-dev -- bash -lc 'cd /var/home/bahram/local-repos/speedofsound && make run'
```

`scripts/setup-distrobox.sh` (consolidated 2026-04-27) installs:
- Fedora 43 distrobox `voicestream-dev`
- JDK 25 via Adoptium Temurin tarball into `/opt/temurin-25` (NOT `java-latest-openjdk` — that ships JDK 26 on Fedora 43, which Gradle's `jvmToolchain(25)` will reject)
- GTK4 + libadwaita (devel + runtime)
- GStreamer 1 (devel + plugins-base + plugins-good + plugins-bad-free + pipewire-gstreamer — runtime plugins are required for `autoaudiosrc`/`pipewiresrc`)
- ALSA + PipeWire devel
- git, git-lfs, make
- `/etc/profile.d/voicestream-jdk.sh` exporting `JAVA_HOME`/`PATH` so login shells pick up Temurin

---

## 5 — Bazzite-specific gotchas (the painful lessons learned)

1. **Bazzite is immutable** (Fedora 44 atomic — verified 2026-05-01). Do not `dnf install` on the host; only inside distrobox/podman/Flatpak/Homebrew. Per `/var/home/bahram/local-repos/readme-p920.md` Rule 1.
2. **Fedora's `java-latest-openjdk` is JDK 26** as of April 2026. The project requires exactly JDK 25 (`jvmToolchain(25)` in `buildSrc/.../kotlin-jvm.gradle.kts`); Gradle's auto-detection won't fall back. Solution: install Temurin 25 separately (script does this).
3. **Whisper Tiny ONNX is Git LFS.** A plain `git clone` puts 133-byte LFS pointers in `core/src/main/resources/models/asr/`, which fails at runtime with "Model file is a Git LFS pointer". Run `git lfs install && git lfs pull` AFTER cloning.
4. **GStreamer needs runtime plugins, not just `-devel`.** `gstreamer1-devel` + `gstreamer1-plugins-base-devel` only give headers. The actual `autoaudiosrc`/`pipewiresrc`/`pulsesrc` plugins live in `gstreamer1-plugins-base` + `gstreamer1-plugins-good` + `pipewire-gstreamer`. Without them: `Failed to create autoaudiosrc element` at startup.
5. **PipeWire & Wayland forward automatically** through distrobox (verified working on Bazzite Kinoite 43). No flag needed.
6. **Nvidia/EGL libs are not in the distrobox** — you'll see harmless `libEGL warning: pci id for fd N: 10de:..., driver (null)` lines. GTK4 falls back to software rendering for any GL bits; the rest of the app is unaffected. To fix later: enable `nvidia-container-toolkit` for distrobox (out of scope so far).
7. **XDG portal "App info not found for io.voicestream.VoiceStream"** at startup is cosmetic — the portal still works via fallback. Fixed by `make install` (meson) or running as a Flatpak.

---

## 6 — Open issues / next steps (in priority order)

### ✓ Closed in v0.15.0
- VAD-15: dedicated `VadProvider` enum; `VoiceModel.provider` widened to `SelectableProvider` so VAD models stop borrowing `AsrProvider.SHERPA_WHISPER`.
- GPU Sherpa native-lib JAR: `scripts/build-gpu-jar.sh` packages the CUDA 12 / cuDNN 9 GPU bundle; `-Pvoicestream.gpu=true` selects it.
- Self-contained AppImage distribution: `make appimage` → CPU (~201 MB), `make appimage-gpu` → GPU (~423 MB).
- Rebrand bug fix in `data/io.voicestream.VoiceStream.metainfo.xml.in` (unreachable `voicestream.io` URLs and wrong fork repo path).

### High value, small effort
*(none currently)*

### Medium value, larger effort
2. **Smoke-test GPU AppImage end-to-end on this host (RTX 3090 / A6000).** Build the GPU artifact (`make appimage-gpu`), launch on a host with NVIDIA driver R535+ and CUDA 12 / cuDNN 9 in `LD_LIBRARY_PATH`, set ComputeProvider=CUDA in Voice prefs, transcribe; confirm Sherpa logs `compute_provider=cuda` instead of falling back to CPU. Also test on a CPU-only host to confirm graceful fallback (`SherpaOfflineAsr` demotes silently per VAD-3).
3. **Test Whisper Turbo** end-to-end. Currently only bundled Whisper Tiny is exercised. From the running app: Preferences → Library → download `sherpa-onnx-whisper-turbo` (989 MB, multilingual, INT8) → Voice prefs → make Turbo the active provider.

### Low value but useful for daily ergonomics
4. **Fix XDG portal app-id warning** by running `make install` (writes `.desktop` + metainfo into `~/.local/share/applications/`). Or just package as Flatpak — that fixes everything at once.
5. **Streaming ASR** (Option B from the original three-options proposal) — adds a `SherpaOnlineAsr` plugin using Zipformer/Paraformer for live partial text. Would coexist with Whisper Turbo as a "Live mode" alternative. Multi-day project.

### Distribution
6. **Build the Flatpak** for daily use. The manifest `io.voicestream.VoiceStream.yml` is ready. Steps:
   ```
   flatpak install --user --noninteractive --or-update flathub \
     org.flatpak.Builder \
     org.gnome.Sdk//50 \
     org.gnome.Platform//50 \
     org.freedesktop.Sdk.Extension.openjdk25//25.08
   flatpak run org.flatpak.Builder \
     --force-clean --user --install --install-deps-from=flathub \
     --repo=repo builddir io.voicestream.VoiceStream.yml
   flatpak run io.voicestream.VoiceStream
   ```
   ~3 GB SDK download first time, ~15-30 min build.

---

## 7 — Key file map

| Concern | Location |
|---|---|
| App ID, display name, version | `core/src/main/kotlin/com/zugaldia/speedofsound/core/Constants.kt` |
| Flatpak manifest | `io.voicestream.VoiceStream.yml` |
| GSettings schema | `data/io.voicestream.VoiceStream.gschema.xml` |
| Snap manifest | `snap/snapcraft.yaml` |
| Build conventions plugin | `buildSrc/src/main/kotlin/kotlin-jvm.gradle.kts` |
| ASR plugin base | `core/src/main/kotlin/com/zugaldia/speedofsound/core/plugins/asr/AsrPluginOptions.kt` (contains `ComputeProvider` enum) |
| ASR Sherpa offline | `core/src/main/kotlin/com/zugaldia/speedofsound/core/plugins/asr/SherpaOfflineAsr.kt` (CUDA fallback lives here) |
| VAD engine | `core/src/main/kotlin/com/zugaldia/speedofsound/core/audio/vad/VadEngine.kt` |
| VAD model catalog | `core/src/main/kotlin/com/zugaldia/speedofsound/core/audio/vad/SileroVadModels.kt` (uses `ArchiveFormat.SINGLE_FILE` for raw `.onnx` download) |
| Archive format dispatch | `core/src/main/kotlin/com/zugaldia/speedofsound/core/models/voice/VoiceModel.kt` (`ArchiveFormat` enum) and `ModelManager.kt` / `ModelFileManager.copyRawComponent` |
| VAD provider             | `core/src/main/kotlin/com/zugaldia/speedofsound/core/audio/vad/VadProvider.kt` (separate from `AsrProvider`) |
| GPU JAR build script     | `scripts/build-gpu-jar.sh` (downloads + verifies + repackages Sherpa CUDA tarball) |
| GPU build flag           | `-Pvoicestream.gpu=true` selects the GPU native-lib JAR in `core/build.gradle.kts` |
| AppImage targets         | `make appimage` (CPU) / `make appimage-gpu` (GPU) — outputs land in `app/build/jpackage/` |
| Director | `core/src/main/kotlin/com/zugaldia/speedofsound/core/plugins/director/DefaultDirector.kt` (VAD subscription lives here) |
| JVM recorder | `core/src/main/kotlin/com/zugaldia/speedofsound/core/plugins/recorder/JvmRecorder.kt` |
| GStreamer recorder | `app/src/main/kotlin/com/zugaldia/speedofsound/app/plugins/recorder/GStreamerRecorder.kt` |
| Voice prefs UI | `app/src/main/kotlin/com/zugaldia/speedofsound/app/screens/preferences/voice/VoiceModelsPage.kt` |
| Settings client + accessors | `core/src/main/kotlin/com/zugaldia/speedofsound/core/desktop/settings/SettingsClient.kt` |
| Spec | `docs/superpowers/specs/2026-04-27-vad-endpointing-and-compute-selector-design.md` |
| Plan | `docs/superpowers/plans/2026-04-27-vad-endpointing-and-compute-selector.md` |
| Setup script | `scripts/setup-distrobox.sh` |

---

## 8 — Useful commands

```
# Build inside distrobox
distrobox enter voicestream-dev -- bash -lc 'cd /var/home/bahram/local-repos/speedofsound && ./gradlew check --console=plain'

# Run with verbose logging (useful for debugging the recorder/VAD chain)
distrobox enter voicestream-dev -- bash -lc 'cd /var/home/bahram/local-repos/speedofsound && SOS_DISABLE_GIO_STORE=true ./gradlew :app:run --console=plain --info'

# Force re-extract bundled model (e.g., after `git lfs pull`)
rm -rf core/build/resources app/build/resources

# Check VAD/ASR settings without launching the GUI (uses Properties backend)
cat ~/.local/share/voicestream/voicestream.properties

# Tear down + rebuild distrobox from scratch
distrobox stop voicestream-dev
distrobox rm voicestream-dev
bash scripts/setup-distrobox.sh
```

---

## 9 — Commit conventions used in this fork

- `feat(scope): ...` — new functionality
- `fix(scope): ...` — bug fix
- `refactor(scope): ...` — code shape change, no behavior change
- `docs(scope): ...` — documentation only
- `chore(scope): ...` — tooling/config that doesn't fit elsewhere
- Fix-up commits append `Addresses code-review feedback on commit <SHA>` so the review trail is grep-able

No `Co-Authored-By` lines (per ground rules in `readme-p920.md`).
