package com.extrive.vigilex.data.api

import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import retrofit2.Response

/** What went wrong, so a screen can say something more useful than "could not connect". */
enum class ErrorKind {
    /** No connection: server not running, wrong address, network blocked. */
    UNREACHABLE,
    /** Connected, but no answer arrived in time. */
    TIMEOUT,
    /** 404: the item does not exist (or belongs to someone else). */
    NOT_FOUND,
    /** 400/422: the server refused the request. */
    REJECTED,
    /** 401: not signed in, wrong credentials, or the session has expired. */
    UNAUTHORIZED,
    /** 409: conflicts with existing data, e.g. an email that already has an account. */
    CONFLICT,
    /** 429: too many attempts; try again later. */
    RATE_LIMITED,
    /** 5xx: the server failed while handling the request. */
    SERVER,
    /** The server answered, but not with what this app expects. */
    BAD_RESPONSE,
    OTHER
}

/** Outcome of a repository call: never throws, always one of these two. */
sealed class ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>()

    /** [message] is safe to show; [detail] is the server's own message when it sent one. */
    data class Error(
        val message: String,
        val kind: ErrorKind = ErrorKind.OTHER,
        val detail: String? = null
    ) : ApiResult<Nothing>()
}

/**
 * Runs a Retrofit call and converts every outcome (success, HTTP error,
 * network failure, timeout, unreadable body) into an ApiResult so screens
 * never have to catch exceptions themselves. Cancellation still propagates.
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
            httpError(response.code(), response.errorBody()?.string())
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        exceptionError(e)
    }
}

internal fun exceptionError(e: Exception): ApiResult.Error = when (e) {
    is SocketTimeoutException -> ApiResult.Error(
        "The VigilEx server did not answer in time. Try again.",
        ErrorKind.TIMEOUT
    )
    is ConnectException, is UnknownHostException, is NoRouteToHostException -> ApiResult.Error(
        "VigilEx couldn't reach the server. Check that it is running and that this device can reach it.",
        ErrorKind.UNREACHABLE
    )
    is SerializationException -> ApiResult.Error(
        "The server sent a response VigilEx could not read.",
        ErrorKind.BAD_RESPONSE
    )
    is IOException -> ApiResult.Error(
        "The connection to the VigilEx server was interrupted. Try again.",
        ErrorKind.UNREACHABLE
    )
    else -> ApiResult.Error("Something went wrong. Please try again.", ErrorKind.OTHER)
}

/**
 * FastAPI error bodies look like {"detail": "message"} for 400/404/500, or
 * {"detail": [{"msg": "...", ...}, ...]} for 422 validation errors. The
 * backend's messages are written for people and never contain paths or
 * stack traces, so they are shown when present.
 */
internal fun httpError(code: Int, rawBody: String?): ApiResult.Error {
    val detail = rawBody?.let(::parseDetail)
    return when (code) {
        401 -> ApiResult.Error(
            detail ?: "Your session has expired. Sign in again.",
            ErrorKind.UNAUTHORIZED,
            detail
        )
        404 -> ApiResult.Error(detail ?: "The requested item was not found.", ErrorKind.NOT_FOUND, detail)
        409 -> ApiResult.Error(detail ?: "This conflicts with existing data.", ErrorKind.CONFLICT, detail)
        429 -> ApiResult.Error(
            detail ?: "Too many attempts. Wait a few minutes and try again.",
            ErrorKind.RATE_LIMITED,
            detail
        )
        // A proxy or another service on this address, not the VigilEx API.
        405 -> ApiResult.Error("The server at this address is not the VigilEx API.", ErrorKind.UNREACHABLE, detail)
        400, 422 -> ApiResult.Error(detail ?: "This request isn't valid.", ErrorKind.REJECTED, detail)
        in 500..599 -> ApiResult.Error(detail ?: "The server ran into a problem. Please try again.", ErrorKind.SERVER, detail)
        else -> ApiResult.Error(detail ?: "Something went wrong (code $code).", ErrorKind.OTHER, detail)
    }
}

private fun parseDetail(raw: String): String? = try {
    when (val element = Json.parseToJsonElement(raw).jsonObject["detail"]) {
        null -> null
        is JsonArray -> element.mapNotNull { item ->
            item.jsonObject["msg"]?.jsonPrimitive?.content
        }.joinToString("; ").ifBlank { null }
        else -> element.jsonPrimitive.content
    }
} catch (e: Exception) {
    null
}
