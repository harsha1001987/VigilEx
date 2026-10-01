package com.extrive.vigilex.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val Sans = FontFamily.SansSerif

// Tabular figures keep columns of numbers aligned.
private const val TABULAR = "tnum"

/**
 * VigilEx type roles. Numbers are the primary visual tool, so they get their
 * own scale; labels are small, uppercase and tracked; prose stays sentence case.
 */
object VxType {
    val splash = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.Medium,
        fontSize = 26.sp,
        letterSpacing = 14.sp
    )

    val wordmark = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.Bold,
        fontSize = 13.sp,
        letterSpacing = 4.sp
    )

    val scoreHero = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.Medium,
        fontSize = 96.sp,
        lineHeight = 96.sp,
        letterSpacing = (-4).sp,
        fontFeatureSettings = TABULAR
    )

    val scoreLarge = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.Medium,
        fontSize = 64.sp,
        lineHeight = 64.sp,
        letterSpacing = (-2.5).sp,
        fontFeatureSettings = TABULAR
    )

    val scoreScale = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.Normal,
        fontSize = 20.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.sp,
        fontFeatureSettings = TABULAR
    )

    /** The assessment conclusion ("MODERATE"): the largest words in the app after the splash. */
    val riskHeadline = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 72.sp,
        lineHeight = 72.sp,
        letterSpacing = (-3).sp
    )

    val riskHeadlineCompact = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 52.sp,
        lineHeight = 54.sp,
        letterSpacing = (-2).sp
    )

    /** Editorial figure for the key finding, e.g. "107.2°". */
    val figure = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.Medium,
        fontSize = 56.sp,
        lineHeight = 58.sp,
        letterSpacing = (-2).sp,
        fontFeatureSettings = TABULAR
    )

    val metric = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.Medium,
        fontSize = 34.sp,
        lineHeight = 38.sp,
        letterSpacing = (-1).sp,
        fontFeatureSettings = TABULAR
    )

    val metricSmall = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.Medium,
        fontSize = 22.sp,
        lineHeight = 26.sp,
        letterSpacing = (-0.4).sp,
        fontFeatureSettings = TABULAR
    )

    val pageTitle = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 34.sp,
        lineHeight = 40.sp,
        letterSpacing = (-1.1).sp
    )

    val pageTitleCompact = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        letterSpacing = (-0.8).sp
    )

    val sectionTitle = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        letterSpacing = (-0.3).sp
    )

    val title = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp
    )

    val body = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 23.sp
    )

    val bodySmall = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 19.sp
    )

    val label = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 1.4.sp
    )

    val labelLarge = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        lineHeight = 16.sp,
        letterSpacing = 1.2.sp
    )

    val tableCell = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        lineHeight = 20.sp,
        fontFeatureSettings = TABULAR
    )

    val mono = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        fontFeatureSettings = TABULAR
    )
}

val Typography = Typography(
    headlineLarge = VxType.pageTitle,
    headlineMedium = VxType.pageTitleCompact,
    titleLarge = VxType.sectionTitle,
    titleMedium = VxType.title,
    bodyLarge = VxType.body,
    bodyMedium = VxType.bodySmall,
    labelLarge = VxType.labelLarge,
    labelSmall = VxType.label
)
