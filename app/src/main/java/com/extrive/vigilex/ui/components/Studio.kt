package com.extrive.vigilex.ui.components

import android.net.Uri
import android.provider.Settings
import android.widget.VideoView
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Accessibility
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CropFree
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.extrive.vigilex.data.media.SelectedVideo
import com.extrive.vigilex.ui.format.EMPTY_VALUE
import com.extrive.vigilex.ui.format.formatBytes
import com.extrive.vigilex.ui.format.formatElapsed
import com.extrive.vigilex.ui.format.formatResolution
import com.extrive.vigilex.ui.format.formatSeconds
import com.extrive.vigilex.ui.studio.StudioState
import com.extrive.vigilex.ui.studio.UploadPhase
import com.extrive.vigilex.ui.theme.Black
import com.extrive.vigilex.ui.theme.Hairline
import com.extrive.vigilex.ui.theme.HairlineStrong
import com.extrive.vigilex.ui.theme.Ink
import com.extrive.vigilex.ui.theme.InkMuted
import com.extrive.vigilex.ui.theme.InkSecondary
import com.extrive.vigilex.ui.theme.Motion
import com.extrive.vigilex.ui.theme.Radius
import com.extrive.vigilex.ui.theme.Space
import com.extrive.vigilex.ui.theme.SurfaceSunken
import com.extrive.vigilex.ui.theme.VxType
import com.extrive.vigilex.ui.theme.White
import com.extrive.vigilex.ui.theme.Yellow
import kotlinx.coroutines.delay

/** True when the user turned animations off (Settings › Accessibility › Remove animations). */
@Composable
fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}

// ------------------------------------------------------------------ steps

enum class StudioStep(val label: String) { SELECT("Video"), PREVIEW("Preview"), ANALYZE("Analyze"), RESULTS("Results") }

/** Four short segments with labels; the current step is yellow and announced. */
@Composable
fun StepIndicator(current: StudioStep, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .clearAndSetSemantics {
                contentDescription = "Step ${current.ordinal + 1} of ${StudioStep.entries.size}: ${current.label}"
            },
        horizontalArrangement = Arrangement.spacedBy(Space.xs)
    ) {
        StudioStep.entries.forEach { step ->
            val reached = step.ordinal <= current.ordinal
            val isCurrent = step == current
            Column(Modifier.weight(1f)) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(Radius.small)
                        .background(
                            when {
                                isCurrent -> Yellow
                                reached -> Ink
                                else -> Hairline
                            }
                        )
                )
                Spacer(Modifier.height(Space.xxs))
                Text(
                    step.label,
                    style = VxType.label,
                    color = if (isCurrent || reached) Ink else InkMuted,
                    maxLines = 1
                )
            }
        }
    }
}

// ------------------------------------------------------------------ entry

/** The two ways in: record (primary) or choose an existing video (secondary). */
@Composable
fun UploadPanel(
    onRecord: () -> Unit,
    onChoose: () -> Unit,
    busy: Boolean,
    modifier: Modifier = Modifier
) {
    Column(modifier.fillMaxWidth()) {
        if (busy) {
            Row(
                Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(color = Ink, trackColor = Hairline, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(Space.sm))
                Text("Reading video…", style = VxType.bodySmall, color = InkSecondary)
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                PrimaryButton(text = "Record a video", onClick = onRecord, icon = Icons.Outlined.Videocam, modifier = Modifier.fillMaxWidth())
                SecondaryButton(text = "Choose from device", onClick = onChoose, icon = Icons.Outlined.VideoLibrary, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

/**
 * Further filming suggestions, collapsed by default. They improve what the
 * camera can show; they do not guarantee a particular result.
 */
@Composable
fun RecordingTips(modifier: Modifier = Modifier) {
    ExpandableSection(title = "Recording tips", modifier = modifier, summary = "How to film so posture can be measured") {
        listOf(
            "Hold the camera steady at about hip height, or rest it on a surface.",
            "Film a few repetitions of the task; about 10–20 seconds is usually enough.",
            "Stand far enough back that the worker stays in frame while they move.",
            "Use good light, and avoid people walking between the camera and the worker.",
            "Keep the video unedited; VigilEx samples frames from the whole clip."
        ).forEach { tip ->
            Row(Modifier.padding(vertical = Space.xxs), verticalAlignment = Alignment.Top) {
                Text("•", style = VxType.body, color = InkMuted, modifier = Modifier.width(Space.md))
                Text(tip, style = VxType.bodySmall, color = InkSecondary)
            }
        }
        Spacer(Modifier.height(Space.xs))
        Text(
            "These suggestions help the camera show posture clearly. They do not guarantee a particular score.",
            style = VxType.bodySmall,
            color = InkMuted
        )
    }
}

/**
 * Filming guidance grounded in how the pipeline works: posture geometry is
 * measured in the side (sagittal) plane and one tracked worker is scored.
 */
@Composable
fun CaptureGuidance(modifier: Modifier = Modifier) {
    val tips = listOf(
        Triple(Icons.Outlined.Accessibility, "Film from the side", "Joint angles are measured in the side view of the body."),
        Triple(Icons.Outlined.CropFree, "Keep the whole body in frame", "Head, arms, hips, knees and ankles are all used."),
        Triple(Icons.Outlined.Person, "One worker per video", "VigilEx tracks and scores a single worker.")
    )
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.md)) {
        tips.forEach { (icon, title, body) ->
            Row(verticalAlignment = Alignment.Top) {
                Icon(icon, contentDescription = null, tint = InkSecondary, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(Space.sm))
                Column {
                    Text(title, style = VxType.title, color = Ink)
                    Text(body, style = VxType.bodySmall, color = InkSecondary)
                }
            }
        }
    }
}

// ------------------------------------------------------------------ preview

/**
 * Plays the selected local video. Tap to play or pause; nothing auto-plays.
 * Height is capped at [maxHeight] so a portrait video never pushes the
 * actions off a phone screen; it is centred on a dark field. If this device's
 * player can't play the file, a note replaces it (never the system error dialog).
 */
@Composable
fun VideoPreview(uri: Uri, aspectRatio: Float, modifier: Modifier = Modifier, maxHeight: Dp = 360.dp) {
    val context = LocalContext.current
    var playing by remember(uri) { mutableStateOf(false) }
    var failed by remember(uri) { mutableStateOf(false) }
    val videoView = remember(uri) {
        VideoView(context).apply {
            setVideoURI(uri)
            setOnPreparedListener { player ->
                player.isLooping = false
                seekTo(1) // Show the first frame instead of black.
            }
            setOnCompletionListener { playing = false }
            // Returning true suppresses VideoView's own "Can't play this video" dialog.
            setOnErrorListener { _, _, _ ->
                playing = false
                failed = true
                true
            }
        }
    }
    DisposableEffect(videoView) {
        onDispose { videoView.stopPlayback() }
    }

    Box(
        modifier
            .fillMaxWidth()
            .heightIn(max = maxHeight)
            .clip(Radius.card)
            .background(Black),
        contentAlignment = Alignment.Center
    ) {
        if (failed) {
            Box(
                Modifier
                    .aspectRatio(aspectRatio.coerceIn(0.4f, 2.4f), matchHeightConstraintsFirst = aspectRatio < 1f)
                    .padding(Space.lg),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "This device can't play a preview of this video. You can still send it for analysis.",
                    style = VxType.bodySmall,
                    color = White
                )
            }
            return@Box
        }
        Box(
            Modifier
                .aspectRatio(aspectRatio.coerceIn(0.4f, 2.4f), matchHeightConstraintsFirst = aspectRatio < 1f)
                .clickable(role = Role.Button, onClickLabel = if (playing) "Pause" else "Play") {
                    if (playing) videoView.pause() else videoView.start()
                    playing = !playing
                }
                .semantics { contentDescription = if (playing) "Video preview, playing" else "Video preview, paused" },
            contentAlignment = Alignment.Center
        ) {
            AndroidView(factory = { videoView }, modifier = Modifier.fillMaxSize())
            val overlayAlpha by animateFloatAsState(if (playing) 0f else 1f, tween(Motion.fast), label = "playOverlay")
            Box(
                Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(Black.copy(alpha = 0.6f * overlayAlpha)),
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
}

/**
 * Name and validation of the selected video: the checks this device made
 * (it opened, has a readable video track and is not empty) before anything is uploaded.
 */
@Composable
fun SelectedVideoSummary(video: SelectedVideo, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        Text(video.displayName, style = VxType.title, color = Ink)
        val facts = listOfNotNull(
            video.durationMs?.let { formatSeconds(it / 1000.0) },
            formatResolution(video.width, video.height).takeIf { it != EMPTY_VALUE },
            formatBytes(video.sizeBytes).takeIf { it != EMPTY_VALUE }
        )
        if (facts.isNotEmpty()) Text(facts.joinToString(" · "), style = VxType.bodySmall, color = InkMuted)
        Spacer(Modifier.height(Space.xs))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.semantics(mergeDescendants = true) {}) {
            Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = Ink, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(Space.xs))
            Text("Video can be read · ready to analyze", style = VxType.bodySmall, color = Ink)
        }
    }
}

/** File, duration, size and resolution of the local video; facts the device could not read are left out. */
@Composable
fun VideoFacts(video: SelectedVideo, modifier: Modifier = Modifier) {
    KeyValueTable(
        rows = listOf(
            "File" to video.displayName,
            "Duration" to formatSeconds(video.durationMs?.let { it / 1000.0 }),
            "Size" to formatBytes(video.sizeBytes),
            "Resolution" to formatResolution(video.width, video.height)
        ).filter { it.second != EMPTY_VALUE },
        modifier = modifier
    )
}

fun SelectedVideo.aspect(): Float =
    if (width != null && height != null && width > 0 && height > 0) width.toFloat() / height else 16f / 9f

// --------------------------------------------------------------- processing

/**
 * Honest progress. Upload progress is measured; server-side work is not, so
 * it is shown as one indeterminate stage with elapsed time, and what the
 * server does is described rather than ticked off.
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

    VxCard(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    "Analyzing assessment",
                    style = VxType.sectionTitle,
                    color = Ink,
                    modifier = Modifier.semantics { heading() }
                )
                Text(
                    state.video.displayName,
                    style = VxType.bodySmall,
                    color = InkMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(Space.sm))
            Text(
                formatElapsed(now - state.startedAtMillis),
                style = VxType.mono,
                color = InkMuted,
                modifier = Modifier.semantics { contentDescription = "Elapsed ${formatElapsed(now - state.startedAtMillis)}" }
            )
        }
        Spacer(Modifier.height(Space.md))

        // 1. Upload: measured.
        Stage(title = "Upload video", done = uploading == null, active = uploading != null)
        if (uploading != null) {
            Spacer(Modifier.height(Space.xs))
            val total = uploading.totalBytes
            val fraction = if (total != null && total > 0) (uploading.sentBytes.toFloat() / total).coerceIn(0f, 1f) else null
            if (fraction != null) {
                val animated by animateFloatAsState(fraction, tween(Motion.base), label = "upload")
                ProgressTrack { Box(Modifier.fillMaxWidth(animated).fillMaxHeight().background(Ink)) }
                Spacer(Modifier.height(Space.xxs))
                Text(
                    "${(fraction * 100).toInt()}% · ${formatBytes(uploading.sentBytes)} of ${formatBytes(total)}",
                    style = VxType.label,
                    color = InkMuted
                )
            } else {
                IndeterminateTrack()
                Spacer(Modifier.height(Space.xxs))
                Text("${formatBytes(uploading.sentBytes)} sent", style = VxType.label, color = InkMuted)
            }
        }
        Spacer(Modifier.height(Space.sm))

        // 2. Server analysis: not measurable, so no percentage and no scores until the result arrives.
        Stage(title = "Analyze posture on the server", done = false, active = uploading == null)
        if (uploading == null) {
            Spacer(Modifier.height(Space.xs))
            IndeterminateTrack()
        }
        Spacer(Modifier.height(Space.md))
        Text(
            if (uploading != null) {
                "The video is being sent to the VigilEx server. The server then detects the worker's posture, " +
                    "follows them through the video and calculates RULA and REBA."
            } else {
                "The server is detecting the worker's posture, following them through the video and calculating " +
                    "RULA and REBA. Longer videos take more time. You can leave this screen; the analysis continues " +
                    "and the result opens when you come back."
            },
            style = VxType.bodySmall,
            color = InkSecondary
        )
        // Only an upload can be stopped safely: once the server has the video it creates the
        // assessment and finishes it, so a later "cancel" would leave an assessment behind.
        if (uploading != null) {
            Spacer(Modifier.height(Space.md))
            SecondaryButton(text = "Cancel upload", onClick = onCancel, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun Stage(title: String, done: Boolean, active: Boolean) {
    val (icon, tint, state) = when {
        done -> Triple<ImageVector, Color, String>(Icons.Outlined.CheckCircle, Ink, "done")
        active -> Triple(Icons.Outlined.RadioButtonUnchecked, Ink, "in progress")
        else -> Triple(Icons.Outlined.RadioButtonUnchecked, HairlineStrong, "waiting")
    }
    Row(
        Modifier.clearAndSetSemantics {
            contentDescription = "$title, $state"
            if (active) liveRegion = LiveRegionMode.Polite
        },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(Space.sm))
        Text(title, style = VxType.labelLarge, color = if (done || active) Ink else InkMuted, modifier = Modifier.weight(1f))
        if (done) Text("Done", style = VxType.label, color = InkMuted)
    }
}

@Composable
private fun ProgressTrack(content: @Composable () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(4.dp)
            .clip(Radius.small)
            .background(SurfaceSunken)
    ) { content() }
}

/**
 * A slow sweep, used only where no real progress figure exists. With
 * animations turned off it is a static half bar beside the stage text.
 */
@Composable
fun IndeterminateTrack() {
    if (rememberReducedMotion()) {
        ProgressTrack { Box(Modifier.fillMaxWidth(0.5f).fillMaxHeight().background(InkMuted)) }
        return
    }
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
