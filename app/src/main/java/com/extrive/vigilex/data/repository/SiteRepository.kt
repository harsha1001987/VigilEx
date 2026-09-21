package com.extrive.vigilex.data.repository

import com.extrive.vigilex.data.api.ApiResult
import com.extrive.vigilex.data.api.NetworkModule
import com.extrive.vigilex.data.api.SiteApi
import com.extrive.vigilex.data.api.safeApiCall
import com.extrive.vigilex.data.model.SiteDto

class SiteRepository(private val api: SiteApi = NetworkModule.siteApi) {
    suspend fun getSites(): ApiResult<List<SiteDto>> = safeApiCall { api.getSites() }

    suspend fun getSite(siteId: String): ApiResult<SiteDto> = safeApiCall { api.getSite(siteId) }
}
