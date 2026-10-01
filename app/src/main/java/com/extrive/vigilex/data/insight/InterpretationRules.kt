package com.extrive.vigilex.data.insight

import com.extrive.vigilex.data.model.BodyMetric
import com.extrive.vigilex.data.model.Method

/**
 * Every presentation rule VigilEx applies on top of the backend result, in
 * one place. None of these compute a score or risk band: the backend owns the
 * RULA/REBA methodology. They only decide which words describe backend values.
 */
object InterpretationRules {

    /** Backend risk labels (posture._rula_risk_level / _reba_risk_level) on one shared scale. */
    fun level(method: Method, risk: String?): RiskLevel? = when (method) {
        Method.RULA -> when (risk) {
            "low" -> RiskLevel.LOW
            "moderate" -> RiskLevel.MODERATE
            "high" -> RiskLevel.HIGH
            "very_high" -> RiskLevel.VERY_HIGH
            else -> null
        }
        Method.REBA -> when (risk) {
            "negligible", "low" -> RiskLevel.LOW
            "medium" -> RiskLevel.MODERATE
            "high" -> RiskLevel.HIGH
            "very_high" -> RiskLevel.VERY_HIGH
            else -> null
        }
    }

    /** Share of scored frames above which a region is described as consistent or repeated. */
    const val CONSISTENT_SHARE = 0.66
    const val REPEATED_SHARE = 0.2

    fun frequencyWord(share: Double): String = when {
        share >= CONSISTENT_SHARE -> "consistently"
        share >= REPEATED_SHARE -> "repeatedly"
        else -> "briefly"
    }

    /** Most findings shown in the summary, and the most regions named in the observation sentence. */
    const val MAX_FINDINGS = 3
    const val MAX_OBSERVATION_REGIONS = 2

    /** At most this many evidence frames are chosen from the keyframes. */
    const val MAX_EVIDENCE_FRAMES = 3

    /** Observation-language title for a region at a given status. Never diagnostic. */
    fun title(metric: BodyMetric, status: RegionStatus): String = when (metric) {
        BodyMetric.UPPER_ARM -> when (status) {
            RegionStatus.ELEVATED, RegionStatus.HIGH -> "Elevated upper-arm loading"
            RegionStatus.ATTENTION -> "Moderate upper-arm elevation"
            else -> "Limited upper-arm elevation"
        }
        BodyMetric.KNEE_FLEXION -> when (status) {
            RegionStatus.ELEVATED, RegionStatus.HIGH -> "Deep knee flexion"
            RegionStatus.ATTENTION -> "Moderate knee flexion"
            else -> "Limited knee flexion"
        }
        BodyMetric.TRUNK -> when (status) {
            RegionStatus.ELEVATED, RegionStatus.HIGH -> "Pronounced trunk flexion"
            RegionStatus.ATTENTION -> "Moderate trunk flexion"
            else -> "Low trunk deviation"
        }
        BodyMetric.NECK -> when (status) {
            RegionStatus.ELEVATED, RegionStatus.HIGH -> "Pronounced neck flexion"
            RegionStatus.ATTENTION -> "Moderate neck flexion"
            else -> "Limited neck flexion"
        }
    }

    /**
     * Follow-up wording per overall level, paraphrasing the action levels the
     * RULA and REBA methods attach to their own score bands.
     */
    fun guidance(level: RiskLevel): String = when (level) {
        RiskLevel.LOW -> "Scores are within the range the methods treat as acceptable."
        RiskLevel.MODERATE -> "Further ergonomic review may be warranted."
        RiskLevel.HIGH -> "Ergonomic review is recommended soon."
        RiskLevel.VERY_HIGH -> "Ergonomic review is recommended promptly."
    }
}
