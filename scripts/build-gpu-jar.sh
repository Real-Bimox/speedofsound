#!/bin/bash
# Downloads the Sherpa ONNX CUDA tarball, extracts the GPU-aware shared libraries,
# and repackages them into a JAR matching the layout the JVM JNI loader expects.
# Outputs core/libs/sherpa-onnx-native-lib-linux-x64-gpu-v${VERSION}.jar plus
# the raw CUDA runtime libs in core/libs/cuda-runtime-linux-x64/ for the
# AppImage to bundle later.
#
# Usage: scripts/build-gpu-jar.sh
# Requires: curl, tar, bzip2, zip, sha256sum
#
# Re-running with both outputs already present is a no-op. Delete to rebuild.

set -euo pipefail

VERSION="v1.12.33"
SHERPA_BASE="https://github.com/k2-fsa/sherpa-onnx/releases/download/${VERSION}"
TARBALL_NAME="sherpa-onnx-${VERSION}-cuda-12.x-cudnn-9.x-linux-x64-gpu.tar.bz2"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
LIBS_DIR="$ROOT_DIR/core/libs"
WORK_DIR="$(mktemp -d)"
trap 'rm -rf "$WORK_DIR"' EXIT

OUTPUT_JAR="$LIBS_DIR/sherpa-onnx-native-lib-linux-x64-gpu-${VERSION}.jar"

if [ -f "$OUTPUT_JAR" ]; then
    echo "GPU JAR already present: $OUTPUT_JAR"
    echo "Delete to rebuild."
    exit 0
fi

echo "Resolving SHA256 for $TARBALL_NAME from $SHERPA_BASE/checksum.txt..."
EXPECTED_SHA256="$(curl -sL "$SHERPA_BASE/checksum.txt" | awk -v t="$TARBALL_NAME" '$1==t{print $2}')"
if [ -z "$EXPECTED_SHA256" ]; then
    echo "Could not find $TARBALL_NAME in $SHERPA_BASE/checksum.txt" >&2
    exit 1
fi
echo "  $EXPECTED_SHA256"

TARBALL_PATH="$WORK_DIR/$TARBALL_NAME"
if [ -n "${SHERPA_CUDA_TARBALL:-}" ] && [ -f "$SHERPA_CUDA_TARBALL" ]; then
    echo "Using cached tarball: $SHERPA_CUDA_TARBALL"
    cp "$SHERPA_CUDA_TARBALL" "$TARBALL_PATH"
else
    echo "Downloading $TARBALL_NAME (~234 MB)..."
    curl -fL --progress-bar "$SHERPA_BASE/$TARBALL_NAME" -o "$TARBALL_PATH"
fi

echo "Verifying SHA256..."
echo "$EXPECTED_SHA256  $TARBALL_PATH" | sha256sum -c -

echo "Extracting..."
tar -xjf "$TARBALL_PATH" -C "$WORK_DIR"
EXTRACTED_DIR="$WORK_DIR/sherpa-onnx-${VERSION}-cuda-12.x-cudnn-9.x-linux-x64-gpu"
if [ ! -d "$EXTRACTED_DIR/lib" ]; then
    echo "Unexpected tarball layout — no lib/ directory in $EXTRACTED_DIR" >&2
    ls "$EXTRACTED_DIR" >&2 || true
    exit 1
fi

# The Sherpa CUDA tarball ships libonnxruntime*.so but NOT libsherpa-onnx-jni.so
# (that file is JVM-specific and only ships in the CPU "native-lib" JAR). The JNI
# shim is a thin C++ wrapper over the public ONNX Runtime C API, so we reuse the
# CPU JNI lib unchanged and pair it with the GPU-aware libonnxruntime.so plus the
# CUDA EP plugin libs. ONNX Runtime loads the EP plugin dynamically when
# setProvider("cuda") is called.
CPU_NATIVE_JAR="$LIBS_DIR/sherpa-onnx-native-lib-linux-x64-${VERSION}.jar"
if [ ! -f "$CPU_NATIVE_JAR" ]; then
    echo "Required CPU native-lib JAR not found: $CPU_NATIVE_JAR" >&2
    echo "Run 'make -C core/libs download-libs' first." >&2
    exit 1
fi

JAR_STAGE="$WORK_DIR/jar"
mkdir -p "$JAR_STAGE/sherpa-onnx/native/linux-x64"

echo "Extracting libsherpa-onnx-jni.so from CPU native-lib JAR..."
( cd "$JAR_STAGE/sherpa-onnx/native/linux-x64" && \
  unzip -j -o "$CPU_NATIVE_JAR" "sherpa-onnx/native/linux-x64/libsherpa-onnx-jni.so" >/dev/null )

echo "Copying GPU-aware libonnxruntime + CUDA EP plugins from tarball..."
cp "$EXTRACTED_DIR/lib/libonnxruntime.so" "$JAR_STAGE/sherpa-onnx/native/linux-x64/"
for plugin in libonnxruntime_providers_cuda.so libonnxruntime_providers_shared.so; do
    if [ -f "$EXTRACTED_DIR/lib/$plugin" ]; then
        cp "$EXTRACTED_DIR/lib/$plugin" "$JAR_STAGE/sherpa-onnx/native/linux-x64/"
    fi
done

mkdir -p "$JAR_STAGE/META-INF"
cat > "$JAR_STAGE/META-INF/MANIFEST.MF" << 'EOM'
Manifest-Version: 1.0
Created-By: VoiceStream build-gpu-jar.sh
EOM

mkdir -p "$LIBS_DIR"
( cd "$JAR_STAGE" && zip -qr "$OUTPUT_JAR" META-INF sherpa-onnx )

echo
echo "Created: $OUTPUT_JAR"
ls -lh "$OUTPUT_JAR"
echo
echo "Note: CUDA runtime libs (libcudart, libcudnn, libcublas, etc.) are NOT bundled"
echo "      in this JAR — Sherpa expects them on the host. To run the GPU build, the"
echo "      target machine must have an NVIDIA driver R535+ AND CUDA 12 + cuDNN 9 in"
echo "      LD_LIBRARY_PATH (or symlinked into a system lib dir). Otherwise"
echo "      SherpaOfflineAsr will silently fall back to CPU at runtime (see VAD-3)."
