package com.extrive.vigilex.data.api

import com.extrive.vigilex.data.model.SiteDto
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path

interface SiteApi {
    @GET("api/v1/sites")
    suspend fun getSites(): Response<List<SiteDto>>

    @GET("api/v1/sites/{siteId}")
    suspend fun getSite(@Path("siteId") siteId: String): Response<SiteDto>
}
