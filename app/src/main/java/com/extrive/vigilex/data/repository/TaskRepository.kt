package com.extrive.vigilex.data.repository

import com.extrive.vigilex.data.api.ApiResult
import com.extrive.vigilex.data.api.NetworkModule
import com.extrive.vigilex.data.api.TaskApi
import com.extrive.vigilex.data.api.safeApiCall
import com.extrive.vigilex.data.model.TaskDto
import com.extrive.vigilex.data.settings.ServerSettings

class TaskRepository(private val apiProvider: (String) -> TaskApi = NetworkModule::taskApi) {
    private val baseUrl: String get() = ServerSettings.serverUrl.value

    suspend fun getTasks(areaId: String): ApiResult<List<TaskDto>> = safeApiCall { apiProvider(baseUrl).getTasks(areaId) }

    suspend fun getTask(taskId: String): ApiResult<TaskDto> = safeApiCall { apiProvider(baseUrl).getTask(taskId) }
}
