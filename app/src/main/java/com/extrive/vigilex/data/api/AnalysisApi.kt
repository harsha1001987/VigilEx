package com.extrive.vigilex.data.api

import okhttp3.MultipartBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

/**
 * Stateless CV analysis API (backend/app/api/routes/cv_analysis.py).
 * Bodies are returned raw so the exact server JSON can be stored locally.
 */
interface AnalysisApi {
    @Multipart
    @POST(ApiConfig.ANALYZE_VIDEO_PATH)
    suspend fun analyzeVideo(@Part file: MultipartBody.Part): Response<ResponseBody>

    @GET("api/v1/health")
    suspend fun health(): Response<ResponseBody>
}
