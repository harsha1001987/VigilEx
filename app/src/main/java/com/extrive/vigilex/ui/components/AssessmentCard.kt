package com.extrive.vigilex.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Accessibility
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.PrecisionManufacturing
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.extrive.vigilex.data.mock.AssessmentStatus
import com.extrive.vigilex.data.mock.RecentAssessment
import com.extrive.vigilex.ui.theme.SurfaceSubtle
import com.extrive.vigilex.ui.theme.TextMuted
import com.extrive.vigilex.ui.theme.TextPrimary
import com.extrive.vigilex.ui.theme.TextSecondary

/**
 * Compact list row for an assessment. Designed to sit inside a bordered list
 * container; the caller decides whether to draw dividers between rows.
 */
@Composable
fun AssessmentCard(
    assessment: RecentAssessment,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val icon = when {
        assessment.taskName.contains("Lift", ignoreCase = true) -> Icons.Outlined.FitnessCenter
        assessment.taskName.contains("Machine", ignoreCase = true) -> Icons.Outlined.PrecisionManufacturing
        assessment.taskName.contains("Fasten", ignoreCase = true) -> Icons.Outlined.Build
        else -> Icons.Outlined.Accessibility
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(assessment.riskLevel.surface()),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = assessment.riskLevel.color(),
                modifier = Modifier.size(22.dp)
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "${assessment.taskName} — ${assessment.areaName}",
                style = MaterialTheme.typography.titleSmall,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = "${assessment.siteName} · ${assessment.timestamp}",
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        if (assessment.status == AssessmentStatus.DRAFT) {
            Text(
                text = "Draft",
                style = MaterialTheme.typography.labelMedium,
                color = TextSecondary,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(SurfaceSubtle)
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            )
        } else {
            RiskBadge(level = assessment.riskLevel)
        }
    }
}
