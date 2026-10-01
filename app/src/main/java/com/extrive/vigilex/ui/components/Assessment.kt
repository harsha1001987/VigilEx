package com.extrive.vigilex.ui.components

import androidx.compose.foundation.background
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.extrive.vigilex.data.insight.ComponentScore
import com.extrive.vigilex.data.insight.Interpretation
import com.extrive.vigilex.data.insight.MethodResult
import com.extrive.vigilex.data.insight.RegionFinding
import com.extrive.vigilex.data.insight.RegionStatus
import com.extrive.vigilex.data.insight.RiskLevel
import com.extrive.vigilex.data.model.BodyMetric
import com.extrive.vigilex.data.model.Method
import com.extrive.vigilex.data.model.Severity
import com.extrive.vigilex.data.model.riskName
import com.extrive.vigilex.ui.format.EMPTY_VALUE
import com.extrive.vigilex.ui.format.formatDegrees
import com.extrive.vigilex.ui.format.formatDegreesNumber
import com.extrive.vigilex.ui.format.formatScale
import com.extrive.vigilex.ui.format.formatScore
import com.extrive.vigilex.ui.theme.Charcoal
import com.extrive.vigilex.ui.theme.Gold
import com.extrive.vigilex.ui.theme.GoldInk
import com.extrive.vigilex.ui.theme.HairlineStrong
import com.extrive.vigilex.ui.theme.Ink
import com.extrive.vigilex.ui.theme.InkFaint
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
    RiskLevel.LOW -> Charcoal
    RiskLevel.MODERATE -> Gold
    RiskLevel.HIGH -> Red
    RiskLevel.VERY_HIGH -> VeryHighRed
    null -> HairlineStrong
}

/** Headline text colour: ink unless the result is genuinely high risk. */
fun RiskLevel?.headlineColor(): Color = when (this) {
    RiskLevel.HIGH -> Red
    RiskLevel.VERY_HIGH -> VeryHighRed
    null -> InkFaint
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

val RiskLevel?.headlineText: String get() = this?.label?.uppercase() ?: "NOT MEASURED"

// ------------------------------------------------------------- conclusion

/**
 * ERGONOMIC RISK / MODERATE / RULA and REBA as supporting evidence, then the
 * generated observation. The one block every assessment view starts with.
 */
@Composable
fun RiskConclusion(
    interpretation: Interpretation,
    modifier: Modifier = Modifier,
    eyebrow: String = "Ergonomic risk",
    showGuidance: Boolean = true
) {
    val compact = LocalWindowClass.current == WindowClass.COMPACT
    val level = interpretation.overall
    Column(modifier.fillMaxWidth()) {
        Text(eyebrow.uppercase(), style = VxType.label, color = InkMuted)
        Spacer(Modifier.height(Space.sm))
        Text(
            level.headlineText,
            style = if (compact) VxType.riskHeadlineCompact else VxType.riskHeadline,
            color = level.headlineColor(),
            modifier = Modifier.semantics {
                heading()
                contentDescription = "Ergonomic risk: ${level?.label ?: "not measured"}"
            }
        )
        Spacer(Modifier.height(Space.md))
        // A short accent bar over the full rule: the level's signal colour, used once.
        Box(
            Modifier
                .width(56.dp)
                .height(4.dp)
                .background(level.accent())
        )
        Hairline(color = Ink)
        MethodLine(interpretation.rula)
        Hairline()
        MethodLine(interpretation.reba)
        Hairline()
        interpretation.observation?.let {
            Spacer(Modifier.height(Space.lg))
            Text(
                it,
                style = if (compact) VxType.body else VxType.sectionTitle.copy(fontWeight = FontWeight.Normal),
                color = Ink,
                modifier = Modifier.widthIn(max = 620.dp)
            )
        }
        if (showGuidance) interpretation.guidance?.let {
            Spacer(Modifier.height(Space.xs))
            Text(it, style = VxType.bodySmall, color = InkSecondary)
        }
    }
}

/** RULA   04 / 07   ■ Moderate */
@Composable
fun MethodLine(result: MethodResult, modifier: Modifier = Modifier) {
    val method = result.method
    Row(
        modifier
            .fillMaxWidth()
            .padding(vertical = Space.sm)
            .clearAndSetSemantics {
                contentDescription = if (result.score == null) "${method.label} not measured"
                else "${method.label} ${result.score} of ${method.scaleMax}, ${riskName(result.risk)}"
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(method.label, style = VxType.label, color = InkMuted, modifier = Modifier.width(64.dp))
        Text(formatScore(result.score), style = VxType.metricSmall, color = if (result.score == null) InkFaint else Ink)
        Text(" / ${formatScale(method.scaleMax)}", style = VxType.mono, color = InkMuted)
        Spacer(Modifier.weight(1f))
        if (result.score != null) {
            KeySquare(result.level.accent(), size = 8.dp)
            Spacer(Modifier.width(Space.xs))
        }
        Text(riskName(result.risk), style = VxType.bodySmall, color = InkSecondary)
    }
}

// ---------------------------------------------------------------- findings

@Composable
fun StatusTag(status: RegionStatus, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        KeySquare(status.marker(), size = 7.dp)
        Spacer(Modifier.width(6.dp))
        Text(
            status.label.uppercase(),
            style = VxType.label.copy(fontWeight = if (status >= RegionStatus.ELEVATED) FontWeight.Bold else FontWeight.SemiBold),
            color = status.textColor()
        )
    }
}

/**
 * Editorial treatment of the most significant finding: a large peak figure,
 * a small label, one statement, one line of evidence.
 */
@Composable
fun KeyFinding(finding: RegionFinding, modifier: Modifier = Modifier, number: Int = 1) {
    Column(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text("KEY FINDING", style = VxType.label, color = Ink, modifier = Modifier.weight(1f))
            Text("%02d".format(number), style = VxType.mono, color = InkMuted)
        }
        Spacer(Modifier.height(Space.xs))
        Hairline(color = Ink)
        Spacer(Modifier.height(Space.md))
        Text(finding.metric.label.uppercase(), style = VxType.label, color = InkMuted)
        Spacer(Modifier.height(Space.xs))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                finding.maximum?.let { formatDegreesNumber(it) + "°" } ?: EMPTY_VALUE,
                style = VxType.figure,
                color = Ink
            )
            Spacer(Modifier.width(Space.sm))
            Text("PEAK", style = VxType.label, color = InkMuted, modifier = Modifier.padding(bottom = 10.dp))
            finding.average?.let {
                Spacer(Modifier.width(Space.md))
                Text(
                    "${formatDegrees(it)} AVG",
                    style = VxType.mono,
                    color = InkMuted,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
        }
        Spacer(Modifier.height(Space.sm))
        Row(verticalAlignment = Alignment.CenterVertically) {
            KeySquare(finding.status.marker(), size = 8.dp)
            Spacer(Modifier.width(Space.xs))
            Text(
                "${finding.title} observed".uppercase(),
                style = VxType.labelLarge,
                color = finding.status.textColor().takeIf { finding.status.flagged } ?: Ink
            )
        }
        Spacer(Modifier.height(Space.xs))
        Text(finding.detail, style = VxType.bodySmall, color = InkSecondary)
    }
}

/** 02  Pronounced neck flexion ........ ELEVATED */
@Composable
fun FindingRow(number: Int, finding: RegionFinding, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.padding(vertical = Space.md), verticalAlignment = Alignment.Top) {
            Text("%02d".format(number), style = VxType.mono, color = InkMuted, modifier = Modifier.width(40.dp))
            Column(Modifier.weight(1f)) {
                Text(finding.title, style = VxType.title, color = Ink)
                Spacer(Modifier.height(2.dp))
                Text(finding.detail, style = VxType.bodySmall, color = InkSecondary)
            }
            Spacer(Modifier.width(Space.md))
            StatusTag(finding.status, Modifier.padding(top = 4.dp))
        }
        Hairline()
    }
}

/** WHAT WE FOUND: the key finding featured, the rest as numbered rows. */
@Composable
fun WhatWeFound(findings: List<RegionFinding>, modifier: Modifier = Modifier) {
    if (findings.isEmpty()) return
    Column(modifier.fillMaxWidth()) {
        KeyFinding(findings.first())
        if (findings.size > 1) {
            Spacer(Modifier.height(Space.xl))
            Text("ALSO FOUND", style = VxType.label, color = InkMuted)
            Spacer(Modifier.height(Space.xs))
            Hairline(color = Ink)
            findings.drop(1).forEachIndexed { i, finding -> FindingRow(i + 2, finding) }
        }
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
            Hairline(color = Ink)
            regions.forEach { region ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = Space.md),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(region.metric.label.uppercase(), style = VxType.label, color = Ink, modifier = Modifier.weight(1f))
                    Text(
                        formatDegrees(region.maximum),
                        style = VxType.metricSmall,
                        color = if (region.status.flagged) Ink else InkSecondary
                    )
                    Text(" MAX", style = VxType.label, color = InkMuted)
                    Box(Modifier.width(112.dp), contentAlignment = Alignment.CenterEnd) { StatusTag(region.status) }
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
                    Text(region.metric.label.uppercase(), style = VxType.label, color = Ink)
                    Spacer(Modifier.height(Space.md))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(formatDegrees(region.maximum), style = VxType.metric, color = if (region.status.flagged) Ink else InkSecondary)
                        Spacer(Modifier.width(Space.xs))
                        Text("MAX", style = VxType.label, color = InkMuted, modifier = Modifier.padding(bottom = 6.dp))
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
            Text(formatScore(result.score), style = VxType.metric, color = Ink)
            Text(" / ${formatScale(method.scaleMax)}", style = VxType.mono, color = InkMuted, modifier = Modifier.padding(bottom = 6.dp))
        }
        Spacer(Modifier.height(Space.xs))
        Row(verticalAlignment = Alignment.CenterVertically) {
            KeySquare(result.level.accent(), size = 8.dp)
            Spacer(Modifier.width(Space.xs))
            Text("${riskName(result.risk)} risk".uppercase(), style = VxType.label, color = Ink)
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
                    peakTimeLabel?.let { "Component scores at the peak-scoring frame ($it)." },
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
