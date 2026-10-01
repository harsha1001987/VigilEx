package com.extrive.vigilex.ui.report

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.extrive.vigilex.data.insight.Interpretation
import com.extrive.vigilex.data.insight.RegionStatus
import com.extrive.vigilex.data.insight.RiskLevel
import com.extrive.vigilex.data.media.VideoInspector
import com.extrive.vigilex.data.model.AnalysisResult
import com.extrive.vigilex.data.model.AssessmentRecord
import com.extrive.vigilex.data.model.BodyMetric
import com.extrive.vigilex.data.model.Keyframe
import com.extrive.vigilex.data.model.orderedDistribution
import com.extrive.vigilex.data.model.riskName
import com.extrive.vigilex.ui.components.CocoEdges
import com.extrive.vigilex.ui.components.DISPLAY_CONFIDENCE
import com.extrive.vigilex.ui.components.describe
import com.extrive.vigilex.ui.format.EMPTY_VALUE
import com.extrive.vigilex.ui.format.formatDateTime
import com.extrive.vigilex.ui.format.formatDegrees
import com.extrive.vigilex.ui.format.formatScale
import com.extrive.vigilex.ui.format.formatScore
import com.extrive.vigilex.ui.format.formatSeconds
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The six sections every report contains, in reading order. Shared by the intro, preview and PDF. */
val ReportSections = listOf(
    "Executive assessment",
    "Key ergonomic findings",
    "RULA / REBA results",
    "Biomechanical measurements",
    "Posture evidence",
    "Methodology"
)

data class GeneratedReport(val file: File, val pageCount: Int)

/**
 * Boundary for report generation. Today reports are built on this device;
 * a server-side implementation can replace this without touching the UI.
 */
interface ReportService {
    suspend fun generate(record: AssessmentRecord, result: AnalysisResult, interpretation: Interpretation): GeneratedReport
}

/**
 * Draws the report as an A4 PDF with Android's PdfDocument. Layout follows
 * the in-app preview: numbered sections, hairline rules, large figures.
 */
class PdfReportService(private val context: Context) : ReportService {

    override suspend fun generate(
        record: AssessmentRecord,
        result: AnalysisResult,
        interpretation: Interpretation
    ): GeneratedReport = withContext(Dispatchers.IO) {
        val frames = loadFrames(record, interpretation)
        val doc = PdfDocument()
        try {
            val writer = PageWriter(doc, footer = "VigilEx · Ergonomic assessment · ${record.fileName}")
            writer.draw(record, result, interpretation, frames)
            val pages = writer.finish()
            val dir = File(context.cacheDir, REPORT_DIR).apply { mkdirs() }
            dir.listFiles()?.forEach { it.delete() } // Keep only the latest report.
            val file = File(dir, fileName(record))
            file.outputStream().use { doc.writeTo(it) }
            GeneratedReport(file, pages)
        } finally {
            doc.close()
            frames.values.forEach { it?.recycle() }
        }
    }

    private suspend fun loadFrames(record: AssessmentRecord, interpretation: Interpretation): Map<Int, Bitmap?> {
        val uri = record.videoUri?.let(Uri::parse) ?: return emptyMap()
        if (!VideoInspector.isReadable(context, uri)) return emptyMap()
        return interpretation.evidence.associate { it.index to VideoInspector.frameAt(context, uri, it.keyframe.t, maxWidth = 900) }
    }

    companion object {
        const val REPORT_DIR = "reports"

        fun fileName(record: AssessmentRecord): String {
            val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(record.createdAtMillis))
            return "VigilEx-ergonomic-assessment-$date.pdf"
        }
    }
}

// ---------------------------------------------------------------- drawing

private const val PAGE_W = 595
private const val PAGE_H = 842
private const val MARGIN = 48f
private const val CONTENT_W = PAGE_W - 2 * MARGIN
private const val FOOTER_SPACE = 40f

private const val INK = 0xFF0A0A0A.toInt()
private const val SECONDARY = 0xFF55554F.toInt()
private const val MUTED = 0xFF8C8C85.toInt()
private const val HAIRLINE = 0xFFE2E2DC.toInt()
private const val SUNKEN = 0xFFEDEDE8.toInt()
private const val GOLD = 0xFFC9A227.toInt()
private const val GOLD_INK = 0xFF7A5F00.toInt()
private const val RED = 0xFFC62828.toInt()
private const val VERY_HIGH_RED = 0xFF8E1B1B.toInt()
private const val YELLOW = 0xFFFFD21F.toInt()
private const val WHITE = 0xFFFFFFFF.toInt()

private val sans: Typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
private val sansMedium: Typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
private val sansBold: Typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)

private fun paint(size: Float, color: Int = INK, face: Typeface = sans, tracking: Float = 0f) = TextPaint().apply {
    isAntiAlias = true
    textSize = size
    this.color = color
    typeface = face
    letterSpacing = tracking
}

private val label = paint(7.5f, MUTED, sansBold, 0.12f)
private val labelInk = paint(7.5f, INK, sansBold, 0.12f)
private val body = paint(10f, SECONDARY)
private val bodyInk = paint(10f, INK)
private val lead = paint(13f, INK)
private val small = paint(8.5f, MUTED)
private val cell = paint(9.5f, INK)
private val cellStrong = paint(9.5f, INK, sansMedium)

private fun RiskLevel?.accent(): Int = when (this) {
    RiskLevel.LOW -> 0xFF171717.toInt()
    RiskLevel.MODERATE -> GOLD
    RiskLevel.HIGH -> RED
    RiskLevel.VERY_HIGH -> VERY_HIGH_RED
    null -> HAIRLINE
}

private fun RiskLevel?.headline(): Int = when (this) {
    RiskLevel.HIGH -> RED
    RiskLevel.VERY_HIGH -> VERY_HIGH_RED
    null -> MUTED
    else -> INK
}

private fun RegionStatus.marker(): Int = when (this) {
    RegionStatus.NOT_MEASURED, RegionStatus.LOW -> HAIRLINE
    RegionStatus.ATTENTION, RegionStatus.ELEVATED -> GOLD
    RegionStatus.HIGH -> RED
}

private fun RegionStatus.text(): Int = when (this) {
    RegionStatus.NOT_MEASURED, RegionStatus.LOW -> MUTED
    RegionStatus.ATTENTION -> INK
    RegionStatus.ELEVATED -> GOLD_INK
    RegionStatus.HIGH -> RED
}

private class PageWriter(private val doc: PdfDocument, private val footer: String) {
    private var page: PdfDocument.Page? = null
    private lateinit var canvas: Canvas
    private var number = 0
    var y = MARGIN
        private set

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)

    private fun startPage() {
        number++
        val p = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, number).create())
        page = p
        canvas = p.canvas
        y = MARGIN
        // Running head: wordmark and document name on every page.
        canvas.drawText("VIGILEX", MARGIN, y + 8f, paint(8f, INK, sansBold, 0.35f))
        val head = "ERGONOMIC ASSESSMENT"
        canvas.drawText(head, PAGE_W - MARGIN - label.measureText(head), y + 8f, label)
        y += 36f
    }

    private fun endPage() {
        val p = page ?: return
        val fy = PAGE_H - MARGIN + 16f
        rect(MARGIN, fy - 14f, CONTENT_W, 0.5f, HAIRLINE)
        canvas.drawText(footer, MARGIN, fy, small)
        val n = "%02d".format(number)
        canvas.drawText(n, PAGE_W - MARGIN - small.measureText(n), fy, small)
        doc.finishPage(p)
        page = null
    }

    fun finish(): Int {
        endPage()
        return number
    }

    private fun ensure(height: Float) {
        if (page == null) startPage()
        if (y + height > PAGE_H - MARGIN - FOOTER_SPACE) {
            endPage()
            startPage()
        }
    }

    private fun gap(h: Float) {
        y += h
    }

    private fun rect(x: Float, top: Float, w: Float, h: Float, color: Int) {
        fill.color = color
        canvas.drawRect(x, top, x + w, top + h, fill)
    }

    private fun rule(color: Int = HAIRLINE, weight: Float = 0.5f) {
        rect(MARGIN, y, CONTENT_W, weight, color)
        y += weight
    }

    private fun layout(text: String, p: TextPaint, width: Float, align: Layout.Alignment = Layout.Alignment.ALIGN_NORMAL) =
        StaticLayout.Builder.obtain(text, 0, text.length, p, width.toInt())
            .setAlignment(align)
            .setLineSpacing(0f, 1.18f)
            .setIncludePad(false)
            .build()

    /** Wrapped text at the cursor; moves the cursor below it. */
    private fun text(value: String, p: TextPaint, x: Float = MARGIN, width: Float = CONTENT_W) {
        val l = layout(value, p, width)
        ensure(l.height.toFloat())
        canvas.save()
        canvas.translate(x, y)
        l.draw(canvas)
        canvas.restore()
        y += l.height
    }

    /** Single-line text with its top at [top]; returns nothing, does not move the cursor. */
    private fun at(value: String, p: TextPaint, x: Float, top: Float, rightAlignTo: Float? = null) {
        val baseline = top - p.fontMetrics.ascent
        val drawX = if (rightAlignTo != null) rightAlignTo - p.measureText(value) else x
        canvas.drawText(value, drawX, baseline, p)
    }

    private fun lineHeight(p: TextPaint) = p.fontMetrics.let { it.descent - it.ascent }

    // ------------------------------------------------------------ sections

    fun draw(record: AssessmentRecord, result: AnalysisResult, i: Interpretation, frames: Map<Int, Bitmap?>) {
        startPage()
        title(record, result)
        section(1, ReportSections[0]); executive(i)
        section(2, ReportSections[1]); findings(i)
        section(3, ReportSections[2]); methods(i)
        section(4, ReportSections[3]); measurements(i)
        if (i.evidence.isNotEmpty()) {
            section(5, ReportSections[4]); evidence(result, i, frames)
        }
        section(6, ReportSections[5]); methodology(result, i)
    }

    private fun title(record: AssessmentRecord, result: AnalysisResult) {
        text("Ergonomic assessment", paint(26f, INK, sansMedium, -0.02f))
        gap(10f)
        val meta = listOf(
            "Video" to record.fileName,
            "Analyzed" to formatDateTime(record.createdAtMillis),
            "Duration" to formatSeconds(result.video?.durationSec)
        )
        meta.forEach { (k, v) ->
            ensure(14f)
            at(k.uppercase(), label, MARGIN, y + 2f)
            at(v, body, MARGIN + 72f, y)
            y += 14f
        }
        gap(28f)
    }

    private fun section(n: Int, name: String) {
        ensure(120f) // Never strand a heading at the foot of a page.
        rule(INK, 1f)
        gap(8f)
        val num = paint(28f, INK, sans, -0.02f)
        at("%02d".format(n), num, MARGIN, y)
        at(name.uppercase(), labelInk, MARGIN + 56f, y + 12f)
        y += lineHeight(num) + 14f
    }

    private fun executive(i: Interpretation) {
        at("ERGONOMIC RISK", label, MARGIN, y); y += 14f
        val word = paint(44f, i.overall.headline(), sansMedium, -0.03f)
        ensure(lineHeight(word) + 12f)
        at(i.overall?.label?.uppercase() ?: "NOT MEASURED", word, MARGIN, y)
        y += lineHeight(word) + 6f
        rect(MARGIN, y, 40f, 3f, i.overall.accent()); y += 3f
        rule(INK, 0.75f)
        listOf(i.rula, i.reba).forEach { m ->
            ensure(26f)
            val top = y + 7f
            at(m.method.label, label, MARGIN, top + 3f)
            at(formatScore(m.score), paint(15f, INK, sansMedium), MARGIN + 64f, top - 2f)
            at("/ ${formatScale(m.method.scaleMax)}", small, MARGIN + 88f, top + 2f)
            rect(PAGE_W - MARGIN - 110f, top + 4f, 6f, 6f, m.level.accent())
            at(riskName(m.risk), body, 0f, top, rightAlignTo = PAGE_W - MARGIN)
            y += 26f
            rule()
        }
        gap(14f)
        i.observation?.let { text(it, lead, width = CONTENT_W * 0.85f) }
        i.guidance?.let { gap(4f); text(it, body) }
        gap(28f)
    }

    private fun findings(i: Interpretation) {
        if (i.findings.isEmpty()) {
            text("No body region could be measured.", body); gap(24f); return
        }
        i.findings.forEachIndexed { index, f ->
            ensure(48f)
            val top = y + 8f
            at("%02d".format(index + 1), paint(9f, MUTED), MARGIN, top + 1f)
            val status = f.status.label.uppercase()
            val statusPaint = paint(7.5f, f.status.text(), sansBold, 0.12f)
            val statusW = statusPaint.measureText(status)
            rect(PAGE_W - MARGIN - statusW - 11f, top + 2f, 6f, 6f, f.status.marker())
            at(status, statusPaint, 0f, top + 1f, rightAlignTo = PAGE_W - MARGIN)
            y = top
            text(f.title, paint(11.5f, INK, sansMedium), x = MARGIN + 36f, width = CONTENT_W - 140f)
            gap(2f)
            val peak = f.maximum?.let { "Peak ${formatDegrees(it)}" + (f.average?.let { a -> ", average ${formatDegrees(a)}. " } ?: ". ") } ?: ""
            text(peak + f.detail, body, x = MARGIN + 36f, width = CONTENT_W - 140f)
            gap(8f)
            rule()
        }
        gap(28f)
    }

    private fun methods(i: Interpretation) {
        listOf(i.rula, i.reba).forEach { m ->
            ensure(60f)
            at(m.method.label, paint(13f, INK, sansMedium), MARGIN, y)
            at("${formatScore(m.score)} / ${formatScale(m.method.scaleMax)} · ${riskName(m.risk)} risk", body, 0f, y + 2f, rightAlignTo = PAGE_W - MARGIN)
            y += 22f
            table(
                headers = listOf("Component", "Angle", "Score"),
                widths = listOf(0.55f, 0.25f, 0.2f),
                rows = m.components.map { c ->
                    listOf(
                        c.name,
                        if (c.measured) formatDegrees(c.angleDeg) else "Not observable",
                        if (c.measured && c.band != null) (c.maxBand?.let { "${c.band} / $it" } ?: c.band.toString()) else EMPTY_VALUE
                    ) to c.measured
                }
            )
            gap(6f)
            val dist = orderedDistribution(m.method, m.distribution).filter { it.second > 0 }
            if (dist.isNotEmpty()) {
                text(
                    "Frames by risk band: " + dist.joinToString(" · ") { "${riskName(it.first)} ${it.second}" } +
                        " of ${m.framesScored} scored.",
                    small
                )
            }
            gap(18f)
        }
        text("Component scores are taken at the frame where each method reached its peak score.", small)
        gap(24f)
    }

    private fun measurements(i: Interpretation) {
        val rows = BodyMetric.entries.mapNotNull { m -> i.regions.firstOrNull { it.metric == m } }
        table(
            headers = listOf("Region", "Avg", "Max", "Status"),
            widths = listOf(0.4f, 0.18f, 0.18f, 0.24f),
            rows = rows.map { r ->
                listOf(r.metric.label, formatDegrees(r.average), formatDegrees(r.maximum), r.status.label) to true
            },
            statusColors = rows.map { it.status.text() }
        )
        gap(6f)
        text("Joint angles in degrees across the frames where each region was measured.", small)
        gap(28f)
    }

    private fun evidence(result: AnalysisResult, i: Interpretation, frames: Map<Int, Bitmap?>) {
        val fw = result.video?.width ?: 0
        val fh = result.video?.height ?: 0
        val aspect = if (fw > 0 && fh > 0) fw.toFloat() / fh else 16f / 9f
        val colW = (CONTENT_W - 16f) / 2
        val imgH = colW / aspect
        i.evidence.chunked(2).forEach { row ->
            ensure(imgH + 40f)
            val top = y
            row.forEachIndexed { col, item ->
                val x = MARGIN + col * (colW + 16f)
                val box = RectF(x, top, x + colW, top + imgH)
                val bmp = frames[item.index]
                if (bmp != null) canvas.drawBitmap(bmp, null, box, Paint(Paint.FILTER_BITMAP_FLAG))
                else { fill.color = SUNKEN; canvas.drawRect(box, fill) }
                skeleton(item.keyframe, box, fw, fh, onImage = bmp != null)
                at("FRAME ${"%02d".format(item.index + 1)} · ${formatSeconds(item.keyframe.t)}", label, x, top + imgH + 6f)
                at(item.reason.describe().uppercase(), labelInk, x, top + imgH + 18f)
            }
            y = top + imgH + 36f
        }
        if (frames.isEmpty()) text("The original video was not available, so the detected pose is shown without the frame.", small)
        gap(24f)
    }

    private fun methodology(result: AnalysisResult, i: Interpretation) {
        text(
            "VigilEx detects the worker's pose in sampled video frames (YOLO11n-Pose), follows the same worker " +
                "through the video (ByteTrack) and measures joint angles in the side view. RULA and REBA are " +
                "scored per frame; this report gives the peak score of each.",
            body
        )
        gap(6f)
        if (i.unmeasured.isNotEmpty()) {
            text(
                "Not observable from video: ${i.unmeasured.joinToString(", ").lowercase()}, as well as force, load, " +
                    "coupling and activity. These are scored at their lowest-risk values.",
                body
            )
            gap(6f)
        }
        text(
            "This report describes observed posture. It does not assess injury, pain, fatigue or health, " +
                "and findings should be reviewed by a qualified person.",
            body
        )
        val facts = listOfNotNull(
            result.video?.framesSampled?.let { "Frames sampled" to it.toString() },
            result.quality?.scoreableFraction?.let { "Scoreable frames" to "${(it * 100).toInt()}%" },
            result.methodologyVersion?.let { "Methodology" to it }
        )
        if (facts.isNotEmpty()) {
            gap(12f)
            facts.forEach { (k, v) ->
                ensure(14f)
                at(k.uppercase(), label, MARGIN, y + 2f)
                at(v, bodyInk, MARGIN + 110f, y)
                y += 14f
            }
        }
    }

    /** Swiss table: tracked headers, a strong rule, hairlines between rows. */
    private fun table(
        headers: List<String>,
        widths: List<Float>,
        rows: List<Pair<List<String>, Boolean>>,
        statusColors: List<Int>? = null
    ) {
        val xs = widths.runningFold(MARGIN) { acc, w -> acc + w * CONTENT_W }
        ensure(20f)
        headers.forEachIndexed { c, h ->
            if (c == 0) at(h.uppercase(), label, xs[c], y) else at(h.uppercase(), label, 0f, y, rightAlignTo = xs[c + 1])
        }
        y += 12f
        rule(INK, 0.75f)
        rows.forEachIndexed { r, (values, strong) ->
            ensure(20f)
            val top = y + 6f
            values.forEachIndexed { c, v ->
                val p = when {
                    !strong -> paint(9.5f, MUTED)
                    statusColors != null && c == values.lastIndex -> paint(9.5f, statusColors[r], sansMedium)
                    c == 0 -> cellStrong
                    else -> cell
                }
                if (c == 0) at(v, p, xs[c], top) else at(v, p, 0f, top, rightAlignTo = xs[c + 1])
            }
            y = top + 14f
            rule()
        }
    }

    private fun skeleton(k: Keyframe, box: RectF, fw: Int, fh: Int, onImage: Boolean) {
        if (k.landmarks.size < 17 || fw <= 0 || fh <= 0) return
        val sx = box.width() / fw
        val sy = box.height() / fh
        fun pt(i: Int) = k.landmarks[i].takeIf { it.confidence >= DISPLAY_CONFIDENCE }
            ?.let { (box.left + it.x * sx).toFloat() to (box.top + it.y * sy).toFloat() }
        val bone = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            strokeWidth = 2f
            strokeCap = Paint.Cap.ROUND
            color = if (onImage) YELLOW else INK
        }
        val under = Paint(bone).apply { strokeWidth = 3.2f; color = 0x8C0A0A0A.toInt() }
        CocoEdges.forEach { (a, b) ->
            val p1 = pt(a) ?: return@forEach
            val p2 = pt(b) ?: return@forEach
            if (onImage) canvas.drawLine(p1.first, p1.second, p2.first, p2.second, under)
            canvas.drawLine(p1.first, p1.second, p2.first, p2.second, bone)
        }
        val dot = Paint(Paint.ANTI_ALIAS_FLAG)
        k.landmarks.indices.forEach { i ->
            val p = pt(i) ?: return@forEach
            dot.color = INK; canvas.drawCircle(p.first, p.second, 2.6f, dot)
            dot.color = if (onImage) WHITE else SUNKEN; canvas.drawCircle(p.first, p.second, 1.4f, dot)
        }
    }
}
