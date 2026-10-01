package com.extrive.vigilex.data.repository

import com.extrive.vigilex.data.api.AnalysisApi
import com.sun.net.httpserver.HttpServer
import java.io.ByteArrayInputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Test
import retrofit2.Retrofit

/**
 * Each failure the Assessment screen distinguishes, produced by a real local
 * server behaving that way, so the classification is observed, not assumed.
 */
class AnalysisFailureClassificationTest {

    private fun repository(readTimeoutMs: Long = 10_000) = AnalysisRepository { baseUrl ->
        Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(
                OkHttpClient.Builder()
                    .connectTimeout(2, TimeUnit.SECONDS)
                    .readTimeout(readTimeoutMs, TimeUnit.MILLISECONDS)
                    .writeTimeout(5, TimeUnit.SECONDS)
                    .retryOnConnectionFailure(false)
                    .build()
            )
            .build()
            .create(AnalysisApi::class.java)
    }

    private fun upload(size: Int) = ByteArray(size).let { bytes ->
        VideoUpload("clip.mp4", "video/mp4", bytes.size.toLong()) { ByteArrayInputStream(bytes) }
    }

    /** A server that answers every request with [status] after reading the whole upload. */
    private fun httpServer(status: Int, body: String, delayMs: Long = 0): HttpServer =
        HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/") { exchange ->
                exchange.requestBody.readBytes()
                Thread.sleep(delayMs)
                val bytes = body.toByteArray()
                exchange.responseHeaders.add("Content-Type", "application/json")
                exchange.sendResponseHeaders(status, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
            }
            start()
        }

    private fun HttpServer.url() = "http://127.0.0.1:${address.port}/"

    @Test
    fun `nothing listening is unreachable`() = runBlocking {
        val port = ServerSocket(0).use { it.localPort } // Free port, then closed.
        val outcome = repository().analyze("http://127.0.0.1:$port/", upload(16))
        assertEquals(FailureReason.UNREACHABLE, (outcome as AnalysisOutcome.Failure).reason)
    }

    @Test
    fun `connection dropped mid-upload is an upload failure`() = runBlocking {
        val server = ServerSocket(0)
        thread {
            server.accept().use { socket ->
                socket.getInputStream().read(ByteArray(4096)) // Headers and a little body, then hang up.
            }
        }
        val outcome = repository().analyze("http://127.0.0.1:${server.localPort}/", upload(32 * 1024 * 1024))
        server.close()
        assertEquals(FailureReason.UPLOAD_FAILED, (outcome as AnalysisOutcome.Failure).reason)
    }

    @Test
    fun `no answer after a complete upload is a timeout`() = runBlocking {
        val server = httpServer(200, "{}", delayMs = 3_000)
        val outcome = repository(readTimeoutMs = 500).analyze(server.url(), upload(1024))
        server.stop(0)
        assertEquals(FailureReason.TIMEOUT, (outcome as AnalysisOutcome.Failure).reason)
    }

    @Test
    fun `server error after upload is an analysis failure`() = runBlocking {
        val server = httpServer(500, """{"detail":"Video processing failed: decoder error"}""")
        val outcome = repository().analyze(server.url(), upload(1024)) as AnalysisOutcome.Failure
        server.stop(0)
        assertEquals(FailureReason.ANALYSIS_FAILED, outcome.reason)
        assertEquals("Video processing failed: decoder error", outcome.detail)
    }

    @Test
    fun `rejected file is an invalid video`() = runBlocking {
        val server = httpServer(413, """{"detail":"File size exceeds maximum allowed limit (100 MB)."}""")
        val outcome = repository().analyze(server.url(), upload(1024)) as AnalysisOutcome.Failure
        server.stop(0)
        assertEquals(FailureReason.INVALID_VIDEO, outcome.reason)
    }

    @Test
    fun `an unrelated server at the address is unreachable`() = runBlocking {
        val server = httpServer(404, """{"detail":"Not Found"}""")
        val outcome = repository().analyze(server.url(), upload(1024)) as AnalysisOutcome.Failure
        server.stop(0)
        assertEquals(FailureReason.UNREACHABLE, outcome.reason)
    }
}
