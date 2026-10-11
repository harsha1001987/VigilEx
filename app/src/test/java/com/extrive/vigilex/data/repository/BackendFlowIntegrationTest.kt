package com.extrive.vigilex.data.repository

import com.extrive.vigilex.data.account.AccountSession
import com.extrive.vigilex.data.account.TestAccounts
import com.extrive.vigilex.data.api.ApiResult
import com.extrive.vigilex.data.api.AuthInterceptor
import com.extrive.vigilex.data.api.ErrorKind
import com.extrive.vigilex.data.api.AssessmentApi
import com.extrive.vigilex.data.insight.interpret
import com.extrive.vigilex.data.model.AnalysisJson
import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/**
 * The whole app flow against a running VigilEx backend with the real test
 * video, through the app's own repositories:
 *
 *   analyze -> assessment_id -> detail -> history -> overview -> report -> PDF
 *
 * Every RULA/REBA value is compared with what the analysis returned, so the
 * backend is shown to be the single source of truth. Skipped unless the
 * server answers at VIGILEX_TEST_SERVER (default http://127.0.0.1:8000/).
 * The assessment it creates is deleted afterwards.
 */
class BackendFlowIntegrationTest {

    private val server = System.getenv("VIGILEX_TEST_SERVER") ?: "http://127.0.0.1:8000/"
    private val video = File(
        System.getenv("VIGILEX_TEST_VIDEO") ?: "../backend/tests/19832490-hd_1920_1080_25fps (1).mp4"
    )

    private val assessments = AssessmentRepository(
        baseUrl = { server },
        apiFor = { base ->
            Retrofit.Builder()
                .baseUrl(base)
                .client(OkHttpClient.Builder().addInterceptor(AuthInterceptor()).readTimeout(60, TimeUnit.SECONDS).build())
                .addConverterFactory(AnalysisJson.asConverterFactory("application/json".toMediaType()))
                .build()
                .create(AssessmentApi::class.java)
        },
        videoUriFor = { null }
    )

    @Test
    fun `real video flows from analysis to a downloaded PDF with one set of scores`() = runBlocking {
        val analysisRepository = AnalysisRepository()
        assumeTrue("VigilEx server not running at $server", analysisRepository.isServerReachable(server))
        assumeTrue("Test video missing: ${video.absolutePath}", video.exists())
        val alice = TestAccounts.signIn(server, TestAccounts.A, "Android A")

        val before = (assessments.overview() as ApiResult.Success).data.totalAssessments

        // Analyze: the backend processes the video and returns the saved assessment's id.
        val outcome = analysisRepository.analyze(
            server,
            VideoUpload(video.name, "video/mp4", video.length()) { video.inputStream() }
        )
        val analysis = (outcome as AnalysisOutcome.Success).result
        val id = requireNotNull(analysis.assessmentId) { "analyze-video returned no assessment_id" }
        val rula = analysis.rula!!.maxScore!!
        val reba = analysis.reba!!.maxScore!!

        try {
            // Detail: the stored result is the analysis result.
            val saved = (assessments.detail(id) as ApiResult.Success).data
            assertEquals(analysis.copy(meta = null), saved.result.copy(meta = null))
            assertEquals(rula, saved.record.rulaMax)
            assertEquals(reba, saved.record.rebaMax)
            val interpretation = interpret(saved.result)

            // History: newest first, so the new assessment leads the first page.
            val history = (assessments.history() as ApiResult.Success).data
            assertEquals(id, history.records.first().id)
            assertEquals(rula, history.records.first().rulaMax)
            assertEquals(reba, history.records.first().rebaMax)
            assertEquals(video.name, history.records.first().fileName)

            // Overview: one more assessment, and it is the latest.
            val overview = (assessments.overview() as ApiResult.Success).data
            assertEquals(before + 1, overview.totalAssessments)
            assertEquals(id, overview.latest.first().id)
            assertTrue(overview.rula.highest!! >= rula)
            assertTrue(overview.reba.highest!! >= reba)

            // Report: generated on the server from the stored values, then retrievable.
            val report = (assessments.generateReport(id) as ApiResult.Success).data
            assertEquals(rula, report.content.rula?.score)
            assertEquals(reba, report.content.reba?.score)
            assertEquals(saved.record.rulaPeakRisk, report.content.rula?.risk)
            assertEquals(saved.record.rebaPeakRisk, report.content.reba?.risk)
            assertEquals(report.content.findings.map { it.title }, interpretation.findings.map { it.title })

            // Interventions: one engine, the same actions on the detail, in history and in the report.
            assertEquals(saved.record.interventions, report.content.interventions)
            assertEquals(saved.record.interventions, history.records.first().interventions)
            assertEquals(saved.record.actionSummary, report.content.actionSummary)
            val primary = requireNotNull(saved.record.priorityAction) { "no priority action for the real video" }
            assertEquals("back", primary.area)
            assertEquals(saved.result.derivedAngles?.trunkMax, primary.evidence?.peakDeg)

            // Product to evaluate and camera-view reliability: the same wherever they are shown.
            assertEquals(saved.record.productRecommendations, report.content.productRecommendations)
            assertEquals(saved.record.productRecommendations, history.records.first().productRecommendations)
            assertEquals("BackEX", saved.record.primaryProduct?.name)
            assertEquals(saved.record.measurementReliability, report.content.measurementReliability)
            assertEquals(saved.record.measurementReliability, history.records.first().measurementReliability)
            println("E2E product: ${saved.record.primaryProduct?.headline}; reliability: ${saved.record.measurementReliability?.status}")
            println("E2E priority action: ${primary.title} (${primary.status}); others: " +
                saved.record.otherActions.joinToString { "${it.title} (${it.status})" })
            assertEquals(report, (assessments.report(id) as ApiResult.Success).data)

            // PDF: downloaded for export and the share sheet.
            val dir = Files.createTempDirectory("vigilex-flow").toFile()
            try {
                val pdf = (assessments.downloadReport(report, dir) as ApiResult.Success).data.file
                assertEquals(report.sizeBytes, pdf.length())
                assertTrue(pdf.readBytes().copyOfRange(0, 4).contentEquals("%PDF".toByteArray()))
                println("E2E assessment $id: RULA $rula (${report.content.rula?.risk}), REBA $reba (${report.content.reba?.risk}), PDF ${pdf.name} ${pdf.length()} bytes")
            } finally {
                dir.deleteRecursively()
            }

            // Another account sees none of it.
            TestAccounts.signIn(server, TestAccounts.B, "Android B")
            val asB = assessments.detail(id) as ApiResult.Error
            assertEquals(ErrorKind.NOT_FOUND, asB.kind)
            assertTrue(assessments.report(id) is ApiResult.Error)
            assertTrue((assessments.history() as ApiResult.Success).data.records.none { it.id == id })
            assertTrue(assessments.delete(id) is ApiResult.Error)
            println("E2E account B cannot see ${alice.email}'s assessment $id")

            // Signing out locally sends no token, so the server refuses.
            AccountSession.signOut()
            assertEquals(ErrorKind.UNAUTHORIZED, (assessments.overview() as ApiResult.Error).kind)

            // A signs back in and still has the assessment.
            TestAccounts.signIn(server, TestAccounts.A)
            assertEquals(id, (assessments.detail(id) as ApiResult.Success).data.record.id)
        } finally {
            TestAccounts.signIn(server, TestAccounts.A)
            assertTrue(assessments.delete(id) is ApiResult.Success)
        }
    }
}
