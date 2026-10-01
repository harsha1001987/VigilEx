package com.extrive.vigilex.data.repository

import com.extrive.vigilex.data.model.Method
import com.extrive.vigilex.data.model.isScoreable
import com.extrive.vigilex.data.model.peakRisk
import java.io.ByteArrayInputStream
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Exercises the app's real upload client against a running VigilEx backend.
 * Skipped unless the server answers at VIGILEX_TEST_SERVER (default
 * http://127.0.0.1:8000/). The video is VIGILEX_TEST_VIDEO or the default
 * path below. Start the server with, from backend/:
 *     python -m uvicorn app.main:app --host 127.0.0.1 --port 8000
 */
class AnalysisRepositoryIntegrationTest {

    private val server = System.getenv("VIGILEX_TEST_SERVER") ?: "http://127.0.0.1:8000/"
    private val repository = AnalysisRepository()
    private val video = File(
        System.getenv("VIGILEX_TEST_VIDEO") ?: "../backend/tests/19832490-hd_1920_1080_25fps (1).mp4"
    )

    private fun requireServer() = runBlocking {
        assumeTrue("VigilEx server not running at $server", repository.isServerReachable(server))
    }

    @Test
    fun `uploads a real video and parses the analysis`() = runBlocking {
        requireServer()
        assumeTrue("Test video missing: ${video.absolutePath}", video.exists())

        var lastProgress = 0L
        val outcome = repository.analyze(
            baseUrl = server,
            upload = VideoUpload(video.name, "video/mp4", video.length()) { video.inputStream() },
            onUploadProgress = { lastProgress = it }
        )

        assertTrue("Expected success, got $outcome", outcome is AnalysisOutcome.Success)
        val result = (outcome as AnalysisOutcome.Success).result
        assertEquals(video.length(), lastProgress)
        assertTrue(result.isScoreable)
        assertTrue(result.rula!!.maxScore!! in 1..Method.RULA.scaleMax)
        assertTrue(result.reba!!.maxScore!! in 1..Method.REBA.scaleMax)
        assertNotNull(result.peakRisk(Method.RULA))
        assertNotNull(result.peakRisk(Method.REBA))
        assertNotNull(result.derivedAngles?.kneeAvg)
        assertTrue(result.keyframes.isNotEmpty())
        assertTrue(outcome.rawJson.contains("\"keyframes\""))
    }

    @Test
    fun `non-video upload is rejected with the server's message`() = runBlocking {
        requireServer()
        val bytes = "not a video".toByteArray()
        val outcome = repository.analyze(
            baseUrl = server,
            upload = VideoUpload("notes.txt", "text/plain", bytes.size.toLong()) { ByteArrayInputStream(bytes) }
        )
        assertTrue("Expected rejection, got $outcome", outcome is AnalysisOutcome.Failure)
        outcome as AnalysisOutcome.Failure
        assertEquals(FailureReason.INVALID_VIDEO, outcome.reason)
        assertTrue(outcome.detail.orEmpty().contains("Unsupported file type"))
    }

    @Test
    fun `unreachable server reports a network failure`() = runBlocking {
        val bytes = ByteArray(16)
        val outcome = repository.analyze(
            baseUrl = "http://127.0.0.1:9/",
            upload = VideoUpload("clip.mp4", "video/mp4", bytes.size.toLong()) { ByteArrayInputStream(bytes) }
        )
        assertEquals(FailureReason.UNREACHABLE, (outcome as AnalysisOutcome.Failure).reason)
    }

    @Test
    fun `fastapi error bodies are parsed into readable text`() {
        assertEquals("Uploaded video file is empty.", parseFastApiDetail("""{"detail":"Uploaded video file is empty."}"""))
        assertEquals("Field required", parseFastApiDetail("""{"detail":[{"msg":"Field required","loc":["body","file"]}]}"""))
        assertEquals(null, parseFastApiDetail("<html>502</html>"))
    }
}
