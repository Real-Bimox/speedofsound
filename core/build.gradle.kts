plugins {
    // Apply the shared build logic from a convention plugin.
    // The shared code is located in `buildSrc/src/main/kotlin/kotlin-jvm.gradle.kts`.
    id("buildsrc.convention.kotlin-jvm")
    alias(libs.plugins.detekt)
    alias(libs.plugins.flatpakGradleGenerator)
    alias(libs.plugins.versions)
    alias(libs.plugins.kotlinPluginSerialization)
    alias(libs.plugins.kover)
}

dependencies {
    implementation(libs.bundles.kotlinxEcosystem)
    implementation(libs.bundles.ktorEcosystem)
    implementation(libs.commonsCompress)
    implementation(libs.log4jSlf4j2Impl)
    runtimeOnly(libs.log4jCore)

    implementation(libs.stargate)

    // Sherpa is not published to Maven Central, we need to add the libraries manually.
    // https://k2-fsa.github.io/sherpa/onnx/java-api/non-android-java.html
    api(files("libs/sherpa-onnx-v1.12.33.jar"))
    // Linux x64: pick GPU-aware native libs when -Pvoicestream.gpu=true is passed; CPU otherwise.
    // The GPU JAR is produced by scripts/build-gpu-jar.sh and is gitignored — see RESUME.md.
    api(
        files(
            run {
                val useGpu = (project.findProperty("voicestream.gpu") as? String)?.toBoolean() == true
                val cpuJar = file("libs/sherpa-onnx-native-lib-linux-x64-v1.12.33.jar")
                val gpuJar = file("libs/sherpa-onnx-native-lib-linux-x64-gpu-v1.12.33.jar")
                val chosen = if (useGpu) gpuJar else cpuJar
                require(chosen.exists()) {
                    if (useGpu) {
                        "GPU build requested but ${gpuJar.name} not found. " +
                            "Run 'make -C core/libs download-gpu-libs' first."
                    } else {
                        "Missing CPU native lib JAR ${cpuJar.name}. " +
                            "Run 'make -C core/libs download-libs'."
                    }
                }
                chosen
            }
        )
    )
    api(files("libs/sherpa-onnx-native-lib-linux-aarch64-v1.12.33.jar"))

    implementation(libs.anthropic)
    implementation(libs.googleGenai)
    implementation(libs.openai)

    testImplementation(kotlin("test"))
}

tasks.flatpakGradleGenerator {
    outputFile = file("flatpak-sources.json")
    downloadDirectory = "offline-repository"
    excludeConfigurations = listOf("testCompileClasspath", "testRuntimeClasspath")
}
