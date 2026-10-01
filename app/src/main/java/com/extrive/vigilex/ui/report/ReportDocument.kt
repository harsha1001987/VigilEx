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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.extrive.vigilex.data.insight.Interpretation
import com.extrive.vigilex.data.model.AnalysisResult
import com.extrive.vigilex.data.model.AssessmentRecord
import com.extrive.vigilex.data.model.orderedDistribution
import com.extrive.vigilex.data.model.riskName
import com.extrive.vigilex.ui.components.EvidenceGallery
import com.extrive.vigilex.ui.components.FindingRow
import com.extrive.vigilex.ui.components.Hairline
import com.extrive.vigilex.ui.components.LocalWindowClass
import com.extrive.vigilex.ui.components.MeasurementsTable
import com.extrive.vigilex.ui.components.MethodBreakdown
import com.extrive.vigilex.ui.components.MethodLine
import com.extrive.vigilex.ui.components.WindowClass
import com.extrive.vigilex.ui.components.accent
import com.extrive.vigilex.ui.components.headlineColor
import com.extrive.vigilex.ui.components.headlineText
import com.extrive.vigilex.ui.format.formatDateTime
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
 * numbered sections with large numerals. Mirrors the exported PDF.
 */
@Composable
fun ReportDocument(
    record: AssessmentRecord,
    result: AnalysisResult,
    interpretation: Interpretation,
    modifier: Modifier = Modifier
) {
    val compact = LocalWindowClass.current == WindowClass.COMPACT
    val pad = if (compact) Space.lg else Space.xxl
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

        DocSection(1) {
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
            interpretation.observation?.let {
                Spacer(Modifier.height(Space.lg))
                Text("PRIMARY OBSERVATION", style = VxType.label, color = InkMuted)
                Spacer(Modifier.height(Space.xs))
                Text(it, style = VxType.body.copy(fontSize = 17.sp, lineHeight = 25.sp), color = Ink)
            }
            interpretation.guidance?.let {
                Spacer(Modifier.height(Space.xs))
                Text(it, style = VxType.bodySmall, color = InkSecondary)
            }
        }

        DocSection(2) {
            Hairline(color = Ink)
            if (interpretation.findings.isEmpty()) {
                Text("No body region could be measured.", style = VxType.body, color = InkSecondary)
            }
            interpretation.findings.forEachIndexed { i, f -> FindingRow(i + 1, f) }
        }

        DocSection(3) {
            listOf(interpretation.rula, interpretation.reba).forEachIndexed { i, m ->
                if (i > 0) Spacer(Modifier.height(Space.xl))
                MethodBreakdown(m, peakTimeLabel = null)
                val dist = orderedDistribution(m.method, m.distribution).filter { it.second > 0 }
                if (dist.isNotEmpty()) {
                    Spacer(Modifier.height(Space.xs))
                    Text(
                        "Frames by risk band: " + dist.joinToString(" · ") { "${riskName(it.first)} ${it.second}" } +
                            " of ${m.framesScored} scored.",
                        style = VxType.bodySmall,
                        color = InkMuted
                    )
                }
            }
            Spacer(Modifier.height(Space.sm))
            Text(
                "Component scores are taken at the frame where each method reached its peak score.",
                style = VxType.bodySmall,
                color = InkMuted
            )
        }

        DocSection(4) {
            MeasurementsTable(interpretation.regions)
        }

        if (interpretation.evidence.isNotEmpty()) {
            DocSection(5) {
                EvidenceGallery(
                    evidence = interpretation.evidence,
                    keyframeCount = result.keyframes.size,
                    videoUri = record.videoUri,
                    frameWidth = result.video?.width ?: 0,
                    frameHeight = result.video?.height ?: 0
                )
            }
        }

        DocSection(6, last = true) {
            Text(
                "VigilEx detects the worker's pose in sampled video frames (YOLO11n-Pose), follows the same worker " +
                    "through the video (ByteTrack) and measures joint angles in the side view. RULA and REBA are " +
                    "scored per frame; this report gives the peak score of each.",
                style = VxType.bodySmall,
                color = InkSecondary
            )
            if (interpretation.unmeasured.isNotEmpty()) {
                Spacer(Modifier.height(Space.xs))
                Text(
                    "Not observable from video: ${interpretation.unmeasured.joinToString(", ").lowercase()}, as well as " +
                        "force, load, coupling and activity. These are scored at their lowest-risk values.",
                    style = VxType.bodySmall,
                    color = InkSecondary
                )
            }
            Spacer(Modifier.height(Space.xs))
            Text(
                "This report describes observed posture. It does not assess injury, pain, fatigue or health, " +
                    "and findings should be reviewed by a qualified person.",
                style = VxType.bodySmall,
                color = InkSecondary
            )
            result.methodologyVersion?.let {
                Spacer(Modifier.height(Space.sm))
                Text("Methodology: $it", style = VxType.mono, color = InkMuted)
            }
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

/** 01 / EXECUTIVE ASSESSMENT under a strong rule; large numeral, quiet title. */
@Composable
private fun DocSection(number: Int, last: Boolean = false, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Hairline(color = Ink, modifier = Modifier.height(2.dp))
        Spacer(Modifier.height(Space.sm))
        Row(verticalAlignment = Alignment.Top) {
            Text(
                "%02d".format(number),
                style = VxType.figure.copy(fontSize = 40.sp, lineHeight = 42.sp),
                color = Ink,
                modifier = Modifier.width(72.dp)
            )
            Text(
                ReportSections[number - 1].uppercase(),
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
