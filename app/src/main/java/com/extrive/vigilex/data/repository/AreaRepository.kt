package com.extrive.vigilex.data.repository

import com.extrive.vigilex.data.api.ApiResult
import com.extrive.vigilex.data.api.AreaApi
import com.extrive.vigilex.data.api.NetworkModule
import com.extrive.vigilex.data.api.safeApiCall
import com.extrive.vigilex.data.model.AreaDto

class AreaRepository(private val api: AreaApi = NetworkModule.areaApi) {
    suspend fun getAreas(siteId: String): ApiResult<List<AreaDto>> = safeApiCall { api.getAreas(siteId) }

    suspend fun getArea(areaId: String): ApiResult<AreaDto> = safeApiCall { api.getArea(areaId) }
}
