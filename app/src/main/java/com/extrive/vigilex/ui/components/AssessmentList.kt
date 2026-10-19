package com.extrive.vigilex.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.extrive.vigilex.data.insight.overallRisk
import com.extrive.vigilex.data.model.AssessmentRecord
import com.extrive.vigilex.data.model.Method
import com.extrive.vigilex.ui.format.formatRelativeDateTime
import com.extrive.vigilex.ui.format.formatScore
import com.extrive.vigilex.ui.format.formatScoreSummary
import com.extrive.vigilex.ui.format.formatSeconds
import com.extrive.vigilex.ui.theme.Ink
import com.extrive.vigilex.ui.theme.InkMuted
import com.extrive.vigilex.ui.theme.InkSecondary
import com.extrive.vigilex.ui.theme.Space
import com.extrive.vigilex.ui.theme.VxType

/**
 * Saved assessments. Phones get one card per assessment; wide screens a table
 * (Date · Assessment · Duration · RULA · REBA · Risk) on a card.
 */
@Composable
fun AssessmentList(
    records: List<AssessmentRecord>,
    onOpen: (AssessmentRecord) -> Unit,
    modifier: Modifier = Modifier
) {
    if (LocalWindowClass.current == WindowClass.COMPACT) {
        Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
            records.forEach { record -> AssessmentCard(record, onClick = { onOpen(record) }) }
        }
    } else {
        VxCard(modifier) {
            DataTable(
                columns = listOf(
                    TableColumn("Date", 1.4f),
                    TableColumn("Assessment", 2.2f),
                    TableColumn("Duration", 0.9f, TextAlign.End),
                    TableColumn("RULA", 0.7f, TextAlign.End),
                    TableColumn("REBA", 0.7f, TextAlign.End),
                    TableColumn("Risk", 1.4f, TextAlign.End)
                ),
                rowCount = records.size,
                onRowClick = { onOpen(records[it]) }
            ) { row, column ->
                val record = records[row]
                when (column) {
                    0 -> CellText(formatRelativeDateTime(record.createdAtMillis), muted = true)
                    1 -> Column(Modifier.fillMaxWidth()) {
                        Text(
                            record.fileName,
                            style = VxType.tableCell,
                            color = Ink,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        record.priorityLine?.let { PriorityLine(it) }
                    }
                    2 -> CellText(formatSeconds(record.durationSec))
                    3 -> CellText(record.rulaMax?.let { "$it/${Method.RULA.scaleMax}" } ?: formatScore(null), strong = true)
                    4 -> CellText(record.rebaMax?.let { "$it/${Method.REBA.scaleMax}" } ?: formatScore(null), strong = true)
                    else -> RiskTag(record.overallRisk())
                }
            }
        }
    }
}

/**
 * One assessment, as in the Overview reference: task, date and time, overall
 * risk tag and a short RULA/REBA summary, with a chevron. The whole card opens it.
 */
@Composable
fun AssessmentCard(record: AssessmentRecord, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val risk = record.overallRisk()
    val summary = formatScoreSummary(record.rulaMax, record.rebaMax)
    VxCard(modifier, onClick = onClick, onClickLabel = "Open assessment") {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(
                    record.fileName,
                    style = VxType.title,
                    color = Ink,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(formatRelativeDateTime(record.createdAtMillis), style = VxType.bodySmall, color = InkMuted)
            }
            Spacer(Modifier.width(Space.xs))
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = InkSecondary)
        }
        Spacer(Modifier.height(Space.sm))
        RiskTag(risk)
        summary?.let {
            Spacer(Modifier.height(Space.sm))
            Text(it, style = VxType.bodySmall, color = InkSecondary)
        }
        record.priorityLine?.let {
            Spacer(Modifier.height(Space.xxs))
            PriorityLine(it)
        }
    }
}

/** The priority action, and the product to evaluate for it, from the backend. */
@Composable
fun PriorityLine(text: String, modifier: Modifier = Modifier) {
    Text(
        "Priority: $text",
        style = VxType.bodySmall,
        color = InkSecondary,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
    )
}
