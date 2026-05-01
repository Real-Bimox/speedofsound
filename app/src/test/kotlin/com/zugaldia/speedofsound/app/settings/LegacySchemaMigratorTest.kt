package com.zugaldia.speedofsound.app.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LegacySchemaMigratorTest {

    private class FakeExecutor(
        private val responses: Map<List<String>, ProcessResult>,
    ) : ProcessExecutor {
        val invocations: MutableList<Pair<List<String>, String?>> = mutableListOf()
        override fun run(cmd: List<String>, stdin: String?): ProcessResult {
            invocations.add(cmd to stdin)
            return responses[cmd]
                ?: throw IllegalStateException("No fake response for $cmd")
        }
    }

    private val listLegacy = listOf("dconf", "list", LegacySchemaMigrator.LEGACY_PATH)
    private val listNew = listOf("dconf", "list", LegacySchemaMigrator.NEW_PATH)
    private val dumpLegacy = listOf("dconf", "dump", LegacySchemaMigrator.LEGACY_PATH)
    private val loadNew = listOf("dconf", "load", LegacySchemaMigrator.NEW_PATH)

    @Test
    fun `skips migration when new schema already has data`() {
        val executor = FakeExecutor(
            mapOf(
                listNew to ProcessResult(exitCode = 0, stdout = "default-language\n", stderr = ""),
            )
        )

        val result = LegacySchemaMigrator(executor).migrate().getOrThrow()

        assertEquals(MigrationOutcome.SKIPPED_NEW_HAS_DATA, result)
        assertEquals(1, executor.invocations.size)
        assertEquals(listNew, executor.invocations[0].first)
    }

    @Test
    fun `skips migration when legacy schema has no data`() {
        val executor = FakeExecutor(
            mapOf(
                listNew to ProcessResult(exitCode = 0, stdout = "", stderr = ""),
                listLegacy to ProcessResult(exitCode = 0, stdout = "", stderr = ""),
            )
        )

        val result = LegacySchemaMigrator(executor).migrate().getOrThrow()

        assertEquals(MigrationOutcome.SKIPPED_NO_LEGACY, result)
        // Did NOT proceed to dump/load.
        assertTrue(executor.invocations.none { it.first == dumpLegacy })
        assertTrue(executor.invocations.none { it.first == loadNew })
    }

    @Test
    fun `migrates by piping dconf dump into dconf load when legacy data exists`() {
        val dumpPayload = "[/]\ndefault-language='en'\nappend-space=true\n"
        val executor = FakeExecutor(
            mapOf(
                listNew to ProcessResult(exitCode = 0, stdout = "", stderr = ""),
                listLegacy to ProcessResult(exitCode = 0, stdout = "default-language\nappend-space\n", stderr = ""),
                dumpLegacy to ProcessResult(exitCode = 0, stdout = dumpPayload, stderr = ""),
                loadNew to ProcessResult(exitCode = 0, stdout = "", stderr = ""),
            )
        )

        val result = LegacySchemaMigrator(executor).migrate().getOrThrow()

        assertEquals(MigrationOutcome.MIGRATED, result)
        // Verify the load step was called with the dump payload as stdin.
        val loadCall = executor.invocations.single { it.first == loadNew }
        assertEquals(dumpPayload, loadCall.second)
    }

    @Test
    fun `returns FAILED_DUMP when dconf dump exits non-zero`() {
        val executor = FakeExecutor(
            mapOf(
                listNew to ProcessResult(exitCode = 0, stdout = "", stderr = ""),
                listLegacy to ProcessResult(exitCode = 0, stdout = "default-language\n", stderr = ""),
                dumpLegacy to ProcessResult(exitCode = 1, stdout = "", stderr = "permission denied"),
            )
        )

        val result = LegacySchemaMigrator(executor).migrate().getOrThrow()

        assertEquals(MigrationOutcome.FAILED_DUMP, result)
    }

    @Test
    fun `returns FAILED_LOAD when dconf load exits non-zero`() {
        val executor = FakeExecutor(
            mapOf(
                listNew to ProcessResult(exitCode = 0, stdout = "", stderr = ""),
                listLegacy to ProcessResult(exitCode = 0, stdout = "default-language\n", stderr = ""),
                dumpLegacy to ProcessResult(exitCode = 0, stdout = "[/]\nx='y'\n", stderr = ""),
                loadNew to ProcessResult(exitCode = 2, stdout = "", stderr = "syntax error"),
            )
        )

        val result = LegacySchemaMigrator(executor).migrate().getOrThrow()

        assertEquals(MigrationOutcome.FAILED_LOAD, result)
    }
}
