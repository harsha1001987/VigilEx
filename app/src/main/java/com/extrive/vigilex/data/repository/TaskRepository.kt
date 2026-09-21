package com.extrive.vigilex.data.repository

import com.extrive.vigilex.data.api.ApiResult
import com.extrive.vigilex.data.api.NetworkModule
import com.extrive.vigilex.data.api.TaskApi
import com.extrive.vigilex.data.api.safeApiCall
import com.extrive.vigilex.data.model.TaskDto

class TaskRepository(private val api: TaskApi = NetworkModule.taskApi) {
    suspend fun getTasks(areaId: String): ApiResult<List<TaskDto>> = safeApiCall { api.getTasks(areaId) }

    suspend fun getTask(taskId: String): ApiResult<TaskDto> = safeApiCall { api.getTask(taskId) }
}
