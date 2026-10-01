package com.extrive.vigilex.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InsightsAndReportTest {

    @Test
    fun `risk tones follow backend bands for each method`() {
        assertEquals(RiskTone.SAFE, riskTone(Method.RULA, "low"))
        assertEquals(RiskTone.MODERATE, riskTone(Method.RULA, "moderate"))
        assertEquals(RiskTone.HIGH, riskTone(Method.RULA, "high"))
        assertEquals(RiskTone.VERY_HIGH, riskTone(Method.RULA, "very_high"))
        assertEquals(RiskTone.SAFE, riskTone(Method.REBA, "negligible"))
        assertEquals(RiskTone.LOW, riskTone(Method.REBA, "low"))
        assertEquals(RiskTone.MODERATE, riskTone(Method.REBA, "medium"))
        assertEquals(RiskTone.UNKNOWN, riskTone(Method.REBA, "unknown"))
        assertEquals(RiskTone.UNKNOWN, riskTone(Method.RULA, null))
    }

    @Test
    fun `risk names are readable and unknown reads as not measured`() {
        assertEquals("Very high", riskName("very_high"))
        assertEquals("Medium", riskName("medium"))
        assertEquals("Not measured", riskName("not_measured"))
        assertEquals("Not measured", riskName(null))
    }

    @Test
    fun `ordered distribution includes zero bands in severity order`() {
        val rows = orderedDistribution(Method.REBA, mapOf("medium" to 3, "negligible" to 1))
        assertEquals(listOf("negligible", "low", "medium", "high", "very_high"), rows.map { it.first })
        assertEquals(listOf(1, 0, 3, 0, 0), rows.map { it.second })
    }

    @Test
    fun `report is null without records`() {
        assertNull(buildReport(emptyList()))
    }

    @Test
    fun `single record reports counts but no trend`() {
        val report = buildReport(listOf(record("a", 1_000, rula = 4, rulaRisk = "moderate", reba = 4, rebaRisk = "medium")))!!
        assertEquals(1, report.assessmentCount)
        assertFalse(report.hasTrend)
        assertEquals(1, report.peakRiskCounts[Method.RULA]!!.toMap()["moderate"])
    }

    @Test
    fun `report aggregates peak risks, metrics and a chronological trend`() {
        val records = listOf(
            record("new", 3_000, rula = 6, rulaRisk = "high", reba = 9, rebaRisk = "high", trunkAvg = 30.0, trunkMax = 60.0),
            record("old", 1_000, rula = 3, rulaRisk = "moderate", reba = 2, rebaRisk = "low", trunkAvg = 10.0, trunkMax = 20.0)
        )
        val report = buildReport(records)!!

        assertTrue(report.hasTrend)
        assertEquals(listOf(1_000L, 3_000L), report.trend.map { it.createdAtMillis })
        assertEquals(listOf(3, 6), report.trend.map { it.rula })

        val reba = report.peakRiskCounts[Method.REBA]!!.toMap()
        assertEquals(1, reba["low"])
        assertEquals(1, reba["high"])
        assertEquals(0, reba["medium"])

        val trunk = report.metrics.first { it.metric == BodyMetric.TRUNK }
        assertEquals(20.0, trunk.meanOfAverages!!, 1e-9)
        assertEquals(60.0, trunk.highestMaximum!!, 1e-9)
        assertEquals(2, trunk.assessments)

        val neck = report.metrics.first { it.metric == BodyMetric.NECK }
        assertNull(neck.meanOfAverages)
        assertEquals(0, neck.assessments)
    }

    @Test
    fun `overall tone is the more severe of the two peaks`() {
        assertEquals(RiskTone.HIGH, record("x", 0, rulaRisk = "high", rebaRisk = "low").overallTone())
        assertEquals(RiskTone.MODERATE, record("y", 0, rulaRisk = "low", rebaRisk = "medium").overallTone())
        assertEquals(RiskTone.UNKNOWN, record("z", 0).overallTone())
    }

    @Test
    fun `record copies backend values without recomputing them`() {
        val raw = javaClass.classLoader!!.getResource("analyze_video_response.json")!!.readText()
        val result = parseAnalysisResult(raw)
        val record = AssessmentRecord.from("id", 42, "clip.mp4", 10, "content://x", result)
        assertEquals(result.rula?.maxScore, record.rulaMax)
        assertEquals(result.reba?.maxScore, record.rebaMax)
        assertEquals(result.peakRisk(Method.RULA), record.rulaPeakRisk)
        assertEquals(result.reba?.riskDistribution, record.rebaDistribution)
        assertEquals(result.derivedAngles, record.derivedAngles)
        assertEquals(result.video?.durationSec, record.durationSec)
    }

    private fun record(
        id: String,
        createdAt: Long,
        rula: Int? = null,
        rulaRisk: String? = null,
        reba: Int? = null,
        rebaRisk: String? = null,
        trunkAvg: Double? = null,
        trunkMax: Double? = null
    ) = AssessmentRecord(
        id = id,
        createdAtMillis = createdAt,
        fileName = "$id.mp4",
        rulaMax = rula,
        rulaPeakRisk = rulaRisk,
        rebaMax = reba,
        rebaPeakRisk = rebaRisk,
        derivedAngles = DerivedAngles(trunkAvg = trunkAvg, trunkMax = trunkMax)
    )
}
