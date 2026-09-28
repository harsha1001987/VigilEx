package com.extrive.vigilex.data.repository

import android.content.Context
import android.net.Uri
import com.extrive.vigilex.data.api.ApiResult
import com.extrive.vigilex.data.api.AssessmentApi
import com.extrive.vigilex.data.api.NetworkModule
import com.extrive.vigilex.data.api.safeApiCall
import com.extrive.vigilex.data.model.AssessmentCreateRequest
import com.extrive.vigilex.data.model.AssessmentDto
import com.extrive.vigilex.data.model.ProcessVideoRequest
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.FileOutputStream

class AssessmentRepository(private val api: AssessmentApi = NetworkModule.assessmentApi) {
    suspend fun createAssessment(request: AssessmentCreateRequest): ApiResult<AssessmentDto> =
        safeApiCall { api.createAssessment(request) }

    suspend fun getAssessment(assessmentId: String): ApiResult<AssessmentDto> =
        safeApiCall { api.getAssessment(assessmentId) }

    suspend fun processVideo(assessmentId: String, request: ProcessVideoRequest): ApiResult<AssessmentDto> =
        safeApiCall { api.processVideo(assessmentId, request) }

    suspend fun uploadVideo(
        context: Context,
        assessmentId: String,
        videoUriString: String
    ): ApiResult<AssessmentDto> = safeApiCall {
        val uri = Uri.parse(videoUriString)
        val cacheFile = File(context.cacheDir, "upload_${assessmentId}.mp4")

        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            FileOutputStream(cacheFile).use { outputStream ->
                inputStream.copyTo(outputStream)
            }
        } ?: throw IllegalArgumentException("Cannot open video stream from URI: $videoUriString")

        val mediaType = "video/mp4".toMediaTypeOrNull()
        val requestFile = cacheFile.asRequestBody(mediaType)
        val body = MultipartBody.Part.createFormData("file", cacheFile.name, requestFile)

        try {
            api.uploadVideo(assessmentId, body)
        } finally {
            if (cacheFile.exists()) {
                cacheFile.delete()
            }
        }
    }
}
