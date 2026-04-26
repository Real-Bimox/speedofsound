package com.zugaldia.speedofsound.core.models.voice

import kotlin.test.Test
import kotlin.test.assertTrue

class ChecksumVerifierTest {

    @Test
    fun `empty hash skips verification`() {
        val verifier = ChecksumVerifier()
        val tempFile = kotlin.io.path.createTempFile("vad", ".onnx").toFile()
        tempFile.writeText("anything")
        try {
            val result = verifier.verifySha256(tempFile, expectedChecksum = "")
            assertTrue(result.isSuccess)
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun `blank hash skips verification`() {
        val verifier = ChecksumVerifier()
        val tempFile = kotlin.io.path.createTempFile("vad", ".onnx").toFile()
        tempFile.writeText("anything")
        try {
            val result = verifier.verifySha256(tempFile, expectedChecksum = "   ")
            assertTrue(result.isSuccess)
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun `mismatched hash returns failure`() {
        val verifier = ChecksumVerifier()
        val tempFile = kotlin.io.path.createTempFile("vad", ".onnx").toFile()
        tempFile.writeText("anything")
        try {
            val result = verifier.verifySha256(tempFile, expectedChecksum = "deadbeef")
            assertTrue(result.isFailure)
        } finally {
            tempFile.delete()
        }
    }
}
