package com.extrive.vigilex.data.model

import kotlinx.serialization.Serializable

/**
 * One saved assessment as the app's lists, detail and report use it. Every
 * number, risk label and intervention is copied from the backend.
 */
@Serializable
data class AssessmentRecord(
    val id: String,
    val createdAtMillis: Long,
    val fileName: String,
    val fileSizeBytes: Long? = null,
    val videoUri: String? = null,
    val durationSec: Double? = null,
    val framesSampled: Int? = null,
    val scoreableFraction: Double? = null,
    val rulaMax: Int? = null,
    val rulaPeakRisk: String? = null,
    val rebaMax: Int? = null,
    val rebaPeakRisk: String? = null,
    val derivedAngles: DerivedAngles? = null,
    val rulaDistribution: Map<String, Int> = emptyMap(),
    val rebaDistribution: Map<String, Int> = emptyMap(),
    val methodologyVersion: String? = null,
    /** From the backend intervention engine; the first is the priority action. */
    val actionSummary: String? = null,
    val interventions: List<InterventionDto> = emptyList(),
    /** Extrive products to evaluate, each tied to a qualifying finding; usually empty. */
    val productRecommendations: List<ProductRecommendationDto> = emptyList(),
    val measurementReliability: MeasurementReliabilityDto? = null
) {
    val priorityAction: InterventionDto? get() = interventions.firstOrNull()
    val otherActions: List<InterventionDto> get() = interventions.drop(1)

    /** The product linked to the priority action, shown directly after it. */
    val primaryProduct: ProductRecommendationDto? get() = productRecommendations.firstOrNull { it.role == "primary" }

    /** One line for lists: the priority action, and the product to evaluate for it. */
    val priorityLine: String?
        get() = priorityAction?.let { action ->
            listOfNotNull(action.title, primaryProduct?.headline).joinToString(" · ")
        }

    companion object {
        fun from(
            id: String,
            createdAtMillis: Long,
            fileName: String,
            fileSizeBytes: Long?,
            videoUri: String?,
            result: AnalysisResult
        ) = AssessmentRecord(
            id = id,
            createdAtMillis = createdAtMillis,
            fileName = fileName,
            fileSizeBytes = fileSizeBytes,
            videoUri = videoUri,
            durationSec = result.video?.durationSec,
            framesSampled = result.video?.framesSampled,
            scoreableFraction = result.quality?.scoreableFraction,
            rulaMax = result.rula?.maxScore,
            rulaPeakRisk = result.peakRisk(Method.RULA),
            rebaMax = result.reba?.maxScore,
            rebaPeakRisk = result.peakRisk(Method.REBA),
            derivedAngles = result.derivedAngles,
            rulaDistribution = result.rula?.riskDistribution.orEmpty(),
            rebaDistribution = result.reba?.riskDistribution.orEmpty(),
            methodologyVersion = result.methodologyVersion
        )
    }
}

fun AssessmentRecord.max(method: Method): Int? = when (method) {
    Method.RULA -> rulaMax
    Method.REBA -> rebaMax
}

fun AssessmentRecord.peakRisk(method: Method): String? = when (method) {
    Method.RULA -> rulaPeakRisk
    Method.REBA -> rebaPeakRisk
}

fun AssessmentRecord.distribution(method: Method): Map<String, Int> = when (method) {
    Method.RULA -> rulaDistribution
    Method.REBA -> rebaDistribution
}

/** The more severe of the two peak risks, used for a single-glance list marker. */
fun AssessmentRecord.overallTone(): RiskTone =
    maxOf(riskTone(Method.RULA, rulaPeakRisk), riskTone(Method.REBA, rebaPeakRisk)) { a, b ->
        toneRank(a).compareTo(toneRank(b))
    }

private fun toneRank(tone: RiskTone): Int = when (tone) {
    RiskTone.UNKNOWN -> -1
    else -> tone.ordinal
}

// ---------------------------------------------------------------- reporting

data class MetricAggregate(
    val metric: BodyMetric,
    val meanOfAverages: Double?,
    val highestMaximum: Double?,
    val assessments: Int
)

data class TrendPoint(val createdAtMillis: Long, val rula: Int?, val reba: Int?)

data class ReportData(
    val assessmentCount: Int,
    val peakRiskCounts: Map<Method, List<Pair<String, Int>>>,
    val metrics: List<MetricAggregate>,
    val trend: List<TrendPoint>
) {
    val hasTrend: Boolean get() = trend.size >= MIN_TREND_POINTS

    companion object {
        const val MIN_TREND_POINTS = 2
    }
}

/** Aggregates saved records. Returns null when there is nothing to report on. */
fun buildReport(records: List<AssessmentRecord>): ReportData? {
    if (records.isEmpty()) return null

    val peakCounts = Method.entries.associateWith { method ->
        val counts = records.mapNotNull { it.peakRisk(method) }.groupingBy { it }.eachCount()
        orderedDistribution(method, counts)
    }

    val metrics = BodyMetric.entries.map { metric ->
        val averages = records.mapNotNull { it.derivedAngles?.average(metric) }
        val maxima = records.mapNotNull { it.derivedAngles?.maximum(metric) }
        MetricAggregate(
            metric = metric,
            meanOfAverages = averages.takeIf { it.isNotEmpty() }?.average(),
            highestMaximum = maxima.maxOrNull(),
            assessments = averages.size
        )
    }

    val trend = records
        .sortedBy { it.createdAtMillis }
        .map { TrendPoint(it.createdAtMillis, it.rulaMax, it.rebaMax) }

    return ReportData(
        assessmentCount = records.size,
        peakRiskCounts = peakCounts,
        metrics = metrics,
        trend = trend
    )
}
