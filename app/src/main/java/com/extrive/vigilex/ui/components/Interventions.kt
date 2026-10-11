package com.extrive.vigilex.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import com.extrive.vigilex.ui.theme.Gold
import com.extrive.vigilex.ui.theme.Ink
import com.extrive.vigilex.ui.theme.InkMuted
import com.extrive.vigilex.ui.theme.InkSecondary
import com.extrive.vigilex.ui.theme.Red
import com.extrive.vigilex.ui.theme.Space
import com.extrive.vigilex.ui.theme.SurfaceSunken
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
 * The one action to take first: a sunken panel with an accent bar, the
 * action as a headline, the recommendation, then what was observed.
 */
@Composable
fun PriorityAction(action: InterventionDto, modifier: Modifier = Modifier) {
    val status = interventionStatus(action.status)
    Row(
        modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .background(SurfaceSunken)
    ) {
        Box(Modifier.width(4.dp).fillMaxHeight().background(if (status == RegionStatus.HIGH) Red else Gold))
        Column(Modifier.padding(horizontal = Space.lg, vertical = Space.lg)) {
            Text(interventionAreaLabel(action.area).uppercase(), style = VxType.label, color = InkMuted)
            Spacer(Modifier.height(Space.xs))
            Text(action.title.uppercase(), style = VxType.pageTitleCompact, color = Ink)
            Spacer(Modifier.height(Space.md))
            Text(action.recommendation, style = VxType.body, color = Ink)
            Spacer(Modifier.height(Space.sm))
            Text(action.observation, style = VxType.bodySmall, color = InkSecondary)
            Spacer(Modifier.height(Space.md))
            StatusTag(status)
        }
    }
}

/** The remaining actions, numbered, each with its recommendation and the evidence for it. */
@Composable
fun OtherActions(actions: List<InterventionDto>, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        Hairline(color = Ink)
        actions.forEachIndexed { i, action ->
            Row(Modifier.padding(vertical = Space.md), verticalAlignment = Alignment.Top) {
                Text("%02d".format(i + 1), style = VxType.mono, color = InkMuted, modifier = Modifier.width(40.dp))
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
        SectionLabel("Priority action")
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
