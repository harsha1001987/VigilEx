package com.extrive.vigilex.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.extrive.vigilex.data.api.ApiResult
import com.extrive.vigilex.data.model.AssessmentDto
import com.extrive.vigilex.data.model.getCoveragePct
import com.extrive.vigilex.data.model.getFramesObserved
import com.extrive.vigilex.data.model.getPrimaryTrackId
import com.extrive.vigilex.data.model.getValidFrames
import com.extrive.vigilex.data.model.rebaScore
import com.extrive.vigilex.data.model.rulaScore
import com.extrive.vigilex.data.repository.AssessmentRepository
import com.extrive.vigilex.ui.components.BottomActionBar
import com.extrive.vigilex.ui.components.ErrorState
import com.extrive.vigilex.ui.components.ListContainer
import com.extrive.vigilex.ui.components.LoadingState
import com.extrive.vigilex.ui.components.MetricCard
import com.extrive.vigilex.ui.components.OverlineLabel
import com.extrive.vigilex.ui.components.PrimaryButton
import com.extrive.vigilex.ui.components.RowDivider
import com.extrive.vigilex.ui.components.SecondaryButton
import com.extrive.vigilex.ui.components.VigilExTopBar
import com.extrive.vigilex.ui.state.UiState
import com.extrive.vigilex.ui.theme.BackgroundWhite
import com.extrive.vigilex.ui.theme.BorderSubtle
import com.extrive.vigilex.ui.theme.OnYellow
import com.extrive.vigilex.ui.theme.TextMuted
import com.extrive.vigilex.ui.theme.TextPrimary
import com.extrive.vigilex.ui.theme.TextSecondary
import com.extrive.vigilex.ui.theme.VigilExYellow
import kotlin.math.roundToInt

/**
 * Displays real assessment results retrieved from PostgreSQL backend API.
 * Shows Assessment Status, Primary Worker Track ID, RULA/REBA Scores, Risk Bands, Coverage %, and Interventions.
 */
@Composable
fun ResultsScreen(
    assessmentId: String,
    onBackClick: () -> Unit,
    onGenerateReport: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier
) {
    val assessmentRepository = remember { AssessmentRepository() }
    var assessmentState by remember { mutableStateOf<UiState<AssessmentDto>>(UiState.Loading) }
    var retryTrigger by remember { mutableIntStateOf(0) }

    LaunchedEffect(assessmentId, retryTrigger) {
        assessmentState = UiState.Loading
        assessmentState = when (val result = assessmentRepository.getAssessment(assessmentId)) {
            is ApiResult.Success -> UiState.Success(result.data)
            is ApiResult.Error -> UiState.Error(result.message)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = BackgroundWhite,
        bottomBar = {
            BottomActionBar {
                PrimaryButton(text = "Generate report", onClick = onGenerateReport)
                Spacer(modifier = Modifier.height(10.dp))
                SecondaryButton(text = "Back to home", onClick = onDone)
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            VigilExTopBar(title = "Results", onBackClick = onBackClick)

            when (val state = assessmentState) {
                is UiState.Loading -> LoadingState()
                is UiState.Error -> ErrorState(message = state.message, onRetry = { retryTrigger++ })
                is UiState.Success -> {
                    val assessment = state.data
                    val rula = assessment.rulaScore
                    val reba = assessment.rebaScore

                    val primaryTrackId = rula?.getPrimaryTrackId() ?: reba?.getPrimaryTrackId()
                    val framesObserved = rula?.getFramesObserved() ?: reba?.getFramesObserved()
                    val rulaValid = rula?.getValidFrames()
                    val rebaValid = reba?.getValidFrames()

                    val rulaValue = rula?.score?.roundToInt()?.toString() ?: "—"
                    val rulaRisk = rula?.riskBand?.replaceFirstChar { it.uppercase() } ?: "Unavailable"
                    val rulaCoverage = rula?.getCoveragePct() ?: "—"

                    val rebaValue = reba?.score?.roundToInt()?.toString() ?: "—"
                    val rebaRisk = reba?.riskBand?.replaceFirstChar { it.uppercase() } ?: "Unavailable"
                    val rebaCoverage = reba?.getCoveragePct() ?: "—"

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Spacer(modifier = Modifier.height(8.dp))

                        // Header Info
                        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                            Text(
                                text = "Assessment ID: ${assessment.id}",
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Status: ${assessment.status.uppercase()} · ${assessment.methodologyVersion ?: "vigilex-rula-reba-v1"}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextMuted
                            )
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        // Primary Worker Card
                        Column(
                            modifier = Modifier
                                .padding(horizontal = 20.dp)
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(20.dp))
                                .background(BackgroundWhite)
                                .border(1.dp, BorderSubtle, RoundedCornerShape(20.dp))
                                .padding(24.dp)
                        ) {
                            OverlineLabel(text = "Primary Worker")
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = if (primaryTrackId != null) "Track ID: $primaryTrackId" else "Track ID: Auto-selected",
                                style = MaterialTheme.typography.headlineLarge,
                                color = TextPrimary
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = if (framesObserved != null) "$framesObserved frames observed in video pipeline" else "Worker tracking complete",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                        }

                        Spacer(modifier = Modifier.height(28.dp))

                        // Method scores (RULA & REBA)
                        OverlineLabel(text = "Ergonomic Scores", modifier = Modifier.padding(horizontal = 20.dp))
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            MetricCard(
                                label = "RULA",
                                value = rulaValue,
                                caption = "$rulaRisk · $rulaCoverage cov",
                                modifier = Modifier.weight(1f)
                            )
                            MetricCard(
                                label = "REBA",
                                value = rebaValue,
                                caption = "$rebaRisk · $rebaCoverage cov",
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Spacer(modifier = Modifier.height(28.dp))

                        // Coverage & Validity Details
                        OverlineLabel(text = "Pipeline Coverage Details", modifier = Modifier.padding(horizontal = 20.dp))
                        Spacer(modifier = Modifier.height(10.dp))
                        ListContainer(modifier = Modifier.padding(horizontal = 20.dp)) {
                            DetailRow(label = "Observed Frames", value = framesObserved?.toString() ?: "—")
                            RowDivider()
                            DetailRow(label = "RULA Valid Frames", value = rulaValid?.toString() ?: "—")
                            RowDivider()
                            DetailRow(label = "RULA Coverage", value = rulaCoverage)
                            RowDivider()
                            DetailRow(label = "REBA Valid Frames", value = rebaValid?.toString() ?: "—")
                            RowDivider()
                            DetailRow(label = "REBA Coverage", value = rebaCoverage)
                        }

                        Spacer(modifier = Modifier.height(28.dp))

                        // Recommended Interventions
                        OverlineLabel(text = "Recommended Interventions", modifier = Modifier.padding(horizontal = 20.dp))
                        Spacer(modifier = Modifier.height(10.dp))
                        ListContainer(modifier = Modifier.padding(horizontal = 20.dp)) {
                            if (assessment.interventions.isEmpty()) {
                                Text(
                                    text = "No specific interventions required for this assessment.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextMuted,
                                    modifier = Modifier.padding(16.dp)
                                )
                            } else {
                                assessment.interventions.forEachIndexed { index, intervention ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(24.dp)
                                                .clip(CircleShape)
                                                .background(VigilExYellow),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = (index + 1).toString(),
                                                style = MaterialTheme.typography.labelMedium,
                                                color = OnYellow
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(14.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "${intervention.riskDriver.replace("_", " ").uppercase()} (${intervention.priority.uppercase()})",
                                                style = MaterialTheme.typography.titleSmall,
                                                color = TextPrimary
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = intervention.recommendation,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = TextSecondary
                                            )
                                            if (!intervention.extriveProduct.isNull_or_empty()) {
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text(
                                                    text = "Product: ${intervention.extriveProduct}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = TextMuted
                                                )
                                            }
                                        }
                                    }
                                    if (index < assessment.interventions.lastIndex) RowDivider()
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(32.dp))
                    }
                }
            }
        }
    }
}

private fun String?.isNull_or_empty() = this == null || this.isEmpty()

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            color = TextPrimary
        )
    }
}
