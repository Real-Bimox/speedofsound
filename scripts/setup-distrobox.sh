#!/usr/bin/env bash
# Stand up a Fedora 43 distrobox with Java 25 + GTK4 + libadwaita + GStreamer
# for building and running VoiceStream on bazzite (immutable host).
#
# Run on the HOST (not inside any container):
#   bash scripts/setup-distrobox.sh
# Then enter with:
#   distrobox enter voicestream-dev
set -euo pipefail

NAME="voicestream-dev"
IMAGE="registry.fedoraproject.org/fedora:43"

if ! command -v distrobox >/dev/null 2>&1; then
    echo "distrobox not found on host. Install it first." >&2
    exit 1
fi

if distrobox list 2>/dev/null | awk 'NR>1 {print $3}' | grep -qx "$NAME"; then
    echo "[info] distrobox '$NAME' already exists; skipping create."
else
    echo "[info] Creating distrobox '$NAME' from $IMAGE ..."
    distrobox create --name "$NAME" --image "$IMAGE" --yes
fi

echo "[info] Installing build/runtime deps inside '$NAME' ..."
distrobox enter "$NAME" -- sudo dnf install -y \
    java-latest-openjdk-devel \
    gtk4-devel \
    libadwaita-devel \
    gstreamer1-devel \
    gstreamer1-plugins-base-devel \
    glib2-devel \
    alsa-lib-devel \
    pipewire-devel \
    make \
    git

echo
echo "[done] Setup complete. Verify with:"
echo "  distrobox enter $NAME -- java -version"
echo "  distrobox enter $NAME -- pkg-config --modversion gtk4 libadwaita-1"
echo
echo "Then build & run from the repo:"
echo "  distrobox enter $NAME"
echo "  cd /var/home/bahram/local-repos/speedofsound"
echo "  make run"
