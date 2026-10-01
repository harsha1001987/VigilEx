package com.extrive.vigilex.data.repository

import com.extrive.vigilex.data.api.AnalysisApi
import com.extrive.vigilex.data.api.ApiConfig
import com.extrive.vigilex.data.api.NetLog
import com.extrive.vigilex.data.api.NetworkModule
import com.extrive.vigilex.data.model.AnalysisResult
import com.extrive.vigilex.data.model.parseAnalysisResult
import java.io.IOException
import java.io.InputStream
import java.net.SocketTimeoutException
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okio.BufferedSink
import okio.source

/** A video to upload. `open` must return a fresh stream on every call. */
class VideoUpload(
    val fileName: String,
    val mimeType: String?,
    val lengthBytes: Long?,
    val open: () -> InputStream
)

enum class FailureReason {
    /** No connection to the analysis service: wrong address, server down, network blocked. */
    UNREACHABLE,
    /** Connected, but the video upload broke off before all bytes were sent. */
    UPLOAD_FAILED,
    /** The video was uploaded but no answer arrived within the read timeout. */
    TIMEOUT,
    /** The server rejected the file (400, 413, 415, 422): empty, too large, not a video. */
    INVALID_VIDEO,
    /** The server received the video but analysis failed (5xx or connection lost while analyzing). */
    ANALYSIS_FAILED,
    /** The server answered 200 but the body was not an analysis result. */
    BAD_RESPONSE,
    /** The local video could not be read. */
    UNREADABLE_FILE
}

sealed interface AnalysisOutcome {
    data class Success(val rawJson: String, val result: AnalysisResult) : AnalysisOutcome
    data class Failure(val reason: FailureReason, val detail: String? = null) : AnalysisOutcome
}

class AnalysisRepository(
    private val apiFor: (String) -> AnalysisApi = NetworkModule::analysisApi
) {
    /**
     * Uploads the video to POST /api/v1/analyze-video as multipart field `file`.
     * `onUploadProgress` receives bytes actually written to the connection.
     * Cancellation propagates so the in-flight HTTP call is aborted.
     */
    suspend fun analyze(
        baseUrl: String,
        upload: VideoUpload,
        onUploadProgress: (sentBytes: Long) -> Unit = {}
    ): AnalysisOutcome = withContext(Dispatchers.IO) {
        val body = StreamingRequestBody(
            contentType = (upload.mimeType ?: "video/mp4").toMediaTypeOrNull(),
            length = upload.lengthBytes,
            open = upload.open,
            onProgress = onUploadProgress
        )
        val part = MultipartBody.Part.createFormData(ApiConfig.VIDEO_PART_NAME, upload.fileName, body)

        NetLog.i("API BASE URL: $baseUrl")
        NetLog.i("REQUEST URL: $baseUrl${ApiConfig.ANALYZE_VIDEO_PATH}")
        NetLog.i("METHOD: POST multipart/form-data")
        NetLog.i(
            "FILE FIELD: ${ApiConfig.VIDEO_PART_NAME} (name=${upload.fileName}, type=${upload.mimeType}, " +
                "bytes=${upload.lengthBytes ?: "unknown"})"
        )
        val started = System.currentTimeMillis()

        val response = try {
            apiFor(baseUrl).analyzeVideo(part)
        } catch (e: CancellationException) {
            NetLog.i("CANCELLED after ${body.sent} bytes")
            throw e
        } catch (e: Exception) {
            val reason = classify(e, body)
            NetLog.i("ERROR: ${e.javaClass.simpleName}: ${e.message} -> $reason (sent ${body.sent} bytes)")
            return@withContext AnalysisOutcome.Failure(reason, e.message)
        }

        NetLog.i("HTTP STATUS: ${response.code()} after ${System.currentTimeMillis() - started} ms (sent ${body.sent} bytes)")
        if (!response.isSuccessful) {
            val detail = response.errorBody()?.string()?.let(::parseFastApiDetail)
            NetLog.i("ERROR: HTTP ${response.code()} ${detail ?: ""}")
            val reason = when (response.code()) {
                400, 413, 415, 422 -> FailureReason.INVALID_VIDEO
                // Something answered, but it is not the VigilEx analysis endpoint.
                404, 405 -> FailureReason.UNREACHABLE
                else -> FailureReason.ANALYSIS_FAILED
            }
            return@withContext AnalysisOutcome.Failure(reason, detail)
        }

        val raw = response.body()?.string()
            ?: return@withContext AnalysisOutcome.Failure(FailureReason.BAD_RESPONSE)

        try {
            AnalysisOutcome.Success(raw, parseAnalysisResult(raw))
        } catch (e: Exception) {
            NetLog.i("ERROR: response is not an analysis result: ${e.message}")
            AnalysisOutcome.Failure(FailureReason.BAD_RESPONSE)
        }
    }

    /** Where the request was when it failed decides what failed. */
    private fun classify(e: Exception, body: StreamingRequestBody): FailureReason = when {
        e is UnreadableVideoException -> FailureReason.UNREADABLE_FILE
        // The body is only written once a connection exists.
        !body.started -> FailureReason.UNREACHABLE
        !body.completed -> FailureReason.UPLOAD_FAILED
        e is SocketTimeoutException -> FailureReason.TIMEOUT
        else -> FailureReason.ANALYSIS_FAILED
    }

    /** True when GET /api/v1/health answers successfully at this address. */
    suspend fun isServerReachable(baseUrl: String): Boolean = withContext(Dispatchers.IO) {
        try {
            apiFor(baseUrl).health().isSuccessful
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            false
        }
    }
}

/** Raised from inside the request body when the local file cannot be opened or read. */
class UnreadableVideoException(cause: Throwable) : IOException(cause)

private class StreamingRequestBody(
    private val contentType: MediaType?,
    private val length: Long?,
    private val open: () -> InputStream,
    private val onProgress: (Long) -> Unit
) : RequestBody() {
    override fun contentType(): MediaType? = contentType

    override fun contentLength(): Long = length ?: -1L

    @Volatile var started = false
        private set
    @Volatile var completed = false
        private set
    @Volatile var sent = 0L
        private set

    override fun writeTo(sink: BufferedSink) {
        started = true
        sent = 0L
        val stream = try {
            open()
        } catch (e: Exception) {
            throw UnreadableVideoException(e)
        }
        stream.source().use { source ->
            while (true) {
                val read = source.read(sink.buffer, SEGMENT_BYTES)
                if (read == -1L) break
                sent += read
                sink.emitCompleteSegments()
                onProgress(sent)
            }
        }
        sink.flush()
        completed = true
        NetLog.i("UPLOAD: complete, $sent bytes")
    }

    private companion object {
        const val SEGMENT_BYTES = 64L * 1024
    }
}

/** FastAPI errors are {"detail": "..."} or {"detail": [{"msg": "..."}]}. */
internal fun parseFastApiDetail(raw: String): String? = try {
    when (val detail = Json.parseToJsonElement(raw).jsonObject["detail"]) {
        null -> null
        is JsonArray -> detail.mapNotNull { it.jsonObject["msg"]?.jsonPrimitive?.content }
            .joinToString("; ")
            .ifBlank { null }
        else -> detail.jsonPrimitive.content
    }
} catch (e: Exception) {
    null
}
