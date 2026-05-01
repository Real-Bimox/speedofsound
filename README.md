<div align="center">
  <img src="assets/logo/logo-square-512.png" width="160" alt="Nexiant Voice logo">
</div>

<div align="center">

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
![Platform](https://img.shields.io/badge/platform-Linux-lightgrey)

</div>

# Nexiant Voice

Voice typing for the Linux desktop.

## Features

- Offline, on-device transcription powered by Whisper, Parakeet, Canary, and more. No data leaves your machine.
- Multiple activation options: click the in-app button, use a global keyboard shortcut, or control the app from the system tray.
- Types the result directly into any focused application using Portals for wide desktop support (X11, Wayland).
- Multi-language support with switchable primary and secondary languages on the fly.
- Works out of the box with a built-in multilingual Whisper model. Download additional models from within the app to improve accuracy and language coverage.
- *Optional* text polishing with LLMs (Anthropic, Google, OpenAI), with support for a custom context and vocabulary.
- Supports self-hosted services like vLLM, Ollama, and llama.cpp (cloud services supported but not required).
- *Optional* GPU acceleration via Sherpa's CUDA build (CUDA 12 / cuDNN 9), with graceful CPU fallback when GPU isn't available.

## Install (Linux AppImage)

Nexiant Voice ships as a self-contained AppImage. No system installation, no admin rights, no system Java required — copy and run.

```bash
# CPU build (works on any glibc-compatible Linux x86_64)
chmod +x voicestream-0.16.0-x86_64.AppImage
./voicestream-0.16.0-x86_64.AppImage
```

For NVIDIA GPU acceleration, grab the `-gpu` variant. Requires NVIDIA driver R535+ AND CUDA 12 + cuDNN 9 installed on the host; falls back to CPU silently otherwise.

```bash
chmod +x voicestream-0.16.0-gpu-x86_64.AppImage
./voicestream-0.16.0-gpu-x86_64.AppImage
```

## Build from source

See [docs/RESUME.md](docs/RESUME.md) for the full setup. Quick path on a Fedora-flavored Linux:

```bash
bash scripts/setup-distrobox.sh
distrobox enter voicestream-dev -- bash -lc 'cd $(pwd) && make run'        # GUI
distrobox enter voicestream-dev -- bash -lc 'cd $(pwd) && make appimage'   # CPU AppImage
distrobox enter voicestream-dev -- bash -lc 'cd $(pwd) && make appimage-gpu' # GPU AppImage
```

## Built with

Nexiant Voice stands on the shoulders of these excellent open source projects:

- [Java-GI](https://codeberg.org/java-gi/java-gi) — GTK/GNOME bindings for Java, enabling access to native libraries (including LibAdwaita and GStreamer) via the modern Panama framework.
- [Sherpa ONNX](https://github.com/k2-fsa/sherpa-onnx) — On-device ASR (and more) using the performant ONNX Runtime, with pre-built models for Whisper, Parakeet, Canary, and many other popular models.
- [Whisper](https://github.com/openai/whisper) — OpenAI's open-source speech recognition model.

Additionally, Nexiant Voice uses Stargate — a JVM library that wraps [XDG Desktop Portals](https://flatpak.github.io/xdg-desktop-portal/docs/index.html) for high-level keyboard simulation on Linux. License credits for all bundled libraries live in `LICENSE` and the JAR `META-INF/`.

## Support and Contributions

For project information and support, visit [nexiant.ai](https://nexiant.ai).

## License

MIT — see [LICENSE](LICENSE).
