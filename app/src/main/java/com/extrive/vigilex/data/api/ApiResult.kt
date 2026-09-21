package com.extrive.vigilex.data.api

import java.io.IOException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import retrofit2.Response

/** Outcome of a repository call: never throws, always one of these two. */
sealed class ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>()
    data class Error(val message: String) : ApiResult<Nothing>()
}

/**
 * Runs a Retrofit call and converts every outcome (success, HTTP error,
 * network failure, timeout) into an ApiResult so screens never have to catch
 * exceptions themselves.
 */
suspend fun <T> safeApiCall(call: suspend () -> Response<T>): ApiResult<T> {
    return try {
        val response = call()
        if (response.isSuccessful) {
            val body = response.body()
            if (body != null) {
                ApiResult.Success(body)
            } else {
                // 204 No Content or an empty body where one was expected.
                @Suppress("UNCHECKED_CAST")
                ApiResult.Success(Unit as T)
            }
        } else {
            ApiResult.Error(errorMessageFor(response))
        }
    } catch (e: IOException) {
        ApiResult.Error("Could not reach the server. Check your connection and try again.")
    } catch (e: Exception) {
        ApiResult.Error("Something went wrong. Please try again.")
    }
}

/**
 * FastAPI error bodies look like {"detail": "message"} for 400/404, or
 * {"detail": [{"msg": "...", ...}, ...]} for 422 validation errors. This pulls
 * a readable message out of either shape without leaking raw JSON/stack traces.
 */
private fun errorMessageFor(response: Response<*>): String {
    val raw = response.errorBody()?.string()
    val detail = raw?.let { parseDetail(it) }
    return detail ?: fallbackMessageFor(response.code())
}

private fun parseDetail(raw: String): String? = try {
    val element = Json.parseToJsonElement(raw).jsonObject["detail"]
    when (element) {
        null -> null
        is JsonArray -> element.mapNotNull { item ->
            item.jsonObject["msg"]?.jsonPrimitive?.content
        }.joinToString("; ").ifBlank { null }
        else -> element.jsonPrimitive.content
    }
} catch (e: Exception) {
    null
}

private fun fallbackMessageFor(code: Int): String = when (code) {
    400, 422 -> "This request isn't valid. Please check your selections."
    404 -> "The requested item was not found."
    in 500..599 -> "The server ran into a problem. Please try again."
    else -> "Something went wrong (code $code)."
}
