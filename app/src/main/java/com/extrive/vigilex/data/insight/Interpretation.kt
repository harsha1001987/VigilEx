package com.extrive.vigilex.data.insight

import com.extrive.vigilex.data.model.AnalysisResult
import com.extrive.vigilex.data.model.AssessmentRecord
import com.extrive.vigilex.data.model.BodyMetric
import com.extrive.vigilex.data.model.DerivedAngles
import com.extrive.vigilex.data.model.Keyframe
import com.extrive.vigilex.data.model.Method
import com.extrive.vigilex.data.model.Severity
import com.extrive.vigilex.data.model.average
import com.extrive.vigilex.data.model.componentOrder
import com.extrive.vigilex.data.model.maximum
import com.extrive.vigilex.data.model.score
import com.extrive.vigilex.data.model.severityOf
import com.extrive.vigilex.data.model.summary

/*
 * Turns a backend analysis into the conclusion VigilEx presents: overall
 * risk, what drove it, and which frames show it. Every number and every
 * severity comes from the backend response; wording lives in
 * InterpretationRules.
 */

/** The overall ergonomic risk: the more severe of the RULA and REBA peak risk levels. */
enum class RiskLevel(val label: String) {
    LOW("Low"),
    MODERATE("Moderate"),
    HIGH("High"),
    VERY_HIGH("Very high")
}

/** How strongly a body region is presented, from the backend component severities. */
enum class RegionStatus(val label: String) {
    NOT_MEASURED("Not measured"),
    LOW("Low"),
    ATTENTION("Attention"),
    ELEVATED("Elevated"),
    /** Elevated in an assessment whose overall risk is high: the only status drawn in red. */
    HIGH("High");

    val flagged: Boolean get() = this >= ATTENTION
}

data class RegionFinding(
    val metric: BodyMetric,
    val status: RegionStatus,
    val average: Double?,
    val maximum: Double?,
    /** Scored frames in which the backend measured this region. */
    val framesMeasured: Int,
    /** Of those, frames at the region's peak severity (moderate or above for Attention). */
    val framesFlagged: Int,
    /** Keyframe with the largest angle for this region, if any. */
    val peakFrameIndex: Int?,
    val title: String,
    val detail: String
) {
    val share: Double get() = if (framesMeasured > 0) framesFlagged.toDouble() / framesMeasured else 0.0
}

data class ComponentScore(
    val key: String,
    val name: String,
    val angleDeg: Double?,
    val band: Int?,
    val maxBand: Int?,
    val measured: Boolean,
    val severity: Severity?
)

data class MethodResult(
    val method: Method,
    val score: Int?,
    val risk: String?,
    val level: RiskLevel?,
    val framesScored: Int,
    val distribution: Map<String, Int>,
    /** First keyframe that reached the peak score. */
    val peakFrameIndex: Int?,
    /** Component bands at [peakFrameIndex]: why the peak score is what it is. */
    val components: List<ComponentScore>
)

sealed interface EvidenceReason {
    data class PeakScore(val method: Method, val score: Int?, val level: RiskLevel?) : EvidenceReason
    data class PeakAngle(val metric: BodyMetric, val angleDeg: Double) : EvidenceReason
}

data class EvidenceFrame(val index: Int, val keyframe: Keyframe, val reason: EvidenceReason)

data class Interpretation(
    val overall: RiskLevel?,
    val rula: MethodResult,
    val reba: MethodResult,
    /** All four regions, most significant first. */
    val regions: List<RegionFinding>,
    /** One to three findings for the summary; the first is the key finding. */
    val findings: List<RegionFinding>,
    /** One sentence built from the flagged findings, or null when nothing was measured. */
    val observation: String?,
    val guidance: String?,
    val evidence: List<EvidenceFrame>,
    /** Components the backend could not observe in any frame (scored at their lowest value). */
    val unmeasured: List<String>
) {
    val keyFinding: RegionFinding? get() = findings.firstOrNull()
    fun method(method: Method): MethodResult = if (method == Method.RULA) rula else reba
}

fun overallLevel(rulaRisk: String?, rebaRisk: String?): RiskLevel? =
    listOfNotNull(
        InterpretationRules.level(Method.RULA, rulaRisk),
        InterpretationRules.level(Method.REBA, rebaRisk)
    ).maxOrNull()

/** Overall risk of a saved assessment, from the peak risks stored in its index entry. */
fun AssessmentRecord.overallRisk(): RiskLevel? = overallLevel(rulaPeakRisk, rebaPeakRisk)

fun interpret(result: AnalysisResult): Interpretation {
    val rula = methodResult(result, Method.RULA)
    val reba = methodResult(result, Method.REBA)
    val overall = listOfNotNull(rula.level, reba.level).maxOrNull()

    val regions = BodyMetric.entries
        .map { regionFinding(result, it, overall) }
        .sortedWith(
            compareByDescending<RegionFinding> { it.status }
                .thenByDescending { it.share }
                .thenBy { it.metric.ordinal }
        )

    val flagged = regions.filter { it.status.flagged }
    val quiet = regions.filter { it.status == RegionStatus.LOW }
    // A calm region is worth stating only when little else was found.
    val quietCount = when {
        flagged.isEmpty() -> 2
        flagged.size < InterpretationRules.MAX_FINDINGS -> 1
        else -> 0
    }
    val findings = (flagged + quiet.take(quietCount)).take(InterpretationRules.MAX_FINDINGS)

    return Interpretation(
        overall = overall,
        rula = rula,
        reba = reba,
        regions = regions,
        findings = findings,
        observation = observation(flagged, measured = regions.any { it.status != RegionStatus.NOT_MEASURED }),
        guidance = overall?.let(InterpretationRules::guidance),
        evidence = evidence(result, rula, reba, findings.firstOrNull()),
        unmeasured = unmeasuredComponents(result)
    )
}

// ------------------------------------------------------------------ methods

private fun methodResult(result: AnalysisResult, method: Method): MethodResult {
    val summary = result.summary(method)
    val max = summary?.maxScore
    val peak = max?.let { m -> result.keyframes.indexOfFirst { it.score(method)?.score == m }.takeIf { it >= 0 } }
    val risk = peak?.let { result.keyframes[it].score(method)?.risk }
    val components = peak?.let { index ->
        result.keyframes[index].score(method)?.components.orEmpty()
            .entries
            .sortedBy { (key, _) -> componentOrder.indexOf(key).let { if (it < 0) Int.MAX_VALUE else it } }
            .map { (key, c) ->
                val measured = c.measured == true
                ComponentScore(
                    key = key,
                    name = c.name ?: key.replace('_', ' ').replaceFirstChar { it.uppercase() },
                    angleDeg = c.angleDeg,
                    band = c.band,
                    maxBand = c.maxBand,
                    measured = measured,
                    severity = if (measured) severityOf(c.severity) else null
                )
            }
    }.orEmpty()

    return MethodResult(
        method = method,
        score = max,
        risk = risk,
        level = InterpretationRules.level(method, risk),
        framesScored = summary?.framesScored ?: 0,
        distribution = summary?.riskDistribution.orEmpty(),
        peakFrameIndex = peak,
        components = components
    )
}

// ------------------------------------------------------------------ regions

/** The more severe of the RULA and REBA severities for this region in one frame. */
private fun Keyframe.regionSeverity(metric: BodyMetric): Severity? =
    listOfNotNull(rula?.components?.get(metric.componentKey), reba?.components?.get(metric.componentKey))
        .filter { it.measured == true }
        .mapNotNull { severityOf(it.severity) }
        .maxOrNull()

private fun Keyframe.angle(metric: BodyMetric): Double? =
    rula?.angles?.get(metric.frameAngleKey) ?: reba?.angles?.get(metric.frameAngleKey)

private fun regionFinding(result: AnalysisResult, metric: BodyMetric, overall: RiskLevel?): RegionFinding {
    val severities = result.keyframes.mapNotNull { it.regionSeverity(metric) }
    val peak = severities.maxOrNull()

    val status = when (peak) {
        null -> RegionStatus.NOT_MEASURED
        Severity.OK -> RegionStatus.LOW
        Severity.MODERATE -> RegionStatus.ATTENTION
        Severity.HIGH -> if (overall != null && overall >= RiskLevel.HIGH) RegionStatus.HIGH else RegionStatus.ELEVATED
    }
    val flagged = when (peak) {
        Severity.HIGH -> severities.count { it == Severity.HIGH }
        Severity.MODERATE -> severities.count { it >= Severity.MODERATE }
        else -> 0
    }

    val peakFrame = result.keyframes.indices
        .mapNotNull { i -> result.keyframes[i].angle(metric)?.let { i to it } }
        .maxByOrNull { it.second }
        ?.first

    val angles: DerivedAngles? = result.derivedAngles
    val measured = severities.size
    val detail = when {
        status == RegionStatus.NOT_MEASURED -> "Not measured in any scored frame."
        status == RegionStatus.LOW -> "Stayed within the lowest scoring range in all $measured measured frames."
        else -> {
            val share = if (measured > 0) flagged.toDouble() / measured else 0.0
            "Observed ${InterpretationRules.frequencyWord(share)} during the analyzed movement: " +
                "$flagged of $measured measured frames."
        }
    }

    return RegionFinding(
        metric = metric,
        status = status,
        average = angles?.average(metric),
        maximum = angles?.maximum(metric),
        framesMeasured = measured,
        framesFlagged = flagged,
        peakFrameIndex = peakFrame,
        title = InterpretationRules.title(metric, status),
        detail = detail
    )
}

// ------------------------------------------------------------------ wording

private fun observation(flagged: List<RegionFinding>, measured: Boolean): String? {
    if (!measured) return null
    val named = flagged.take(InterpretationRules.MAX_OBSERVATION_REGIONS)
    if (named.isEmpty()) {
        return "All measured body regions stayed within their lowest scoring range during the analyzed movement."
    }
    val phrases = named.mapIndexed { i, finding ->
        if (i == 0) finding.title else finding.title.replaceFirstChar { it.lowercase() }
    }
    val verb = if (phrases.size == 1) "was" else "were"
    return "${phrases.joinToString(" and ")} $verb observed during the analyzed movement."
}

// ------------------------------------------------------------------ evidence

private fun evidence(
    result: AnalysisResult,
    rula: MethodResult,
    reba: MethodResult,
    key: RegionFinding?
): List<EvidenceFrame> {
    // Lead with the method that set the overall risk; REBA on a tie (it covers the whole body).
    val (lead, other) = if ((rula.level ?: RiskLevel.LOW) > (reba.level ?: RiskLevel.LOW)) rula to reba else reba to rula
    val candidates = buildList {
        lead.peakFrameIndex?.let { add(it to EvidenceReason.PeakScore(lead.method, lead.score, lead.level)) }
        if (key != null && key.status.flagged) {
            val index = key.peakFrameIndex
            val angle = index?.let { result.keyframes[it].angle(key.metric) }
            if (index != null && angle != null) add(index to EvidenceReason.PeakAngle(key.metric, angle))
        }
        other.peakFrameIndex?.let { add(it to EvidenceReason.PeakScore(other.method, other.score, other.level)) }
    }
    return candidates
        .distinctBy { it.first }
        .take(InterpretationRules.MAX_EVIDENCE_FRAMES)
        .map { (index, reason) -> EvidenceFrame(index, result.keyframes[index], reason) }
}

private fun unmeasuredComponents(result: AnalysisResult): List<String> {
    val all = result.keyframes.flatMap { k ->
        listOfNotNull(k.rula, k.reba).flatMap { it.components.entries }
    }
    if (all.isEmpty()) return emptyList()
    val measuredKeys = all.filter { it.value.measured == true }.map { it.key }.toSet()
    return all.filter { it.key !in measuredKeys }
        .distinctBy { it.key }
        .sortedBy { componentOrder.indexOf(it.key) }
        .map { it.value.name ?: it.key.replace('_', ' ').replaceFirstChar { c -> c.uppercase() } }
}
