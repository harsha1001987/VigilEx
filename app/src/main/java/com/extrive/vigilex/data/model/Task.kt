package com.extrive.vigilex.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Matches app/schemas/task.py TaskRead on the backend. */
@Serializable
data class TaskDto(
    val id: String,
    @SerialName("area_id") val areaId: String,
    val name: String,
    val description: String? = null
)
