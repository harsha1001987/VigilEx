package com.extrive.vigilex.data.model

import com.extrive.vigilex.data.repository.toRecord
import com.extrive.vigilex.ui.components.COMPONENT_HEADING
import com.extrive.vigilex.ui.components.COMPONENT_NOTE
import com.extrive.vigilex.ui.components.NO_VIEW_LIMITATION
import com.extrive.vigilex.ui.components.PRODUCT_SECTION
import com.extrive.vigilex.ui.components.TECHNICAL_SECTION
import com.extrive.vigilex.ui.report.reportSections
import java.io.ByteArrayOutputStream
import java.util.zip.Inflater
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Product suggestions, measurement reliability and peak-score-frame
 * explanations as the backend sends them, from responses and the PDF captured
 * from the VigilEx backend for the real test video. The app and the PDF must
 * show the same recommendation and the same measurement explanations.
 */
class ReportQualityTest {

    private fun bytes(name: String) =
        requireNotNull(javaClass.classLoader!!.getResourceAsStream(name)) { "missing $name" }.readBytes()

    private fun text(name: String) = String(bytes(name), Charsets.UTF_8)

    private val history by lazy { AnalysisJson.decodeFromString(AssessmentPageDto.serializer(), text("backend_history.json")) }
    private val detail by lazy { AnalysisJson.decodeFromString(AssessmentDetailDto.serializer(), text("backend_assessment_detail.json")) }
    private val report by lazy { AnalysisJson.decodeFromString(ReportMetadataDto.serializer(), text("backend_report.json")) }
    private val pdf by lazy { pdfText(bytes("backend_report.pdf")) }

    @Test
    fun `a back priority action is followed by BackEX as the primary product`() {
        val record = detail.summary.toRecord()
        assertEquals("back", record.priorityAction?.area)
        val product = requireNotNull(record.primaryProduct)
        assertEquals("BackEX", product.name)
        assertEquals("back", product.linkedArea)
        assertEquals("Evaluate BackEX for this task", product.headline)
        assertTrue(product.verified)
        assertTrue(product.sources.isNotEmpty())
        assertTrue(product.qualification.contains("does not establish that this worker needs BackEX"))
        assertEquals("${record.priorityAction!!.title} · ${product.headline}", record.priorityLine)
    }

    @Test
    fun `detail, history and report carry the same product and reliability`() {
        val summary = detail.summary
        assertEquals(summary.productRecommendations, history.items.single().productRecommendations)
        assertEquals(summary.productRecommendations, report.content.productRecommendations)
        assertEquals(summary.measurementReliability, history.items.single().measurementReliability)
        assertEquals(summary.measurementReliability, report.content.measurementReliability)
    }

    @Test
    fun `app section order and wording match the server report`() {
        assertEquals(reportSections(hasProduct = true), report.content.sections)
        assertEquals(TECHNICAL_SECTION, report.content.technicalSection)
        assertEquals(COMPONENT_NOTE, report.content.componentNote)
        assertTrue(report.content.sections.contains(PRODUCT_SECTION))
        assertFalse(reportSections(hasProduct = false).contains(PRODUCT_SECTION))
    }

    @Test
    fun `the PDF shows the same recommendation and explanations the app shows`() {
        val product = report.content.productRecommendations.first()
        val flat = pdf.replace(Regex("\\s+"), " ")
        report.content.sections.forEach { assertTrue(it, flat.contains(it.uppercase())) }
        assertTrue(flat.contains(product.headline))
        assertTrue(flat.contains("WHY IT MAY HELP"))
        assertTrue(flat.contains("BEST RESULTS COME FROM COMBINING APPROACHES"))
        assertTrue(flat.contains(COMPONENT_HEADING.uppercase()))
        assertTrue(flat.contains(COMPONENT_NOTE.substring(0, 60)))
        val reliability = requireNotNull(report.content.measurementReliability)
        if (reliability.limited) assertTrue(flat.contains("LIMITED MEASUREMENT RELIABILITY"))
        else assertTrue(flat.contains(NO_VIEW_LIMITATION.substring(0, 50)))
    }

    @Test
    fun `the real clip's limited camera view is explained with its own values`() {
        val reliability = requireNotNull(detail.summary.measurementReliability)
        val back = requireNotNull(report.content.backPosture)
        assertEquals(back.sideOnFraction, reliability.sideOnFraction)
        assertEquals(back.viewLimited, reliability.limited)
        if (reliability.limited) {
            assertEquals("Limited measurement reliability", reliability.title)
            assertTrue(reliability.reasons.isNotEmpty())
            assertTrue(reliability.recommendation!!.contains("record a new video"))
        }
    }

    @Test
    fun `peak-score-frame components are labeled apart from the overall peaks`() {
        val trunkPeak = report.content.measurements.first { it.metric == "trunk" }.maximumDeg!!
        report.content.componentTables.forEach { table ->
            assertEquals("${table.method} — component measurements at the peak-score frame", table.title)
            val trunk = table.rows.first { it.name.startsWith("Trunk") }
            assertTrue(trunk.angleDeg!! <= trunkPeak)
        }
        assertEquals(listOf("RULA", "REBA"), report.content.componentTables.map { it.method })
    }

    @Test
    fun `unobservable components are never given an angle or band`() {
        report.content.componentTables.flatMap { it.rows }.filterNot { it.measured }.forEach {
            assertNull(it.angleDeg)
            assertNull(it.band)
        }
        assertTrue(report.content.measurementNote.contains("not evidence"))
    }

    @Test
    fun `responses without products or reliability still parse`() {
        val old = """{"id":"x","status":"completed","created_at":"2026-10-08T13:48:47+05:30"}"""
        val record = AnalysisJson.decodeFromString(AssessmentSummaryDto.serializer(), old).toRecord()
        assertTrue(record.productRecommendations.isEmpty())
        assertNull(record.measurementReliability)
        assertNull(record.primaryProduct)
        assertNull(record.priorityLine)
    }

    /** Text drawn in a reportlab PDF: each content stream is ASCII85 then Flate encoded. */
    private fun pdfText(data: ByteArray): String {
        val raw = String(data, Charsets.ISO_8859_1)
        val out = StringBuilder()
        Regex("stream\\r?\\n(.*?)endstream", RegexOption.DOT_MATCHES_ALL).findAll(raw).forEach { m ->
            val decoded = runCatching { inflate(ascii85(m.groupValues[1])) }.getOrNull() ?: return@forEach
            Regex("\\((.*?)(?<!\\\\)\\)\\s*Tj").findAll(decoded).forEach { out.append(it.groupValues[1]).append(' ') }
        }
        return out.toString()
    }

    private fun ascii85(text: String): ByteArray {
        val body = text.filterNot { it.isWhitespace() }.removeSuffix("~>")
        val out = ByteArrayOutputStream()
        var i = 0
        while (i < body.length) {
            if (body[i] == 'z') { out.write(ByteArray(4)); i++; continue }
            val chunk = body.substring(i, minOf(i + 5, body.length))
            val padded = chunk.padEnd(5, 'u')
            var value = 0L
            padded.forEach { value = value * 85 + (it.code - 33) }
            val bytes = byteArrayOf((value shr 24).toByte(), (value shr 16).toByte(), (value shr 8).toByte(), value.toByte())
            out.write(bytes, 0, chunk.length - 1)
            i += 5
        }
        return out.toByteArray()
    }

    private fun inflate(data: ByteArray): String {
        val inflater = Inflater().apply { setInput(data) }
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (!inflater.finished()) {
            val n = inflater.inflate(buffer)
            if (n == 0 && (inflater.needsInput() || inflater.needsDictionary())) break
            out.write(buffer, 0, n)
        }
        return String(out.toByteArray(), Charsets.ISO_8859_1)
    }
}
