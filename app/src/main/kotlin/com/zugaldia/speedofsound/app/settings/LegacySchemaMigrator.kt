package com.zugaldia.speedofsound.app.settings

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.concurrent.TimeUnit

/**
 * One-shot dconf migration from the v0.15.0 schema (`io.voicestream.VoiceStream`)
 * to the v0.16.0 Nexiant Voice schema (`ai.nexiant.voicestream`). Stored values move
 * from `/io/voicestream/VoiceStream/` to `/ai/nexiant/voicestream/`.
 *
 * Skips when:
 * - the new dconf path already has any user-set keys (avoids overwriting), OR
 * - the legacy dconf path has no keys (nothing to migrate).
 *
 * Uses the dconf CLI directly because reading values whose schema has been
 * uninstalled cannot go through GSettings — dconf is the underlying key-value
 * store and reads/writes path-keyed entries regardless of schema availability.
 */
class LegacySchemaMigrator(
    private val executor: ProcessExecutor = DefaultProcessExecutor(),
) {
    private val logger: Logger = LoggerFactory.getLogger(LegacySchemaMigrator::class.java)

    fun migrate(): Result<MigrationOutcome> = runCatching {
        val newKeys = executor.run(listOf("dconf", "list", NEW_PATH))
        if (newKeys.exitCode == 0 && newKeys.stdout.isNotBlank()) {
            logger.debug("New schema path already populated; skipping migration.")
            return@runCatching MigrationOutcome.SKIPPED_NEW_HAS_DATA
        }

        val legacyKeys = executor.run(listOf("dconf", "list", LEGACY_PATH))
        if (legacyKeys.exitCode != 0 || legacyKeys.stdout.isBlank()) {
            logger.debug("No legacy keys at $LEGACY_PATH; nothing to migrate.")
            return@runCatching MigrationOutcome.SKIPPED_NO_LEGACY
        }

        logger.info("Legacy keys detected at $LEGACY_PATH; copying to $NEW_PATH.")

        val dump = executor.run(listOf("dconf", "dump", LEGACY_PATH))
        if (dump.exitCode != 0) {
            logger.warn("dconf dump $LEGACY_PATH failed (exit=${dump.exitCode}): ${dump.stderr}")
            return@runCatching MigrationOutcome.FAILED_DUMP
        }

        val load = executor.run(listOf("dconf", "load", NEW_PATH), stdin = dump.stdout)
        if (load.exitCode != 0) {
            logger.warn("dconf load $NEW_PATH failed (exit=${load.exitCode}): ${load.stderr}")
            return@runCatching MigrationOutcome.FAILED_LOAD
        }

        logger.info("Legacy GSettings migrated from $LEGACY_PATH to $NEW_PATH.")
        MigrationOutcome.MIGRATED
    }

    companion object {
        const val LEGACY_PATH = "/io/voicestream/VoiceStream/"
        const val NEW_PATH = "/ai/nexiant/voicestream/"
    }
}

/** Result of a single dconf invocation. */
data class ProcessResult(val exitCode: Int, val stdout: String, val stderr: String)

/** Indicates whether and why the migration ran or skipped. */
enum class MigrationOutcome {
    SKIPPED_NEW_HAS_DATA,
    SKIPPED_NO_LEGACY,
    MIGRATED,
    FAILED_DUMP,
    FAILED_LOAD,
}

/** Abstraction over `Runtime.exec` so unit tests can inject canned responses. */
interface ProcessExecutor {
    fun run(cmd: List<String>, stdin: String? = null): ProcessResult
}

class DefaultProcessExecutor(
    private val timeoutSeconds: Long = DEFAULT_TIMEOUT_SECONDS,
) : ProcessExecutor {
    override fun run(cmd: List<String>, stdin: String?): ProcessResult {
        return runCatching {
            val process = ProcessBuilder(cmd)
                .redirectErrorStream(false)
                .start()
            if (stdin != null) {
                process.outputStream.use { it.write(stdin.toByteArray(Charsets.UTF_8)) }
            } else {
                process.outputStream.close()
            }
            val stdout = process.inputStream.bufferedReader(Charsets.UTF_8).readText()
            val stderr = process.errorStream.bufferedReader(Charsets.UTF_8).readText()
            val finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS)
            if (!finished) {
                process.destroyForcibly()
                ProcessResult(exitCode = -1, stdout = stdout, stderr = "process timed out after ${timeoutSeconds}s")
            } else {
                ProcessResult(exitCode = process.exitValue(), stdout = stdout, stderr = stderr)
            }
        }.getOrElse { error ->
            ProcessResult(exitCode = -1, stdout = "", stderr = error.message ?: error::class.simpleName.orEmpty())
        }
    }

    companion object {
        const val DEFAULT_TIMEOUT_SECONDS = 5L
    }
}
