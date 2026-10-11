package com.extrive.vigilex.data.account

import com.extrive.vigilex.data.api.ApiResult
import com.extrive.vigilex.data.api.AuthApi
import com.extrive.vigilex.data.api.LoginRequest
import com.extrive.vigilex.data.api.NetworkModule
import com.extrive.vigilex.data.api.RegisterRequest
import com.extrive.vigilex.data.api.TokenResponse
import com.extrive.vigilex.data.api.UserDto
import com.extrive.vigilex.data.api.safeApiCall
import com.extrive.vigilex.data.settings.ServerSettings

/**
 * Creates accounts and signs in and out against the configured VigilEx
 * server. A successful sign-in starts an [AccountSession] bound to that
 * server; nothing here stores the password.
 */
class AuthRepository(
    private val baseUrl: () -> String = { ServerSettings.serverUrl.value },
    private val apiFor: (String) -> AuthApi = NetworkModule::authApi,
    private val session: AccountSession = AccountSession
) {
    suspend fun register(name: String, email: String, password: String): ApiResult<Account> {
        val server = baseUrl()
        return start(server, safeApiCall { apiFor(server).register(RegisterRequest(name.trim(), email.trim(), password)) })
    }

    suspend fun login(email: String, password: String): ApiResult<Account> {
        val server = baseUrl()
        return start(server, safeApiCall { apiFor(server).login(LoginRequest(email.trim(), password)) })
    }

    /**
     * Confirms the restored session with the server and refreshes the account.
     * A 401 ends the session (AuthInterceptor); a network failure keeps it, so
     * the app still opens offline and retries on the next request.
     */
    suspend fun refreshAccount(): ApiResult<Account> {
        val server = session.serverUrl ?: return ApiResult.Error("Not signed in.")
        return when (val me = safeApiCall { apiFor(server).me() }) {
            is ApiResult.Error -> me
            is ApiResult.Success -> me.data.toAccount().also(session::updateAccount).let { ApiResult.Success(it) }
        }
    }

    /**
     * Signs out on the server (revoking the token there) and always on this
     * device, even when the server cannot be reached.
     */
    suspend fun signOut() {
        val server = session.serverUrl
        if (server != null) safeApiCall { apiFor(server).logout() }
        session.signOut(SignOutReason.USER)
    }

    private fun start(server: String, result: ApiResult<TokenResponse>): ApiResult<Account> = when (result) {
        is ApiResult.Error -> result
        is ApiResult.Success -> {
            val body = result.data
            val account = body.user.toAccount()
            session.signIn(
                StoredSession(
                    token = body.accessToken,
                    // From the device clock, so a skewed server clock cannot keep an expired token alive.
                    expiresAtMillis = session.clock() + body.expiresIn * 1000,
                    serverUrl = server,
                    account = account
                )
            )
            ApiResult.Success(account)
        }
    }
}

internal fun UserDto.toAccount() = Account(id = id, name = name, email = email)
