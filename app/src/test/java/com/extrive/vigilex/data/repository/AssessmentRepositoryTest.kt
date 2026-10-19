package com.extrive.vigilex.data.repository

import com.extrive.vigilex.data.api.AnalysisApi
import com.extrive.vigilex.data.api.ApiResult
import com.extrive.vigilex.data.api.AssessmentApi
import com.extrive.vigilex.data.api.ErrorKind
import com.extrive.vigilex.data.insight.interpret
import com.extrive.vigilex.data.model.AnalysisJson
import com.extrive.vigilex.data.model.AssessmentPageDto
import com.extrive.vigilex.data.model.OverviewDto
import com.extrive.vigilex.data.model.needsAttention
import com.extrive.vigilex.data.model.ReportMetadataDto
import com.sun.net.httpserver.HttpServer
import java.io.ByteArrayInputStream
import java.io.File
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/**
 * The app's backend client against a real local HTTP server replaying
 * responses captured from the VigilEx backend for the real test video
 * (src/test/resources/backend_*.json, backend_report.pdf). Nothing here
 * invents a score: expected values are read from those captured responses.
 */
class AssessmentRepositoryTest {

    private val id = "cb03a719-ecc6-4642-9420-36d891f7bb27"
    private val servers = mutableListOf<HttpServer>()
    private val tempDirs = mutableListOf<File>()

    @After
    fun tearDown() {
        servers.forEach { it.stop(0) }
        tempDirs.forEach { it.deleteRecursively() }
    }

    private fun resource(name: String): ByteArray =
        requireNotNull(javaClass.classLoader!!.getResourceAsStream(name)) { "missing test resource $name" }.readBytes()

    private fun text(name: String) = String(resource(name), Charsets.UTF_8)

    private class Route(val status: Int, val body: ByteArray, val contentType: String = "application/json", val delayMs: Long = 0)

    private fun json(status: Int, body: String) = Route(status, body.toByteArray())

    /** Serves [routes] by "METHOD /path"; anything else is a FastAPI-style 404. Records each request line. */
    private fun server(routes: Map<String, Route>, requests: MutableList<String> = mutableListOf()): String {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/") { exchange ->
                exchange.requestBody.readBytes()
                val path = exchange.requestURI.path
                requests += "${exchange.requestMethod} ${exchange.requestURI}"
                val route = routes["${exchange.requestMethod} $path"] ?: json(404, """{"detail":"Not Found"}""")
                Thread.sleep(route.delayMs)
                exchange.responseHeaders.add("Content-Type", route.contentType)
                exchange.sendResponseHeaders(route.status, if (route.body.isEmpty()) -1 else route.body.size.toLong())
                exchange.responseBody.use { it.write(route.body) }
            }
            start()
        }
        servers += server
        return "http://127.0.0.1:${server.address.port}/"
    }

    private fun api(baseUrl: String, readTimeoutMs: Long = 10_000): AssessmentApi = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(
            OkHttpClient.Builder()
                .connectTimeout(2, TimeUnit.SECONDS)
                .readTimeout(readTimeoutMs, TimeUnit.MILLISECONDS)
                .build()
        )
        .addConverterFactory(AnalysisJson.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(AssessmentApi::class.java)

    private fun repository(baseUrl: String, readTimeoutMs: Long = 10_000, videos: Map<String, String> = emptyMap()) =
        AssessmentRepository(baseUrl = { baseUrl }, apiFor = { api(it, readTimeoutMs) }, videoUriFor = videos::get)

    private fun tempDir() = Files.createTempDirectory("vigilex-reports").toFile().also { tempDirs += it }

    private val detailJson by lazy { text("backend_assessment_detail.json") }
    private val reportJson by lazy { text("backend_report.json") }
    private val reportMeta by lazy { AnalysisJson.decodeFromString(ReportMetadataDto.serializer(), reportJson) }
    private val analysisNode by lazy { AnalysisJson.parseToJsonElement(detailJson).jsonObject["analysis"]!!.jsonObject }

    // ================================================================= analysis

    @Test
    fun `successful video analysis returns the backend assessment id`() = runBlocking {
        val body = text("backend_analyze_video_response.json")
        val url = server(mapOf("POST /api/v1/analyze-video" to json(200, body)))
        val analysis = AnalysisRepository { base ->
            Retrofit.Builder().baseUrl(base).client(OkHttpClient()).build().create(AnalysisApi::class.java)
        }
        val bytes = ByteArray(1024)
        val outcome = analysis.analyze(url, VideoUpload("clip.mp4", "video/mp4", 1024) { ByteArrayInputStream(bytes) })

        val result = (outcome as AnalysisOutcome.Success).result
        val raw = AnalysisJson.parseToJsonElement(body).jsonObject
        assertEquals(raw["assessment_id"]!!.jsonPrimitive.content, result.assessmentId)
        // The existing analysis contract is still parsed in full.
        assertEquals(4, result.rula?.maxScore)
        assertEquals(4, result.reba?.maxScore)
        assertEquals(29, result.keyframes.size)
        assertEquals(body, outcome.rawJson)
    }

    // ================================================================== history

    @Test
    fun `history loads backend assessments newest first with their stored scores`() = runBlocking {
        val requests = mutableListOf<String>()
        val url = server(mapOf("GET /api/v1/assessments" to json(200, text("backend_history.json"))), requests)
        val page = (repository(url, videos = mapOf(id to "content://video/1")).history() as ApiResult.Success).data

        val raw = AnalysisJson.decodeFromString(AssessmentPageDto.serializer(), text("backend_history.json"))
        assertEquals(raw.total, page.total)
        assertFalse(page.hasMore)
        val record = page.records.single()
        val row = raw.items.single()
        assertEquals(row.id, record.id)
        assertEquals(row.videoName, record.fileName)
        assertEquals(row.rula?.score, record.rulaMax)
        assertEquals(row.rula?.risk, record.rulaPeakRisk)
        assertEquals(row.reba?.score, record.rebaMax)
        assertEquals(row.reba?.risk, record.rebaPeakRisk)
        assertEquals(row.rula?.riskDistribution, record.rulaDistribution)
        assertEquals(row.durationSec, record.durationSec)
        assertEquals(row.derivedAngles, record.derivedAngles)
        assertEquals("content://video/1", record.videoUri)
        // Backend sent 2026-10-08T13:48:47.611652+05:30.
        assertEquals(java.time.Instant.parse("2026-10-08T08:18:47.611Z").toEpochMilli(), record.createdAtMillis)
        assertEquals(listOf("GET /api/v1/assessments?limit=20&offset=0"), requests)
    }

    @Test
    fun `empty history is an empty page`() = runBlocking {
        val url = server(mapOf("GET /api/v1/assessments" to json(200, """{"items":[],"total":0,"limit":20,"offset":0}""")))
        val page = (repository(url).history() as ApiResult.Success).data
        assertTrue(page.records.isEmpty())
        assertEquals(0, page.total)
        assertFalse(page.hasMore)
    }

    @Test
    fun `more pages are reported and requested by offset`() = runBlocking {
        val more = text("backend_history.json").replace("\"total\":1,", "\"total\":5,")
        assertTrue(more.contains("\"total\":5,"))
        val requests = mutableListOf<String>()
        val url = server(mapOf("GET /api/v1/assessments" to json(200, more)), requests)
        val page = (repository(url).history(offset = 2) as ApiResult.Success).data
        assertTrue(page.hasMore)
        assertEquals("GET /api/v1/assessments?limit=20&offset=2", requests.single())
    }

    // =================================================================== detail

    @Test
    fun `assessment detail loads the stored analysis from the backend`() = runBlocking {
        val url = server(mapOf("GET /api/v1/assessments/$id" to json(200, detailJson)))
        val saved = (repository(url).detail(id) as ApiResult.Success).data
        val analysis = AnalysisJson.decodeFromJsonElement(com.extrive.vigilex.data.model.AnalysisResult.serializer(), analysisNode)

        assertEquals(analysis, saved.result)
        assertEquals(id, saved.record.id)
        assertEquals(saved.result.rula?.maxScore, saved.record.rulaMax)
        assertEquals(saved.result.reba?.maxScore, saved.record.rebaMax)
        assertEquals("moderate", saved.record.rulaPeakRisk)
        assertEquals("medium", saved.record.rebaPeakRisk)
        assertEquals(saved.result.keyframes.size, 29)
    }

    @Test
    fun `missing assessment is not found with the backend's message`() = runBlocking {
        val url = server(mapOf("GET /api/v1/assessments/$id" to json(404, """{"detail":"Assessment not found"}""")))
        val error = repository(url).detail(id) as ApiResult.Error
        assertEquals(ErrorKind.NOT_FOUND, error.kind)
        assertEquals("Assessment not found", error.message)
    }

    @Test
    fun `failed assessment is not shown as a completed one`() = runBlocking {
        val failed = AnalysisJson.parseToJsonElement(detailJson).jsonObject.toMutableMap().apply {
            put("status", AnalysisJson.parseToJsonElement("\"failed\""))
            put("analysis", kotlinx.serialization.json.JsonNull)
        }
        val url = server(mapOf("GET /api/v1/assessments/$id" to json(200, kotlinx.serialization.json.JsonObject(failed).toString())))
        assertEquals(ErrorKind.NOT_FOUND, (repository(url).detail(id) as ApiResult.Error).kind)
    }

    @Test
    fun `delete calls the backend`() = runBlocking {
        val requests = mutableListOf<String>()
        val url = server(mapOf("DELETE /api/v1/assessments/$id" to Route(204, ByteArray(0))), requests)
        assertTrue(repository(url).delete(id) is ApiResult.Success)
        assertEquals("DELETE /api/v1/assessments/$id", requests.single())
    }

    // ================================================================= overview

    @Test
    fun `overview loads the backend aggregates`() = runBlocking {
        val url = server(mapOf("GET /api/v1/overview" to json(200, text("backend_overview.json"))))
        val overview = (repository(url).overview() as ApiResult.Success).data
        assertEquals(1, overview.totalAssessments)
        assertEquals(4, overview.rula.highest)
        assertEquals(4, overview.reba.highest)
        assertEquals(7, overview.rula.scaleMax)
        assertEquals(15, overview.reba.scaleMax)
        assertEquals(mapOf("moderate" to 1), overview.rula.distribution)
        assertEquals(mapOf("medium" to 1), overview.reba.distribution)
        assertEquals(id, overview.latest.single().id)
        // A moderate overall risk does not count as needing attention.
        assertEquals(0, overview.needsAttention())
    }

    @Test
    fun `needs attention counts high and very high overall risk only`() {
        val overview = AnalysisJson.decodeFromString(
            OverviewDto.serializer(),
            """{"total_assessments":6,"recent_window_days":30,"recent_assessments":2,
            "overall_risk_distribution":{"low":1,"moderate":2,"high":2,"very_high":1},
            "rula":{"assessments_scored":6,"scale_max":7},"reba":{"assessments_scored":6,"scale_max":15}}"""
        )
        assertEquals(3, overview.needsAttention())
    }

    @Test
    fun `empty overview has no statistics`() = runBlocking {
        val empty = """{"total_assessments":0,"recent_window_days":30,"recent_assessments":0,
            "overall_risk_distribution":{},
            "rula":{"assessments_scored":0,"average":null,"highest":null,"scale_max":7,"distribution":{}},
            "reba":{"assessments_scored":0,"average":null,"highest":null,"scale_max":15,"distribution":{}},
            "elevated_measurements":[],"latest":[]}"""
        val url = server(mapOf("GET /api/v1/overview" to json(200, empty)))
        val overview = (repository(url).overview() as ApiResult.Success).data
        assertEquals(0, overview.totalAssessments)
        assertNull(overview.rula.highest)
        assertNull(overview.reba.average)
        assertTrue(overview.latest.isEmpty())
    }

    // ================================================================== reports

    @Test
    fun `report generation returns the backend report built from the stored assessment`() = runBlocking {
        val url = server(
            mapOf(
                "POST /api/v1/assessments/$id/report" to json(201, reportJson),
                "GET /api/v1/assessments/$id" to json(200, detailJson)
            )
        )
        val repo = repository(url)
        val report = (repo.generateReport(id) as ApiResult.Success).data
        val saved = (repo.detail(id) as ApiResult.Success).data

        assertEquals(saved.result.rula?.maxScore, report.content.rula?.score)
        assertEquals(saved.result.reba?.maxScore, report.content.reba?.score)
        assertEquals(saved.record.rulaPeakRisk, report.content.rula?.risk)
        assertEquals(saved.record.rebaPeakRisk, report.content.reba?.risk)
        assertEquals(saved.result.rula?.riskDistribution, report.content.rula?.riskDistribution)
        assertEquals("/api/v1/assessments/$id/report/pdf", report.downloadUrl)
        assertFalse(reportJson.contains(":\\\\") || reportJson.contains("/tmp/"))
    }

    @Test
    fun `the preview interprets the stored analysis exactly as the server report does`() {
        val result = AnalysisJson.decodeFromJsonElement(com.extrive.vigilex.data.model.AnalysisResult.serializer(), analysisNode)
        val interpretation = interpret(result)
        val content = reportMeta.content

        assertEquals(content.overallRisk, interpretation.overall?.name?.lowercase())
        assertEquals(content.rula?.score, interpretation.rula.score)
        assertEquals(content.reba?.score, interpretation.reba.score)
        assertEquals(content.findings.map { it.title }, interpretation.findings.map { it.title })
        assertEquals(content.findings.map { it.status }, interpretation.findings.map { it.status.label })
        assertEquals(content.observation, listOfNotNull(interpretation.observation, interpretation.guidance).joinToString(" "))
    }

    @Test
    fun `existing report is retrieved and missing report is not found`() = runBlocking {
        val url = server(mapOf("GET /api/v1/assessments/$id/report" to json(200, reportJson)))
        assertEquals(reportMeta, (repository(url).report(id) as ApiResult.Success).data)
        val none = server(mapOf("GET /api/v1/assessments/$id/report" to json(404, """{"detail":"Report has not been generated."}""")))
        assertEquals(ErrorKind.NOT_FOUND, (repository(none).report(id) as ApiResult.Error).kind)
    }

    @Test
    fun `report generation failure carries the server's message`() = runBlocking {
        val url = server(mapOf("POST /api/v1/assessments/$id/report" to json(500, """{"detail":"The report could not be generated."}""")))
        val error = repository(url).generateReport(id) as ApiResult.Error
        assertEquals(ErrorKind.SERVER, error.kind)
        assertEquals("The report could not be generated.", error.detail)
    }

    @Test
    fun `pdf download saves the server's PDF under its file name`() = runBlocking {
        val pdf = resource("backend_report.pdf")
        val url = server(mapOf("GET /api/v1/assessments/$id/report/pdf" to Route(200, pdf, "application/pdf")))
        val dir = tempDir()
        File(dir, "older-report.pdf").writeText("old")

        val file = (repository(url).downloadReport(reportMeta, dir) as ApiResult.Success).data.file
        assertEquals(reportMeta.fileName, file.name)
        assertEquals(dir, file.parentFile)
        assertArrayEquals(pdf, file.readBytes())
        assertEquals(listOf(file.name), dir.list()!!.toList())
    }

    @Test
    fun `a download that is not a PDF is rejected and nothing is kept`() = runBlocking {
        val url = server(mapOf("GET /api/v1/assessments/$id/report/pdf" to Route(200, "<html>proxy</html>".toByteArray(), "text/html")))
        val dir = tempDir()
        val error = repository(url).downloadReport(reportMeta, dir) as ApiResult.Error
        assertEquals(ErrorKind.BAD_RESPONSE, error.kind)
        assertTrue(dir.list()!!.isEmpty())
    }

    @Test
    fun `a missing PDF is not found`() = runBlocking {
        val url = server(mapOf("GET /api/v1/assessments/$id/report/pdf" to json(404, """{"detail":"Report has not been generated."}""")))
        val error = repository(url).downloadReport(reportMeta, tempDir()) as ApiResult.Error
        assertEquals(ErrorKind.NOT_FOUND, error.kind)
        assertEquals("Report has not been generated.", error.message)
    }

    @Test
    fun `server file names cannot escape the report directory`() {
        assertEquals("evil.pdf", safeFileName("../../evil.pdf"))
        assertEquals("a_b.pdf", safeFileName("a b.pdf"))
        assertEquals("VigilEx-ergonomic-assessment.pdf", safeFileName("report.exe"))
    }

    // =================================================================== errors

    @Test
    fun `backend unavailable is reported as unreachable`() = runBlocking {
        val port = ServerSocket(0).use { it.localPort } // Free port, then closed.
        val error = repository("http://127.0.0.1:$port/").history() as ApiResult.Error
        assertEquals(ErrorKind.UNREACHABLE, error.kind)
    }

    @Test
    fun `no answer in time is a timeout`() = runBlocking {
        val url = server(mapOf("GET /api/v1/overview" to Route(200, text("backend_overview.json").toByteArray(), delayMs = 2_000)))
        val error = repository(url, readTimeoutMs = 300).overview() as ApiResult.Error
        assertEquals(ErrorKind.TIMEOUT, error.kind)
    }

    @Test
    fun `malformed responses are reported, not crashed on`() = runBlocking {
        val url = server(
            mapOf(
                "GET /api/v1/overview" to json(200, "<html>not json</html>"),
                "GET /api/v1/assessments" to json(200, """{"items":[{"id":1}]}"""),
                "GET /api/v1/assessments/$id" to json(502, "<html>Bad gateway</html>")
            )
        )
        val repo = repository(url)
        assertEquals(ErrorKind.BAD_RESPONSE, (repo.overview() as ApiResult.Error).kind)
        assertEquals(ErrorKind.BAD_RESPONSE, (repo.history() as ApiResult.Error).kind)
        val gateway = repo.detail(id) as ApiResult.Error
        assertEquals(ErrorKind.SERVER, gateway.kind)
        assertNull(gateway.detail)
    }
}
