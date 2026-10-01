package com.extrive.vigilex.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.extrive.vigilex.data.model.Keyframe
import com.extrive.vigilex.data.model.Landmark
import com.extrive.vigilex.data.model.Method
import com.extrive.vigilex.data.model.riskTone
import com.extrive.vigilex.data.model.score
import com.extrive.vigilex.ui.format.formatSeconds
import com.extrive.vigilex.ui.theme.Black
import com.extrive.vigilex.ui.theme.Hairline
import com.extrive.vigilex.ui.theme.HairlineStrong
import com.extrive.vigilex.ui.theme.Ink
import com.extrive.vigilex.ui.theme.InkMuted
import com.extrive.vigilex.ui.theme.Radius
import com.extrive.vigilex.ui.theme.Space
import com.extrive.vigilex.ui.theme.SurfaceSunken
import com.extrive.vigilex.ui.theme.VxType
import com.extrive.vigilex.ui.theme.White
import com.extrive.vigilex.ui.theme.Yellow

/** COCO-17 skeleton connections (YOLO11n-Pose keypoint order). */
val CocoEdges = listOf(
    0 to 1, 0 to 2, 1 to 3, 2 to 4,
    5 to 6, 5 to 7, 7 to 9, 6 to 8, 8 to 10,
    5 to 11, 6 to 12, 11 to 12,
    11 to 13, 13 to 15, 12 to 14, 14 to 16
)

/** Points below this confidence are not drawn (matches the backend's landmark threshold). */
const val DISPLAY_CONFIDENCE = 0.5

/**
 * The analysed frame with the detected pose drawn over it. Landmarks are in
 * the coordinate space of the frame the backend analysed (`video.width` ×
 * `video.height`), so they are scaled to the displayed box.
 */
@Composable
fun PoseEvidence(
    keyframe: Keyframe,
    frameWidth: Int,
    frameHeight: Int,
    frame: ImageBitmap?,
    modifier: Modifier = Modifier
) {
    val aspect = if (frameWidth > 0 && frameHeight > 0) frameWidth.toFloat() / frameHeight else 16f / 9f
    Box(
        modifier
            .fillMaxWidth()
            .aspectRatio(aspect)
            .clip(Radius.small)
            .background(if (frame != null) Black else SurfaceSunken)
            .semantics { contentDescription = "Detected pose at ${formatSeconds(keyframe.t)}" }
    ) {
        if (frame != null) {
            Image(
                bitmap = frame,
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                modifier = Modifier.fillMaxSize()
            )
        }
        Canvas(Modifier.fillMaxSize()) {
            drawSkeleton(keyframe.landmarks, frameWidth, frameHeight, onImage = frame != null)
        }
    }
}

private fun DrawScope.drawSkeleton(points: List<Landmark>, frameWidth: Int, frameHeight: Int, onImage: Boolean) {
    if (points.size < 17 || frameWidth <= 0 || frameHeight <= 0) return
    val sx = size.width / frameWidth
    val sy = size.height / frameHeight
    fun at(i: Int): Offset? = points[i].takeIf { it.confidence >= DISPLAY_CONFIDENCE }
        ?.let { Offset((it.x * sx).toFloat(), (it.y * sy).toFloat()) }

    val bone = if (onImage) Yellow else Ink
    val boneWidth = 3.dp.toPx()
    CocoEdges.forEach { (a, b) ->
        val p1 = at(a) ?: return@forEach
        val p2 = at(b) ?: return@forEach
        // Dark underlay keeps the line legible on bright footage; no glow.
        if (onImage) drawLine(Black.copy(alpha = 0.55f), p1, p2, boneWidth + 2.dp.toPx(), StrokeCap.Round)
        drawLine(bone, p1, p2, boneWidth, StrokeCap.Round)
    }
    points.indices.forEach { i ->
        val p = at(i) ?: return@forEach
        drawCircle(if (onImage) Black else Ink, radius = 4.5.dp.toPx(), center = p)
        drawCircle(if (onImage) White else SurfaceSunken, radius = 2.5.dp.toPx(), center = p)
    }
}

/**
 * Per-frame RULA and REBA scores over the video. Tap or drag to pick the
 * frame shown as evidence. Bars are coloured by each frame's backend risk band.
 */
@Composable
fun ScoreTimeline(
    keyframes: List<Keyframe>,
    durationSec: Double?,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    if (keyframes.isEmpty()) return
    Column(modifier.fillMaxWidth()) {
        Method.entries.forEach { method ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.width(52.dp)) {
                    Text(method.label, style = VxType.label, color = Ink)
                    Text("0–${method.scaleMax}", style = VxType.mono.copy(fontSize = VxType.label.fontSize), color = InkMuted)
                }
                TimelineBars(
                    keyframes = keyframes,
                    method = method,
                    selected = selected,
                    onSelect = onSelect,
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp)
                )
            }
            Spacer(Modifier.height(Space.sm))
        }
        Row(Modifier.padding(start = 52.dp)) {
            Text("0 s", style = VxType.mono, color = InkMuted, modifier = Modifier.weight(1f))
            Text(formatSeconds(durationSec ?: keyframes.last().t), style = VxType.mono, color = InkMuted)
        }
    }
}

@Composable
private fun TimelineBars(
    keyframes: List<Keyframe>,
    method: Method,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier
) {
    val count = keyframes.size
    fun indexAt(x: Float, width: Int): Int = ((x / width) * count).toInt().coerceIn(0, count - 1)

    Canvas(
        modifier
            .semantics { contentDescription = "${method.label} score per sampled frame" }
            .pointerInput(count) {
                detectTapGestures { onSelect(indexAt(it.x, size.width)) }
            }
            .pointerInput(count) {
                detectHorizontalDragGestures { change, _ -> onSelect(indexAt(change.position.x, size.width)) }
            }
    ) {
        val slot = size.width / count
        val gap = if (slot > 6.dp.toPx()) 2.dp.toPx() else 0.5.dp.toPx()
        drawLine(HairlineStrong, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
        keyframes.forEachIndexed { i, frame ->
            val score = frame.score(method)
            val left = i * slot + gap / 2
            val width = (slot - gap).coerceAtLeast(1f)
            val value = score?.score
            if (value == null) {
                drawRect(Hairline, Offset(left, size.height - 2.dp.toPx()), Size(width, 2.dp.toPx()))
            } else {
                val h = size.height * (value.toFloat() / method.scaleMax).coerceIn(0.04f, 1f)
                val color = riskTone(method, score.risk).color()
                drawRect(
                    color = if (i == selected) color else color.copy(alpha = 0.55f),
                    topLeft = Offset(left, size.height - h),
                    size = Size(width, h)
                )
            }
            if (i == selected) {
                drawLine(Ink, Offset(left + width / 2, 0f), Offset(left + width / 2, size.height), 1.dp.toPx())
            }
        }
    }
}

/**
 * Peak scores across saved assessments in chronological order: a restrained
 * line with points, the latest point marked in brand yellow.
 */
@Composable
fun TrendChart(
    values: List<Int?>,
    scaleMax: Int,
    modifier: Modifier = Modifier,
    lineColor: Color = Ink
) {
    Canvas(
        modifier
            .fillMaxWidth()
            .height(140.dp)
            .semantics { contentDescription = "Trend of ${values.size} assessments" }
    ) {
        val left = 0f
        val right = size.width
        val top = 8.dp.toPx()
        val bottom = size.height - 8.dp.toPx()
        listOf(0f, 0.5f, 1f).forEach { f ->
            val y = bottom - (bottom - top) * f
            drawLine(if (f == 0f) HairlineStrong else Hairline, Offset(left, y), Offset(right, y), 1.dp.toPx())
        }
        if (values.isEmpty()) return@Canvas
        val step = if (values.size > 1) (right - left - 16.dp.toPx()) / (values.size - 1) else 0f
        val points = values.mapIndexed { i, v ->
            v?.let { Offset(left + 8.dp.toPx() + step * i, bottom - (bottom - top) * (it.toFloat() / scaleMax)) }
        }
        points.zipWithNext().forEach { (a, b) ->
            if (a != null && b != null) drawLine(lineColor, a, b, 2.dp.toPx(), StrokeCap.Round)
        }
        points.forEachIndexed { i, p ->
            if (p == null) return@forEachIndexed
            val last = i == points.lastIndex
            drawCircle(Ink, radius = if (last) 6.dp.toPx() else 3.5.dp.toPx(), center = p)
            if (last) drawCircle(Yellow, radius = 4.dp.toPx(), center = p)
        }
    }
}
