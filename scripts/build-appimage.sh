#!/bin/bash

# Build an AppImage from the jpackage app-image output (which is NOT a valid AppImage, but it's close).
# The version is always read from the VERSION file.
# Requires appimagetool to be available in PATH.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
APP_DIR="$ROOT_DIR/app/build/jpackage/voicestream"
OUTPUT_DIR="$ROOT_DIR/app/build/jpackage"
VERSION="$(tr -d '[:space:]' < "$ROOT_DIR/VERSION")"
ARCH="$(uname -m)"

if [ ! -d "$APP_DIR" ]; then
    echo "Error: jpackage app-image directory not found at $APP_DIR"
    echo "Run 'make jpackage-app-image' first."
    exit 1
fi

# AppRun: symlink to the jpackage native launcher
ln -sf bin/voicestream "$APP_DIR/AppRun"

# Desktop file under usr/share/applications (for appstreamcli and FHS convention),
# symlinked to the AppDir root (required by the AppImage spec)
mkdir -p "$APP_DIR/usr/share/applications"
cat > "$APP_DIR/usr/share/applications/ai.nexiant.voicestream.desktop" << 'DESKTOP'
[Desktop Entry]
Version=1.0
Type=Application
Name=Nexiant Voice
Comment=Voice typing for the Linux desktop
Categories=Utility;Accessibility;
Keywords=voice;typing;dictation;transcription;speech;microphone;whisper;
Icon=voicestream
Exec=voicestream
Terminal=false
StartupNotify=true
DESKTOP
ln -sf usr/share/applications/ai.nexiant.voicestream.desktop \
    "$APP_DIR/ai.nexiant.voicestream.desktop"

# Icon at root (appimagetool requires it here, matching Icon= above)
ln -sf lib/voicestream.png "$APP_DIR/voicestream.png"

# .DirIcon: required by AppDir spec for thumbnailers and file managers
ln -sf lib/voicestream.png "$APP_DIR/.DirIcon"

# AppStream metadata
mkdir -p "$APP_DIR/usr/share/metainfo"
cp "$ROOT_DIR/data/ai.nexiant.voicestream.metainfo.xml.in" \
    "$APP_DIR/usr/share/metainfo/ai.nexiant.voicestream.appdata.xml"

# GPU vs CPU is signaled by the caller via VOICESTREAM_GPU=1 (the Gradle build
# already picked the GPU Sherpa JAR via -Pvoicestream.gpu=true; this just adjusts
# the output filename). Sherpa's GPU build expects host-supplied CUDA 12 +
# cuDNN 9 in LD_LIBRARY_PATH at runtime — we don't bundle those because they
# would push the AppImage past 1 GB and conflict with the user's own CUDA.
GPU_SUFFIX=""
if [ "${VOICESTREAM_GPU:-0}" = "1" ]; then
    GPU_SUFFIX="-gpu"
fi

# Build the AppImage. APPIMAGE_EXTRACT_AND_RUN=1 avoids a FUSE dependency.
OUTPUT="$OUTPUT_DIR/voicestream-${VERSION}${GPU_SUFFIX}-${ARCH}.AppImage"
APPIMAGE_EXTRACT_AND_RUN=1 appimagetool "$APP_DIR" "$OUTPUT"
echo "AppImage created: $OUTPUT"
