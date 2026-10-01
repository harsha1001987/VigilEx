package com.extrive.vigilex.ui.components

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import com.extrive.vigilex.data.insight.EvidenceFrame
import com.extrive.vigilex.data.insight.EvidenceReason
import com.extrive.vigilex.data.media.VideoInspector
import com.extrive.vigilex.data.model.Keyframe
import com.extrive.vigilex.ui.format.formatDegrees
import com.extrive.vigilex.ui.format.formatScale
import com.extrive.vigilex.ui.format.formatScore
import com.extrive.vigilex.ui.format.formatSeconds
import com.extrive.vigilex.ui.theme.Ink
import com.extrive.vigilex.ui.theme.InkMuted
import com.extrive.vigilex.ui.theme.Space
import com.extrive.vigilex.ui.theme.VxType

/** "Peak REBA · 04 / 15 · Medium" or "Peak knee flexion · 107.2°". */
fun EvidenceReason.describe(): String = when (this) {
    is EvidenceReason.PeakScore ->
        "Peak ${method.label} · ${formatScore(score)} / ${formatScale(method.scaleMax)}" +
            (level?.let { " · ${it.label}" } ?: "")
    is EvidenceReason.PeakAngle -> "Peak ${metric.label.lowercase()} · ${formatDegrees(angleDeg)}"
}

/** Null while checking, then whether the original video can still be read on this device. */
@Composable
fun rememberVideoAvailable(videoUri: String?): Boolean? {
    val context = LocalContext.current
    var available by remember(videoUri) { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(videoUri) {
        available = videoUri?.let { VideoInspector.isReadable(context, Uri.parse(it)) } ?: false
    }
    return available
}

/** The video frame at [seconds], or null when unavailable. */
@Composable
fun rememberVideoFrame(videoUri: String?, seconds: Double, available: Boolean?): ImageBitmap? {
    val context = LocalContext.current
    var frame by remember(videoUri, seconds) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(videoUri, seconds, available) {
        if (available == true && videoUri != null) {
            frame = VideoInspector.frameAt(context, Uri.parse(videoUri), seconds, maxWidth = 960)?.asImageBitmap()
        }
    }
    return frame
}

/**
 * The chosen evidence frames: the first large, the others beneath in a row.
 * Each is the real analysed frame with the detected pose, or the pose alone
 * when the video is no longer on the device.
 */
@Composable
fun EvidenceGallery(
    evidence: List<EvidenceFrame>,
    keyframeCount: Int,
    videoUri: String?,
    frameWidth: Int,
    frameHeight: Int,
    modifier: Modifier = Modifier
) {
    if (evidence.isEmpty()) return
    val available = rememberVideoAvailable(videoUri)
    Column(modifier.fillMaxWidth()) {
        EvidenceItem(evidence.first(), keyframeCount, videoUri, available, frameWidth, frameHeight)
        val rest = evidence.drop(1)
        if (rest.isNotEmpty()) {
            Spacer(Modifier.height(Space.lg))
            Row(horizontalArrangement = Arrangement.spacedBy(Space.md)) {
                rest.forEach {
                    EvidenceItem(it, keyframeCount, videoUri, available, frameWidth, frameHeight, Modifier.weight(1f), small = true)
                }
                if (rest.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        if (available == false) {
            Spacer(Modifier.height(Space.sm))
            Text(
                "The original video is not on this device, so only the detected pose is shown.",
                style = VxType.bodySmall,
                color = InkMuted
            )
        }
    }
}

@Composable
private fun EvidenceItem(
    item: EvidenceFrame,
    keyframeCount: Int,
    videoUri: String?,
    available: Boolean?,
    frameWidth: Int,
    frameHeight: Int,
    modifier: Modifier = Modifier,
    small: Boolean = false
) {
    val frame = rememberVideoFrame(videoUri, item.keyframe.t, available)
    Column(modifier) {
        PoseEvidence(item.keyframe, frameWidth, frameHeight, frame)
        Spacer(Modifier.height(Space.xs))
        FrameCaption(item.keyframe, item.index, keyframeCount)
        Text(
            item.reason.describe().uppercase(),
            style = if (small) VxType.label else VxType.labelLarge,
            color = Ink
        )
    }
}

@Composable
private fun FrameCaption(keyframe: Keyframe, index: Int, count: Int) {
    Text(
        "FRAME ${"%02d".format(index + 1)} / ${"%02d".format(count)} · ${formatSeconds(keyframe.t)}",
        style = VxType.label,
        color = InkMuted
    )
}
