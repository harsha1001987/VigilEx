package com.extrive.vigilex.data.model

import com.extrive.vigilex.data.insight.RegionStatus
import com.extrive.vigilex.data.insight.interpret
import com.extrive.vigilex.data.repository.toRecord
import com.extrive.vigilex.ui.components.interventionStatus
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Interventions as the backend sends them, parsed from responses captured
 * from the VigilEx backend for the real test video. Expected values are read
 * from those responses; the app never decides what to recommend.
 */
class InterventionsTest {

    private fun text(name: String) =
        String(requireNotNull(javaClass.classLoader!!.getResourceAsStream(name)) { "missing $name" }.readBytes())

    private val history by lazy { AnalysisJson.decodeFromString(AssessmentPageDto.serializer(), text("backend_history.json")) }
    private val detail by lazy { AnalysisJson.decodeFromString(AssessmentDetailDto.serializer(), text("backend_assessment_detail.json")) }
    private val report by lazy { AnalysisJson.decodeFromString(ReportMetadataDto.serializer(), text("backend_report.json")) }

    @Test
    fun `interventions parse field for field from the backend response`() {
        val raw = AnalysisJson.parseToJsonElement(text("backend_history.json"))
            .jsonObject["items"]!!.jsonArray[0].jsonObject["interventions"]!!.jsonArray
        val parsed = history.items.single().interventions
        assertEquals(raw.size, parsed.size)
        raw.zip(parsed).forEach { (json, dto) ->
            val o = json.jsonObject
            assertEquals(o["area"]!!.jsonPrimitive.content, dto.area)
            assertEquals(o["priority"]!!.jsonPrimitive.content, dto.priority)
            assertEquals(o["status"]!!.jsonPrimitive.content, dto.status)
            assertEquals(o["title"]!!.jsonPrimitive.content, dto.title)
            assertEquals(o["observation"]!!.jsonPrimitive.content, dto.observation)
            assertEquals(o["recommendation"]!!.jsonPrimitive.content, dto.recommendation)
            val evidence = o["evidence"]!!.jsonObject
            assertEquals(evidence["frames_flagged"]!!.jsonPrimitive.content.toInt(), dto.evidence!!.framesFlagged)
            assertEquals(evidence["frames_measured"]!!.jsonPrimitive.content.toInt(), dto.evidence!!.framesMeasured)
        }
    }

    @Test
    fun `the real video's priority action is the back, from the backend`() {
        val record = history.items.single().toRecord()
        val primary = requireNotNull(record.priorityAction)
        assertEquals("back", primary.area)
        assertEquals("high", primary.priority)
        // The evidence is the stored trunk measurement.
        assertEquals(record.derivedAngles?.trunkMax, primary.evidence?.peakDeg)
        assertEquals(record.derivedAngles?.trunkAvg, primary.evidence?.averageDeg)
        assertEquals(record.interventions.drop(1), record.otherActions)
        assertTrue(record.actionSummary!!.startsWith("Posture risk was ${record.rulaPeakRisk}"))
    }

    @Test
    fun `detail, history and report carry the same interventions`() {
        val fromHistory = history.items.single().interventions
        assertEquals(fromHistory, detail.summary.interventions)
        assertEquals(fromHistory, report.content.interventions)
        assertEquals(history.items.single().actionSummary, report.content.actionSummary)
        assertEquals(detail.summary.toRecord().interventions, fromHistory)
    }

    @Test
    fun `report carries the back posture metric`() {
        val back = requireNotNull(report.content.backPosture)
        assertEquals(detail.summary.derivedAngles?.trunkMax, back.peakDeg)
        assertTrue(back.framesInclined <= back.framesMeasured)
        assertTrue(back.sideOnFraction in 0.0..1.0)
        // The backend explains a limited camera view in the back intervention itself.
        if (back.viewLimited) {
            assertTrue(report.content.interventions.first { it.area == "back" }.observation.contains("not side-on"))
        }
    }

    @Test
    fun `responses without interventions still parse`() {
        val old = """{"id":"x","status":"completed","created_at":"2026-10-08T13:48:47+05:30"}"""
        val summary = AnalysisJson.decodeFromString(AssessmentSummaryDto.serializer(), old)
        assertTrue(summary.interventions.isEmpty())
        assertNull(summary.actionSummary)
        assertNull(summary.toRecord().priorityAction)
    }

    @Test
    fun `intervention statuses map onto the app's status scale`() {
        assertEquals(RegionStatus.ATTENTION, interventionStatus("attention"))
        assertEquals(RegionStatus.ELEVATED, interventionStatus("elevated"))
        assertEquals(RegionStatus.HIGH, interventionStatus("high"))
        assertEquals(RegionStatus.NOT_MEASURED, interventionStatus("something new"))
    }

    @Test
    fun `a component at its lowest band is ok, whatever the raw label`() {
        // posture.py labels REBA neck 1/2 and RULA neck 1/3 "moderate".
        assertEquals(Severity.OK, FrameComponent(band = 1, maxBand = 2, measured = true, severity = "moderate").effectiveSeverity())
        assertEquals(Severity.OK, FrameComponent(band = 1, maxBand = 3, measured = true, severity = "moderate").effectiveSeverity())
        assertEquals(Severity.MODERATE, FrameComponent(band = 2, maxBand = 4, measured = true, severity = "moderate").effectiveSeverity())
        assertEquals(Severity.HIGH, FrameComponent(band = 3, maxBand = 3, measured = true, severity = "high").effectiveSeverity())
    }

    @Test
    fun `the app's region statuses match the backend's measurements table`() {
        val result = AnalysisJson.decodeFromJsonElement(AnalysisResult.serializer(), detail.analysis!!)
        val overall = report.content.overallRisk
        val backend = report.content.measurements.associate { m ->
            m.metric to when (m.peakSeverity) {
                null -> RegionStatus.NOT_MEASURED
                "ok" -> RegionStatus.LOW
                "moderate" -> RegionStatus.ATTENTION
                else -> if (overall == "high" || overall == "very_high") RegionStatus.HIGH else RegionStatus.ELEVATED
            }
        }
        val app = interpret(result).regions.associate { r ->
            val key = when (r.metric) {
                BodyMetric.TRUNK -> "trunk"
                BodyMetric.UPPER_ARM -> "upper_arm"
                BodyMetric.KNEE_FLEXION -> "knee_flexion"
                BodyMetric.NECK -> "neck"
            }
            key to r.status
        }
        assertEquals(backend, app)
    }
}
