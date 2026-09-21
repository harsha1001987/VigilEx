package com.extrive.vigilex.data.repository

import com.extrive.vigilex.data.api.ApiResult
import com.extrive.vigilex.data.api.AssessmentApi
import com.extrive.vigilex.data.api.NetworkModule
import com.extrive.vigilex.data.api.safeApiCall
import com.extrive.vigilex.data.model.AssessmentCreateRequest
import com.extrive.vigilex.data.model.AssessmentDto

class AssessmentRepository(private val api: AssessmentApi = NetworkModule.assessmentApi) {
    suspend fun createAssessment(request: AssessmentCreateRequest): ApiResult<AssessmentDto> =
        safeApiCall { api.createAssessment(request) }

    suspend fun getAssessment(assessmentId: String): ApiResult<AssessmentDto> =
        safeApiCall { api.getAssessment(assessmentId) }
}
