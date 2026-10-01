package com.extrive.vigilex.ui.screens

import android.net.Uri
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import com.extrive.vigilex.data.insight.Interpretation
import com.extrive.vigilex.data.insight.interpret
import com.extrive.vigilex.data.media.VideoInspector
import com.extrive.vigilex.data.model.AnalysisResult
import com.extrive.vigilex.data.model.AssessmentRecord
import com.extrive.vigilex.data.model.ComponentRow
import com.extrive.vigilex.data.model.FrameComponent
import com.extrive.vigilex.data.model.Keyframe
import com.extrive.vigilex.data.model.Method
import com.extrive.vigilex.data.model.componentRows
import com.extrive.vigilex.data.model.score
import com.extrive.vigilex.data.store.AssessmentStore
import com.extrive.vigilex.ui.components.ArrowLink
import com.extrive.vigilex.ui.components.BottomActionBar
import com.extrive.vigilex.ui.components.CellText
import com.extrive.vigilex.ui.components.DataTable
import com.extrive.vigilex.ui.components.EmptyState
import com.extrive.vigilex.ui.components.EvidenceGallery
import com.extrive.vigilex.ui.components.KeyValueTable
import com.extrive.vigilex.ui.components.LoadingState
import com.extrive.vigilex.ui.components.LocalWindowClass
import com.extrive.vigilex.ui.components.MeasurementsTable
import com.extrive.vigilex.ui.components.MethodBreakdown
import com.extrive.vigilex.ui.components.PageContainer
import com.extrive.vigilex.ui.components.PoseEvidence
import com.extrive.vigilex.ui.components.PrimaryButton
import com.extrive.vigilex.ui.components.RiskBadge
import com.extrive.vigilex.ui.components.RiskConclusion
import com.extrive.vigilex.ui.components.RiskDistribution
import com.extrive.vigilex.ui.components.ScoreTimeline
import com.extrive.vigilex.ui.components.SecondaryButton
import com.extrive.vigilex.ui.components.SectionLabel
import com.extrive.vigilex.ui.components.SubPageHeader
import com.extrive.vigilex.ui.components.TableColumn
import com.extrive.vigilex.ui.components.WhatWeFound
import com.extrive.vigilex.ui.components.WhyThisResult
import com.extrive.vigilex.ui.components.WindowClass
import com.extrive.vigilex.ui.format.EMPTY_VALUE
import com.extrive.vigilex.ui.format.formatDateTime
import com.extrive.vigilex.ui.format.formatDegrees
import com.extrive.vigilex.ui.format.formatFps
import com.extrive.vigilex.ui.format.formatPercent
import com.extrive.vigilex.ui.format.formatResolution
import com.extrive.vigilex.ui.format.formatScore
import com.extrive.vigilex.ui.format.formatSeconds
import com.extrive.vigilex.ui.theme.Ink
import com.extrive.vigilex.ui.theme.InkMuted
import com.extrive.vigilex.ui.theme.InkSecondary
import com.extrive.vigilex.ui.theme.Red
import com.extrive.vigilex.ui.theme.Space
import com.extrive.vigilex.ui.theme.VxType
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val FRAME_DEBOUNCE_MS = 120L

private sealed interface DetailLoad {
    data object Loading : DetailLoad
    data object Missing : DetailLoad
    data class Ready(val result: AnalysisResult, val interpretation: Interpretation) : DetailLoad
}

/**
 * The assessment, in two levels. Level one answers what VigilEx found, how
 * serious it is and what drives it. Level two (ASSESSMENT DETAIL) holds the
 * RULA/REBA breakdowns, frame-by-frame scores, capture quality and method.
 */
@Composable
fun AssessmentDetailScreen(id: String, onBack: () -> Unit, onGenerateReport: () -> Unit) {
    val records by AssessmentStore.records.collectAsState()
    val loaded by AssessmentStore.loaded.collectAsState()
    val record = records.firstOrNull { it.id == id }
    val compact = LocalWindowClass.current == WindowClass.COMPACT

    var load by remember(id) { mutableStateOf<DetailLoad>(DetailLoad.Loading) }
    LaunchedEffect(id, loaded) {
        if (!loaded) return@LaunchedEffect
        load = AssessmentStore.loadResult(id)?.let { DetailLoad.Ready(it, interpret(it)) } ?: DetailLoad.Missing
    }

    val scroll = rememberScrollState()
    val ready = load as? DetailLoad.Ready
    var viewportTop by remember { mutableFloatStateOf(0f) }
    Box(Modifier.fillMaxSize().onGloballyPositioned { viewportTop = it.positionInRoot().y }) {
        PageContainer(scrollState = scroll) {
            SubPageHeader(
                context = "Assessment",
                meta = record?.let { "${it.fileName} · ${formatDateTime(it.createdAtMillis)}" },
                onBack = onBack
            )
            val current = load
            when {
                current is DetailLoad.Loading -> LoadingState()
                current is DetailLoad.Missing || record == null -> EmptyState(
                    title = "Assessment not found",
                    body = "This assessment is not saved on this device. It may have been deleted."
                )
                current is DetailLoad.Ready -> AssessmentContent(
                    record = record,
                    result = current.result,
                    interpretation = current.interpretation,
                    scroll = scroll,
                    viewportTop = viewportTop,
                    onGenerateReport = onGenerateReport,
                    onDeleted = onBack
                )
            }
            // Room for the sticky action bar on phones.
            if (compact && ready != null) Spacer(Modifier.height(Space.huge))
        }
        if (compact && ready != null && record != null) {
            BottomActionBar(Modifier.align(Alignment.BottomCenter)) {
                PrimaryButton("Generate report", onClick = onGenerateReport, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun AssessmentContent(
    record: AssessmentRecord,
    result: AnalysisResult,
    interpretation: Interpretation,
    scroll: ScrollState,
    viewportTop: Float,
    onGenerateReport: () -> Unit,
    onDeleted: () -> Unit
) {
    val compact = LocalWindowClass.current == WindowClass.COMPACT
    val expanded = LocalWindowClass.current == WindowClass.EXPANDED
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    var confirmDelete by remember { mutableStateOf(false) }

    // Anchors for "View RULA / REBA breakdown", stored as offsets within the scrolled content.
    var rulaAnchor by remember { mutableFloatStateOf(0f) }
    var rebaAnchor by remember { mutableFloatStateOf(0f) }
    val margin = with(density) { Space.lg.toPx() }
    fun scrollTo(anchor: Float) {
        scope.launch { scroll.animateScrollTo((anchor - viewportTop - margin).toInt().coerceAtLeast(0)) }
    }

    val frameWidth = result.video?.width ?: 0
    val frameHeight = result.video?.height ?: 0
    val evidence: @Composable () -> Unit = {
        if (interpretation.evidence.isNotEmpty()) {
            SectionLabel("Posture evidence")
            EvidenceGallery(
                evidence = interpretation.evidence,
                keyframeCount = result.keyframes.size,
                videoUri = record.videoUri,
                frameWidth = frameWidth,
                frameHeight = frameHeight
            )
        }
    }

    // ---------------------------------------------------------- level one
    if (expanded) {
        // Conclusion and findings on the left, the evidence that supports them on the right.
        Row(horizontalArrangement = Arrangement.spacedBy(Space.xxl), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                RiskConclusion(interpretation)
                Spacer(Modifier.height(Space.xxl))
                WhatWeFound(interpretation.findings)
                Spacer(Modifier.height(Space.xl))
                PrimaryButton("Generate report", onClick = onGenerateReport)
            }
            Column(Modifier.weight(1.1f)) { evidence() }
        }
    } else {
        RiskConclusion(interpretation)
        Spacer(Modifier.height(Space.xxl))
        WhatWeFound(interpretation.findings)
        if (!compact) {
            Spacer(Modifier.height(Space.xl))
            PrimaryButton("Generate report", onClick = onGenerateReport)
        }
    }
    Spacer(Modifier.height(Space.xxl))

    SectionLabel("Why this result")
    WhyThisResult(
        regions = interpretation.regions,
        onViewRula = { scrollTo(rulaAnchor) },
        onViewReba = { scrollTo(rebaAnchor) }
    )
    Spacer(Modifier.height(Space.xxl))

    if (!expanded) {
        evidence()
        Spacer(Modifier.height(Space.xxl))
    }

    SectionLabel("Measurements")
    MeasurementsTable(interpretation.regions)
    Spacer(Modifier.height(Space.sm))
    Text(
        "Joint angles in degrees across the frames where each region was measured. " +
            "Status reflects the RULA and REBA bands the region reached.",
        style = VxType.bodySmall,
        color = InkMuted
    )
    Spacer(Modifier.height(Space.huge))

    // ---------------------------------------------------------- level two
    Text("ASSESSMENT DETAIL", style = VxType.label, color = InkMuted)
    Spacer(Modifier.height(Space.xs))
    Text("How the scores were reached", style = VxType.pageTitleCompact, color = Ink)
    Spacer(Modifier.height(Space.xl))

    val peakLabel = { method: Method ->
        interpretation.method(method).peakFrameIndex?.let { i ->
            "frame ${i + 1}, ${formatSeconds(result.keyframes[i].t)}"
        }
    }
    val rulaBlock: @Composable (Modifier) -> Unit = { m ->
        Column(m.onGloballyPositioned { rulaAnchor = it.positionInRoot().y + scroll.value }) {
            SectionLabel("RULA · Rapid Upper Limb Assessment")
            MethodBreakdown(interpretation.rula, peakLabel(Method.RULA))
            Spacer(Modifier.height(Space.xl))
            RiskDistribution(Method.RULA, interpretation.rula.distribution)
        }
    }
    val rebaBlock: @Composable (Modifier) -> Unit = { m ->
        Column(m.onGloballyPositioned { rebaAnchor = it.positionInRoot().y + scroll.value }) {
            SectionLabel("REBA · Rapid Entire Body Assessment")
            MethodBreakdown(interpretation.reba, peakLabel(Method.REBA))
            Spacer(Modifier.height(Space.xl))
            RiskDistribution(Method.REBA, interpretation.reba.distribution)
        }
    }
    if (compact) {
        rulaBlock(Modifier)
        Spacer(Modifier.height(Space.xxl))
        rebaBlock(Modifier)
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(Space.xxl), verticalAlignment = Alignment.Top) {
            rulaBlock(Modifier.weight(1f))
            rebaBlock(Modifier.weight(1f))
        }
    }
    Spacer(Modifier.height(Space.xxl))

    if (result.keyframes.isNotEmpty()) {
        SectionLabel("Frame by frame")
        FrameExplorer(record, result)
        Spacer(Modifier.height(Space.xxl))
    }

    val captureRows = listOf(
        "Video duration" to formatSeconds(result.video?.durationSec),
        "Frame rate" to formatFps(result.video?.fps),
        "Analyzed resolution" to formatResolution(result.video?.width, result.video?.height),
        "Frames sampled" to (result.video?.framesSampled?.let { n ->
            result.video.sampleHz?.let { "$n at ${it.toInt()} per second" } ?: n.toString()
        } ?: EMPTY_VALUE),
        "Worker detected in" to formatPercent(result.quality?.detectionFraction),
        "Scoreable frames" to formatPercent(result.quality?.scoreableFraction),
        "Camera view" to when (result.quality?.cameraViewOk) {
            true -> "Suitable"
            false -> "Not suitable"
            null -> EMPTY_VALUE
        }
    )
    val methodBlock: @Composable () -> Unit = {
        SectionLabel("Methodology")
        Text(
            "VigilEx detects the worker's pose in sampled video frames (YOLO11n-Pose), follows the same " +
                "worker through the video (ByteTrack) and measures joint angles in the side view. RULA and " +
                "REBA are scored per frame; the assessment reports the peak score of each.",
            style = VxType.bodySmall,
            color = InkSecondary
        )
        if (interpretation.unmeasured.isNotEmpty()) {
            Spacer(Modifier.height(Space.sm))
            Text(
                "Not observable from video: ${interpretation.unmeasured.joinToString(", ").lowercase()
                    .replaceFirstChar { it.uppercase() }}, as well as force, load, coupling and activity. " +
                    "These are scored at their lowest-risk values.",
                style = VxType.bodySmall,
                color = InkSecondary
            )
        }
        Spacer(Modifier.height(Space.sm))
        Text(
            "The assessment describes observed posture. It does not assess injury, fatigue or health.",
            style = VxType.bodySmall,
            color = InkSecondary
        )
        result.methodologyVersion?.let {
            Spacer(Modifier.height(Space.sm))
            Text("Methodology: $it", style = VxType.mono, color = InkMuted)
        }
    }
    if (compact) {
        SectionLabel("Capture quality")
        KeyValueTable(captureRows)
        Spacer(Modifier.height(Space.xxl))
        methodBlock()
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(Space.xxl)) {
            Column(Modifier.weight(1f)) {
                SectionLabel("Capture quality")
                KeyValueTable(captureRows)
            }
            Column(Modifier.weight(1f)) { methodBlock() }
        }
    }

    Spacer(Modifier.height(Space.xxl))
    if (!compact) {
        ArrowLink("Generate report", onClick = onGenerateReport)
        Spacer(Modifier.height(Space.lg))
    }
    SecondaryButton(text = "Delete assessment", onClick = { confirmDelete = true }, destructive = true)

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete assessment?", style = VxType.sectionTitle) },
            text = {
                Text(
                    "This removes the saved result for ${record.fileName} from this device. The video itself is not deleted.",
                    style = VxType.body
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    scope.launch {
                        AssessmentStore.delete(record.id)
                        onDeleted()
                    }
                }) { Text("DELETE", style = VxType.labelLarge, color = Red) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text("CANCEL", style = VxType.labelLarge, color = Ink)
                }
            }
        )
    }
}

/** Level two: any sampled frame, its pose, and each component's angle and band. */
@Composable
private fun FrameExplorer(record: AssessmentRecord, result: AnalysisResult) {
    val context = LocalContext.current
    val compact = LocalWindowClass.current == WindowClass.COMPACT
    val keyframes = result.keyframes
    val initial = interpretPeak(result)
    var selected by rememberSaveable(record.id) { mutableIntStateOf(initial) }
    val keyframe = keyframes[selected.coerceIn(0, keyframes.lastIndex)]

    val videoUri = remember(record.videoUri) { record.videoUri?.let(Uri::parse) }
    var videoAvailable by remember(record.id) { mutableStateOf<Boolean?>(null) }
    // The skeleton is only ever drawn over the frame it was detected in.
    var shown by remember(record.id) { mutableStateOf<Pair<Keyframe, ImageBitmap?>?>(null) }

    LaunchedEffect(videoUri) {
        videoAvailable = videoUri != null && VideoInspector.isReadable(context, videoUri)
    }
    LaunchedEffect(keyframe, videoAvailable) {
        if (videoAvailable == true && videoUri != null) {
            delay(FRAME_DEBOUNCE_MS) // Skip extraction for frames passed over while dragging.
            shown = keyframe to VideoInspector.frameAt(context, videoUri, keyframe.t)?.asImageBitmap()
        }
    }

    val frameWidth = result.video?.width ?: 0
    val frameHeight = result.video?.height ?: 0
    val pose: @Composable () -> Unit = {
        val pair = shown
        if (videoAvailable == true && pair != null) PoseEvidence(pair.first, frameWidth, frameHeight, frame = pair.second)
        else PoseEvidence(keyframe, frameWidth, frameHeight, frame = null)
    }
    val detail: @Composable () -> Unit = {
        FrameHeader(keyframe, selected, keyframes.size)
        Spacer(Modifier.height(Space.lg))
        ComponentTable(keyframe.componentRows())
    }

    ScoreTimeline(
        keyframes = keyframes,
        durationSec = result.video?.durationSec,
        selected = selected,
        onSelect = { selected = it }
    )
    Spacer(Modifier.height(Space.xs))
    Text("Tap or drag to inspect a sampled frame.", style = VxType.bodySmall, color = InkMuted)
    Spacer(Modifier.height(Space.xl))
    if (compact) {
        pose()
        Spacer(Modifier.height(Space.lg))
        detail()
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(Space.xxl), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1.3f)) { pose() }
            Column(Modifier.weight(1f)) { detail() }
        }
    }
}

private fun interpretPeak(result: AnalysisResult): Int {
    val max = result.reba?.maxScore ?: result.rula?.maxScore ?: return 0
    val method = if (result.reba?.maxScore != null) Method.REBA else Method.RULA
    return result.keyframes.indexOfFirst { it.score(method)?.score == max }.coerceAtLeast(0)
}

@Composable
private fun FrameHeader(keyframe: Keyframe, index: Int, count: Int) {
    Column {
        Text(
            "FRAME ${index + 1} OF $count · ${formatSeconds(keyframe.t)}",
            style = VxType.label,
            color = InkMuted
        )
        Spacer(Modifier.height(Space.sm))
        Row(horizontalArrangement = Arrangement.spacedBy(Space.lg)) {
            Method.entries.forEach { method ->
                val score = keyframe.score(method)
                Column {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(method.label, style = VxType.label, color = InkMuted)
                        Spacer(Modifier.width(Space.xs))
                        Text(formatScore(score?.score), style = VxType.metricSmall, color = Ink)
                    }
                    Spacer(Modifier.height(Space.xs))
                    RiskBadge(method, score?.risk)
                }
            }
        }
        if (keyframe.sideViewOk == false) {
            Spacer(Modifier.height(Space.sm))
            Text("Camera view not suitable in this frame; it was not scored.", style = VxType.bodySmall, color = InkMuted)
        }
    }
}

@Composable
private fun ComponentTable(rows: List<ComponentRow>) {
    if (rows.isEmpty()) return
    DataTable(
        columns = listOf(
            TableColumn("Component", 1.6f),
            TableColumn("Angle", 1f, TextAlign.End),
            TableColumn("RULA", 0.8f, TextAlign.End),
            TableColumn("REBA", 0.8f, TextAlign.End)
        ),
        rowCount = rows.size
    ) { row, column ->
        val item = rows[row]
        when (column) {
            0 -> CellText(item.name, muted = !item.measured, strong = item.measured)
            1 -> CellText(if (item.measured) formatDegrees(item.angleDeg) else "Not observable", muted = !item.measured)
            2 -> CellText(if (item.measured) bandText(item.rula) else EMPTY_VALUE, muted = !item.measured)
            else -> CellText(if (item.measured) bandText(item.reba) else EMPTY_VALUE, muted = !item.measured)
        }
    }
}

private fun bandText(component: FrameComponent?): String {
    val band = component?.band ?: return EMPTY_VALUE
    val max = component.maxBand ?: return band.toString()
    return "$band / $max"
}
