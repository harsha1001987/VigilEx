package com.extrive.vigilex.ui.screens

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
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.runtime.remember
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.extrive.vigilex.data.insight.interpret
import com.extrive.vigilex.ui.components.KeyFinding
import com.extrive.vigilex.ui.components.KeySquare
import com.extrive.vigilex.ui.components.RiskConclusion
import com.extrive.vigilex.ui.theme.Yellow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.extrive.vigilex.data.media.SelectedVideo
import com.extrive.vigilex.data.model.AnalysisResult
import com.extrive.vigilex.data.settings.ServerSettings
import com.extrive.vigilex.ui.components.ArrowLink
import com.extrive.vigilex.ui.components.ButtonRow
import com.extrive.vigilex.ui.components.CaptureGuidance
import com.extrive.vigilex.ui.components.ErrorState
import com.extrive.vigilex.ui.components.LocalWindowClass
import com.extrive.vigilex.ui.components.PageContainer
import com.extrive.vigilex.ui.components.PageHeader
import com.extrive.vigilex.ui.components.PrimaryButton
import com.extrive.vigilex.ui.components.ProcessingPanel
import com.extrive.vigilex.ui.components.SecondaryButton
import com.extrive.vigilex.ui.components.StepIndicator
import com.extrive.vigilex.ui.components.StudioStep
import com.extrive.vigilex.ui.components.UploadPanel
import com.extrive.vigilex.ui.components.VideoFacts
import com.extrive.vigilex.ui.components.VideoPreview
import com.extrive.vigilex.ui.components.WindowClass
import com.extrive.vigilex.ui.components.aspect
import com.extrive.vigilex.ui.format.formatPercent
import com.extrive.vigilex.ui.studio.AssessmentStudio
import com.extrive.vigilex.ui.studio.StudioState
import com.extrive.vigilex.ui.theme.Ink
import com.extrive.vigilex.ui.theme.InkMuted
import com.extrive.vigilex.ui.theme.InkSecondary
import com.extrive.vigilex.ui.theme.Motion
import com.extrive.vigilex.ui.theme.Space
import com.extrive.vigilex.ui.theme.VxType

@Composable
fun AssessmentStudioScreen(
    onOpenDiagnostic: (String) -> Unit,
    onOpenDeveloperOptions: () -> Unit
) {
    val state by AssessmentStudio.state.collectAsState()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) AssessmentStudio.onVideoPicked(uri)
    }
    val chooseVideo = { picker.launch(arrayOf("video/*")) }

    PageContainer {
        PageHeader(
            eyebrow = "Assessment studio",
            title = "Video ergonomic analysis",
            supporting = "Computer vision posture assessment using RULA and REBA."
                .takeUnless { state is StudioState.Completed }
        )
        StepIndicator(stepFor(state))
        Spacer(Modifier.height(Space.xxl))

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
                        onChoose = chooseVideo
                    )
                    is StudioState.Ready -> PreviewStep(current.video, chooseVideo)
                    is StudioState.Analyzing -> TwoPane(
                        start = { VideoPreview(current.video.uri, current.video.aspect()) },
                        end = { ProcessingPanel(current, onCancel = AssessmentStudio::cancel) }
                    )
                    is StudioState.Completed -> ResultStep(
                        result = current.result,
                        video = current.video,
                        onOpenDiagnostic = { onOpenDiagnostic(current.recordId) },
                        onNew = AssessmentStudio::reset
                    )
                    is StudioState.Inconclusive -> InconclusiveStep(current.result, chooseVideo)
                    is StudioState.Failed -> FailedStep(current, chooseVideo, onOpenDeveloperOptions)
                }
            }
        }
    }
}

private fun stepFor(state: StudioState): StudioStep = when (state) {
    StudioState.Empty, StudioState.Inspecting -> StudioStep.SELECT
    is StudioState.Ready -> StudioStep.PREVIEW
    is StudioState.Analyzing -> StudioStep.ANALYZE
    is StudioState.Completed, is StudioState.Inconclusive -> StudioStep.RESULTS
    is StudioState.Failed -> if (state.video == null) StudioStep.SELECT else StudioStep.ANALYZE
}

/** Video on the left, workflow on the right; stacked on phones. */
@Composable
private fun TwoPane(
    start: @Composable ColumnScope.() -> Unit,
    end: @Composable ColumnScope.() -> Unit
) {
    if (LocalWindowClass.current == WindowClass.COMPACT) {
        Column(Modifier.fillMaxWidth()) {
            start()
            Spacer(Modifier.height(Space.xl))
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
private fun SelectStep(busy: Boolean, onChoose: () -> Unit) {
    TwoPane(
        start = { UploadPanel(onChoose = onChoose, busy = busy) },
        end = {
            Text("BEFORE YOU FILM", style = VxType.label, color = InkMuted)
            Spacer(Modifier.height(Space.sm))
            CaptureGuidance()
        }
    )
}

@Composable
private fun PreviewStep(video: SelectedVideo, onChooseAnother: () -> Unit) {
    TwoPane(
        start = { VideoPreview(video.uri, video.aspect()) },
        end = {
            Text("SELECTED VIDEO", style = VxType.label, color = InkMuted)
            Spacer(Modifier.height(Space.sm))
            VideoFacts(video)
            Spacer(Modifier.height(Space.lg))
            Text(
                "Analyzing uploads this video to VigilEx, which detects the worker's posture, " +
                    "tracks them through the video and calculates RULA and REBA. " +
                    "The result is saved on this device.",
                style = VxType.bodySmall,
                color = InkSecondary
            )
            Spacer(Modifier.height(Space.lg))
            ButtonRow {
                PrimaryButton(text = "Analyze video", onClick = AssessmentStudio::analyze)
                SecondaryButton(text = "Choose another", onClick = onChooseAnother)
            }
        }
    )
}

/**
 * The conclusion, not a celebration: overall risk with RULA and REBA as its
 * evidence, the key finding, and the way into the full assessment.
 */
@Composable
private fun ResultStep(
    result: AnalysisResult,
    video: SelectedVideo,
    onOpenDiagnostic: () -> Unit,
    onNew: () -> Unit
) {
    val interpretation = remember(result) { interpret(result) }
    val compact = LocalWindowClass.current == WindowClass.COMPACT
    Row(verticalAlignment = Alignment.CenterVertically) {
        KeySquare(Yellow, size = 10.dp)
        Spacer(Modifier.width(Space.sm))
        Text("ASSESSMENT COMPLETE", style = VxType.labelLarge, color = Ink)
    }
    Spacer(Modifier.height(Space.xxs))
    Text(video.displayName, style = VxType.bodySmall, color = InkMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
    Spacer(Modifier.height(Space.xl))

    val key = interpretation.keyFinding
    if (compact || key == null) {
        RiskConclusion(interpretation, showGuidance = false)
        key?.let {
            Spacer(Modifier.height(Space.xl))
            KeyFinding(it)
        }
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(Space.xxl), verticalAlignment = Alignment.Top) {
            RiskConclusion(interpretation, Modifier.weight(1.2f), showGuidance = false)
            KeyFinding(key, Modifier.weight(1f))
        }
    }
    Spacer(Modifier.height(Space.xl))
    ButtonRow {
        PrimaryButton(text = "View full assessment", onClick = onOpenDiagnostic, icon = Icons.AutoMirrored.Outlined.ArrowForward)
        SecondaryButton(text = "New assessment", onClick = onNew)
    }
}

@Composable
private fun InconclusiveStep(result: AnalysisResult, onChooseAnother: () -> Unit) {
    val sampled = result.video?.framesSampled ?: 0
    val detection = formatPercent(result.quality?.detectionFraction)
    ErrorState(
        title = "No worker assessed",
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
private fun FailedStep(state: StudioState.Failed, onChooseAnother: () -> Unit, onOpenDeveloperOptions: () -> Unit) {
    val failure = state.failure
    val developer by ServerSettings.developerOptions.collectAsState()
    ErrorState(
        title = failure.title,
        message = failure.message,
        actions = {
            if (failure.canRetry && state.video != null) {
                PrimaryButton(text = "Try again", onClick = AssessmentStudio::analyze)
                SecondaryButton(text = "Choose another video", onClick = onChooseAnother)
            } else {
                PrimaryButton(text = "Choose video", onClick = onChooseAnother)
            }
        }
    )
    if (developer && failure.developerDetail != null) {
        Text(failure.developerDetail, style = VxType.mono, color = InkMuted)
        Spacer(Modifier.height(Space.sm))
    }
    // Only developers can change the server; normal users just retry.
    if (failure.suggestSettings && developer) {
        ArrowLink("Developer options", onClick = onOpenDeveloperOptions)
    }
}
