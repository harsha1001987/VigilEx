package com.extrive.vigilex.data.api

import com.extrive.vigilex.data.model.AssessmentCreateRequest
import com.extrive.vigilex.data.model.AssessmentDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface AssessmentApi {
    @POST("api/v1/assessments")
    suspend fun createAssessment(@Body body: AssessmentCreateRequest): Response<AssessmentDto>

    @GET("api/v1/assessments/{assessmentId}")
    suspend fun getAssessment(@Path("assessmentId") assessmentId: String): Response<AssessmentDto>
}
