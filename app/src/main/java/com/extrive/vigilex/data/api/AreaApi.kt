package com.extrive.vigilex.data.api

import com.extrive.vigilex.data.model.AreaDto
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface AreaApi {
    @GET("api/v1/areas")
    suspend fun getAreas(@Query("site_id") siteId: String): Response<List<AreaDto>>

    @GET("api/v1/areas/{areaId}")
    suspend fun getArea(@Path("areaId") areaId: String): Response<AreaDto>
}
