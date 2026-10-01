package com.extrive.vigilex.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.extrive.vigilex.data.model.Method
import com.extrive.vigilex.data.model.ReportData
import com.extrive.vigilex.data.model.TrendPoint
import com.extrive.vigilex.data.model.buildReport
import com.extrive.vigilex.data.store.AssessmentStore
import com.extrive.vigilex.ui.components.CellText
import com.extrive.vigilex.ui.components.DataTable
import com.extrive.vigilex.ui.components.EmptyState
import com.extrive.vigilex.ui.components.GridRows
import com.extrive.vigilex.ui.components.Hairline
import com.extrive.vigilex.ui.components.LoadingState
import com.extrive.vigilex.ui.components.LocalWindowClass
import com.extrive.vigilex.ui.components.PageContainer
import com.extrive.vigilex.ui.components.PageHeader
import com.extrive.vigilex.ui.components.RiskDistribution
import com.extrive.vigilex.ui.components.SectionLabel
import com.extrive.vigilex.ui.components.TableColumn
import com.extrive.vigilex.ui.components.TrendChart
import com.extrive.vigilex.ui.components.WindowClass
import com.extrive.vigilex.ui.format.EMPTY_VALUE
import com.extrive.vigilex.ui.format.formatDegrees
import com.extrive.vigilex.ui.format.formatScale
import com.extrive.vigilex.ui.format.formatScore
import com.extrive.vigilex.ui.format.formatShortDate
import com.extrive.vigilex.ui.theme.Ink
import com.extrive.vigilex.ui.theme.InkMuted
import com.extrive.vigilex.ui.theme.InkSecondary
import com.extrive.vigilex.ui.theme.Space
import com.extrive.vigilex.ui.theme.VxType

@Composable
fun ReportsScreen(onStartAssessment: () -> Unit) {
    val records by AssessmentStore.records.collectAsState()
    val loaded by AssessmentStore.loaded.collectAsState()
    val report = remember(records) { buildReport(records) }

    PageContainer {
        PageHeader(
            eyebrow = "Reports",
            title = "Ergonomic reports",
            supporting = "Aggregated from the assessments saved on this device."
        )
        when {
            !loaded -> LoadingState()
            report == null -> EmptyState(
                title = "No report data yet",
                body = "Complete assessments to begin building your ergonomic reports. " +
                    "Score trends appear after two or more assessments.",
                actionLabel = "Start assessment",
                onAction = onStartAssessment
            )
            else -> ReportContent(report)
        }
    }
}

@Composable
private fun ReportContent(report: ReportData) {
    val compact = LocalWindowClass.current == WindowClass.COMPACT

    val trend = report.trend
    val figures = listOf(
        "Assessments" to report.assessmentCount.toString(),
        "Highest RULA" to (trend.mapNotNull { it.rula }.maxOrNull()?.let { "${formatScore(it)} / ${formatScale(Method.RULA.scaleMax)}" } ?: EMPTY_VALUE),
        "Highest REBA" to (trend.mapNotNull { it.reba }.maxOrNull()?.let { "${formatScore(it)} / ${formatScale(Method.REBA.scaleMax)}" } ?: EMPTY_VALUE),
        "Period" to if (trend.size > 1) "${formatShortDate(trend.first().createdAtMillis)} – ${formatShortDate(trend.last().createdAtMillis)}"
        else formatShortDate(trend.first().createdAtMillis)
    )
    GridRows(figures, if (compact) 2 else 4, verticalGap = Space.lg) { (label, value) ->
        Column {
            Hairline()
            Spacer(Modifier.height(Space.sm))
            Text(label.uppercase(), style = VxType.label, color = InkMuted)
            Spacer(Modifier.height(Space.xs))
            Text(value, style = VxType.metricSmall, color = Ink)
        }
    }
    Spacer(Modifier.height(Space.xxl))

    SectionLabel("Peak score trend")
    if (report.hasTrend) {
        if (compact) {
            TrendBlock(Method.RULA, trend)
            Spacer(Modifier.height(Space.xl))
            TrendBlock(Method.REBA, trend)
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(Space.xxl)) {
                TrendBlock(Method.RULA, trend, Modifier.weight(1f))
                TrendBlock(Method.REBA, trend, Modifier.weight(1f))
            }
        }
    } else {
        Text(
            "Trends appear after ${ReportData.MIN_TREND_POINTS} or more assessments. " +
                "One assessment has been completed so far.",
            style = VxType.body,
            color = InkSecondary
        )
    }
    Spacer(Modifier.height(Space.xxl))

    SectionLabel("Peak risk across assessments")
    val rula = report.peakRiskCounts[Method.RULA].orEmpty().toMap()
    val reba = report.peakRiskCounts[Method.REBA].orEmpty().toMap()
    if (compact) {
        RiskDistribution(Method.RULA, rula, unit = "assessments")
        Spacer(Modifier.height(Space.xl))
        RiskDistribution(Method.REBA, reba, unit = "assessments")
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(Space.xxl)) {
            RiskDistribution(Method.RULA, rula, Modifier.weight(1f), unit = "assessments")
            RiskDistribution(Method.REBA, reba, Modifier.weight(1f), unit = "assessments")
        }
    }
    Spacer(Modifier.height(Space.sm))
    Text("Each assessment counted once, by the risk band of its peak score.", style = VxType.bodySmall, color = InkMuted)
    Spacer(Modifier.height(Space.xxl))

    SectionLabel("Posture metrics across assessments")
    DataTable(
        columns = listOf(
            TableColumn("Metric", 1.4f),
            TableColumn(if (compact) "Mean avg" else "Mean of averages", 1.1f, TextAlign.End),
            TableColumn(if (compact) "Top max" else "Highest maximum", 1.1f, TextAlign.End),
            TableColumn("n", 0.5f, TextAlign.End)
        ),
        rowCount = report.metrics.size
    ) { row, column ->
        val item = report.metrics[row]
        when (column) {
            0 -> CellText(item.metric.label, strong = true)
            1 -> CellText(formatDegrees(item.meanOfAverages))
            2 -> CellText(formatDegrees(item.highestMaximum), strong = true)
            else -> CellText(item.assessments.toString(), muted = true)
        }
    }
}

@Composable
private fun TrendBlock(method: Method, trend: List<TrendPoint>, modifier: Modifier = Modifier) {
    val values = trend.map { if (method == Method.RULA) it.rula else it.reba }
    Column(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(method.label, style = VxType.title, color = Ink, modifier = Modifier.weight(1f))
            Text("Scale 0–${method.scaleMax}", style = VxType.mono, color = InkMuted)
        }
        Spacer(Modifier.height(Space.sm))
        TrendChart(values = values, scaleMax = method.scaleMax)
        Row {
            Text(formatShortDate(trend.first().createdAtMillis), style = VxType.mono, color = InkMuted, modifier = Modifier.weight(1f))
            Text(formatShortDate(trend.last().createdAtMillis), style = VxType.mono, color = InkMuted)
        }
    }
}
