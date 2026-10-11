package com.extrive.vigilex.data.api

import com.extrive.vigilex.data.account.AccountSession
import com.extrive.vigilex.data.account.SessionTokens
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Attaches the session's access token to every request to the server that
 * issued it, and ends the session when that server refuses the token (401).
 * Sign-in, registration and health checks are sent without a token.
 */
class AuthInterceptor(private val session: SessionTokens = AccountSession) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (isPublic(request.url)) return chain.proceed(request)
        val token = session.tokenFor(request.url) ?: return chain.proceed(request)
        val response = chain.proceed(request.newBuilder().header(HEADER, "Bearer $token").build())
        if (response.code == 401) session.rejected(token)
        return response
    }

    companion object {
        const val HEADER = "Authorization"
        private val PUBLIC = listOf("api/v1/auth/login", "api/v1/auth/register", "api/v1/health")

        fun isPublic(url: HttpUrl): Boolean {
            val path = url.encodedPath.trimStart('/')
            return PUBLIC.any { path == it || path.endsWith("/$it") || path.startsWith("$it/") }
        }
    }
}
