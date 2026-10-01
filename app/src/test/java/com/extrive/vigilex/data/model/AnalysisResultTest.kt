package com.extrive.vigilex.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Parses a real response captured from POST /api/v1/analyze-video on the
 * backend test video (src/test/resources/analyze_video_response.json).
 */
class AnalysisResultTest {

    private val result: AnalysisResult by lazy {
        val raw = javaClass.classLoader!!.getResource("analyze_video_response.json")!!.readText()
        parseAnalysisResult(raw)
    }

    @Test
    fun `parses video, worker and quality blocks`() {
        assertEquals("vigilex-rula-reba-v1", result.methodologyVersion)
        assertEquals(1280, result.video?.width)
        assertEquals(720, result.video?.height)
        assertEquals(25.0, result.video?.fps!!, 0.0)
        assertEquals(29, result.video?.framesSampled)
        assertEquals(1, result.worker?.trackId)
        assertEquals(true, result.quality?.cameraViewOk)
    }

    @Test
    fun `parses RULA and REBA summaries`() {
        assertEquals(4, result.rula?.maxScore)
        assertEquals(29, result.rula?.framesScored)
        assertEquals(mapOf("moderate" to 20, "low" to 9), result.rula?.riskDistribution)
        assertEquals(4, result.reba?.maxScore)
        assertEquals(mapOf("medium" to 15, "low" to 9, "negligible" to 5), result.reba?.riskDistribution)
    }

    @Test
    fun `parses derived angles including knee flexion`() {
        val angles = result.derivedAngles!!
        assertNotNull(angles.kneeAvg)
        assertNotNull(angles.kneeMax)
        assertEquals(107.15, angles.kneeMax!!, 1e-9)
        BodyMetric.entries.forEach { metric ->
            assertNotNull(metric.name, angles.average(metric))
            assertNotNull(metric.name, angles.maximum(metric))
        }
    }

    @Test
    fun `parses keyframes with COCO-17 landmarks and per-frame components`() {
        assertEquals(29, result.keyframes.size)
        val frame = result.keyframes.first()
        assertEquals(17, frame.landmarks.size)
        assertNotNull(frame.rula?.score)
        assertTrue(frame.reba!!.components.keys.containsAll(listOf("upper_arm", "lower_arm", "wrist", "neck", "trunk", "legs")))
        assertTrue(frame.rula!!.components.containsKey("wrist_twist"))
        assertEquals(false, frame.rula!!.components["wrist"]?.measured)
    }

    @Test
    fun `peak risk is read from the keyframe that produced the maximum score`() {
        val index = result.peakFrameIndex(Method.REBA)!!
        assertEquals(result.reba?.maxScore, result.keyframes[index].reba?.score)
        assertEquals(result.keyframes[index].reba?.risk, result.peakRisk(Method.REBA))
        assertEquals("moderate", result.peakRisk(Method.RULA))
        assertEquals("medium", result.peakRisk(Method.REBA))
    }

    @Test
    fun `impact is the worst measured backend severity for the region`() {
        BodyMetric.entries.forEach { metric ->
            val expected = result.keyframes
                .flatMap { listOfNotNull(it.rula, it.reba) }
                .mapNotNull { it.components[metric.componentKey] }
                .filter { it.measured == true }
                .mapNotNull { severityOf(it.severity) }
                .maxOrNull()
            assertEquals(metric.name, expected, result.impact(metric))
        }
    }

    @Test
    fun `component rows follow body order and flag unmeasured wrist`() {
        val rows = result.keyframes.first().componentRows()
        assertEquals(listOf("upper_arm", "lower_arm", "wrist", "wrist_twist", "neck", "trunk", "legs"), rows.map { it.key })
        assertFalse(rows.first { it.key == "wrist" }.measured)
        assertTrue(rows.first { it.key == "trunk" }.measured)
    }

    @Test
    fun `result is scoreable`() {
        assertTrue(result.isScoreable)
    }

    @Test
    fun `unknown fields and missing blocks do not break parsing`() {
        val parsed = parseAnalysisResult("""{"future_field": 1, "rula": {"max_score": null, "frames_scored": 0}}""")
        assertNull(parsed.rula?.maxScore)
        assertFalse(parsed.isScoreable)
        assertNull(parsed.peakRisk(Method.RULA))
        assertTrue(parsed.keyframes.isEmpty())
    }
}
