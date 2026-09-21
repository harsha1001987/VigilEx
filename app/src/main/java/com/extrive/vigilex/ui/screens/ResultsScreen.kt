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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.extrive.vigilex.data.mock.JointResult
import com.extrive.vigilex.data.mock.MockData
import com.extrive.vigilex.data.mock.RiskLevel
import com.extrive.vigilex.ui.components.BottomActionBar
import com.extrive.vigilex.ui.components.ListContainer
import com.extrive.vigilex.ui.components.MetricCard
import com.extrive.vigilex.ui.components.OverlineLabel
import com.extrive.vigilex.ui.components.PrimaryButton
import com.extrive.vigilex.ui.components.RiskBadge
import com.extrive.vigilex.ui.components.RowDivider
import com.extrive.vigilex.ui.components.SecondaryButton
import com.extrive.vigilex.ui.components.VigilExTopBar
import com.extrive.vigilex.ui.components.color
import com.extrive.vigilex.ui.components.label
import com.extrive.vigilex.ui.theme.BackgroundWhite
import com.extrive.vigilex.ui.theme.BorderSubtle
import com.extrive.vigilex.ui.theme.DividerColor
import com.extrive.vigilex.ui.theme.OnYellow
import com.extrive.vigilex.ui.theme.TextMuted
import com.extrive.vigilex.ui.theme.TextPrimary
import com.extrive.vigilex.ui.theme.TextSecondary
import com.extrive.vigilex.ui.theme.VigilExYellow

/**
 * Results layout for a completed analysis. Scoring is not implemented yet, so
 * values come from mock data; the structure is ready for REBA/RULA/NIOSH output.
 */
@Composable
fun ResultsScreen(
    onBackClick: () -> Unit,
    onGenerateReport: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier
) {
    val overallScore = 72
    val overallLevel = RiskLevel.MODERATE
    val assessment = MockData.assessments.first()

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

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
            ) {
                Spacer(modifier = Modifier.height(8.dp))

                // Context line
                Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                    Text(
                        text = "${assessment.taskName} — ${assessment.areaName}",
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${assessment.siteName} · ${assessment.timestamp}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextMuted
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Overall risk hero
                Column(
                    modifier = Modifier
                        .padding(horizontal = 20.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(BackgroundWhite)
                        .border(1.dp, BorderSubtle, RoundedCornerShape(20.dp))
                        .padding(24.dp)
                ) {
                    OverlineLabel(text = "Overall risk")
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = overallScore.toString(),
                            style = MaterialTheme.typography.displayLarge,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "/ 100",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextMuted,
                            modifier = Modifier.padding(bottom = 10.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(overallLevel.color())
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = overallLevel.label().uppercase(),
                            style = MaterialTheme.typography.titleMedium,
                            color = overallLevel.color()
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    RiskScale(level = overallLevel)
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "Change is needed soon. Shoulder loading is the main driver of this score.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }

                Spacer(modifier = Modifier.height(28.dp))

                // Method scores
                OverlineLabel(text = "Method scores", modifier = Modifier.padding(horizontal = 20.dp))
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MetricCard(label = "REBA", value = "9", caption = "High", modifier = Modifier.weight(1f))
                    MetricCard(label = "RULA", value = "5", caption = "Investigate", modifier = Modifier.weight(1f))
                    MetricCard(
                        label = "NIOSH",
                        value = "—",
                        caption = "No load given",
                        valueColor = TextMuted,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(28.dp))

                // Joint breakdown
                OverlineLabel(text = "Joint breakdown", modifier = Modifier.padding(horizontal = 20.dp))
                Spacer(modifier = Modifier.height(10.dp))
                ListContainer(modifier = Modifier.padding(horizontal = 20.dp)) {
                    MockData.jointResults.forEachIndexed { index, joint ->
                        JointRow(joint = joint)
                        if (index < MockData.jointResults.lastIndex) RowDivider()
                    }
                }

                Spacer(modifier = Modifier.height(28.dp))

                // Recommendations
                OverlineLabel(text = "Recommended interventions", modifier = Modifier.padding(horizontal = 20.dp))
                Spacer(modifier = Modifier.height(10.dp))
                ListContainer(modifier = Modifier.padding(horizontal = 20.dp)) {
                    MockData.recommendations.forEachIndexed { index, text ->
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
                            Text(
                                text = text,
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextPrimary,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (index < MockData.recommendations.lastIndex) RowDivider()
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun RiskScale(level: RiskLevel) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        RiskLevel.entries.forEach { segment ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(if (segment == level) segment.color() else DividerColor)
            )
        }
    }
    Spacer(modifier = Modifier.height(6.dp))
    Row(modifier = Modifier.fillMaxWidth()) {
        RiskLevel.entries.forEach { segment ->
            Text(
                text = segment.label(),
                style = MaterialTheme.typography.labelMedium,
                color = if (segment == level) segment.color() else TextMuted,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun JointRow(joint: JointResult) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(joint.level.color())
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = joint.name,
                style = MaterialTheme.typography.titleSmall,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = joint.detail,
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted
            )
        }
        RiskBadge(level = joint.level)
    }
}
