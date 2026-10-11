package com.extrive.vigilex.ui.report

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.extrive.vigilex.data.insight.Interpretation
import com.extrive.vigilex.data.model.AnalysisResult
import com.extrive.vigilex.data.model.AssessmentRecord
import com.extrive.vigilex.data.model.ComponentTableDto
import com.extrive.vigilex.data.model.MethodResultDto
import com.extrive.vigilex.data.model.ReportContentDto
import com.extrive.vigilex.data.model.riskName
import com.extrive.vigilex.ui.components.COMPONENT_HEADING
import com.extrive.vigilex.ui.components.CameraViewReliability
import com.extrive.vigilex.ui.components.CellText
import com.extrive.vigilex.ui.components.DataTable
import com.extrive.vigilex.ui.components.EvidenceGallery
import com.extrive.vigilex.ui.components.Hairline
import com.extrive.vigilex.ui.components.LocalWindowClass
import com.extrive.vigilex.ui.components.MeasurementsTable
import com.extrive.vigilex.ui.components.MethodLine
import com.extrive.vigilex.ui.components.OtherActions
import com.extrive.vigilex.ui.components.PRODUCT_SECTION
import com.extrive.vigilex.ui.components.PriorityAction
import com.extrive.vigilex.ui.components.ProductRecommendation
import com.extrive.vigilex.ui.components.ReliabilityNotice
import com.extrive.vigilex.ui.components.TECHNICAL_SECTION
import com.extrive.vigilex.ui.components.TableColumn
import com.extrive.vigilex.ui.components.WindowClass
import com.extrive.vigilex.ui.components.accent
import com.extrive.vigilex.ui.components.headlineColor
import com.extrive.vigilex.ui.components.headlineText
import com.extrive.vigilex.ui.format.EMPTY_VALUE
import com.extrive.vigilex.ui.format.formatDateTime
import com.extrive.vigilex.ui.format.formatDegrees
import com.extrive.vigilex.ui.format.formatSeconds
import com.extrive.vigilex.ui.theme.Border
import com.extrive.vigilex.ui.theme.Hairline as HairlineColor
import com.extrive.vigilex.ui.theme.Ink
import com.extrive.vigilex.ui.theme.InkMuted
import com.extrive.vigilex.ui.theme.InkSecondary
import com.extrive.vigilex.ui.theme.Space
import com.extrive.vigilex.ui.theme.Surface
import com.extrive.vigilex.ui.theme.VxType

/**
 * The report as a document: white paper on the canvas, a running head,
 * numbered sections with large numerals, then the lettered technical
 * appendix. Mirrors the server's PDF section for section; every finding,
 * product suggestion, reliability notice and explanation is the report
 * content the server built the PDF from ([content]).
 */
@Composable
fun ReportDocument(
    record: AssessmentRecord,
    result: AnalysisResult,
    interpretation: Interpretation,
    content: ReportContentDto,
    modifier: Modifier = Modifier
) {
    val compact = LocalWindowClass.current == WindowClass.COMPACT
    val pad = if (compact) Space.lg else Space.xxl
    val sections = content.sections.ifEmpty { reportSections(content.productRecommendations.isNotEmpty()) }
    val reliability = content.measurementReliability
    @Composable
    fun Section(name: String, body: @Composable () -> Unit) {
        val number = sections.indexOf(name)
        if (number >= 0) DocSection("%02d".format(number + 1), name, content = body)
    }

    Column(
        modifier
            .fillMaxWidth()
            .background(Surface)
            .border(Border.hairline, HairlineColor)
            .padding(horizontal = pad, vertical = pad)
    ) {
        // Running head
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("VIGILEX", style = VxType.wordmark, color = Ink, modifier = Modifier.weight(1f))
            Text("ERGONOMIC ASSESSMENT", style = VxType.label, color = InkMuted)
        }
        Spacer(Modifier.height(Space.xxl))
        Text("Ergonomic assessment", style = if (compact) VxType.pageTitleCompact else VxType.pageTitle, color = Ink)
        Spacer(Modifier.height(Space.md))
        MetaLine("Video", record.fileName)
        MetaLine("Analyzed", formatDateTime(record.createdAtMillis))
        MetaLine("Duration", formatSeconds(result.video?.durationSec))
        Spacer(Modifier.height(Space.xxl))

        // What was observed, in one glance, with its limitation beside it.
        Section("Overall assessment") {
            Text("ERGONOMIC RISK", style = VxType.label, color = InkMuted)
            Spacer(Modifier.height(Space.xs))
            Text(
                interpretation.overall.headlineText,
                style = VxType.riskHeadlineCompact,
                color = interpretation.overall.headlineColor()
            )
            Spacer(Modifier.height(Space.sm))
            Box(Modifier.width(48.dp).height(4.dp).background(interpretation.overall.accent()))
            Hairline(color = Ink)
            MethodLine(interpretation.rula)
            Hairline()
            MethodLine(interpretation.reba)
            Hairline()
            (content.actionSummary ?: interpretation.observation)?.let {
                Spacer(Modifier.height(Space.lg))
                Text(it, style = VxType.body.copy(fontSize = 17.sp, lineHeight = 25.sp), color = Ink)
            }
            if (reliability?.limited == true) {
                Spacer(Modifier.height(Space.lg))
                ReliabilityNotice(reliability)
            }
        }

        Section("Priority action") {
            val primary = content.interventions.firstOrNull()
            if (primary == null) {
                Text("No body region was flagged often enough to need a specific action.", style = VxType.body, color = InkSecondary)
            } else {
                PriorityAction(primary)
            }
        }

        content.productRecommendations.firstOrNull()?.let { product ->
            Section(PRODUCT_SECTION) { ProductRecommendation(product) }
        }

        Section("Other actions") {
            val others = content.interventions.drop(1)
            if (others.isEmpty()) {
                Text("No other body region needed action.", style = VxType.body, color = InkSecondary)
            } else {
                OtherActions(others)
            }
        }

        Section("Measurements") {
            MeasurementsTable(interpretation.regions)
            Spacer(Modifier.height(Space.xs))
            Text(content.measurementNote, style = VxType.bodySmall, color = InkMuted)
        }

        Section("Posture evidence") {
            if (interpretation.evidence.isEmpty()) {
                Text("No scored frame is available to show.", style = VxType.body, color = InkSecondary)
            } else {
                EvidenceGallery(
                    evidence = interpretation.evidence,
                    keyframeCount = result.keyframes.size,
                    videoUri = record.videoUri,
                    frameWidth = result.video?.width ?: 0,
                    frameHeight = result.video?.height ?: 0
                )
            }
        }

        Section("Camera view and reliability") {
            reliability?.let { CameraViewReliability(it) }
        }

        // Technical appendix, for professional review.
        DocSection("A", content.technicalSection.ifEmpty { TECHNICAL_SECTION }, last = true) {
            Text("RULA / REBA SCORES", style = VxType.label, color = Ink)
            Spacer(Modifier.height(Space.xs))
            listOf("RULA" to content.rula, "REBA" to content.reba).forEach { (method, r) ->
                r?.let { MethodScoreLine(method, it) }
            }
            Text(
                "Scores are the peak per-frame score reported by the VigilEx pipeline; band is the component's " +
                    "score within its range.",
                style = VxType.bodySmall,
                color = InkMuted
            )
            if (content.componentTables.isNotEmpty()) {
                Spacer(Modifier.height(Space.xl))
                Text(COMPONENT_HEADING.uppercase(), style = VxType.label, color = Ink)
                Spacer(Modifier.height(Space.xs))
                Text(content.componentNote, style = VxType.bodySmall, color = Ink)
                content.componentTables.forEach {
                    Spacer(Modifier.height(Space.lg))
                    ComponentTable(it)
                }
            }
            Spacer(Modifier.height(Space.xl))
            Text("METHODOLOGY", style = VxType.label, color = Ink)
            content.methodology.forEach {
                Spacer(Modifier.height(Space.xs))
                Text(it, style = VxType.bodySmall, color = InkSecondary)
            }
            Spacer(Modifier.height(Space.lg))
            Text("LIMITATIONS", style = VxType.label, color = Ink)
            content.limitations.forEach {
                Spacer(Modifier.height(Space.xs))
                Text(it, style = VxType.bodySmall, color = InkSecondary)
            }
            content.methodologyVersion?.let {
                Spacer(Modifier.height(Space.sm))
                Text("Methodology: $it", style = VxType.mono, color = InkMuted)
            }
        }
    }
}

@Composable
private fun MethodScoreLine(method: String, r: MethodResultDto) {
    val dist = r.riskDistribution.filterValues { it > 0 }.entries.joinToString(", ") { "${riskName(it.key)} ${it.value}" }
    Text(
        "$method ${"%02d".format(r.score ?: 0)} / ${"%02d".format(r.scaleMax)} · ${riskName(r.risk)} risk. " +
            "${r.framesScored} frames scored." + (if (dist.isNotEmpty()) " Frames by risk band: $dist." else ""),
        style = VxType.bodySmall,
        color = InkSecondary
    )
    Spacer(Modifier.height(Space.xs))
}

/** One method's components at its peak-score frame, exactly as the PDF lists them. */
@Composable
private fun ComponentTable(table: ComponentTableDto) {
    val at = "frame ${table.frameNumber}" + (table.frameTimeSec?.let { " · ${formatSeconds(it)}" } ?: "")
    Text(table.title, style = VxType.title, color = Ink)
    Text(
        "Score ${"%02d".format(table.score)} / ${"%02d".format(table.scaleMax)} · $at",
        style = VxType.mono,
        color = InkMuted
    )
    Spacer(Modifier.height(Space.xs))
    DataTable(
        columns = listOf(
            TableColumn("Component", 1.6f),
            TableColumn("Angle", 1f, TextAlign.End),
            TableColumn("Band", 0.8f, TextAlign.End)
        ),
        rowCount = table.rows.size
    ) { row, column ->
        val c = table.rows[row]
        when (column) {
            0 -> CellText(c.name, strong = c.measured, muted = !c.measured)
            1 -> CellText(if (c.measured) formatDegrees(c.angleDeg) else "Not observable", muted = !c.measured)
            else -> CellText(
                if (c.measured && c.band != null) "${c.band} / ${c.maxBand ?: EMPTY_VALUE}" else EMPTY_VALUE,
                muted = !c.measured
            )
        }
    }
}

@Composable
private fun MetaLine(label: String, value: String) {
    Row(Modifier.padding(vertical = 2.dp)) {
        Text(label.uppercase(), style = VxType.label, color = InkMuted, modifier = Modifier.width(88.dp).padding(top = 3.dp))
        Text(value, style = VxType.bodySmall, color = Ink, modifier = Modifier.weight(1f))
    }
}

/** 01 / OVERALL ASSESSMENT under a strong rule; large numeral (or letter), quiet title. */
@Composable
private fun DocSection(mark: String, name: String, last: Boolean = false, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Hairline(color = Ink, modifier = Modifier.height(2.dp))
        Spacer(Modifier.height(Space.sm))
        Row(verticalAlignment = Alignment.Top) {
            Text(
                mark,
                style = VxType.figure.copy(fontSize = 40.sp, lineHeight = 42.sp),
                color = Ink,
                modifier = Modifier.width(72.dp)
            )
            Text(
                name.uppercase(),
                style = VxType.labelLarge,
                color = Ink,
                modifier = Modifier
                    .padding(top = 8.dp)
                    .semantics { heading() }
            )
        }
        Spacer(Modifier.height(Space.lg))
        content()
        if (!last) Spacer(Modifier.height(Space.xxl))
    }
}
