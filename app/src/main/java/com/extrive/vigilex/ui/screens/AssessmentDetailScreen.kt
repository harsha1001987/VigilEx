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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
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
import com.extrive.vigilex.data.api.ApiResult
import com.extrive.vigilex.data.repository.AssessmentRepository
import com.extrive.vigilex.data.store.AssessmentStore
import com.extrive.vigilex.ui.components.BackendErrorState
import com.extrive.vigilex.ui.components.Callout
import com.extrive.vigilex.ui.components.CalloutTone
import com.extrive.vigilex.ui.components.ConfirmDialog
import com.extrive.vigilex.ui.components.ExpandableSection
import com.extrive.vigilex.ui.components.Hairline
import com.extrive.vigilex.data.model.riskName
import com.extrive.vigilex.ui.theme.Motion
import com.extrive.vigilex.ui.components.COMPONENT_NOTE
import com.extrive.vigilex.ui.components.CameraViewReliability
import com.extrive.vigilex.ui.components.ReliabilityNotice
import com.extrive.vigilex.ui.components.TECHNICAL_SECTION
import com.extrive.vigilex.ui.components.ArrowLink
import com.extrive.vigilex.ui.components.BottomActionBar
import com.extrive.vigilex.ui.components.CellText
import com.extrive.vigilex.ui.components.DataTable
import com.extrive.vigilex.ui.components.EvidenceGallery
import com.extrive.vigilex.ui.components.KeyValueTable
import com.extrive.vigilex.ui.components.LoadingState
import com.extrive.vigilex.ui.components.LocalWindowClass
import com.extrive.vigilex.ui.components.MeasurementsTable
import com.extrive.vigilex.ui.components.MethodBreakdown
import com.extrive.vigilex.ui.components.PageContainer
import com.extrive.vigilex.ui.components.PoseEvidence
import com.extrive.vigilex.ui.components.PrimaryButton
import com.extrive.vigilex.ui.components.RecommendedActions
import com.extrive.vigilex.ui.components.RiskBadge
import com.extrive.vigilex.ui.components.RiskConclusion
import com.extrive.vigilex.ui.components.RiskDistribution
import com.extrive.vigilex.ui.components.ScoreTimeline
import com.extrive.vigilex.ui.components.SecondaryButton
import com.extrive.vigilex.ui.components.SectionLabel
import com.extrive.vigilex.ui.components.SubPageHeader
import com.extrive.vigilex.ui.components.TableColumn
import com.extrive.vigilex.ui.components.WhatWeFound
import com.extrive.vigilex.ui.components.WindowClass
import com.extrive.vigilex.ui.format.EMPTY_VALUE
import com.extrive.vigilex.ui.format.formatRelativeDateTime
import com.extrive.vigilex.ui.format.formatDegrees
import com.extrive.vigilex.ui.format.formatFps
import com.extrive.vigilex.ui.format.formatPercent
import com.extrive.vigilex.ui.format.formatResolution
import com.extrive.vigilex.ui.format.formatScore
import com.extrive.vigilex.ui.format.formatSeconds
import com.extrive.vigilex.ui.theme.Ink
import com.extrive.vigilex.ui.theme.InkMuted
import com.extrive.vigilex.ui.theme.InkSecondary
import com.extrive.vigilex.ui.theme.Space
import com.extrive.vigilex.ui.theme.VxType
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val FRAME_DEBOUNCE_MS = 120L

private sealed interface DetailLoad {
    data object Loading : DetailLoad
    data class Failed(val error: ApiResult.Error) : DetailLoad
    data class Ready(
        val record: AssessmentRecord,
        val result: AnalysisResult,
        val interpretation: Interpretation,
        val reportAvailable: Boolean
    ) : DetailLoad
}

/**
 * The assessment result, in reading order: what was assessed, the overall
 * result, the key findings, what to do about them, what the video cannot
 * show, then (collapsed) the technical detail behind it. Everything shown is
 * the assessment as stored on the backend; nothing here scores or ranks.
 */
@Composable
fun AssessmentDetailScreen(id: String, onBack: () -> Unit, onGenerateReport: () -> Unit) {
    val repository = remember { AssessmentRepository() }
    val compact = LocalWindowClass.current == WindowClass.COMPACT
    val density = LocalDensity.current

    var attempt by remember(id) { mutableIntStateOf(0) }
    var load by remember(id) { mutableStateOf<DetailLoad>(DetailLoad.Loading) }
    LaunchedEffect(id, attempt) {
        load = DetailLoad.Loading
        load = when (val detail = repository.detail(id)) {
            is ApiResult.Success -> DetailLoad.Ready(
                detail.data.record,
                detail.data.result,
                interpret(detail.data.result),
                detail.data.reportAvailable
            )
            is ApiResult.Error -> DetailLoad.Failed(detail)
        }
    }

    val scroll = rememberScrollState()
    val ready = load as? DetailLoad.Ready
    // The result is fetched again when coming back from the report (it may now exist);
    // the reading position is restored once it has loaded.
    var savedScroll by rememberSaveable(id) { mutableIntStateOf(0) }
    LaunchedEffect(ready != null) {
        if (ready == null) return@LaunchedEffect
        if (savedScroll > 0) scroll.scrollTo(savedScroll)
        snapshotFlow { scroll.value }.collect { savedScroll = it }
    }

    var viewportTop by remember { mutableFloatStateOf(0f) }
    var barHeight by remember { mutableIntStateOf(0) }
    val reportLabel = if (ready?.reportAvailable == true) "View report" else "Generate PDF report"
    Box(Modifier.fillMaxSize().onGloballyPositioned { viewportTop = it.positionInRoot().y }) {
        PageContainer(scrollState = scroll) {
            SubPageHeader(context = "Assessment result", meta = null, onBack = onBack)
            when (val current = load) {
                DetailLoad.Loading -> LoadingState(label = "Loading the assessment result…")
                is DetailLoad.Failed -> BackendErrorState(
                    current.error,
                    onRetry = { attempt++ },
                    notFoundTitle = "Assessment not found",
                    notFoundMessage = "This assessment is not in your account on the VigilEx server. It may have been deleted."
                )
                is DetailLoad.Ready -> AssessmentContent(
                    record = current.record,
                    result = current.result,
                    interpretation = current.interpretation,
                    reportLabel = reportLabel,
                    scroll = scroll,
                    viewportTop = viewportTop,
                    repository = repository,
                    onGenerateReport = onGenerateReport,
                    onDeleted = onBack
                )
            }
            // Room for the action bar on phones, however tall large text makes it.
            if (compact && ready != null) Spacer(Modifier.height(with(density) { barHeight.toDp() } + Space.md))
        }
        if (compact && ready != null) {
            BottomActionBar(
                Modifier
                    .align(Alignment.BottomCenter)
                    .onSizeChanged { barHeight = it.height }
            ) {
                PrimaryButton(reportLabel, onClick = onGenerateReport, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

/** Name of what was assessed, when, and the video's length; long names wrap. */
@Composable
private fun AssessmentHeader(record: AssessmentRecord, result: AnalysisResult) {
    Text(
        record.fileName,
        style = VxType.pageTitleCompact,
        color = Ink,
        modifier = Modifier.semantics { heading() }
    )
    Spacer(Modifier.height(Space.xxs))
    Text(
        listOfNotNull(
            formatRelativeDateTime(record.createdAtMillis),
            result.video?.durationSec?.let { "${formatSeconds(it)} video" }
        ).joinToString(" · "),
        style = VxType.bodySmall,
        color = InkMuted
    )
    Spacer(Modifier.height(Space.lg))
}

@Composable
private fun AssessmentContent(
    record: AssessmentRecord,
    result: AnalysisResult,
    interpretation: Interpretation,
    reportLabel: String,
    scroll: ScrollState,
    viewportTop: Float,
    repository: AssessmentRepository,
    onGenerateReport: () -> Unit,
    onDeleted: () -> Unit
) {
    val compact = LocalWindowClass.current == WindowClass.COMPACT
    val expanded = LocalWindowClass.current == WindowClass.EXPANDED
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    var confirmDelete by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var deleteError by remember { mutableStateOf<String?>(null) }

    // "RULA details" / "REBA details" open Technical details and the method's section, then
    // scroll to it. Anchors are offsets within the scrolled content, measured on the section header.
    var technicalOpen by rememberSaveable(record.id) { mutableStateOf(false) }
    var rulaOpen by rememberSaveable(record.id) { mutableStateOf(false) }
    var rebaOpen by rememberSaveable(record.id) { mutableStateOf(false) }
    var rulaAnchor by remember { mutableFloatStateOf(0f) }
    var rebaAnchor by remember { mutableFloatStateOf(0f) }
    val margin = with(density) { Space.md.toPx() }
    fun scrollTo(anchor: () -> Float) {
        scope.launch {
            delay(Motion.base.toLong()) // Let the opened sections lay out first.
            scroll.animateScrollTo((anchor() - viewportTop - margin).toInt().coerceAtLeast(0))
        }
    }

    val frameWidth = result.video?.width ?: 0
    val frameHeight = result.video?.height ?: 0

    AssessmentHeader(record, result)

    // ---------------------------------------------------------- result, findings, actions
    val summary: @Composable () -> Unit = {
        RiskConclusion(interpretation)
        // Next to the result, so it is never read without its camera-view limitation.
        if (record.measurementReliability?.limited == true) {
            Spacer(Modifier.height(Space.md))
            ReliabilityNotice(record.measurementReliability)
        }
        if (interpretation.findings.isNotEmpty()) {
            Spacer(Modifier.height(Space.xl))
            SectionLabel("Key findings")
            WhatWeFound(interpretation.findings)
        }
        // From the backend intervention engine: the priority action first, the Extrive
        // product to evaluate for it when the finding qualifies, then the other actions.
        if (record.interventions.isNotEmpty()) {
            Spacer(Modifier.height(Space.xl))
            RecommendedActions(record.interventions, product = record.primaryProduct)
        } else if (interpretation.overall != null) {
            // The same statement the PDF report makes; no action is invented to fill the space.
            Spacer(Modifier.height(Space.xl))
            SectionLabel("Priority action")
            Text(
                "No body region was flagged often enough to need a specific action.",
                style = VxType.body,
                color = InkSecondary
            )
        }
        if (!compact) {
            Spacer(Modifier.height(Space.lg))
            PrimaryButton(reportLabel, onClick = onGenerateReport)
        }
    }
    val evidence: @Composable () -> Unit = {
        if (interpretation.evidence.isNotEmpty()) {
            ExpandableSection(
                title = "Posture evidence",
                summary = "Frames that show the peak scores and the key finding",
                initiallyExpanded = expanded
            ) {
                EvidenceGallery(
                    evidence = interpretation.evidence,
                    keyframeCount = result.keyframes.size,
                    videoUri = record.videoUri,
                    frameWidth = frameWidth,
                    frameHeight = frameHeight
                )
            }
        }
    }

    if (expanded) {
        // Result and findings on the left, the evidence that supports them on the right.
        Row(horizontalArrangement = Arrangement.spacedBy(Space.xxl), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) { summary() }
            Column(Modifier.weight(1.1f)) { evidence() }
        }
    } else {
        summary()
    }
    Spacer(Modifier.height(Space.xl))

    // Never collapsed: what the video cannot show is part of the result.
    Callout(
        title = "Assessment limitations",
        body = listOfNotNull(
            interpretation.unmeasured.takeIf { it.isNotEmpty() }?.let { missing ->
                "Not observable from video: ${missing.joinToString(", ").lowercase().replaceFirstChar { it.uppercase() }}, " +
                    "as well as force, load, coupling and activity. These are scored at their lowest-risk values; " +
                    "this is not evidence that they were low risk."
            },
            "The assessment describes observed posture from 2D video. It does not assess injury, fatigue or health."
        ).joinToString("\n\n")
    )
    Spacer(Modifier.height(Space.lg))

    if (!expanded) evidence()

    // ---------------------------------------------------------- technical details
    val peakLabel = { method: Method ->
        interpretation.method(method).peakFrameIndex?.let { i ->
            "frame ${i + 1}, ${formatSeconds(result.keyframes[i].t)}"
        }
    }
    ExpandableSection(
        title = "Technical details",
        summary = "Body-region angles, RULA and REBA breakdowns, frames, capture quality and method",
        expanded = technicalOpen,
        onExpandedChange = { technicalOpen = it }
    ) {
        Text("Body-region measurements", style = VxType.labelLarge, color = Ink, modifier = Modifier.semantics { heading() })
        Spacer(Modifier.height(Space.xs))
        MeasurementsTable(interpretation.regions)
        Spacer(Modifier.height(Space.xs))
        Text(
            "Average and peak joint angles across all analyzed frames in which each region was observable. " +
                "Status reflects the RULA and REBA bands the region reached. Not measured means the region could " +
                "not be observed; it is not evidence of low risk.",
            style = VxType.bodySmall,
            color = InkMuted
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Space.md)) {
            ArrowLink("RULA details", onClick = { rulaOpen = true; scrollTo { rulaAnchor } })
            ArrowLink("REBA details", onClick = { rebaOpen = true; scrollTo { rebaAnchor } })
        }
        Spacer(Modifier.height(Space.sm))

        val methodSection: @Composable (Method, Boolean, (Boolean) -> Unit, (Float) -> Unit) -> Unit = { method, open, setOpen, setAnchor ->
            val methodResult = interpretation.method(method)
            Column(Modifier.onGloballyPositioned { setAnchor(it.positionInRoot().y + scroll.value) }) {
                ExpandableSection(
                    title = if (method == Method.RULA) "RULA · Rapid Upper Limb Assessment" else "REBA · Rapid Entire Body Assessment",
                    summary = methodResult.score?.let { "Peak score $it of ${method.scaleMax} · ${riskName(methodResult.risk)} risk" }
                        ?: "Not measured",
                    expanded = open,
                    onExpandedChange = setOpen
                ) {
                    Text(COMPONENT_NOTE, style = VxType.bodySmall, color = InkSecondary)
                    Spacer(Modifier.height(Space.md))
                    MethodBreakdown(methodResult, peakLabel(method))
                    Spacer(Modifier.height(Space.lg))
                    RiskDistribution(method, methodResult.distribution)
                }
            }
        }
        methodSection(Method.RULA, rulaOpen, { rulaOpen = it }, { rulaAnchor = it })
        methodSection(Method.REBA, rebaOpen, { rebaOpen = it }, { rebaAnchor = it })

        record.measurementReliability?.let {
            ExpandableSection(
                title = "Camera view and reliability",
                summary = if (it.limited) "Limitation detected" else "No camera-view limitation detected"
            ) { CameraViewReliability(it) }
        }

        if (result.keyframes.isNotEmpty()) {
            ExpandableSection(title = "Frame by frame", summary = "Inspect any sampled frame and its component scores") {
                FrameExplorer(record, result)
            }
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
        ExpandableSection(title = "Capture quality", summary = "How much of the video could be scored") {
            KeyValueTable(captureRows)
        }
        ExpandableSection(title = "Methodology", summary = TECHNICAL_SECTION) {
            Text(
                "VigilEx detects the worker's pose in sampled video frames (YOLO11n-Pose) and follows the same " +
                    "worker through the video (ByteTrack). Joint angles are measured from the detected 2D landmarks. " +
                    "RULA and REBA are scored per frame; the assessment reports the peak score of each.",
                style = VxType.bodySmall,
                color = InkSecondary
            )
            result.methodologyVersion?.let {
                Spacer(Modifier.height(Space.sm))
                Text("Methodology version: $it", style = VxType.mono, color = InkMuted)
            }
        }
    }
    Hairline()

    Spacer(Modifier.height(Space.xl))
    SecondaryButton(
        text = "Delete assessment",
        onClick = { confirmDelete = true },
        loading = deleting,
        destructive = true
    )
    deleteError?.let {
        Spacer(Modifier.height(Space.xs))
        Callout(title = "Not deleted", body = it, tone = CalloutTone.ERROR)
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = "Delete assessment?",
            message = "This permanently removes the assessment of ${record.fileName} and its report from your account. " +
                "The video itself is not deleted.",
            confirmLabel = "Delete",
            destructive = true,
            onDismiss = { confirmDelete = false },
            onConfirm = {
                confirmDelete = false
                deleting = true
                deleteError = null
                scope.launch {
                    when (val deletion = repository.delete(record.id)) {
                        is ApiResult.Success -> {
                            AssessmentStore.deleted(record.id)
                            onDeleted()
                        }
                        is ApiResult.Error -> {
                            deleting = false
                            deleteError = deletion.message
                        }
                    }
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
            "Frame ${index + 1} of $count · ${formatSeconds(keyframe.t)}",
            style = VxType.labelLarge,
            color = Ink
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
