package com.extrive.vigilex.ui.studio

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.extrive.vigilex.data.api.ApiConfig
import com.extrive.vigilex.data.media.InspectionResult
import com.extrive.vigilex.data.media.SelectedVideo
import com.extrive.vigilex.data.media.VideoInspector
import com.extrive.vigilex.data.model.AnalysisResult
import com.extrive.vigilex.data.model.AssessmentRecord
import com.extrive.vigilex.data.model.isScoreable
import com.extrive.vigilex.data.repository.AnalysisOutcome
import com.extrive.vigilex.data.repository.AnalysisRepository
import com.extrive.vigilex.data.repository.FailureReason
import com.extrive.vigilex.data.repository.VideoUpload
import com.extrive.vigilex.data.settings.ServerSettings
import com.extrive.vigilex.data.store.AssessmentStore
import java.util.UUID
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class StudioFailure(
    val title: String,
    val message: String,
    val canRetry: Boolean,
    val suggestSettings: Boolean = false,
    /** Failure class and request URL, shown only with Developer options on. */
    val developerDetail: String? = null
)

sealed interface UploadPhase {
    data class Uploading(val sentBytes: Long, val totalBytes: Long?) : UploadPhase
    /** Upload finished; the server is running pose, tracking and scoring. */
    data object Processing : UploadPhase
}

sealed interface StudioState {
    data object Empty : StudioState
    data object Inspecting : StudioState
    data class Ready(val video: SelectedVideo) : StudioState
    data class Analyzing(
        val video: SelectedVideo,
        val phase: UploadPhase,
        val startedAtMillis: Long
    ) : StudioState
    data class Completed(
        val video: SelectedVideo,
        val recordId: String,
        val result: AnalysisResult
    ) : StudioState
    /** The server answered, but no worker could be scored in any sampled frame. */
    data class Inconclusive(val video: SelectedVideo, val result: AnalysisResult) : StudioState
    data class Failed(val video: SelectedVideo?, val failure: StudioFailure) : StudioState
}

/**
 * Owns the Assessment Studio workflow outside of any screen, so an analysis
 * keeps running across rotation and while the user visits other sections.
 */
object AssessmentStudio {
    private lateinit var appContext: Context
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val repository = AnalysisRepository()
    private var job: Job? = null

    private val _state = MutableStateFlow<StudioState>(StudioState.Empty)
    val state: StateFlow<StudioState> = _state.asStateFlow()

    fun init(context: Context) {
        if (!::appContext.isInitialized) appContext = context.applicationContext
    }

    fun onVideoPicked(uri: Uri) {
        job?.cancel()
        try {
            // Keeps the video readable later for the diagnostic evidence view.
            appContext.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (e: SecurityException) {
            // Not all providers grant persistable access; the preview still works now.
        }
        _state.value = StudioState.Inspecting
        job = scope.launch {
            _state.value = when (val inspection = VideoInspector.inspect(appContext, uri)) {
                is InspectionResult.Valid -> StudioState.Ready(inspection.video)
                is InspectionResult.Invalid -> StudioState.Failed(
                    video = null,
                    failure = StudioFailure(
                        title = "Invalid video",
                        message = inspection.message,
                        canRetry = false
                    )
                )
            }
        }
    }

    fun analyze() {
        val video = when (val current = _state.value) {
            is StudioState.Ready -> current.video
            is StudioState.Failed -> current.video
            is StudioState.Inconclusive -> current.video
            else -> null
        } ?: return

        job?.cancel()
        val started = System.currentTimeMillis()
        _state.value = StudioState.Analyzing(video, UploadPhase.Uploading(0, video.sizeBytes), started)

        val baseUrl = ServerSettings.serverUrl.value
        val upload = VideoUpload(
            fileName = video.displayName,
            mimeType = video.mimeType,
            lengthBytes = video.sizeBytes,
            open = {
                appContext.contentResolver.openInputStream(video.uri)
                    ?: throw IllegalStateException("Video stream unavailable")
            }
        )

        job = scope.launch {
            var lastReportedPercent = -1
            val outcome = repository.analyze(baseUrl, upload) { sent ->
                val total = video.sizeBytes
                val phase = if (total != null && sent >= total) {
                    UploadPhase.Processing
                } else {
                    val percent = if (total != null && total > 0) ((sent * 100) / total).toInt() else -1
                    if (percent == lastReportedPercent && percent != -1) return@analyze
                    lastReportedPercent = percent
                    UploadPhase.Uploading(sent, total)
                }
                // Called on the upload thread; compareAndSet so a late progress
                // tick can never overwrite a cancellation made on the main thread.
                val current = _state.value
                if (current is StudioState.Analyzing && current.phase != phase) {
                    _state.compareAndSet(current, current.copy(phase = phase))
                }
            }

            _state.value = when (outcome) {
                is AnalysisOutcome.Success -> handleSuccess(video, outcome)
                is AnalysisOutcome.Failure -> StudioState.Failed(video, failureFor(outcome, baseUrl))
            }
        }
    }

    fun cancel() {
        val current = _state.value
        job?.cancel()
        if (current is StudioState.Analyzing) _state.value = StudioState.Ready(current.video)
    }

    fun reset() {
        job?.cancel()
        _state.value = StudioState.Empty
    }

    private suspend fun handleSuccess(video: SelectedVideo, outcome: AnalysisOutcome.Success): StudioState {
        val result = outcome.result
        if (!result.isScoreable) return StudioState.Inconclusive(video, result)

        val record = AssessmentRecord.from(
            id = UUID.randomUUID().toString(),
            createdAtMillis = System.currentTimeMillis(),
            fileName = video.displayName,
            fileSizeBytes = video.sizeBytes,
            videoUri = video.uri.toString(),
            result = result
        )
        return try {
            AssessmentStore.save(record, outcome.rawJson)
            StudioState.Completed(video, record.id, result)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            StudioState.Failed(
                video,
                StudioFailure(
                    title = "Result not saved",
                    message = "The analysis finished but could not be saved on this device. Check free storage and try again.",
                    canRetry = true
                )
            )
        }
    }

    private fun failureFor(failure: AnalysisOutcome.Failure, baseUrl: String): StudioFailure {
        // Technical cause for developers only (shown when Developer options are on).
        val technical = "${failure.reason} · ${baseUrl}${ApiConfig.ANALYZE_VIDEO_PATH}" +
            (failure.detail?.let { " · $it" } ?: "")
        return when (failure.reason) {
            FailureReason.UNREACHABLE -> StudioFailure(
                title = "Could not connect",
                message = "VigilEx couldn't reach the analysis service. Check your connection and try again.",
                canRetry = true,
                suggestSettings = true,
                developerDetail = technical
            )
            FailureReason.UPLOAD_FAILED -> StudioFailure(
                title = "Upload failed",
                message = "The connection was lost while the video was uploading. Check your connection and try again.",
                canRetry = true,
                suggestSettings = true,
                developerDetail = technical
            )
            FailureReason.TIMEOUT -> StudioFailure(
                title = "Analysis took too long",
                message = "The video was uploaded, but no result arrived in time. Longer videos take more time; " +
                    "try again or use a shorter clip.",
                canRetry = true,
                developerDetail = technical
            )
            FailureReason.INVALID_VIDEO -> StudioFailure(
                title = "Video not accepted",
                message = failure.detail ?: "VigilEx could not accept this video.",
                canRetry = false,
                developerDetail = technical
            )
            FailureReason.ANALYSIS_FAILED -> StudioFailure(
                title = "Analysis failed",
                message = "VigilEx received the video but could not analyze it. Try again, or try another video.",
                canRetry = true,
                developerDetail = technical
            )
            FailureReason.BAD_RESPONSE -> StudioFailure(
                title = "Analysis failed",
                message = "VigilEx could not read the analysis result. Try again.",
                canRetry = true,
                developerDetail = technical
            )
            FailureReason.UNREADABLE_FILE -> StudioFailure(
                title = "Video unavailable",
                message = "This video can no longer be read from the device. Choose it again.",
                canRetry = false
            )
        }
    }
}
