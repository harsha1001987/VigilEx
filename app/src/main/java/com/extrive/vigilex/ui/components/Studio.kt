package com.extrive.vigilex.ui.components

import android.net.Uri
import android.widget.VideoView
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.extrive.vigilex.data.media.SelectedVideo
import com.extrive.vigilex.ui.format.formatBytes
import com.extrive.vigilex.ui.format.formatElapsed
import com.extrive.vigilex.ui.format.formatResolution
import com.extrive.vigilex.ui.format.formatSeconds
import com.extrive.vigilex.ui.studio.StudioState
import com.extrive.vigilex.ui.studio.UploadPhase
import com.extrive.vigilex.ui.theme.Black
import com.extrive.vigilex.ui.theme.Border
import com.extrive.vigilex.ui.theme.Hairline
import com.extrive.vigilex.ui.theme.HairlineStrong
import com.extrive.vigilex.ui.theme.Ink
import com.extrive.vigilex.ui.theme.InkFaint
import com.extrive.vigilex.ui.theme.InkMuted
import com.extrive.vigilex.ui.theme.InkSecondary
import com.extrive.vigilex.ui.theme.Motion
import com.extrive.vigilex.ui.theme.Radius
import com.extrive.vigilex.ui.theme.Space
import com.extrive.vigilex.ui.theme.Surface
import com.extrive.vigilex.ui.theme.VxType
import com.extrive.vigilex.ui.theme.White
import com.extrive.vigilex.ui.theme.Yellow
import kotlinx.coroutines.delay

// ------------------------------------------------------------------ steps

enum class StudioStep(val label: String) { SELECT("Select"), PREVIEW("Preview"), ANALYZE("Analyze"), RESULTS("Results") }

/** 01 Select · 02 Preview · 03 Analyze · 04 Results, current step underlined. */
@Composable
fun StepIndicator(current: StudioStep, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
        StudioStep.entries.forEach { step ->
            val reached = step.ordinal <= current.ordinal
            val isCurrent = step == current
            Column(
                Modifier
                    .weight(1f)
                    .semantics { if (isCurrent) contentDescription = "Current step: ${step.label}" }
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(if (isCurrent) 3.dp else 1.dp)
                        .background(if (isCurrent) Yellow else if (reached) Ink else HairlineStrong)
                )
                Spacer(Modifier.height(Space.xs))
                Text(
                    "%02d".format(step.ordinal + 1),
                    style = VxType.mono,
                    color = if (reached) Ink else InkFaint
                )
                Text(
                    step.label.uppercase(),
                    style = VxType.label,
                    color = if (reached) Ink else InkFaint
                )
            }
        }
    }
}

// ------------------------------------------------------------------ upload

@Composable
fun UploadPanel(onChoose: () -> Unit, busy: Boolean, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(Radius.small)
            .background(Surface)
            .border(Border.hairline, HairlineStrong, Radius.small)
            .clickable(enabled = !busy, role = Role.Button, onClick = onChoose)
            .padding(horizontal = Space.lg, vertical = Space.xxl)
    ) {
        Text("SELECT VIDEO", style = VxType.label, color = InkMuted)
        Spacer(Modifier.height(Space.sm))
        Text("Choose a workplace video", style = VxType.sectionTitle, color = Ink)
        Spacer(Modifier.height(Space.xs))
        Text(
            "MP4, MOV, AVI, 3GP, MKV or WEBM from this device.",
            style = VxType.bodySmall,
            color = InkSecondary
        )
        Spacer(Modifier.height(Space.lg))
        if (busy) {
            Text("Reading video…", style = VxType.bodySmall, color = InkMuted)
        } else {
            PrimaryButton(text = "Choose video", onClick = onChoose)
        }
    }
}

/**
 * Filming guidance grounded in how the pipeline works: posture geometry is
 * measured in the side (sagittal) plane and one tracked worker is scored.
 */
@Composable
fun CaptureGuidance(modifier: Modifier = Modifier) {
    val tips = listOf(
        "Film from the side" to "Joint angles are measured in the side view of the body.",
        "Keep the whole body in frame" to "Head, arms, hips, knees and ankles are all used.",
        "One worker per video" to "VigilEx tracks and scores a single primary worker."
    )
    Column(modifier.fillMaxWidth()) {
        tips.forEachIndexed { index, (title, body) ->
            Hairline()
            Row(Modifier.padding(vertical = Space.md)) {
                Text("%02d".format(index + 1), style = VxType.mono, color = InkMuted, modifier = Modifier.width(36.dp))
                Column {
                    Text(title, style = VxType.title, color = Ink)
                    Spacer(Modifier.height(2.dp))
                    Text(body, style = VxType.bodySmall, color = InkSecondary)
                }
            }
        }
        Hairline()
    }
}

// ------------------------------------------------------------------ preview

/** Plays the selected local video. Tap to play or pause; nothing auto-plays. */
@Composable
fun VideoPreview(uri: Uri, aspectRatio: Float, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var playing by remember(uri) { mutableStateOf(false) }
    val videoView = remember(uri) {
        VideoView(context).apply {
            setVideoURI(uri)
            setOnPreparedListener { player ->
                player.isLooping = false
                seekTo(1) // Show the first frame instead of black.
            }
            setOnCompletionListener { playing = false }
        }
    }
    DisposableEffect(videoView) {
        onDispose { videoView.stopPlayback() }
    }

    Box(
        modifier
            .fillMaxWidth()
            .aspectRatio(aspectRatio.coerceIn(0.4f, 2.4f))
            .clip(Radius.small)
            .background(Black)
            .clickable(role = Role.Button) {
                if (playing) videoView.pause() else videoView.start()
                playing = !playing
            }
            .semantics { contentDescription = if (playing) "Pause video preview" else "Play video preview" },
        contentAlignment = Alignment.Center
    ) {
        AndroidView(factory = { videoView }, modifier = Modifier.fillMaxSize())
        val overlayAlpha by animateFloatAsState(if (playing) 0f else 1f, tween(Motion.fast), label = "playOverlay")
        Box(
            Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(Black.copy(alpha = 0.72f * overlayAlpha)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = null,
                tint = White.copy(alpha = overlayAlpha),
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

/** FILE / SIZE / DURATION / RESOLUTION of the local video, read on device. */
@Composable
fun VideoFacts(video: SelectedVideo, modifier: Modifier = Modifier) {
    KeyValueTable(
        rows = listOf(
            "File" to video.displayName,
            "Size" to formatBytes(video.sizeBytes),
            "Duration" to formatSeconds(video.durationMs?.let { it / 1000.0 }),
            "Resolution" to formatResolution(video.width, video.height)
        ),
        modifier = modifier
    )
}

fun SelectedVideo.aspect(): Float =
    if (width != null && height != null && width > 0 && height > 0) width.toFloat() / height else 16f / 9f

// --------------------------------------------------------------- processing

private val serverStages = listOf(
    "Detecting posture",
    "Tracking worker",
    "Calculating RULA",
    "Calculating REBA",
    "Preparing assessment"
)

/**
 * Honest progress: upload progress is measured, server-side work is not, so
 * the server stages are listed as what happens, with elapsed time only.
 */
@Composable
fun ProcessingPanel(state: StudioState.Analyzing, onCancel: () -> Unit, modifier: Modifier = Modifier) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(state.startedAtMillis) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    val uploading = state.phase as? UploadPhase.Uploading

    Column(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text("ANALYZING VIDEO", style = VxType.labelLarge, color = Ink, modifier = Modifier.weight(1f))
            Text(formatElapsed(now - state.startedAtMillis), style = VxType.mono, color = InkMuted)
        }
        Spacer(Modifier.height(Space.xs))
        Text(state.video.displayName, style = VxType.bodySmall, color = InkSecondary, maxLines = 1)
        Spacer(Modifier.height(Space.lg))

        // 01 Upload: measured.
        StageHeader(number = 1, title = "Uploading video", done = uploading == null)
        Spacer(Modifier.height(Space.sm))
        if (uploading != null) {
            val total = uploading.totalBytes
            val fraction = if (total != null && total > 0) (uploading.sentBytes.toFloat() / total).coerceIn(0f, 1f) else null
            if (fraction != null) {
                val animated by animateFloatAsState(fraction, tween(Motion.base), label = "upload")
                ProgressTrack { Box(Modifier.fillMaxWidth(animated).fillMaxHeight().background(Ink)) }
                Spacer(Modifier.height(Space.xs))
                Text(
                    "${(fraction * 100).toInt()}% · ${formatBytes(uploading.sentBytes)} of ${formatBytes(total)}",
                    style = VxType.mono,
                    color = InkMuted
                )
            } else {
                IndeterminateTrack()
                Spacer(Modifier.height(Space.xs))
                Text("${formatBytes(uploading.sentBytes)} sent", style = VxType.mono, color = InkMuted)
            }
        } else {
            ProgressTrack { Box(Modifier.fillMaxSize().background(Ink)) }
        }

        Spacer(Modifier.height(Space.xl))

        // 02 Analysis: not measurable, so no percentage and no scores until the result arrives.
        StageHeader(number = 2, title = "Analyzing video", done = false, active = uploading == null)
        Spacer(Modifier.height(Space.sm))
        if (uploading == null) IndeterminateTrack() else ProgressTrack {}
        Spacer(Modifier.height(Space.md))
        serverStages.forEach { stage ->
            Row(
                Modifier.padding(vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                KeySquare(if (uploading == null) Ink else HairlineStrong, size = 4.dp)
                Spacer(Modifier.width(Space.sm))
                Text(stage, style = VxType.bodySmall, color = if (uploading == null) Ink else InkMuted)
            }
        }
        Spacer(Modifier.height(Space.md))
        Text(
            "VigilEx is analyzing your video. Longer videos may take more time.",
            style = VxType.bodySmall,
            color = InkMuted
        )
        Spacer(Modifier.height(Space.lg))
        SecondaryButton(text = "Cancel", onClick = onCancel)
    }
}

@Composable
private fun StageHeader(number: Int, title: String, done: Boolean, active: Boolean = !done) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("%02d".format(number), style = VxType.mono, color = InkMuted, modifier = Modifier.width(32.dp))
        Text(title, style = VxType.title, color = if (active || done) Ink else InkMuted, modifier = Modifier.weight(1f))
        if (done) Text("DONE", style = VxType.label, color = InkMuted)
    }
}

@Composable
private fun ProgressTrack(content: @Composable () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(3.dp)
            .background(Hairline)
    ) { content() }
}

/** A slow sweep, used only where no real progress figure exists. */
@Composable
fun IndeterminateTrack() {
    val transition = rememberInfiniteTransition(label = "indeterminate")
    val offset by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1400, easing = Motion.standard), RepeatMode.Restart),
        label = "sweep"
    )
    ProgressTrack {
        Row(Modifier.fillMaxSize()) {
            if (offset > 0.001f) Spacer(Modifier.weight(offset))
            Box(Modifier.weight(0.3f).fillMaxHeight().background(Ink))
            if (offset < 0.999f) Spacer(Modifier.weight(1f - offset))
        }
    }
}
