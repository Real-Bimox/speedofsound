#!/usr/bin/env bash
# Stand up a fully-working VoiceStream dev/run environment on bazzite (immutable host).
#
# Idempotent — safe to re-run. Creates a Fedora 43 distrobox named "voicestream-dev"
# with the complete toolchain: Adoptium Temurin JDK 25, GTK4, libadwaita, GStreamer
# (incl. runtime audio plugins), PipeWire, ALSA, git-lfs.
#
# Run on the HOST (not inside any container):
#   bash scripts/setup-distrobox.sh
# Then build & launch:
#   distrobox enter voicestream-dev -- bash -lc 'cd /var/home/bahram/local-repos/speedofsound && make run'
#
# This script does NOT modify the host system. Everything is contained in the distrobox.
set -euo pipefail

NAME="voicestream-dev"
IMAGE="registry.fedoraproject.org/fedora:43"
JDK_DOWNLOAD_URL="https://api.adoptium.net/v3/binary/latest/25/ga/linux/x64/jdk/hotspot/normal/eclipse"
JDK_INSTALL_DIR="/opt/temurin-25"
PROFILE_FILE="/etc/profile.d/voicestream-jdk.sh"

if ! command -v distrobox >/dev/null 2>&1; then
    echo "[err] distrobox not found on host. Install it first." >&2
    exit 1
fi

# 1. Create the distrobox if needed
if distrobox list 2>/dev/null | awk 'NR>1 {print $3}' | grep -qx "$NAME"; then
    echo "[info] distrobox '$NAME' already exists; skipping create."
else
    echo "[info] Creating distrobox '$NAME' from $IMAGE ..."
    distrobox create --name "$NAME" --image "$IMAGE" --yes
fi

# 2. Install Fedora packages: build deps + runtime audio plugins + git-lfs
#    Fedora's `java-latest-openjdk` is JDK 26 on F43 (not 25 — Gradle's
#    `jvmToolchain(25)` will reject it), so we install JDK 25 separately below.
echo "[info] Installing Fedora packages inside '$NAME' ..."
distrobox enter "$NAME" -- sudo dnf install -y \
    gtk4-devel \
    libadwaita-devel \
    gstreamer1-devel \
    gstreamer1-plugins-base \
    gstreamer1-plugins-base-devel \
    gstreamer1-plugins-good \
    gstreamer1-plugins-bad-free \
    pipewire-gstreamer \
    glib2-devel \
    alsa-lib-devel \
    pipewire-devel \
    git \
    git-lfs \
    make \
    curl

# 3. Install Adoptium Temurin JDK 25 into the distrobox /opt
echo "[info] Installing Adoptium Temurin JDK 25 into $JDK_INSTALL_DIR ..."
distrobox enter "$NAME" -- bash -c "
    set -e
    if [ -x $JDK_INSTALL_DIR/bin/java ]; then
        echo '[info] Temurin 25 already installed.'
    else
        cd /tmp
        curl -fsSL '$JDK_DOWNLOAD_URL' -o temurin-25.tar.gz
        sudo mkdir -p /opt
        sudo tar -xf temurin-25.tar.gz -C /opt
        sudo mv /opt/jdk-25* $JDK_INSTALL_DIR
        rm temurin-25.tar.gz
    fi
    sudo tee $PROFILE_FILE > /dev/null <<'EOF'
export JAVA_HOME=$JDK_INSTALL_DIR
export PATH=\$JAVA_HOME/bin:\$PATH
EOF
"

# 4. Pull LFS objects (bundled Whisper Tiny ONNX is in Git LFS)
REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
echo "[info] Ensuring Git LFS objects are present in $REPO_ROOT ..."
distrobox enter "$NAME" -- bash -c "
    set -e
    cd '$REPO_ROOT'
    git lfs install --local 2>/dev/null || true
    git lfs pull
"

# 5. Install appimagetool (used by 'make appimage' for distribution builds).
#    It's a single AppImage binary; drop into ~/.local/bin which is on PATH.
echo "[info] Ensuring appimagetool is on PATH inside the distrobox ..."
distrobox enter "$NAME" -- bash -c "
    set -e
    if ! command -v appimagetool >/dev/null 2>&1; then
        mkdir -p ~/.local/bin
        curl -fL --progress-bar \
            -o ~/.local/bin/appimagetool \
            https://github.com/AppImage/appimagetool/releases/download/continuous/appimagetool-x86_64.AppImage
        chmod +x ~/.local/bin/appimagetool
        echo '[info] Installed appimagetool to ~/.local/bin/appimagetool'
    else
        echo '[info] appimagetool already present'
    fi
"

# 6. Verify
echo
echo "[info] Verifying toolchain ..."
distrobox enter "$NAME" -- bash -lc 'java -version 2>&1 | head -1'
distrobox enter "$NAME" -- bash -lc 'pkg-config --modversion gtk4 libadwaita-1 gstreamer-1.0 2>&1'
distrobox enter "$NAME" -- bash -lc 'gst-inspect-1.0 autoaudiosrc 2>&1 | grep -q "Auto audio source" && echo "GStreamer audio source: OK"'
echo "Repo LFS state:"
distrobox enter "$NAME" -- bash -lc "ls -la $REPO_ROOT/core/src/main/resources/models/asr/tiny-encoder.int8.onnx"

echo
echo "[done] Setup complete. Build & run:"
echo "  distrobox enter $NAME -- bash -lc 'cd $REPO_ROOT && make run'"
echo
echo "Daily commands inside the distrobox:"
echo "  distrobox enter $NAME"
echo "  cd $REPO_ROOT"
echo "  make run         # GUI"
echo "  make check       # full ./gradlew check"
echo "  ./cli.sh asr ... # CLI ASR"
