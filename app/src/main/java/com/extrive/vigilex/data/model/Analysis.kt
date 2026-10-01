package com.extrive.vigilex.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Response of POST /api/v1/analyze-video, i.e. the dict produced by
 * backend/app/cv/batch_processor.process_video(). Field names mirror the
 * backend exactly; every field is optional so a partial response never
 * crashes the app.
 */
@Serializable
data class AnalysisResult(
    @SerialName("methodology_version") val methodologyVersion: String? = null,
    val video: VideoInfo? = null,
    val worker: WorkerInfo? = null,
    val quality: QualityInfo? = null,
    @SerialName("derived_angles") val derivedAngles: DerivedAngles? = null,
    val rula: MethodSummary? = null,
    val reba: MethodSummary? = null,
    val keyframes: List<Keyframe> = emptyList(),
    val meta: AnalysisMeta? = null
)

@Serializable
data class VideoInfo(
    @SerialName("duration_sec") val durationSec: Double? = null,
    val fps: Double? = null,
    val width: Int? = null,
    val height: Int? = null,
    @SerialName("frames_total") val framesTotal: Int? = null,
    @SerialName("frames_sampled") val framesSampled: Int? = null,
    @SerialName("sample_hz") val sampleHz: Double? = null
)

@Serializable
data class WorkerInfo(
    @SerialName("track_id") val trackId: Int? = null,
    @SerialName("first_seen_sec") val firstSeenSec: Double? = null,
    @SerialName("last_seen_sec") val lastSeenSec: Double? = null,
    @SerialName("detected_frames") val detectedFrames: Int? = null
)

@Serializable
data class QualityInfo(
    @SerialName("detection_fraction") val detectionFraction: Double? = null,
    @SerialName("scoreable_fraction") val scoreableFraction: Double? = null,
    @SerialName("camera_view_ok") val cameraViewOk: Boolean? = null,
    @SerialName("max_camera_yaw_deg") val maxCameraYawDeg: Double? = null
)

@Serializable
data class DerivedAngles(
    @SerialName("trunk_deg_avg") val trunkAvg: Double? = null,
    @SerialName("trunk_deg_max") val trunkMax: Double? = null,
    @SerialName("upper_arm_deg_avg") val upperArmAvg: Double? = null,
    @SerialName("upper_arm_deg_max") val upperArmMax: Double? = null,
    @SerialName("knee_deg_avg") val kneeAvg: Double? = null,
    @SerialName("knee_deg_max") val kneeMax: Double? = null,
    @SerialName("neck_deg_avg") val neckAvg: Double? = null,
    @SerialName("neck_deg_max") val neckMax: Double? = null
)

@Serializable
data class MethodSummary(
    @SerialName("max_score") val maxScore: Int? = null,
    @SerialName("frames_scored") val framesScored: Int = 0,
    @SerialName("risk_distribution") val riskDistribution: Map<String, Int> = emptyMap()
)

@Serializable
data class Keyframe(
    val t: Double = 0.0,
    @SerialName("track_id") val trackId: Int? = null,
    @SerialName("camera_yaw_deg") val cameraYawDeg: Double? = null,
    @SerialName("side_view_ok") val sideViewOk: Boolean? = null,
    val landmarks: List<Landmark> = emptyList(),
    val rula: FrameScore? = null,
    val reba: FrameScore? = null
)

@Serializable
data class Landmark(
    val x: Double,
    val y: Double,
    val confidence: Double
)

@Serializable
data class FrameScore(
    val score: Int? = null,
    val risk: String? = null,
    val angles: Map<String, Double?> = emptyMap(),
    val components: Map<String, FrameComponent> = emptyMap()
)

@Serializable
data class FrameComponent(
    val name: String? = null,
    @SerialName("angle_deg") val angleDeg: Double? = null,
    val band: Int? = null,
    @SerialName("max_band") val maxBand: Int? = null,
    val measured: Boolean? = null,
    val severity: String? = null
)

@Serializable
data class AnalysisMeta(
    @SerialName("pose_model") val poseModel: String? = null,
    val processor: String? = null,
    @SerialName("input_video") val inputVideo: String? = null
)

val AnalysisJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    coerceInputValues = true
}

fun parseAnalysisResult(raw: String): AnalysisResult =
    AnalysisJson.decodeFromString(AnalysisResult.serializer(), raw)
