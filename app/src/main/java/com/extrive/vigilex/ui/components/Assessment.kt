package com.extrive.vigilex.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import com.extrive.vigilex.ui.theme.RedInk
import com.extrive.vigilex.ui.theme.RedTint
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.extrive.vigilex.data.insight.ComponentScore
import com.extrive.vigilex.data.insight.Interpretation
import com.extrive.vigilex.data.insight.MethodResult
import com.extrive.vigilex.data.insight.RegionFinding
import com.extrive.vigilex.data.insight.RegionStatus
import com.extrive.vigilex.data.insight.RiskLevel
import com.extrive.vigilex.data.model.BodyMetric
import com.extrive.vigilex.data.model.Severity
import com.extrive.vigilex.data.model.riskName
import com.extrive.vigilex.ui.format.EMPTY_VALUE
import com.extrive.vigilex.ui.format.formatDegrees
import com.extrive.vigilex.ui.theme.Gold
import com.extrive.vigilex.ui.theme.GoldInk
import com.extrive.vigilex.ui.theme.HairlineStrong
import com.extrive.vigilex.ui.theme.Ink
import com.extrive.vigilex.ui.theme.InkMuted
import com.extrive.vigilex.ui.theme.InkSecondary
import com.extrive.vigilex.ui.theme.Red
import com.extrive.vigilex.ui.theme.Space
import com.extrive.vigilex.ui.theme.VeryHighRed
import com.extrive.vigilex.ui.theme.VxType

/*
 * The assessment's visual language. Two levels: the conclusion in plain words
 * first, the RULA/REBA evidence beneath it. Colour is a signal only: neutral
 * for low, gold for attention, red solely for a high-risk result.
 */

// ------------------------------------------------------------------ colour

/** Accent rule / marker colour for an overall risk level. */
fun RiskLevel?.accent(): Color = when (this) {
    RiskLevel.LOW -> InkMuted
    RiskLevel.MODERATE -> Gold
    RiskLevel.HIGH -> Red
    RiskLevel.VERY_HIGH -> VeryHighRed
    null -> HairlineStrong
}

/** Headline text colour: ink unless the result is genuinely high risk. */
fun RiskLevel?.headlineColor(): Color = when (this) {
    RiskLevel.HIGH -> Red
    RiskLevel.VERY_HIGH -> VeryHighRed
    null -> InkMuted
    else -> Ink
}

fun RegionStatus.marker(): Color = when (this) {
    RegionStatus.NOT_MEASURED -> HairlineStrong
    RegionStatus.LOW -> HairlineStrong
    RegionStatus.ATTENTION, RegionStatus.ELEVATED -> Gold
    RegionStatus.HIGH -> Red
}

fun RegionStatus.textColor(): Color = when (this) {
    RegionStatus.NOT_MEASURED, RegionStatus.LOW -> InkMuted
    RegionStatus.ATTENTION -> Ink
    RegionStatus.ELEVATED -> GoldInk
    RegionStatus.HIGH -> Red
}

val RiskLevel?.headlineText: String get() = this?.let { "${it.label} risk" } ?: "Not measured"

// ------------------------------------------------------------- conclusion

/**
 * Overall risk in words, what it means, then RULA and REBA as its evidence.
 * The one block every assessment view starts with.
 */
@Composable
fun RiskConclusion(
    interpretation: Interpretation,
    modifier: Modifier = Modifier,
    eyebrow: String = "Overall posture risk",
    showGuidance: Boolean = true
) {
    val compact = LocalWindowClass.current == WindowClass.COMPACT
    val level = interpretation.overall
    VxCard(modifier) {
        Text(eyebrow, style = VxType.label, color = InkMuted)
        Spacer(Modifier.height(Space.xs))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(level.accent())
            )
            Spacer(Modifier.width(Space.sm))
            Text(
                level.headlineText,
                style = if (compact) VxType.riskHeadlineCompact else VxType.riskHeadline,
                color = level.headlineColor(),
                modifier = Modifier.semantics {
                    heading()
                    contentDescription = "Overall posture risk: ${level?.label ?: "not measured"}"
                }
            )
        }
        if (showGuidance) interpretation.guidance?.let {
            Spacer(Modifier.height(Space.xs))
            Text(it, style = VxType.body, color = InkSecondary)
        }
        Spacer(Modifier.height(Space.md))
        Hairline()
        MethodLine(interpretation.rula)
        Hairline()
        MethodLine(interpretation.reba)
        interpretation.observation?.let {
            Hairline()
            Spacer(Modifier.height(Space.sm))
            Text(it, style = VxType.body, color = Ink, modifier = Modifier.widthIn(max = 620.dp))
        }
    }
}

/** RULA peak score  4 / 7  [Moderate risk] */
@Composable
fun MethodLine(result: MethodResult, modifier: Modifier = Modifier) {
    val method = result.method
    Row(
        modifier
            .fillMaxWidth()
            .padding(vertical = Space.sm)
            .clearAndSetSemantics {
                contentDescription = if (result.score == null) "${method.label} peak score not measured"
                else "${method.label} peak score ${result.score} of ${method.scaleMax}, ${riskName(result.risk)} risk"
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text("${method.label} peak score", style = VxType.bodySmall, color = InkSecondary)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(result.score?.toString() ?: EMPTY_VALUE, style = VxType.metricSmall, color = if (result.score == null) InkMuted else Ink)
                Text(
                    " / ${method.scaleMax}",
                    style = VxType.bodySmall,
                    color = InkMuted,
                    modifier = Modifier.padding(bottom = 2.dp)
                )
            }
        }
        RiskBadge(method, result.risk)
    }
}

// ---------------------------------------------------------------- findings

@Composable
fun StatusTag(status: RegionStatus, modifier: Modifier = Modifier) {
    when (status) {
        RegionStatus.HIGH -> Tag(status.label, Red, modifier, background = RedTint, textColor = RedInk)
        RegionStatus.ELEVATED, RegionStatus.ATTENTION -> Tag(status.label, Gold, modifier)
        RegionStatus.LOW -> Tag(status.label, InkMuted, modifier)
        RegionStatus.NOT_MEASURED -> Tag(status.label, InkMuted, modifier, textColor = InkMuted, hollow = true)
    }
}

/**
 * One finding, in the same layout for every finding: what was observed and
 * its status, the body region with its measured angles, then one sentence
 * from the interpretation of this assessment's own frames.
 */
@Composable
fun FindingCard(finding: RegionFinding, modifier: Modifier = Modifier) {
    VxCard(modifier.semantics(mergeDescendants = true) {}) {
        Row(verticalAlignment = Alignment.Top) {
            Text(finding.title, style = VxType.title, color = Ink, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(Space.sm))
            StatusTag(finding.status)
        }
        Spacer(Modifier.height(Space.xxs))
        Text(
            listOfNotNull(
                finding.metric.label,
                finding.maximum?.let { "peak ${formatDegrees(it)}" },
                finding.average?.let { "average ${formatDegrees(it)}" }
            ).joinToString(" · "),
            style = VxType.bodySmall,
            color = InkSecondary
        )
        Spacer(Modifier.height(Space.xs))
        Text(finding.detail, style = VxType.bodySmall, color = InkMuted)
    }
}

/** Key findings in the order the interpretation ranks them; nothing is drawn when there are none. */
@Composable
fun WhatWeFound(findings: List<RegionFinding>, modifier: Modifier = Modifier) {
    if (findings.isEmpty()) return
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
        findings.forEach { FindingCard(it) }
    }
}

/**
 * WHY THIS RESULT: every body region by significance, peak angle and status.
 * Plain words first; the RULA/REBA breakdown is one link away.
 */
@Composable
fun WhyThisResult(
    regions: List<RegionFinding>,
    modifier: Modifier = Modifier,
    onViewRula: (() -> Unit)? = null,
    onViewReba: (() -> Unit)? = null
) {
    val compact = LocalWindowClass.current == WindowClass.COMPACT
    Column(modifier.fillMaxWidth()) {
        if (compact) {
            Hairline()
            regions.forEach { region ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = Space.md),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(region.metric.label, style = VxType.title, color = Ink)
                        Text("Peak ${formatDegrees(region.maximum)}", style = VxType.bodySmall, color = InkSecondary)
                    }
                    Spacer(Modifier.width(Space.sm))
                    StatusTag(region.status)
                }
                Hairline()
            }
        } else {
            GridRows(regions, columns = 4, horizontalGap = Space.lg) { region ->
                Column {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(if (region.status.flagged) 2.dp else 1.dp)
                            .background(if (region.status.flagged) Ink else HairlineStrong)
                    )
                    Spacer(Modifier.height(Space.sm))
                    Text(region.metric.label, style = VxType.title, color = Ink)
                    Spacer(Modifier.height(Space.sm))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(formatDegrees(region.maximum), style = VxType.metric, color = if (region.status.flagged) Ink else InkSecondary)
                        Spacer(Modifier.width(Space.xs))
                        Text("peak", style = VxType.label, color = InkMuted, modifier = Modifier.padding(bottom = 6.dp))
                    }
                    Spacer(Modifier.height(Space.sm))
                    StatusTag(region.status)
                }
            }
        }
        if (onViewRula != null || onViewReba != null) {
            Spacer(Modifier.height(Space.md))
            Row(horizontalArrangement = Arrangement.spacedBy(Space.lg)) {
                onViewRula?.let { ArrowLink("View RULA breakdown", onClick = it) }
                onViewReba?.let { ArrowLink("View REBA breakdown", onClick = it) }
            }
        }
    }
}

// ------------------------------------------------------------- level two

/** MEASUREMENTS: region · avg · max · status, in fixed anatomical order. */
@Composable
fun MeasurementsTable(regions: List<RegionFinding>, modifier: Modifier = Modifier) {
    val rows = BodyMetric.entries.mapNotNull { m -> regions.firstOrNull { it.metric == m } }
    DataTable(
        columns = listOf(
            TableColumn("Region", 1.5f),
            TableColumn("Avg", 1f, TextAlign.End),
            TableColumn("Max", 1f, TextAlign.End),
            TableColumn("Status", 1.4f, TextAlign.End)
        ),
        rowCount = rows.size,
        modifier = modifier
    ) { row, column ->
        val region = rows[row]
        when (column) {
            0 -> CellText(region.metric.label, strong = true)
            1 -> CellText(formatDegrees(region.average), muted = true)
            2 -> CellText(formatDegrees(region.maximum), strong = true)
            else -> StatusTag(region.status)
        }
    }
}

/**
 * One method's peak score with the component bands that produced it, at the
 * frame where the peak occurred. Components the camera cannot observe are
 * listed as such, never given a measured-looking value.
 */
@Composable
fun MethodBreakdown(result: MethodResult, peakTimeLabel: String?, modifier: Modifier = Modifier) {
    val method = result.method
    Column(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(method.label, style = VxType.sectionTitle, color = Ink, modifier = Modifier.weight(1f))
            Text(result.score?.toString() ?: EMPTY_VALUE, style = VxType.metric, color = Ink)
            Text(" / ${method.scaleMax}", style = VxType.bodySmall, color = InkMuted, modifier = Modifier.padding(bottom = 6.dp))
        }
        Text("Peak score", style = VxType.label, color = InkMuted)
        Spacer(Modifier.height(Space.xs))
        Row(verticalAlignment = Alignment.CenterVertically) {
            RiskBadge(method, result.risk)
            Spacer(Modifier.weight(1f))
            Text("${result.framesScored} frames scored", style = VxType.mono, color = InkMuted)
        }
        Spacer(Modifier.height(Space.lg))
        if (result.components.isNotEmpty()) {
            DataTable(
                columns = listOf(
                    TableColumn("Component", 1.7f),
                    TableColumn("Angle", 1f, TextAlign.End),
                    TableColumn("Score", 0.9f, TextAlign.End)
                ),
                rowCount = result.components.size
            ) { row, column ->
                val c = result.components[row]
                when (column) {
                    0 -> CellText(c.name, strong = c.measured, muted = !c.measured)
                    1 -> CellText(if (c.measured) formatDegrees(c.angleDeg) else "Not observable", muted = !c.measured)
                    else -> ComponentScoreCell(c)
                }
            }
            Spacer(Modifier.height(Space.sm))
            Text(
                listOfNotNull(
                    peakTimeLabel?.let { "${method.label} component measurements at the peak-score frame ($it)." },
                    "Score is the band within the component's range."
                ).joinToString(" "),
                style = VxType.bodySmall,
                color = InkMuted
            )
        }
    }
}

@Composable
private fun ComponentScoreCell(c: ComponentScore) {
    if (!c.measured || c.band == null) {
        CellText(EMPTY_VALUE, muted = true)
        return
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        c.severity?.let {
            KeySquare(if (it == Severity.OK) HairlineStrong else Gold, size = 6.dp)
            Spacer(Modifier.width(6.dp))
        }
        CellText(c.maxBand?.let { "${c.band} / $it" } ?: c.band.toString(), strong = true)
    }
}
