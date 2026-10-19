package com.extrive.vigilex.ui.screens

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.extrive.vigilex.data.media.SelectedVideo
import com.extrive.vigilex.data.model.AnalysisResult
import com.extrive.vigilex.data.settings.ServerSettings
import com.extrive.vigilex.ui.components.ArrowLink
import com.extrive.vigilex.ui.components.Callout
import com.extrive.vigilex.ui.components.CalloutTone
import com.extrive.vigilex.ui.components.CaptureGuidance
import com.extrive.vigilex.ui.components.ErrorState
import com.extrive.vigilex.ui.components.LoadingState
import com.extrive.vigilex.ui.components.RecordingTips
import com.extrive.vigilex.ui.components.SelectedVideoSummary
import com.extrive.vigilex.ui.components.LocalWindowClass
import com.extrive.vigilex.ui.components.PageContainer
import com.extrive.vigilex.ui.components.PageHeader
import com.extrive.vigilex.ui.components.PrimaryButton
import com.extrive.vigilex.ui.components.ProcessingPanel
import com.extrive.vigilex.ui.components.SecondaryButton
import com.extrive.vigilex.ui.components.SectionLabel
import com.extrive.vigilex.ui.components.StepIndicator
import com.extrive.vigilex.ui.components.StudioStep
import com.extrive.vigilex.ui.components.UploadPanel
import com.extrive.vigilex.ui.components.VideoPreview
import com.extrive.vigilex.ui.components.WindowClass
import com.extrive.vigilex.ui.components.aspect
import com.extrive.vigilex.ui.format.formatPercent
import com.extrive.vigilex.ui.studio.AssessmentStudio
import com.extrive.vigilex.ui.studio.StudioState
import com.extrive.vigilex.ui.theme.InkMuted
import com.extrive.vigilex.ui.theme.InkSecondary
import com.extrive.vigilex.ui.theme.Motion
import com.extrive.vigilex.ui.theme.Space
import com.extrive.vigilex.ui.theme.VxType
import java.io.File

/** Why recording could not start, and whether Android will still show the permission prompt. */
private data class CameraProblem(val message: String, val needsSettings: Boolean)

/**
 * New assessment: choose a video → review it → analyze → the result.
 * A successful analysis opens the saved assessment directly; the studio then
 * starts over, so Back from the result returns to a fresh New assessment.
 */
@Composable
fun AssessmentStudioScreen(
    onOpenResult: (String) -> Unit,
    onOpenHistory: () -> Unit,
    onOpenDeveloperOptions: () -> Unit
) {
    val context = LocalContext.current
    val state by AssessmentStudio.state.collectAsState()

    // Open the result once. The studio is reset only after this screen has left the
    // composition, so it never flashes back to the start while navigating away.
    (state as? StudioState.Completed)?.let { done ->
        LaunchedEffect(done.recordId) { onOpenResult(done.recordId) }
        DisposableEffect(done.recordId) { onDispose { AssessmentStudio.resultOpened(done.recordId) } }
    }

    var recordingUri by remember { mutableStateOf<Uri?>(null) }
    var cameraProblem by remember { mutableStateOf<CameraProblem?>(null) }
    // Set once Android stops showing the permission prompt ("Don't allow" twice, or "Don't ask again").
    var cameraBlocked by rememberSaveable { mutableStateOf(false) }

    // Cancelling the camera (success = false) leaves the current video, if any, untouched.
    val recordLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CaptureVideo()) { success ->
        if (success) recordingUri?.let { uri -> AssessmentStudio.onVideoPicked(uri) }
    }

    val startCamera = {
        cameraProblem = null
        launchCameraRecording(
            context = context,
            onUriReady = { uri ->
                recordingUri = uri
                recordLauncher.launch(uri)
            },
            onError = { err -> cameraProblem = CameraProblem(err, needsSettings = false) }
        )
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            cameraBlocked = false
            startCamera()
        } else {
            val activity = context.findActivity()
            cameraBlocked = activity != null &&
                !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.CAMERA)
            cameraProblem = blockedOrDenied(cameraBlocked)
        }
    }

    // Cancelling the picker (uri = null) leaves the current video, if any, untouched.
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            cameraProblem = null
            AssessmentStudio.onVideoPicked(uri)
        }
    }

    val chooseVideo = { picker.launch(arrayOf("video/*")) }

    val recordVideo = {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        when {
            granted -> startCamera()
            // The prompt would not appear; point to app settings instead of asking again.
            cameraBlocked -> cameraProblem = blockedOrDenied(blocked = true)
            else -> {
                cameraProblem = null
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }
    val openAppSettings = {
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    PageContainer {
        PageHeader(
            title = "New assessment",
            supporting = "Record or choose a short video of a task. VigilEx measures the worker's posture and scores it with RULA and REBA."
                .takeIf { state == StudioState.Empty || state == StudioState.Inspecting }
        )
        StepIndicator(stepFor(state))
        Spacer(Modifier.height(Space.lg))

        cameraProblem?.takeIf { state is StudioState.Empty || state is StudioState.Ready || state is StudioState.Failed }?.let { problem ->
            Callout(
                title = if (problem.needsSettings) "Camera access is off" else "Camera unavailable",
                body = problem.message,
                tone = CalloutTone.ATTENTION,
                actions = if (problem.needsSettings) ({ SecondaryButton("Open app settings", onClick = openAppSettings) }) else null
            )
            Spacer(Modifier.height(Space.md))
        }

        AnimatedContent(
            targetState = state,
            contentKey = { it::class },
            transitionSpec = { fadeIn(tween(Motion.base)) togetherWith fadeOut(tween(Motion.fast)) },
            label = "studio"
        ) { current ->
            Column(Modifier.fillMaxWidth()) {
                when (current) {
                    StudioState.Empty, StudioState.Inspecting -> SelectStep(
                        busy = current == StudioState.Inspecting,
                        onRecord = recordVideo,
                        onChoose = chooseVideo
                    )
                    is StudioState.Ready -> PreviewStep(
                        video = current.video,
                        rejected = current.rejected,
                        onRecordAgain = recordVideo,
                        onReplace = chooseVideo
                    )
                    is StudioState.Analyzing -> TwoPane(
                        // Status first, so it stays in view; the video is secondary and small.
                        start = { ProcessingPanel(current, onCancel = AssessmentStudio::cancel) },
                        end = {
                            Text(current.video.displayName, style = VxType.bodySmall, color = InkMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Spacer(Modifier.height(Space.xs))
                            VideoPreview(current.video.uri, current.video.aspect(), maxHeight = 160.dp)
                        }
                    )
                    // Only shown for the moment before the result screen opens.
                    is StudioState.Completed -> LoadingState(label = "Opening the result…")
                    is StudioState.Inconclusive -> InconclusiveStep(current.result, chooseVideo)
                    is StudioState.Failed -> FailedStep(current, chooseVideo, onOpenHistory, onOpenDeveloperOptions)
                }
            }
        }
    }
}

private fun blockedOrDenied(blocked: Boolean) = if (blocked) {
    CameraProblem(
        "Camera permission is turned off for VigilEx. Turn it on in app settings to record, or choose a video from your device.",
        needsSettings = true
    )
} else {
    CameraProblem(
        "VigilEx needs the camera only to record the task. Allow it to record, or choose a video from your device instead.",
        needsSettings = false
    )
}

private fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

private fun launchCameraRecording(
    context: Context,
    onUriReady: (Uri) -> Unit,
    onError: (String) -> Unit
) {
    try {
        val recordDir = File(context.cacheDir, "recordings").apply { mkdirs() }
        val tempFile = File.createTempFile("record_", ".mp4", recordDir)
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.reports",
            tempFile
        )
        onUriReady(uri)
    } catch (e: Exception) {
        onError("The camera could not be started: ${e.localizedMessage ?: "no file for the recording"}. Choose a video from your device instead.")
    }
}

private fun stepFor(state: StudioState): StudioStep = when (state) {
    StudioState.Empty, StudioState.Inspecting -> StudioStep.SELECT
    is StudioState.Ready -> StudioStep.PREVIEW
    is StudioState.Analyzing -> StudioStep.ANALYZE
    is StudioState.Completed, is StudioState.Inconclusive -> StudioStep.RESULTS
    is StudioState.Failed -> if (state.video == null) StudioStep.SELECT else StudioStep.ANALYZE
}

/** Two columns on wide screens; stacked on phones, [start] first. */
@Composable
private fun TwoPane(
    start: @Composable ColumnScope.() -> Unit,
    end: @Composable ColumnScope.() -> Unit
) {
    if (LocalWindowClass.current == WindowClass.COMPACT) {
        Column(Modifier.fillMaxWidth()) {
            start()
            Spacer(Modifier.height(Space.lg))
            end()
        }
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(Space.xxl), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1.25f), content = start)
            Column(Modifier.weight(1f), content = end)
        }
    }
}

@Composable
private fun SelectStep(
    busy: Boolean,
    onRecord: () -> Unit,
    onChoose: () -> Unit
) {
    TwoPane(
        start = { UploadPanel(onRecord = onRecord, onChoose = onChoose, busy = busy) },
        end = {
            SectionLabel("Before you film")
            CaptureGuidance()
            Spacer(Modifier.height(Space.md))
            RecordingTips()
        }
    )
}

@Composable
private fun PreviewStep(
    video: SelectedVideo,
    rejected: String?,
    onRecordAgain: () -> Unit,
    onReplace: () -> Unit
) {
    val compact = LocalWindowClass.current == WindowClass.COMPACT
    rejected?.let {
        Callout(
            title = "That video can't be used",
            body = "$it Your previous video is still selected.",
            tone = CalloutTone.ATTENTION
        )
        Spacer(Modifier.height(Space.md))
    }
    TwoPane(
        start = { VideoPreview(video.uri, video.aspect(), maxHeight = if (compact) 260.dp else 420.dp) },
        end = {
            SelectedVideoSummary(video)
            Spacer(Modifier.height(Space.md))
            Text(
                "Analyzing uploads this video to the VigilEx server and saves the assessment to your account. " +
                    "The video on this device is not changed.",
                style = VxType.bodySmall,
                color = InkSecondary
            )
            Spacer(Modifier.height(Space.lg))
            PrimaryButton(text = "Analyze video", onClick = AssessmentStudio::analyze, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(Space.sm))
            SecondaryButton(text = "Replace video", onClick = onReplace, modifier = Modifier.fillMaxWidth())
            ArrowLink("Record a new video", onClick = onRecordAgain)
        }
    )
}

@Composable
private fun InconclusiveStep(result: AnalysisResult, onChooseAnother: () -> Unit) {
    val sampled = result.video?.framesSampled ?: 0
    val detection = formatPercent(result.quality?.detectionFraction)
    ErrorState(
        title = "No worker could be assessed",
        message = "VigilEx analyzed $sampled sampled frames but could not score a worker in any of them " +
            "(worker detected in $detection of frames). Use a side-view video with the worker's whole body in frame. " +
            "This result was not saved.",
        actions = {
            PrimaryButton(text = "Choose another video", onClick = onChooseAnother)
            SecondaryButton(text = "Try again", onClick = AssessmentStudio::analyze)
        }
    )
}

@Composable
private fun FailedStep(
    state: StudioState.Failed,
    onChooseAnother: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenDeveloperOptions: () -> Unit
) {
    val failure = state.failure
    val developer by ServerSettings.developerOptions.collectAsState()
    state.video?.let {
        Text("Video: ${it.displayName}", style = VxType.bodySmall, color = InkMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(Space.sm))
    }
    ErrorState(
        title = failure.title,
        message = failure.message,
        actions = {
            if (failure.canRetry && state.video != null && failure.mayHaveSaved) {
                // The server may already have saved this analysis: checking History comes first,
                // so a retry is a deliberate second step rather than a likely duplicate.
                PrimaryButton(text = "Open history", onClick = onOpenHistory)
                SecondaryButton(text = "Analyze again", onClick = AssessmentStudio::analyze)
            } else if (failure.canRetry && state.video != null) {
                PrimaryButton(text = "Try again", onClick = AssessmentStudio::analyze)
                SecondaryButton(text = "Choose another video", onClick = onChooseAnother)
            } else {
                PrimaryButton(text = "Choose a video", onClick = onChooseAnother)
            }
        }
    )
    if (failure.mayHaveSaved) {
        // The server had the whole video, so it may still have saved the assessment.
        Spacer(Modifier.height(Space.md))
        Callout(
            title = "Check History before trying again",
            body = "The server may still finish this analysis and save it. If it appears in History, " +
                "open it there instead of analyzing the video again."
        )
    }
    if (developer && failure.developerDetail != null) {
        Spacer(Modifier.height(Space.sm))
        Text(failure.developerDetail, style = VxType.mono, color = InkMuted)
    }
    // Only developers can change the server; normal users just retry.
    if (failure.suggestSettings && developer) {
        ArrowLink("Developer options", onClick = onOpenDeveloperOptions)
    }
}
