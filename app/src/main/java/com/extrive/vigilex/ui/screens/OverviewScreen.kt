package com.extrive.vigilex.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.extrive.vigilex.data.api.ApiResult
import com.extrive.vigilex.data.model.OverviewDto
import com.extrive.vigilex.data.model.needsAttention
import com.extrive.vigilex.data.repository.AssessmentRepository
import com.extrive.vigilex.data.store.AssessmentStore
import com.extrive.vigilex.ui.components.ArrowLink
import com.extrive.vigilex.ui.components.AssessmentList
import com.extrive.vigilex.ui.components.BackendErrorState
import com.extrive.vigilex.ui.components.Callout
import com.extrive.vigilex.ui.components.EmptyState
import com.extrive.vigilex.ui.components.LoadingState
import com.extrive.vigilex.ui.components.LocalWindowClass
import com.extrive.vigilex.ui.components.PageContainer
import com.extrive.vigilex.ui.components.PageHeader
import com.extrive.vigilex.ui.components.PrimaryButton
import com.extrive.vigilex.ui.components.SectionLabel
import com.extrive.vigilex.ui.components.WindowClass
import com.extrive.vigilex.ui.studio.AssessmentStudio
import com.extrive.vigilex.ui.studio.StudioState
import com.extrive.vigilex.ui.theme.Ink
import com.extrive.vigilex.ui.theme.InkMuted
import com.extrive.vigilex.ui.theme.Space
import com.extrive.vigilex.ui.theme.VxType

/** Recent assessments shown on the overview; the rest are one tap away in History. */
private const val RECENT_COUNT = 3

/**
 * The Overview, as in the Stitch reference: one yellow action, the most
 * recent assessments, and a short summary. Everything comes from one call
 * (GET /overview); the list items use the summaries it returns, so no
 * assessment detail is fetched here.
 */
@Composable
fun OverviewScreen(
    onStartAssessment: () -> Unit,
    onOpenAssessment: (String) -> Unit,
    onOpenHistory: () -> Unit
) {
    val repository = remember { AssessmentRepository() }
    val revision by AssessmentStore.revision.collectAsState()
    val studio by AssessmentStudio.state.collectAsState()
    var attempt by remember { mutableIntStateOf(0) }
    var overview by remember { mutableStateOf<ApiResult<OverviewDto>?>(null) }

    LaunchedEffect(revision, attempt) {
        // Keep showing loaded content while refreshing; only a first load or a failure shows the spinner.
        if (overview !is ApiResult.Success) overview = null
        overview = repository.overview()
    }

    PageContainer {
        PageHeader(
            title = "Overview",
            supporting = "Your workplace risk assessments, at a glance."
        )

        val running = studio as? StudioState.Analyzing
        if (running != null) {
            Callout(
                title = "Analysis in progress",
                body = running.video.displayName,
                actions = { ArrowLink("Open", onClick = onStartAssessment) }
            )
        } else {
            PrimaryButton(
                text = "New assessment",
                onClick = onStartAssessment,
                icon = Icons.Outlined.Add,
                modifier = Modifier
                    .widthIn(max = 480.dp)
                    .fillMaxWidth()
            )
        }
        Spacer(Modifier.height(Space.xl))

        when (val current = overview) {
            null -> LoadingState(label = "Loading assessments…")
            is ApiResult.Error -> BackendErrorState(current, onRetry = { attempt++ })
            is ApiResult.Success -> if (current.data.totalAssessments == 0) {
                EmptyState(
                    title = "No assessments yet",
                    body = "Record or choose a video of a task to create your first posture assessment."
                )
            } else {
                OverviewContent(current.data, repository, onOpenAssessment, onOpenHistory)
            }
        }
    }
}

@Composable
private fun OverviewContent(
    overview: OverviewDto,
    repository: AssessmentRepository,
    onOpenAssessment: (String) -> Unit,
    onOpenHistory: () -> Unit
) {
    val recent = remember(overview) { overview.latest.take(RECENT_COUNT).map(repository::toRecord) }

    SectionLabel("Recent assessments") {
        ArrowLink("View all", onClick = onOpenHistory)
    }
    AssessmentList(recent, onOpen = { onOpenAssessment(it.id) })
    Spacer(Modifier.height(Space.xl))

    SectionLabel("Assessment summary")
    val compact = LocalWindowClass.current == WindowClass.COMPACT
    Row(
        Modifier
            .widthIn(max = if (compact) 600.dp else 720.dp)
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Space.md)
    ) {
        SummaryFigure(overview.totalAssessments, "Total assessments", Modifier.weight(1f))
        SummaryFigure(overview.needsAttention(), "High or very high risk", Modifier.weight(1f))
        SummaryFigure(overview.recentAssessments, "In the last ${overview.recentWindowDays} days", Modifier.weight(1f))
    }
}

/** A large count with its label, read together by screen readers. */
@Composable
private fun SummaryFigure(value: Int, label: String, modifier: Modifier = Modifier) {
    Column(modifier.semantics(mergeDescendants = true) { contentDescription = "$value $label" }) {
        Text(value.toString(), style = VxType.metric, color = Ink)
        Spacer(Modifier.height(Space.xxs))
        Text(label, style = VxType.bodySmall, color = InkMuted)
    }
}
