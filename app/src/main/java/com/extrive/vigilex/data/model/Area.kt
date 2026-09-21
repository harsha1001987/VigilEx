package com.extrive.vigilex.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Matches app/schemas/area.py AreaRead on the backend. */
@Serializable
data class AreaDto(
    val id: String,
    @SerialName("site_id") val siteId: String,
    val name: String
)
