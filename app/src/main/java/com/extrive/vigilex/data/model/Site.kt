package com.extrive.vigilex.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Matches app/schemas/site.py SiteRead on the backend. */
@Serializable
data class SiteDto(
    val id: String,
    @SerialName("organization_id") val organizationId: String,
    val name: String,
    val location: String? = null
)
