package com.extrive.vigilex.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.extrive.vigilex.data.model.AssessmentRecord
import com.extrive.vigilex.data.model.Method
import com.extrive.vigilex.data.insight.Interpretation
import com.extrive.vigilex.data.insight.interpret
import com.extrive.vigilex.data.store.AssessmentStore
import com.extrive.vigilex.ui.components.ArrowLink
import com.extrive.vigilex.ui.components.AssessmentList
import com.extrive.vigilex.ui.components.EmptyState
import com.extrive.vigilex.ui.components.GridRows
import com.extrive.vigilex.ui.components.Hairline
import com.extrive.vigilex.ui.components.KeyFinding
import com.extrive.vigilex.ui.components.KeySquare
import com.extrive.vigilex.ui.components.RiskConclusion
import com.extrive.vigilex.ui.components.LoadingState
import com.extrive.vigilex.ui.components.LocalWindowClass
import com.extrive.vigilex.ui.components.PageContainer
import com.extrive.vigilex.ui.components.PageHeader
import com.extrive.vigilex.ui.components.SectionLabel
import com.extrive.vigilex.ui.components.WindowClass
import com.extrive.vigilex.ui.format.EMPTY_VALUE
import com.extrive.vigilex.ui.format.formatDate
import com.extrive.vigilex.ui.format.formatDateTime
import com.extrive.vigilex.ui.format.formatScale
import com.extrive.vigilex.ui.format.formatScore
import com.extrive.vigilex.ui.format.formatSeconds
import com.extrive.vigilex.ui.studio.AssessmentStudio
import com.extrive.vigilex.ui.studio.StudioState
import com.extrive.vigilex.ui.theme.Ink
import com.extrive.vigilex.ui.theme.InkMuted
import com.extrive.vigilex.ui.theme.InkSecondary
import com.extrive.vigilex.ui.theme.Radius
import com.extrive.vigilex.ui.theme.Space
import com.extrive.vigilex.ui.theme.Surface
import com.extrive.vigilex.ui.theme.VxType
import com.extrive.vigilex.ui.theme.Yellow

private const val RECENT_COUNT = 5

@Composable
fun OverviewScreen(
    onStartAssessment: () -> Unit,
    onOpenAssessment: (String) -> Unit,
    onOpenHistory: () -> Unit
) {
    val records by AssessmentStore.records.collectAsState()
    val loaded by AssessmentStore.loaded.collectAsState()
    val studio by AssessmentStudio.state.collectAsState()

    PageContainer {
        PageHeader(
            eyebrow = "Overview",
            title = "Workplace ergonomic overview"
        )

        (studio as? StudioState.Analyzing)?.let { running ->
            InProgressNotice(running.video.displayName, onStartAssessment)
            Spacer(Modifier.height(Space.xl))
        }

        when {
            !loaded -> LoadingState()
            records.isEmpty() -> EmptyState(
                title = "No assessments yet",
                body = "Analyze a workplace video to begin your ergonomic assessment history.",
                actionLabel = "Start assessment",
                onAction = onStartAssessment
            )
            else -> OverviewContent(records, onOpenAssessment, onOpenHistory, onStartAssessment)
        }
    }
}

@Composable
private fun OverviewContent(
    records: List<AssessmentRecord>,
    onOpenAssessment: (String) -> Unit,
    onOpenHistory: () -> Unit,
    onStartAssessment: () -> Unit
) {
    val compact = LocalWindowClass.current == WindowClass.COMPACT
    val latest = records.first()

    SummaryFigures(records)
    Spacer(Modifier.height(Space.xxl))

    SectionLabel("Latest assessment") {
        ArrowLink("Full assessment", onClick = { onOpenAssessment(latest.id) })
    }
    Text(
        latest.fileName,
        style = VxType.title,
        color = Ink,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
    Text(
        "${formatDateTime(latest.createdAtMillis)} · ${formatSeconds(latest.durationSec)} of video",
        style = VxType.bodySmall,
        color = InkMuted
    )
    Spacer(Modifier.height(Space.xl))

    // The saved result is read once so the conclusion is interpreted from the full response.
    val interpretation by produceState<Interpretation?>(null, latest.id) {
        value = AssessmentStore.loadResult(latest.id)?.let(::interpret)
    }
    val current = interpretation
    val key = current?.keyFinding
    when {
        current == null -> LoadingState()
        compact || key == null -> {
            RiskConclusion(current, showGuidance = false)
            key?.let {
                Spacer(Modifier.height(Space.xl))
                KeyFinding(it)
            }
        }
        else -> Row(horizontalArrangement = Arrangement.spacedBy(Space.xxl), verticalAlignment = Alignment.Top) {
            RiskConclusion(current, Modifier.weight(1.2f), showGuidance = false)
            KeyFinding(key, Modifier.weight(1f))
        }
    }

    if (records.size > 1) {
        Spacer(Modifier.height(Space.xxl))
        SectionLabel("Recent assessments") {
            ArrowLink("All history", onClick = onOpenHistory)
        }
        AssessmentList(records.take(RECENT_COUNT), onOpen = { onOpenAssessment(it.id) })
    }

    Spacer(Modifier.height(Space.xxl))
    ArrowLink("New assessment", onClick = onStartAssessment)
}

@Composable
private fun SummaryFigures(records: List<AssessmentRecord>) {
    val columns = if (LocalWindowClass.current == WindowClass.COMPACT) 2 else 4
    val figures = listOf(
        "Total assessments" to records.size.toString(),
        "Highest RULA" to (records.mapNotNull { it.rulaMax }.maxOrNull()?.let { "${formatScore(it)} / ${formatScale(Method.RULA.scaleMax)}" } ?: EMPTY_VALUE),
        "Highest REBA" to (records.mapNotNull { it.rebaMax }.maxOrNull()?.let { "${formatScore(it)} / ${formatScale(Method.REBA.scaleMax)}" } ?: EMPTY_VALUE),
        "Last analyzed" to formatDate(records.first().createdAtMillis)
    )
    GridRows(figures, columns, verticalGap = Space.lg) { (label, value) ->
        Column {
            Hairline()
            Spacer(Modifier.height(Space.sm))
            Text(label.uppercase(), style = VxType.label, color = InkMuted)
            Spacer(Modifier.height(Space.xs))
            Text(value, style = VxType.metricSmall, color = Ink)
        }
    }
}

@Composable
private fun InProgressNotice(fileName: String, onOpen: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Surface, Radius.small)
            .clickable(onClick = onOpen)
            .padding(Space.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        KeySquare(Yellow)
        Spacer(Modifier.width(Space.sm))
        Column(Modifier.weight(1f)) {
            Text("Analysis in progress", style = VxType.title, color = Ink)
            Text(fileName, style = VxType.bodySmall, color = InkSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        ArrowLink("Open", onClick = onOpen)
    }
}
