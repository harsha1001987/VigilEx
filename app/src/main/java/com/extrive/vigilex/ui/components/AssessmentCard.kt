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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.extrive.vigilex.data.mock.AssessmentStatus
import com.extrive.vigilex.data.mock.RecentAssessment
import com.extrive.vigilex.ui.theme.DividerColor
import com.extrive.vigilex.ui.theme.RiskGreen
import com.extrive.vigilex.ui.theme.RiskOrange
import com.extrive.vigilex.ui.theme.TextMuted
import com.extrive.vigilex.ui.theme.TextPrimary
import com.extrive.vigilex.ui.theme.TextSecondary

@Composable
fun AssessmentCard(assessment: RecentAssessment) {
    val statusColor = when (assessment.status) {
        AssessmentStatus.COMPLETED -> RiskGreen
        AssessmentStatus.REVIEW_REQUIRED -> RiskOrange
    }
    val statusLabel = when (assessment.status) {
        AssessmentStatus.COMPLETED -> "Completed"
        AssessmentStatus.REVIEW_REQUIRED -> "Review required"
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = assessment.areaName,
                style = MaterialTheme.typography.titleSmall,
                color = TextPrimary
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(statusColor)
                )
                Text(
                    text = "  $statusLabel",
                    style = MaterialTheme.typography.labelSmall,
                    color = statusColor
                )
            }
        }
        Spacer(modifier = Modifier.height(3.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = assessment.siteName,
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted
            )
            Text(
                text = assessment.timestamp,
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted
            )
        }
    }
}
