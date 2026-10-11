package com.extrive.vigilex.data.api

import com.extrive.vigilex.data.model.AssessmentDetailDto
import com.extrive.vigilex.data.model.AssessmentPageDto
import com.extrive.vigilex.data.model.OverviewDto
import com.extrive.vigilex.data.model.ReportMetadataDto
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Streaming
import retrofit2.http.Url

/**
 * Saved assessments, overview and reports (backend/app/api/routes/assessments.py
 * and overview.py). The analysis itself is POST /analyze-video in AnalysisApi.
 */
interface AssessmentApi {
    @GET("api/v1/assessments")
    suspend fun listAssessments(
        @Query("limit") limit: Int,
        @Query("offset") offset: Int
    ): Response<AssessmentPageDto>

    @GET("api/v1/assessments/{id}")
    suspend fun getAssessment(@Path("id") id: String): Response<AssessmentDetailDto>

    @DELETE("api/v1/assessments/{id}")
    suspend fun deleteAssessment(@Path("id") id: String): Response<Unit>

    @GET("api/v1/overview")
    suspend fun overview(): Response<OverviewDto>

    @POST("api/v1/assessments/{id}/report")
    suspend fun generateReport(@Path("id") id: String): Response<ReportMetadataDto>

    @GET("api/v1/assessments/{id}/report")
    suspend fun getReport(@Path("id") id: String): Response<ReportMetadataDto>

    /** The report's `download_url`, relative to the server address. */
    @Streaming
    @GET
    suspend fun downloadReport(@Url url: String): Response<ResponseBody>
}
