package com.extrive.vigilex.data.repository

import com.extrive.vigilex.data.api.ApiResult
import com.extrive.vigilex.data.api.NetworkModule
import com.extrive.vigilex.data.api.SiteApi
import com.extrive.vigilex.data.api.safeApiCall
import com.extrive.vigilex.data.model.SiteDto
import com.extrive.vigilex.data.settings.ServerSettings

class SiteRepository(private val apiProvider: (String) -> SiteApi = NetworkModule::siteApi) {
    private val baseUrl: String get() = ServerSettings.serverUrl.value

    suspend fun getSites(): ApiResult<List<SiteDto>> = safeApiCall { apiProvider(baseUrl).getSites() }

    suspend fun getSite(siteId: String): ApiResult<SiteDto> = safeApiCall { apiProvider(baseUrl).getSite(siteId) }
}
