package com.extrive.vigilex.data.api

import com.extrive.vigilex.data.model.TaskDto
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface TaskApi {
    @GET("api/v1/tasks")
    suspend fun getTasks(@Query("area_id") areaId: String): Response<List<TaskDto>>

    @GET("api/v1/tasks/{taskId}")
    suspend fun getTask(@Path("taskId") taskId: String): Response<TaskDto>
}
