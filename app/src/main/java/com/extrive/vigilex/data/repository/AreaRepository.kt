package com.extrive.vigilex.data.repository

import com.extrive.vigilex.data.api.ApiResult
import com.extrive.vigilex.data.api.AreaApi
import com.extrive.vigilex.data.api.NetworkModule
import com.extrive.vigilex.data.api.safeApiCall
import com.extrive.vigilex.data.model.AreaDto
import com.extrive.vigilex.data.settings.ServerSettings

class AreaRepository(private val apiProvider: (String) -> AreaApi = NetworkModule::areaApi) {
    private val baseUrl: String get() = ServerSettings.serverUrl.value

    suspend fun getAreas(siteId: String): ApiResult<List<AreaDto>> = safeApiCall { apiProvider(baseUrl).getAreas(siteId) }

    suspend fun getArea(areaId: String): ApiResult<AreaDto> = safeApiCall { apiProvider(baseUrl).getArea(areaId) }
}
