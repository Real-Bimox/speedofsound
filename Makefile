APP_ID = io.voicestream.VoiceStream
export GRADLE_OPTS = --enable-native-access=ALL-UNNAMED

.PHONY: clean run run-light run-dark build shadow-build shadow-run check resources \
	meson-clean meson-setup meson-build meson-install uninstall install \
	flatpak-sources flatpak-linter flatpak-build flatpak-bundle flatpak-run flatpak-remove desktop-validate \
	snapcraft-clean snapcraft-pack snapcraft-lint snap-install snap-remove \
	jpackage-deb jpackage-rpm jpackage-app-image appimage \
	actionlint \
	docs-serve docs-build

clean:
	./gradlew clean

run: clean
	SOS_DISABLE_GIO_STORE=true SOS_DISABLE_GSTREAMER=false ./gradlew :app:run

run-light: clean
	SOS_DISABLE_GIO_STORE=true SOS_DISABLE_GSTREAMER=false SOS_COLOR_SCHEME=light ./gradlew :app:run

run-dark: clean
	SOS_DISABLE_GIO_STORE=true SOS_DISABLE_GSTREAMER=false SOS_COLOR_SCHEME=dark ./gradlew :app:run

build:
	./gradlew build

shadow-build: clean
	./gradlew :app:shadowJar

shadow-run: shadow-build
	java --enable-native-access=ALL-UNNAMED -jar app/build/libs/voicestream.jar

check:
	./gradlew check

resources:
	rm -f app/src/main/resources/voicestream.gresource
	./gradlew :app:compileResources

#
# Meson build
#

meson-clean:
	rm -rf builddir

meson-setup: meson-clean
	meson setup builddir --prefix=$(HOME)/.local

meson-build:
	ninja -C builddir

meson-install:
	ninja -C builddir install

uninstall:
	ninja -C builddir uninstall

install: meson-setup meson-build meson-install

#
# Flatpak
#

flatpak-sources:
	rm -f buildSrc/flatpak-sources.json core/flatpak-sources.json app/flatpak-sources.json
	./gradlew --project-dir buildSrc flatpakGradleGenerator --no-configuration-cache
	./gradlew :app:flatpakGradleGenerator :core:flatpakGradleGenerator --no-configuration-cache

flatpak-linter:
	flatpak run --command=flatpak-builder-lint org.flatpak.Builder appstream data/$(APP_ID).metainfo.xml.in
	flatpak run --command=flatpak-builder-lint org.flatpak.Builder manifest $(APP_ID).yml

flatpak-clean:
	rm -rf builddir repo

flatpak-build:
	flatpak-builder --force-clean --user --install-deps-from=flathub --repo=repo --install builddir $(APP_ID).yml

flatpak-bundle:
	rm -f voicestream.flatpak
	flatpak build-bundle repo voicestream.flatpak $(APP_ID) --runtime-repo=https://flathub.org/repo/flathub.flatpakrepo

flatpak-run:
	flatpak run $(APP_ID)

flatpak-remove:
	flatpak remove --user $(APP_ID)

desktop-validate:
	desktop-file-validate data/$(APP_ID).desktop.in

#
# Snap
#

snapcraft-clean:
	snapcraft clean

snapcraft-pack:
	rm -f voicestream_*.snap
	snapcraft pack

snapcraft-lint:
	snapcraft lint voicestream_*.snap

snap-install:
	snap install voicestream_*_amd64.snap --dangerous
	snap connect voicestream:audio-record
	snap connect voicestream:alsa

snap-remove:
	snap remove voicestream

#
# GitHub Actions
#

actionlint:
	actionlint -verbose

#
# jpackage
#

jpackage-deb:
	rm -rf app/build/jpackage
	./gradlew :app:jpackage-deb --no-configuration-cache

jpackage-rpm:
	rm -rf app/build/jpackage
	./gradlew :app:jpackage-rpm --no-configuration-cache

jpackage-app-image:
	rm -rf app/build/jpackage
	./gradlew :app:jpackage-app-image --no-configuration-cache

appimage: jpackage-app-image
	./scripts/build-appimage.sh

# GPU AppImage. Builds the GPU Sherpa JAR first if missing, then re-runs jpackage
# with -Pvoicestream.gpu=true so the GPU JAR is bundled. Output filename gets a
# -gpu suffix. Requires CUDA 12 + cuDNN 9 on the target host at runtime.
appimage-gpu:
	@if [ ! -f core/libs/sherpa-onnx-native-lib-linux-x64-gpu-v1.12.33.jar ]; then \
		echo "GPU JAR missing; running build-gpu-jar.sh first..."; \
		bash scripts/build-gpu-jar.sh; \
	fi
	rm -rf app/build/jpackage
	./gradlew -Pvoicestream.gpu=true :app:jpackage-app-image --no-configuration-cache
	VOICESTREAM_GPU=1 ./scripts/build-appimage.sh


#
# Docs
#

docs-serve:
	mkdocs serve

docs-build:
	mkdocs build
