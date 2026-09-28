package com.extrive.vigilex.data.api

import com.extrive.vigilex.data.model.AssessmentCreateRequest
import com.extrive.vigilex.data.model.AssessmentDto
import com.extrive.vigilex.data.model.ProcessVideoRequest
import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path

interface AssessmentApi {
    @POST("api/v1/assessments")
    suspend fun createAssessment(@Body body: AssessmentCreateRequest): Response<AssessmentDto>

    @GET("api/v1/assessments/{assessmentId}")
    suspend fun getAssessment(@Path("assessmentId") assessmentId: String): Response<AssessmentDto>

    @POST("api/v1/assessments/{assessmentId}/process-video")
    suspend fun processVideo(
        @Path("assessmentId") assessmentId: String,
        @Body body: ProcessVideoRequest
    ): Response<AssessmentDto>

    @Multipart
    @POST("api/v1/assessments/{assessmentId}/upload-video")
    suspend fun uploadVideo(
        @Path("assessmentId") assessmentId: String,
        @Part file: MultipartBody.Part
    ): Response<AssessmentDto>
}
