package com.extrive.vigilex.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.extrive.vigilex.data.model.Method
import com.extrive.vigilex.data.model.RiskTone
import com.extrive.vigilex.data.model.Severity
import com.extrive.vigilex.data.model.orderedDistribution
import com.extrive.vigilex.data.model.riskName
import com.extrive.vigilex.data.model.riskTone
import com.extrive.vigilex.ui.theme.Gold
import com.extrive.vigilex.ui.theme.GoldTint
import com.extrive.vigilex.ui.theme.Green
import com.extrive.vigilex.ui.theme.GreenTint
import com.extrive.vigilex.ui.theme.HairlineStrong
import com.extrive.vigilex.ui.theme.Ink
import com.extrive.vigilex.ui.theme.InkFaint
import com.extrive.vigilex.ui.theme.InkMuted
import com.extrive.vigilex.ui.theme.InkSecondary
import com.extrive.vigilex.ui.theme.Motion
import com.extrive.vigilex.ui.theme.Radius
import com.extrive.vigilex.ui.theme.Red
import com.extrive.vigilex.ui.theme.RedTint
import com.extrive.vigilex.ui.theme.Space
import com.extrive.vigilex.ui.theme.SurfaceSunken
import com.extrive.vigilex.ui.theme.VxType
import com.extrive.vigilex.ui.theme.VeryHighRed
import com.extrive.vigilex.ui.theme.White
import kotlin.math.roundToInt


/** Solid colour of a risk tone, used for keys, bars and chart marks. */
fun RiskTone.color(): Color = when (this) {
    RiskTone.SAFE -> Green
    RiskTone.LOW -> InkFaint
    RiskTone.MODERATE -> Gold
    RiskTone.HIGH -> Red
    RiskTone.VERY_HIGH -> VeryHighRed
    RiskTone.UNKNOWN -> HairlineStrong
}

private fun RiskTone.badgeBackground(): Color = when (this) {
    RiskTone.SAFE -> GreenTint
    RiskTone.LOW -> SurfaceSunken
    RiskTone.MODERATE -> GoldTint
    RiskTone.HIGH -> RedTint
    RiskTone.VERY_HIGH -> Red
    RiskTone.UNKNOWN -> SurfaceSunken
}

private fun RiskTone.badgeText(): Color = when (this) {
    RiskTone.SAFE -> Green
    RiskTone.LOW -> Ink
    RiskTone.MODERATE -> Ink
    RiskTone.HIGH -> Red
    RiskTone.VERY_HIGH -> White
    RiskTone.UNKNOWN -> InkMuted
}

/** "MODERATE RISK" in the colour of the backend's risk band. */
@Composable
fun RiskBadge(method: Method, risk: String?, modifier: Modifier = Modifier) {
    val tone = riskTone(method, risk)
    val text = if (tone == RiskTone.UNKNOWN) "Not measured" else "${riskName(risk)} risk"
    Row(
        modifier = modifier
            .background(tone.badgeBackground(), Radius.sharp)
            .padding(horizontal = Space.xs, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        KeySquare(if (tone == RiskTone.VERY_HIGH) White else tone.color(), size = 6.dp)
        Spacer(Modifier.width(6.dp))
        Text(text.uppercase(), style = VxType.label, color = tone.badgeText())
    }
}

/** Component severity reported by the backend, as a key square and word. */
@Composable
fun SeverityText(severity: Severity?, modifier: Modifier = Modifier) {
    val (label, color) = when (severity) {
        Severity.OK -> "OK" to InkSecondary
        Severity.MODERATE -> "Moderate" to Gold
        Severity.HIGH -> "High" to Red
        null -> "Not measured" to InkMuted
    }
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        KeySquare(if (severity == null) HairlineStrong else color, size = 6.dp)
        Spacer(Modifier.width(6.dp))
        Text(
            text = label,
            style = VxType.bodySmall,
            color = if (severity == Severity.HIGH) Red else if (severity == null) InkMuted else Ink
        )
    }
}

/**
 * Share of sampled frames in each backend risk band, as one stacked bar plus
 * a legend with counts. Values come straight from `risk_distribution`.
 */
@Composable
fun RiskDistribution(
    method: Method,
    distribution: Map<String, Int>,
    modifier: Modifier = Modifier,
    unit: String = "frames"
) {
    val rows = orderedDistribution(method, distribution)
    val total = rows.sumOf { it.second }
    var revealed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { revealed = true }
    val grow by animateFloatAsState(
        targetValue = if (revealed) 1f else 0f,
        animationSpec = tween(Motion.slow, easing = Motion.standard),
        label = "distributionGrow"
    )

    Column(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(method.label, style = VxType.title, color = Ink, modifier = Modifier.weight(1f))
            Text("$total $unit", style = VxType.mono, color = InkMuted)
        }
        Spacer(Modifier.height(Space.sm))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .background(SurfaceSunken)
                .semantics {
                    contentDescription = rows.filter { it.second > 0 }
                        .joinToString { "${riskName(it.first)}: ${it.second}" }
                },
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            if (total > 0) {
                rows.filter { it.second > 0 }.forEach { (key, count) ->
                    Box(
                        Modifier
                            .weight(count.toFloat())
                            .fillMaxHeight(),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth(grow)
                                .fillMaxHeight()
                                .background(riskTone(method, key).color())
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(Space.sm))
        rows.forEach { (key, count) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                KeySquare(riskTone(method, key).color())
                Spacer(Modifier.width(Space.xs))
                Text(
                    riskName(key),
                    style = VxType.bodySmall,
                    color = if (count > 0) Ink else InkMuted,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    count.toString(),
                    style = VxType.mono,
                    color = if (count > 0) Ink else InkMuted,
                    modifier = Modifier.width(40.dp),
                    textAlign = TextAlign.End
                )
                Text(
                    if (total > 0) "${(count * 100f / total).roundToInt()}%" else "—",
                    style = VxType.mono,
                    color = InkMuted,
                    modifier = Modifier.width(48.dp),
                    textAlign = TextAlign.End
                )
            }
        }
    }
}

