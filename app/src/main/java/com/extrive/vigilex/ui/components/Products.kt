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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.extrive.vigilex.data.model.MeasurementReliabilityDto
import com.extrive.vigilex.data.model.ProductRecommendationDto
import com.extrive.vigilex.ui.theme.Gold
import com.extrive.vigilex.ui.theme.GoldInk
import com.extrive.vigilex.ui.theme.GoldTint
import com.extrive.vigilex.ui.theme.Ink
import com.extrive.vigilex.ui.theme.InkMuted
import com.extrive.vigilex.ui.theme.InkSecondary
import com.extrive.vigilex.ui.theme.Space
import com.extrive.vigilex.ui.theme.SurfaceSunken
import com.extrive.vigilex.ui.theme.VxType

/*
 * Product suggestions and camera-view reliability come from the backend
 * (services/products.py, analysis_assessments.measurement_reliability) as
 * finished text, the same text the PDF prints. Nothing here decides them.
 */

/** Matches PRODUCT_SECTION in backend/app/services/reports.py. */
const val PRODUCT_SECTION = "Recommended product to evaluate"

/** Match COMPONENT_HEADING and COMPONENT_NOTE in backend/app/services/reports.py. */
const val COMPONENT_HEADING = "Component measurements at the peak-score frame"
const val COMPONENT_NOTE =
    "The overall peak shown in Measurements is the highest observed value across analyzed frames. Component " +
        "angles below are taken from the frame where the RULA or REBA score reached its peak, so they may differ."

/** Matches TECHNICAL_SECTION in backend/app/services/reports.py. */
const val TECHNICAL_SECTION = "Technical details and assessment limitations"

/** Matches the PDF wording when no camera-view check failed. */
const val NO_VIEW_LIMITATION =
    "No camera-view limitation was detected by these checks. This is not a confidence rating; " +
        "the limitations above still apply."

/**
 * An Extrive product to evaluate, tied to the finding that triggered it: why
 * it is suggested, what it is (approved content only), the task changes it
 * complements, how to evaluate it, and its qualification.
 */
@Composable
fun ProductRecommendation(product: ProductRecommendationDto, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .background(SurfaceSunken)
    ) {
        Box(Modifier.width(4.dp).fillMaxHeight().background(Ink))
        Column(Modifier.padding(horizontal = Space.lg, vertical = Space.lg)) {
            Text(listOfNotNull(product.maker, product.category).joinToString(" · ").uppercase(), style = VxType.label, color = InkMuted)
            Spacer(Modifier.height(Space.xs))
            Text(product.headline, style = VxType.sectionTitle, color = Ink)
            Spacer(Modifier.height(Space.md))
            Text(product.rationale, style = VxType.body, color = Ink)
            Spacer(Modifier.height(Space.lg))
            if (product.verified) {
                ProductHeading("Why it may help")
                Text(
                    listOfNotNull(product.intendedUse, product.operatingPrinciple).joinToString(" "),
                    style = VxType.bodySmall,
                    color = Ink
                )
                if (product.taskTypes.isNotEmpty()) {
                    Spacer(Modifier.height(Space.xs))
                    Text(
                        "Relevant tasks: " + product.taskTypes.joinToString("; ") { it.replaceFirstChar(Char::lowercase) } + ".",
                        style = VxType.bodySmall,
                        color = InkSecondary
                    )
                }
            } else {
                ProductHeading("Product information pending", GoldInk)
                Text(
                    "Missing approved content: ${product.contentGaps.joinToString(", ")}.",
                    style = VxType.bodySmall,
                    color = InkSecondary
                )
            }
            Spacer(Modifier.height(Space.lg))
            ProductHeading("Best results come from combining approaches")
            Bullets(product.complementaryActions)
            Spacer(Modifier.height(Space.lg))
            ProductHeading("Before introducing it")
            Bullets(product.suitability)
            Spacer(Modifier.height(Space.lg))
            product.evidenceNote?.let {
                Text(it, style = VxType.bodySmall, color = InkSecondary)
                Spacer(Modifier.height(Space.xs))
            }
            Text(product.qualification, style = VxType.bodySmall, color = InkSecondary)
            if (product.sources.isNotEmpty()) {
                Spacer(Modifier.height(Space.sm))
                Text("Product information: ${product.sources.joinToString("; ")}.", style = VxType.bodySmall, color = InkMuted)
            }
        }
    }
}

@Composable
private fun ProductHeading(text: String, color: Color = Ink) {
    Text(text.uppercase(), style = VxType.label, color = color)
    Spacer(Modifier.height(Space.xs))
}

@Composable
private fun Bullets(items: List<String>) {
    items.forEach { item ->
        Row(Modifier.padding(vertical = 2.dp)) {
            Text("•", style = VxType.bodySmall, color = Ink, modifier = Modifier.width(16.dp))
            Text(item, style = VxType.bodySmall, color = Ink)
        }
    }
}

/**
 * The "Limited measurement reliability" notice: why the camera view limits
 * this assessment and what to record instead. Draws nothing when no
 * limitation was detected.
 */
@Composable
fun ReliabilityNotice(reliability: MeasurementReliabilityDto?, modifier: Modifier = Modifier) {
    if (reliability == null || !reliability.limited) return
    Row(
        modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .background(GoldTint)
    ) {
        Box(Modifier.width(4.dp).fillMaxHeight().background(Gold))
        Column(Modifier.padding(horizontal = Space.lg, vertical = Space.md)) {
            Text((reliability.title ?: "Limited measurement reliability").uppercase(), style = VxType.label, color = GoldInk)
            Spacer(Modifier.height(Space.xs))
            reliability.reasons.forEach {
                Text(it, style = VxType.bodySmall, color = Ink)
                Spacer(Modifier.height(2.dp))
            }
            reliability.recommendation?.let {
                Spacer(Modifier.height(Space.xs))
                Text(it, style = VxType.bodySmall, color = InkSecondary)
            }
        }
    }
}

/** The camera-view explanation, this assessment's view values, and the limitation when there is one. */
@Composable
fun CameraViewReliability(reliability: MeasurementReliabilityDto, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        Text(reliability.explanation, style = VxType.bodySmall, color = Ink)
        Spacer(Modifier.height(Space.md))
        KeyValueTable(
            listOf(
                "Measured frames filmed close to side-on" to percent(reliability.sideOnFraction),
                "Sampled frames with the worker detected" to percent(reliability.detectionFraction)
            )
        )
        Spacer(Modifier.height(Space.md))
        if (reliability.limited) ReliabilityNotice(reliability)
        else Text(NO_VIEW_LIMITATION, style = VxType.bodySmall, color = InkSecondary)
    }
}

private fun percent(fraction: Double?): String = fraction?.let { "%.0f%%".format(it * 100) } ?: "—"
