package com.extrive.vigilex.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.extrive.vigilex.data.model.BodyMetric
import com.extrive.vigilex.data.model.DerivedAngles
import com.extrive.vigilex.data.model.Method
import com.extrive.vigilex.data.model.Severity
import com.extrive.vigilex.data.model.average
import com.extrive.vigilex.data.model.maximum
import com.extrive.vigilex.data.model.riskName
import com.extrive.vigilex.ui.format.formatDegrees
import com.extrive.vigilex.ui.format.formatDegreesNumber
import com.extrive.vigilex.ui.format.formatScale
import com.extrive.vigilex.ui.format.formatScore
import com.extrive.vigilex.ui.theme.Ink
import com.extrive.vigilex.ui.theme.InkFaint
import com.extrive.vigilex.ui.theme.InkMuted
import com.extrive.vigilex.ui.theme.Space
import com.extrive.vigilex.ui.theme.VxType

/** "04 / 07" with the backend risk band beneath it. */
@Composable
fun ScoreBlock(
    method: Method,
    score: Int?,
    risk: String?,
    modifier: Modifier = Modifier,
    large: Boolean = LocalWindowClass.current != WindowClass.COMPACT,
    caption: String? = null
) {
    Column(
        modifier.clearAndSetSemantics {
            contentDescription = if (score == null) "${method.label} not measured"
            else "${method.label} score $score of ${method.scaleMax}, ${riskName(risk)} risk"
        }
    ) {
        Text(method.label, style = VxType.label, color = InkMuted)
        Spacer(Modifier.height(Space.xs))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                formatScore(score),
                style = if (large) VxType.scoreHero else VxType.scoreLarge,
                color = if (score == null) InkFaint else Ink
            )
            Spacer(Modifier.width(Space.xs))
            Text(
                "/ ${formatScale(method.scaleMax)}",
                style = VxType.scoreScale,
                color = InkMuted,
                modifier = Modifier.padding(bottom = if (large) 14.dp else 9.dp)
            )
        }
        Spacer(Modifier.height(Space.sm))
        RiskBadge(method, risk)
        if (caption != null) {
            Spacer(Modifier.height(Space.xs))
            Text(caption, style = VxType.bodySmall, color = InkMuted)
        }
    }
}

/** RULA and REBA side by side, divided by a hairline: the primary result. */
@Composable
fun AssessmentScores(
    rulaScore: Int?,
    rulaRisk: String?,
    rebaScore: Int?,
    rebaRisk: String?,
    modifier: Modifier = Modifier,
    rulaCaption: String? = null,
    rebaCaption: String? = null
) {
    Row(
        modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
    ) {
        ScoreBlock(Method.RULA, rulaScore, rulaRisk, Modifier.weight(1f), caption = rulaCaption)
        VerticalHairline(Modifier.fillMaxHeight())
        ScoreBlock(
            Method.REBA,
            rebaScore,
            rebaRisk,
            Modifier
                .weight(1f)
                .padding(start = Space.lg),
            caption = rebaCaption
        )
    }
}

/** One body region: large average and maximum angles with small labels. */
@Composable
fun BodyMetricBlock(
    metric: BodyMetric,
    angles: DerivedAngles?,
    modifier: Modifier = Modifier,
    impact: Severity? = null,
    showImpact: Boolean = false
) {
    Column(modifier) {
        Hairline()
        Spacer(Modifier.height(Space.sm))
        Text(metric.label.uppercase(), style = VxType.label, color = Ink)
        Spacer(Modifier.height(Space.md))
        Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
            AngleFigure(angles?.average(metric), "Avg", emphasized = true)
            AngleFigure(angles?.maximum(metric), "Max", emphasized = false)
        }
        if (showImpact) {
            Spacer(Modifier.height(Space.sm))
            SeverityText(impact)
        }
    }
}

@Composable
private fun AngleFigure(value: Double?, label: String, emphasized: Boolean) {
    val compact = LocalWindowClass.current == WindowClass.COMPACT
    val style = when {
        emphasized && compact -> VxType.metric.copy(fontSize = 28.sp, lineHeight = 32.sp)
        emphasized -> VxType.metric
        else -> VxType.metricSmall
    }
    Row(verticalAlignment = Alignment.Bottom) {
        Text(formatDegreesNumber(value), style = style, color = if (value == null) InkFaint else Ink)
        if (value != null) Text("°", style = style, color = InkMuted)
        Spacer(Modifier.width(Space.xs))
        Text(
            label.uppercase(),
            style = VxType.label,
            color = InkMuted,
            modifier = Modifier.padding(bottom = 5.dp)
        )
    }
}

/** The four derived angles, 2 × 2 on phones and in one row on wider screens. */
@Composable
fun BodyMetricsGrid(
    angles: DerivedAngles?,
    modifier: Modifier = Modifier,
    impacts: Map<BodyMetric, Severity?>? = null
) {
    val columns = if (LocalWindowClass.current == WindowClass.COMPACT) 2 else 4
    GridRows(BodyMetric.entries, columns, modifier) { metric ->
        BodyMetricBlock(
            metric = metric,
            angles = angles,
            impact = impacts?.get(metric),
            showImpact = impacts != null
        )
    }
}

// --------------------------------------------------------------------- tables

class TableColumn(
    val header: String,
    val weight: Float,
    val align: TextAlign = TextAlign.Start
)

/** A Swiss table: small tracked headers, hairline rules, tabular figures. */
@Composable
fun DataTable(
    columns: List<TableColumn>,
    rowCount: Int,
    modifier: Modifier = Modifier,
    onRowClick: ((Int) -> Unit)? = null,
    cell: @Composable (row: Int, column: Int) -> Unit
) {
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(bottom = Space.xs)
        ) {
            columns.forEach { column ->
                Text(
                    column.header.uppercase(),
                    style = VxType.label,
                    color = InkMuted,
                    textAlign = column.align,
                    modifier = Modifier.weight(column.weight)
                )
            }
        }
        Hairline(color = Ink)
        repeat(rowCount) { row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (onRowClick != null) Modifier.clickable { onRowClick(row) } else Modifier)
                    .padding(vertical = Space.md),
                verticalAlignment = Alignment.CenterVertically
            ) {
                columns.forEachIndexed { index, column ->
                    Column(
                        Modifier.weight(column.weight),
                        horizontalAlignment = when (column.align) {
                            TextAlign.End -> Alignment.End
                            TextAlign.Center -> Alignment.CenterHorizontally
                            else -> Alignment.Start
                        }
                    ) { cell(row, index) }
                }
            }
            Hairline()
        }
    }
}

@Composable
fun CellText(text: String, muted: Boolean = false, strong: Boolean = false) {
    Text(
        text,
        style = if (strong) VxType.tableCell else VxType.tableCell.copy(fontWeight = FontWeight.Normal),
        color = if (muted) InkMuted else Ink
    )
}

/** METRIC · AVG · MAX · IMPACT for the four derived angles. */
@Composable
fun BiomechanicalTable(
    angles: DerivedAngles?,
    impacts: Map<BodyMetric, Severity?>,
    modifier: Modifier = Modifier
) {
    val metrics = BodyMetric.entries
    DataTable(
        columns = listOf(
            TableColumn("Metric", 1.5f),
            TableColumn("Avg", 1f, TextAlign.End),
            TableColumn("Max", 1f, TextAlign.End),
            TableColumn("Impact", 1.4f, TextAlign.End)
        ),
        rowCount = metrics.size,
        modifier = modifier
    ) { row, column ->
        val metric = metrics[row]
        when (column) {
            0 -> CellText(metric.label, strong = true)
            1 -> CellText(formatDegrees(angles?.average(metric)))
            2 -> CellText(formatDegrees(angles?.maximum(metric)), strong = true)
            else -> SeverityText(impacts[metric])
        }
    }
}

/** Label / value pairs, e.g. capture quality. */
@Composable
fun KeyValueTable(rows: List<Pair<String, String>>, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        rows.forEach { (label, value) ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = Space.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(label, style = VxType.bodySmall, color = InkMuted, modifier = Modifier.weight(1f))
                Text(value, style = VxType.mono.copy(fontSize = VxType.tableCell.fontSize), color = Ink)
            }
            Hairline()
        }
    }
}
