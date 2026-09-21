package com.extrive.vigilex.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.extrive.vigilex.data.mock.RiskLevel
import com.extrive.vigilex.ui.theme.RiskGreen
import com.extrive.vigilex.ui.theme.RiskGreenSurface
import com.extrive.vigilex.ui.theme.RiskOrange
import com.extrive.vigilex.ui.theme.RiskOrangeSurface
import com.extrive.vigilex.ui.theme.RiskRed
import com.extrive.vigilex.ui.theme.RiskRedSurface

fun RiskLevel.color(): Color = when (this) {
    RiskLevel.LOW -> RiskGreen
    RiskLevel.MODERATE -> RiskOrange
    RiskLevel.HIGH -> RiskRed
}

fun RiskLevel.surface(): Color = when (this) {
    RiskLevel.LOW -> RiskGreenSurface
    RiskLevel.MODERATE -> RiskOrangeSurface
    RiskLevel.HIGH -> RiskRedSurface
}

fun RiskLevel.label(): String = when (this) {
    RiskLevel.LOW -> "Low"
    RiskLevel.MODERATE -> "Moderate"
    RiskLevel.HIGH -> "High"
}

@Composable
fun RiskBadge(
    level: RiskLevel,
    modifier: Modifier = Modifier,
    showSuffix: Boolean = false
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(level.surface())
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(level.color())
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = if (showSuffix) "${level.label()} risk" else level.label(),
            style = MaterialTheme.typography.labelMedium,
            color = level.color()
        )
    }
}
