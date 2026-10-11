package com.extrive.vigilex.data.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

/** Accounts (backend/app/api/routes/auth.py). */
interface AuthApi {
    @POST("api/v1/auth/register")
    suspend fun register(@Body body: RegisterRequest): Response<TokenResponse>

    @POST("api/v1/auth/login")
    suspend fun login(@Body body: LoginRequest): Response<TokenResponse>

    @GET("api/v1/auth/me")
    suspend fun me(): Response<UserDto>

    @POST("api/v1/auth/logout")
    suspend fun logout(): Response<Unit>
}

@Serializable
data class RegisterRequest(val name: String, val email: String, val password: String)

@Serializable
data class LoginRequest(val email: String, val password: String)

@Serializable
data class UserDto(
    val id: String,
    val name: String,
    val email: String,
    @SerialName("organization_id") val organizationId: String? = null
)

@Serializable
data class TokenResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("token_type") val tokenType: String = "bearer",
    @SerialName("expires_in") val expiresIn: Long,
    val user: UserDto
)
