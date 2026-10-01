package com.extrive.vigilex.data.insight

import com.extrive.vigilex.data.model.AnalysisResult
import com.extrive.vigilex.data.model.BodyMetric
import com.extrive.vigilex.data.model.FrameComponent
import com.extrive.vigilex.data.model.FrameScore
import com.extrive.vigilex.data.model.Keyframe
import com.extrive.vigilex.data.model.Method
import com.extrive.vigilex.data.model.MethodSummary
import com.extrive.vigilex.data.model.parseAnalysisResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InterpretationTest {

    private val sample: AnalysisResult by lazy {
        parseAnalysisResult(javaClass.classLoader!!.getResource("analyze_video_response.json")!!.readText())
    }

    @Test
    fun `overall risk is the more severe backend risk level`() {
        assertEquals(RiskLevel.MODERATE, overallLevel("moderate", "low"))
        assertEquals(RiskLevel.HIGH, overallLevel("moderate", "high"))
        assertEquals(RiskLevel.LOW, overallLevel(null, "negligible"))
        assertNull(overallLevel(null, "not_measured"))
    }

    @Test
    fun `sample response reads as moderate with backend scores`() {
        val i = interpret(sample)
        assertEquals(RiskLevel.MODERATE, i.overall)
        assertEquals(4, i.rula.score)
        assertEquals(4, i.reba.score)
        assertEquals("moderate", i.rula.risk)
        assertEquals("medium", i.reba.risk)
        assertEquals(29, i.rula.framesScored)
    }

    @Test
    fun `regions are prioritized and only flagged regions reach the observation`() {
        val i = interpret(sample)
        println("regions: " + i.regions.map { "${it.metric} ${it.status} ${it.framesFlagged}/${it.framesMeasured}" })
        println("findings: " + i.findings.map { it.title + " — " + it.detail })
        println("observation: " + i.observation)
        println("evidence: " + i.evidence.map { "${it.index} ${it.reason}" })
        println("unmeasured: " + i.unmeasured)

        // Sorted by status, most significant first.
        assertEquals(i.regions.sortedByDescending { it.status }.map { it.status }, i.regions.map { it.status })
        // Knee is at its top band in every measured frame; trunk only reaches REBA's middle band.
        assertEquals(BodyMetric.KNEE_FLEXION, i.keyFinding!!.metric)
        assertEquals(RegionStatus.ATTENTION, i.regions.first { it.metric == BodyMetric.TRUNK }.status)
        assertEquals(BodyMetric.TRUNK, i.regions.last().metric)
        assertTrue(i.findings.size in 1..InterpretationRules.MAX_FINDINGS)
        // No red in a moderate assessment.
        assertTrue(i.regions.none { it.status == RegionStatus.HIGH })
        val observation = i.observation!!
        assertEquals("Deep knee flexion and pronounced neck flexion were observed during the analyzed movement.", observation)
        assertTrue(observation.endsWith("observed during the analyzed movement."))
    }

    @Test
    fun `evidence frames are distinct real keyframes`() {
        val i = interpret(sample)
        assertTrue(i.evidence.isNotEmpty())
        assertTrue(i.evidence.size <= InterpretationRules.MAX_EVIDENCE_FRAMES)
        assertEquals(i.evidence.size, i.evidence.map { it.index }.distinct().size)
        i.evidence.forEach { assertTrue(it.keyframe === sample.keyframes[it.index]) }
    }

    @Test
    fun `components the backend could not observe are reported as unmeasured`() {
        val i = interpret(sample)
        assertTrue("Wrist" in i.unmeasured)
        assertTrue(i.rula.components.first { it.key == "wrist" }.let { !it.measured && it.severity == null })
    }

    @Test
    fun `high severity region is red only in a high risk assessment`() {
        fun frame(score: Int, risk: String) = Keyframe(
            rula = FrameScore(
                score = score,
                risk = risk,
                components = mapOf("upper_arm" to FrameComponent(band = 4, maxBand = 4, measured = true, severity = "high"))
            )
        )
        val high = AnalysisResult(rula = MethodSummary(maxScore = 6, framesScored = 1), keyframes = listOf(frame(6, "high")))
        val moderate = AnalysisResult(rula = MethodSummary(maxScore = 4, framesScored = 1), keyframes = listOf(frame(4, "moderate")))
        assertEquals(RegionStatus.HIGH, interpret(high).regions.first().status)
        assertEquals(RegionStatus.ELEVATED, interpret(moderate).regions.first().status)
        assertEquals(Method.RULA, (interpret(high).evidence.first().reason as EvidenceReason.PeakScore).method)
    }

    @Test
    fun `nothing measured gives no observation and no findings`() {
        val i = interpret(AnalysisResult())
        assertNull(i.overall)
        assertNull(i.observation)
        assertTrue(i.findings.isEmpty())
        assertTrue(i.evidence.isEmpty())
    }
}
