package com.extrive.vigilex.data.model

/**
 * Read-only interpretation of backend analysis data for display.
 *
 * Nothing here computes a RULA or REBA score or assigns a risk band: scores,
 * risk labels and component severities are all taken from the backend
 * response. This file only selects, orders and aggregates those values.
 */

enum class Method(val label: String, val scaleMax: Int) {
    RULA("RULA", 7),
    REBA("REBA", 15)
}

/** Visual severity used to pick a colour for a backend risk label. */
enum class RiskTone { SAFE, LOW, MODERATE, HIGH, VERY_HIGH, UNKNOWN }

/** Backend risk keys, least to most severe (see posture._rula_risk_level / _reba_risk_level). */
fun riskKeys(method: Method): List<String> = when (method) {
    Method.RULA -> listOf("low", "moderate", "high", "very_high")
    Method.REBA -> listOf("negligible", "low", "medium", "high", "very_high")
}

fun riskTone(method: Method, risk: String?): RiskTone = when (method) {
    Method.RULA -> when (risk) {
        "low" -> RiskTone.SAFE
        "moderate" -> RiskTone.MODERATE
        "high" -> RiskTone.HIGH
        "very_high" -> RiskTone.VERY_HIGH
        else -> RiskTone.UNKNOWN
    }
    Method.REBA -> when (risk) {
        "negligible" -> RiskTone.SAFE
        "low" -> RiskTone.LOW
        "medium" -> RiskTone.MODERATE
        "high" -> RiskTone.HIGH
        "very_high" -> RiskTone.VERY_HIGH
        else -> RiskTone.UNKNOWN
    }
}

/** "very_high" -> "Very high". Unknown / unmeasured values read as "Not measured". */
fun riskName(risk: String?): String = when (risk) {
    null, "", "unknown", "not_measured" -> "Not measured"
    else -> risk.replace('_', ' ').replaceFirstChar { it.uppercase() }
}

fun AnalysisResult.summary(method: Method): MethodSummary? = when (method) {
    Method.RULA -> rula
    Method.REBA -> reba
}

fun Keyframe.score(method: Method): FrameScore? = when (method) {
    Method.RULA -> rula
    Method.REBA -> reba
}

val AnalysisResult.isScoreable: Boolean
    get() = (rula?.framesScored ?: 0) > 0 || (reba?.framesScored ?: 0) > 0

/** Index of the first keyframe whose score equals the method's maximum. */
fun AnalysisResult.peakFrameIndex(method: Method): Int? {
    val max = summary(method)?.maxScore ?: return null
    return keyframes.indexOfFirst { it.score(method)?.score == max }.takeIf { it >= 0 }
}

/**
 * Risk label of the peak score, read from the keyframe that produced it, so
 * the label always comes from the backend rather than being re-derived here.
 */
fun AnalysisResult.peakRisk(method: Method): String? =
    peakFrameIndex(method)?.let { keyframes[it].score(method)?.risk }

// --------------------------------------------------------------- body metrics

/**
 * The four regions with derived angles. [componentKey] is the RULA/REBA
 * component scored from the region; [frameAngleKey] its per-frame angle.
 */
enum class BodyMetric(val label: String, val componentKey: String, val frameAngleKey: String) {
    TRUNK("Trunk", "trunk", "trunk_deg"),
    UPPER_ARM("Upper arm", "upper_arm", "upper_arm_deg"),
    KNEE_FLEXION("Knee flexion", "legs", "knee_flexion_deg"),
    NECK("Neck", "neck", "neck_deg")
}

fun DerivedAngles.average(metric: BodyMetric): Double? = when (metric) {
    BodyMetric.TRUNK -> trunkAvg
    BodyMetric.UPPER_ARM -> upperArmAvg
    BodyMetric.KNEE_FLEXION -> kneeAvg
    BodyMetric.NECK -> neckAvg
}

fun DerivedAngles.maximum(metric: BodyMetric): Double? = when (metric) {
    BodyMetric.TRUNK -> trunkMax
    BodyMetric.UPPER_ARM -> upperArmMax
    BodyMetric.KNEE_FLEXION -> kneeMax
    BodyMetric.NECK -> neckMax
}

enum class Severity { OK, MODERATE, HIGH }

fun severityOf(raw: String?): Severity? = when (raw) {
    "ok" -> Severity.OK
    "moderate" -> Severity.MODERATE
    "high" -> Severity.HIGH
    else -> null
}

/**
 * Highest component severity the backend reported for this body region in
 * any scored keyframe, across both RULA and REBA. Null when never measured.
 */
fun AnalysisResult.impact(metric: BodyMetric): Severity? =
    keyframes.asSequence()
        .flatMap { sequenceOf(it.rula, it.reba) }
        .mapNotNull { it?.components?.get(metric.componentKey) }
        .filter { it.measured == true }
        .mapNotNull { severityOf(it.severity) }
        .maxOrNull()

// ------------------------------------------------------- per-frame components

data class ComponentRow(
    val key: String,
    val name: String,
    val angleDeg: Double?,
    val measured: Boolean,
    val rula: FrameComponent?,
    val reba: FrameComponent?
)

val componentOrder = listOf("upper_arm", "lower_arm", "wrist", "wrist_twist", "neck", "trunk", "legs")

fun Keyframe.componentRows(): List<ComponentRow> {
    val keys = (rula?.components?.keys.orEmpty() + reba?.components?.keys.orEmpty())
        .distinct()
        .sortedBy { componentOrder.indexOf(it).let { i -> if (i < 0) Int.MAX_VALUE else i } }
    return keys.map { key ->
        val r = rula?.components?.get(key)
        val b = reba?.components?.get(key)
        ComponentRow(
            key = key,
            name = r?.name ?: b?.name ?: key.replace('_', ' ').replaceFirstChar { it.uppercase() },
            angleDeg = r?.angleDeg ?: b?.angleDeg,
            measured = (r?.measured ?: false) || (b?.measured ?: false),
            rula = r,
            reba = b
        )
    }
}

/** Distribution counts in severity order, including zero-count bands. */
fun orderedDistribution(method: Method, distribution: Map<String, Int>): List<Pair<String, Int>> {
    val known = riskKeys(method)
    val extra = distribution.keys.filter { it !in known && it != "unknown" }
    return (known + extra).map { it to (distribution[it] ?: 0) }
}
