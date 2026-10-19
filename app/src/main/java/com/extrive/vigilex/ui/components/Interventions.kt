package com.extrive.vigilex.ui.components

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
import androidx.compose.ui.unit.dp
import com.extrive.vigilex.data.insight.RegionStatus
import com.extrive.vigilex.data.model.InterventionDto
import com.extrive.vigilex.data.model.ProductRecommendationDto
import com.extrive.vigilex.ui.theme.Ink
import com.extrive.vigilex.ui.theme.InkMuted
import com.extrive.vigilex.ui.theme.InkSecondary
import com.extrive.vigilex.ui.theme.Space
import com.extrive.vigilex.ui.theme.VxType

/*
 * Interventions come from the backend intervention engine as finished text;
 * these components only lay them out. Nothing here decides what to recommend.
 */

/** The backend's region status as the app's status scale. */
fun interventionStatus(raw: String): RegionStatus = when (raw) {
    "attention" -> RegionStatus.ATTENTION
    "elevated" -> RegionStatus.ELEVATED
    "high" -> RegionStatus.HIGH
    "low" -> RegionStatus.LOW
    else -> RegionStatus.NOT_MEASURED
}

fun interventionAreaLabel(area: String): String = when (area) {
    "back" -> "Back / trunk"
    "knees" -> "Knees"
    "neck" -> "Neck"
    "upper_arm" -> "Upper arm"
    else -> area.replace('_', ' ').replaceFirstChar { it.uppercase() }
}

/**
 * The one action to take first: a card with an accent edge, the action as a
 * headline, the recommendation, then what was observed.
 */
@Composable
fun PriorityAction(action: InterventionDto, modifier: Modifier = Modifier, compact: Boolean = false) {
    val status = interventionStatus(action.status)
    VxCard(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(interventionAreaLabel(action.area), style = VxType.label, color = InkMuted, modifier = Modifier.weight(1f))
            StatusTag(status)
        }
        Spacer(Modifier.height(Space.xs))
        Text(action.title, style = VxType.sectionTitle, color = Ink)
        Spacer(Modifier.height(Space.xs))
        Text(action.recommendation, style = VxType.body, color = Ink)
        if (!compact) {
            Spacer(Modifier.height(Space.xs))
            Text(action.observation, style = VxType.bodySmall, color = InkSecondary)
        }
    }
}

/** The remaining actions, numbered, each with its recommendation and the evidence for it. */
@Composable
fun OtherActions(actions: List<InterventionDto>, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        Hairline()
        actions.forEach { action ->
            Row(Modifier.padding(vertical = Space.md), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(action.title, style = VxType.title, color = Ink)
                    Spacer(Modifier.height(2.dp))
                    Text(action.recommendation, style = VxType.bodySmall, color = Ink)
                    Spacer(Modifier.height(2.dp))
                    Text(action.observation, style = VxType.bodySmall, color = InkMuted)
                }
                Spacer(Modifier.width(Space.md))
                StatusTag(interventionStatus(action.status), Modifier.padding(top = 4.dp))
            }
            Hairline()
        }
    }
}

/**
 * PRIORITY ACTION, the product to evaluate for it (when one qualifies), then
 * OTHER ACTIONS, as shown on the assessment and in the report. Draws nothing when there are none.
 */
@Composable
fun RecommendedActions(
    actions: List<InterventionDto>,
    modifier: Modifier = Modifier,
    product: ProductRecommendationDto? = null
) {
    val primary = actions.firstOrNull() ?: return
    Column(modifier.fillMaxWidth()) {
        SectionLabel("Priority action") // Same section name as the PDF report.
        PriorityAction(primary)
        product?.let {
            Spacer(Modifier.height(Space.xl))
            SectionLabel(PRODUCT_SECTION)
            ProductRecommendation(it)
        }
        if (actions.size > 1) {
            Spacer(Modifier.height(Space.xl))
            SectionLabel("Other actions")
            OtherActions(actions.drop(1))
        }
    }
}
