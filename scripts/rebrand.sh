#!/usr/bin/env bash
# One-shot rebrand: Speed of Sound -> Sound Of Stream.
# - Preserves the Kotlin source package `com.zugaldia.speedofsound.*` (negative lookbehind).
# - Skips docs (README/CONTRIBUTING/CLAUDE/docs), upstream CI, LICENSE, build/, .git/.
# - Touches code, build files, resource manifests, GSettings/desktop/metainfo/Flatpak files.
set -euo pipefail

cd "$(dirname "$0")/.."

# Build file list: Kotlin/Gradle/Make/Meson/properties/yml/json/xml/in/sh/svg
mapfile -t FILES < <(git ls-files -- \
    '*.kt' '*.kts' '*.xml' '*.xml.in' '*.in' '*.gresource.xml' '*.yml' '*.json' \
    '*.properties' 'Makefile' 'meson.build' '*.sh' \
    | grep -v -E '^(README|CONTRIBUTING|CLAUDE)\.md$' \
    | grep -v -E '^docs/' \
    | grep -v -E '^\.github/' \
    | grep -v -E '^scripts/(setup-distrobox|rebrand)\.sh$' \
    | grep -v -E '^LICENSE$')

echo "[info] Touching ${#FILES[@]} files."

# Three-pass perl in-place edit:
#   1. CamelCase Display name -> 'Sound Of Stream'
#   2. CamelCase identifier   -> 'SoundOfStream'
#   3. lowercase identifier   -> 'soundofstream'  (skip when preceded by 'zugaldia.')
for f in "${FILES[@]}"; do
    perl -i -pe '
        s/Speed of Sound/Sound Of Stream/g;
        s/SpeedOfSound/SoundOfStream/g;
        s/(?<!zugaldia\.)speedofsound/soundofstream/g;
    ' "$f"
done

echo "[done] Rebrand pass complete."
