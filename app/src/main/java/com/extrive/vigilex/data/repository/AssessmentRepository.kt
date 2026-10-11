package com.extrive.vigilex.data.repository

import com.extrive.vigilex.data.api.ApiResult
import com.extrive.vigilex.data.api.AssessmentApi
import com.extrive.vigilex.data.api.ErrorKind
import com.extrive.vigilex.data.api.NetworkModule
import com.extrive.vigilex.data.api.exceptionError
import com.extrive.vigilex.data.api.httpError
import com.extrive.vigilex.data.api.safeApiCall
import com.extrive.vigilex.data.model.AnalysisJson
import com.extrive.vigilex.data.model.AnalysisResult
import com.extrive.vigilex.data.model.AssessmentRecord
import com.extrive.vigilex.data.model.AssessmentSummaryDto
import com.extrive.vigilex.data.model.OverviewDto
import com.extrive.vigilex.data.model.ReportMetadataDto
import com.extrive.vigilex.data.settings.ServerSettings
import com.extrive.vigilex.data.store.AssessmentStore
import java.io.File
import java.time.OffsetDateTime
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** One page of history, newest first. */
data class HistoryPage(val records: List<AssessmentRecord>, val total: Int, val offset: Int) {
    val hasMore: Boolean get() = offset + records.size < total
}

/** A saved assessment: its history row and the stored analysis, exactly as the backend holds them. */
data class SavedAssessment(val record: AssessmentRecord, val result: AnalysisResult, val reportAvailable: Boolean)

/** The server-generated PDF, downloaded for export and the share sheet. */
data class ReportFile(val metadata: ReportMetadataDto, val file: File)

/**
 * Saved assessments, overview and reports from the VigilEx backend, which is
 * their source of truth. Every score and risk label is read from the backend;
 * nothing is recomputed here.
 */
class AssessmentRepository(
    private val baseUrl: () -> String = { ServerSettings.serverUrl.value },
    private val apiFor: (String) -> AssessmentApi = NetworkModule::assessmentApi,
    private val videoUriFor: (String) -> String? = AssessmentStore::videoUri
) {
    private val api: AssessmentApi get() = apiFor(baseUrl())

    suspend fun history(offset: Int = 0, limit: Int = PAGE_SIZE): ApiResult<HistoryPage> =
        safeApiCall { api.listAssessments(limit = limit, offset = offset) }.map { page ->
            HistoryPage(page.items.map(::toRecord), page.total, page.offset)
        }

    /** Every completed assessment, newest first, for history-wide aggregates. */
    suspend fun allCompleted(): ApiResult<List<AssessmentRecord>> {
        val records = mutableListOf<AssessmentRecord>()
        while (true) {
            when (val page = history(offset = records.size, limit = MAX_PAGE_SIZE)) {
                is ApiResult.Error -> return page
                is ApiResult.Success -> {
                    records += page.data.records
                    if (!page.data.hasMore || page.data.records.isEmpty()) return ApiResult.Success(records)
                }
            }
        }
    }

    suspend fun detail(id: String): ApiResult<SavedAssessment> =
        when (val response = safeApiCall { api.getAssessment(id) }) {
            is ApiResult.Error -> response
            is ApiResult.Success -> {
                val dto = response.data
                val analysis = dto.analysis
                if (dto.status != "completed" || analysis == null) {
                    // Failed or still running analyses are not assessments the app can show.
                    ApiResult.Error("This assessment has no completed result.", ErrorKind.NOT_FOUND)
                } else {
                    try {
                        val result = AnalysisJson.decodeFromJsonElement(AnalysisResult.serializer(), analysis)
                        ApiResult.Success(SavedAssessment(toRecord(dto.summary), result, dto.reportAvailable))
                    } catch (e: Exception) {
                        ApiResult.Error("The server sent an assessment VigilEx could not read.", ErrorKind.BAD_RESPONSE)
                    }
                }
            }
        }

    suspend fun delete(id: String): ApiResult<Unit> =
        safeApiCall { api.deleteAssessment(id) }.map { }

    suspend fun overview(): ApiResult<OverviewDto> = safeApiCall { api.overview() }

    /** Generates (or regenerates) the report from the stored assessment on the server. */
    suspend fun generateReport(id: String): ApiResult<ReportMetadataDto> = safeApiCall { api.generateReport(id) }

    /** The report already generated for this assessment; NOT_FOUND when there is none yet. */
    suspend fun report(id: String): ApiResult<ReportMetadataDto> = safeApiCall { api.getReport(id) }

    /**
     * Downloads the generated PDF into [directory] under the server's file
     * name, replacing older reports there. Only a body that is really a PDF
     * is kept.
     */
    suspend fun downloadReport(metadata: ReportMetadataDto, directory: File): ApiResult<ReportFile> =
        withContext(Dispatchers.IO) {
            val target = File(directory.apply { mkdirs() }, safeFileName(metadata.fileName))
            val partial = File(directory, "${target.name}.part")
            try {
                val response = api.downloadReport(metadata.downloadUrl.trimStart('/'))
                if (!response.isSuccessful) {
                    return@withContext httpError(response.code(), response.errorBody()?.string())
                }
                val body = response.body() ?: return@withContext badPdf()
                if (body.contentType()?.subtype != "pdf") return@withContext badPdf()
                body.byteStream().use { input -> partial.outputStream().use { input.copyTo(it) } }
                if (!partial.startsWithPdfHeader()) {
                    partial.delete()
                    return@withContext badPdf()
                }
                directory.listFiles()?.filter { it != partial }?.forEach { it.delete() } // Keep only this report.
                if (!partial.renameTo(target)) return@withContext badPdf()
                ApiResult.Success(ReportFile(metadata, target))
            } catch (e: CancellationException) {
                partial.delete()
                throw e
            } catch (e: Exception) {
                partial.delete()
                exceptionError(e)
            }
        }

    /** A backend row with this device's video for it, if the video came from here. */
    fun toRecord(summary: AssessmentSummaryDto): AssessmentRecord = summary.toRecord(videoUriFor(summary.id))

    companion object {
        const val PAGE_SIZE = 20
        /** The backend's largest page (MAX_PAGE in routes/assessments.py). */
        const val MAX_PAGE_SIZE = 100
    }
}

/** A backend history row in the shape the app's lists and aggregates already use. */
fun AssessmentSummaryDto.toRecord(videoUri: String? = null) = AssessmentRecord(
    id = id,
    createdAtMillis = parseInstantMillis(createdAt),
    fileName = videoName ?: "Video assessment",
    videoUri = videoUri,
    durationSec = durationSec,
    rulaMax = rula?.score,
    rulaPeakRisk = rula?.risk,
    rebaMax = reba?.score,
    rebaPeakRisk = reba?.risk,
    derivedAngles = derivedAngles,
    rulaDistribution = rula?.riskDistribution.orEmpty(),
    rebaDistribution = reba?.riskDistribution.orEmpty(),
    methodologyVersion = methodologyVersion,
    actionSummary = actionSummary,
    interventions = interventions,
    productRecommendations = productRecommendations,
    measurementReliability = measurementReliability
)

/** Backend timestamps are ISO-8601 with an offset, e.g. 2026-10-08T13:48:47.611652+05:30. */
fun parseInstantMillis(iso: String): Long = OffsetDateTime.parse(iso).toInstant().toEpochMilli()

private inline fun <T, R> ApiResult<T>.map(transform: (T) -> R): ApiResult<R> = when (this) {
    is ApiResult.Success -> ApiResult.Success(transform(data))
    is ApiResult.Error -> this
}

private fun badPdf() = ApiResult.Error("The server did not send a PDF report.", ErrorKind.BAD_RESPONSE)

private fun File.startsWithPdfHeader(): Boolean =
    length() > 4 && inputStream().use { input -> ByteArray(4).also { input.read(it) } }.contentEquals("%PDF".toByteArray())

/** The server's file name, reduced to characters every file system and share target accepts. */
internal fun safeFileName(name: String): String =
    name.substringAfterLast('/').substringAfterLast('\\')
        .replace(Regex("[^A-Za-z0-9._-]"), "_")
        .takeIf { it.endsWith(".pdf") && it.length > 4 } ?: "VigilEx-ergonomic-assessment.pdf"
