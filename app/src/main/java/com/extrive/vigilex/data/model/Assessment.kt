package com.extrive.vigilex.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlin.math.roundToInt

@Serializable
data class AssessmentCreateRequest(
    @SerialName("organization_id") val organizationId: String,
    @SerialName("site_id") val siteId: String,
    @SerialName("area_id") val areaId: String,
    @SerialName("task_id") val taskId: String,
    val status: String = "draft",
    @SerialName("load_value") val loadValue: Double? = null,
    @SerialName("load_unit") val loadUnit: String? = null,
    @SerialName("load_source") val loadSource: String? = null,
    @SerialName("consent_given") val consentGiven: Boolean = false
)

@Serializable
data class AssessmentScoreDto(
    val id: String? = null,
    @SerialName("assessment_id") val assessmentId: String? = null,
    val method: String,
    @SerialName("methodology_version") val methodologyVersion: String? = null,
    val score: Double? = null,
    @SerialName("risk_band") val riskBand: String? = null,
    @SerialName("action_level") val actionLevel: Int? = null,
    val inputs: Map<String, JsonElement>? = null,
    val details: Map<String, JsonElement>? = null
)

@Serializable
data class AssessmentInterventionDto(
    val id: String? = null,
    @SerialName("assessment_id") val assessmentId: String? = null,
    @SerialName("risk_driver") val riskDriver: String,
    val recommendation: String,
    val priority: String,
    @SerialName("extrive_product") val extriveProduct: String? = null
)

@Serializable
data class AssessmentDto(
    val id: String,
    @SerialName("organization_id") val organizationId: String,
    @SerialName("site_id") val siteId: String,
    @SerialName("area_id") val areaId: String,
    @SerialName("task_id") val taskId: String,
    val status: String,
    @SerialName("methodology_version") val methodologyVersion: String? = null,
    @SerialName("load_value") val loadValue: String? = null,
    @SerialName("load_unit") val loadUnit: String? = null,
    @SerialName("load_source") val loadSource: String? = null,
    val scores: List<AssessmentScoreDto> = emptyList(),
    val interventions: List<AssessmentInterventionDto> = emptyList()
)

@Serializable
data class ProcessVideoRequest(
    @SerialName("video_path") val videoPath: String,
    @SerialName("stride_hz") val strideHz: Double = 25.0,
    @SerialName("requested_track_id") val requestedTrackId: Int? = null
)

val AssessmentDto.rulaScore: AssessmentScoreDto?
    get() = scores.firstOrNull { it.method.equals("RULA", ignoreCase = true) }

val AssessmentDto.rebaScore: AssessmentScoreDto?
    get() = scores.firstOrNull { it.method.equals("REBA", ignoreCase = true) }

fun AssessmentScoreDto.getCoveragePct(): String {
    val pct = inputs?.get("coverage_pct")
    if (pct != null && pct is JsonPrimitive) {
        return pct.content
    }
    val cov = inputs?.get("coverage")
    if (cov != null && cov is JsonPrimitive) {
        val d = cov.doubleOrNull
        if (d != null) return "${(d * 100).roundToInt()}%"
    }
    return "—"
}

fun AssessmentScoreDto.getValidFrames(): Int? {
    val el = inputs?.get("valid_frames")
    return if (el != null && el is JsonPrimitive) el.intOrNull else null
}

fun AssessmentScoreDto.getFramesObserved(): Int? {
    val el = inputs?.get("frames_observed")
    return if (el != null && el is JsonPrimitive) el.intOrNull else null
}

fun AssessmentScoreDto.getPrimaryTrackId(): Int? {
    val el = inputs?.get("primary_track_id")
    return if (el != null && el is JsonPrimitive) el.intOrNull else null
}
