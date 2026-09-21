package com.extrive.vigilex.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Matches app/schemas/assessment.py AssessmentCreate on the backend.
 *
 * worker_id and assessor_id are omitted: there is no Worker lookup/creation
 * API yet to resolve the free-text worker reference the UI collects into a
 * real worker_id, and there is no authentication yet to supply an
 * assessor_id. Both are optional on the backend, so omitting them is valid.
 */
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

/**
 * Matches app/schemas/assessment.py AssessmentResponse - only the fields this
 * app currently displays or needs. Extra fields the backend returns (JSONB
 * payloads, scores, interventions) are simply ignored by the JSON parser.
 *
 * Note: the backend serializes load_value as a JSON string (Decimal), not a
 * number - confirmed against the live API response, not guessed.
 */
@Serializable
data class AssessmentDto(
    val id: String,
    @SerialName("organization_id") val organizationId: String,
    @SerialName("site_id") val siteId: String,
    @SerialName("area_id") val areaId: String,
    @SerialName("task_id") val taskId: String,
    val status: String,
    @SerialName("load_value") val loadValue: String? = null,
    @SerialName("load_unit") val loadUnit: String? = null,
    @SerialName("load_source") val loadSource: String? = null
)
