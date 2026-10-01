package com.extrive.vigilex.ui.components

import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.extrive.vigilex.data.model.AssessmentRecord
import com.extrive.vigilex.data.insight.overallRisk
import com.extrive.vigilex.data.model.Method
import com.extrive.vigilex.ui.format.formatDateTime
import com.extrive.vigilex.ui.format.formatScore
import com.extrive.vigilex.ui.format.formatSeconds
import com.extrive.vigilex.ui.theme.Ink
import com.extrive.vigilex.ui.theme.InkMuted
import com.extrive.vigilex.ui.theme.Space
import com.extrive.vigilex.ui.theme.VxType

/**
 * Saved assessments. Wide screens get a full table
 * (DATE · ASSESSMENT · DURATION · RULA · REBA · RISK); phones a two-line list.
 */
@Composable
fun AssessmentList(
    records: List<AssessmentRecord>,
    onOpen: (AssessmentRecord) -> Unit,
    modifier: Modifier = Modifier
) {
    if (LocalWindowClass.current == WindowClass.COMPACT) {
        Column(modifier.fillMaxWidth()) {
            Hairline(color = Ink)
            records.forEach { record ->
                CompactRow(record) { onOpen(record) }
                Hairline()
            }
        }
    } else {
        DataTable(
            columns = listOf(
                TableColumn("Date", 1.4f),
                TableColumn("Assessment", 2.2f),
                TableColumn("Duration", 0.9f, TextAlign.End),
                TableColumn("RULA", 0.7f, TextAlign.End),
                TableColumn("REBA", 0.7f, TextAlign.End),
                TableColumn("Risk", 1.3f, TextAlign.End)
            ),
            rowCount = records.size,
            modifier = modifier,
            onRowClick = { onOpen(records[it]) }
        ) { row, column ->
            val record = records[row]
            when (column) {
                0 -> CellText(formatDateTime(record.createdAtMillis), muted = true)
                1 -> Text(
                    record.fileName,
                    style = VxType.tableCell,
                    color = Ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(end = Space.md)
                )
                2 -> CellText(formatSeconds(record.durationSec))
                3 -> CellText(formatScore(record.rulaMax), strong = true)
                4 -> CellText(formatScore(record.rebaMax), strong = true)
                else -> OverallRisk(record)
            }
        }
    }
}

@Composable
private fun CompactRow(record: AssessmentRecord, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = Space.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        KeySquare(record.overallRisk().accent(), size = 8.dp)
        Spacer(Modifier.width(Space.sm))
        Column(Modifier.weight(1f)) {
            Text(
                record.fileName,
                style = VxType.title,
                color = Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                listOfNotNull(
                    record.overallRisk()?.let { "${it.label} risk" },
                    formatDateTime(record.createdAtMillis)
                ).joinToString(" · "),
                style = VxType.bodySmall,
                color = InkMuted
            )
        }
        Spacer(Modifier.width(Space.md))
        ScorePill(Method.RULA, record.rulaMax)
        Spacer(Modifier.width(Space.md))
        ScorePill(Method.REBA, record.rebaMax)
    }
}

@Composable
private fun ScorePill(method: Method, score: Int?) {
    Column(horizontalAlignment = Alignment.End) {
        Text(formatScore(score), style = VxType.metricSmall, color = Ink)
        Text(method.label, style = VxType.label, color = InkMuted)
    }
}

/** Overall ergonomic risk: the more severe backend risk level of RULA and REBA. */
@Composable
private fun OverallRisk(record: AssessmentRecord) {
    val level = record.overallRisk()
    if (level == null) {
        CellText("—", muted = true)
        return
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        KeySquare(level.accent(), size = 7.dp)
        Spacer(Modifier.width(6.dp))
        Text(level.label.uppercase(), style = VxType.label, color = level.headlineColor())
    }
}
