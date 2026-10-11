package com.extrive.vigilex.data.account

import com.extrive.vigilex.data.api.ApiResult
import com.extrive.vigilex.data.api.ErrorKind

/**
 * Fixed accounts for the live-server integration tests: created the first
 * time, signed into afterwards, so repeated runs do not pile up accounts.
 * Test-only credentials for a development server; never real ones.
 */
object TestAccounts {
    const val PASSWORD = "android-integration-test-password"
    const val A = "android-it-a@example.com"
    const val B = "android-it-b@example.com"

    /** Signs [AccountSession] into [email] on [server], creating the account if needed. */
    suspend fun signIn(server: String, email: String, name: String = email.substringBefore('@')): Account {
        AccountSession.init(InMemorySessionStore())
        val auth = AuthRepository(baseUrl = { server })
        // Sign in first: registration attempts are rate limited, so only the first run creates the account.
        return when (val login = auth.login(email, PASSWORD)) {
            is ApiResult.Success -> login.data
            is ApiResult.Error -> {
                check(login.kind == ErrorKind.UNAUTHORIZED) { "Could not sign in as $email: ${login.message}" }
                when (val created = auth.register(name, email, PASSWORD)) {
                    is ApiResult.Success -> created.data
                    is ApiResult.Error -> error("Could not create $email: ${created.message}")
                }
            }
        }
    }
}
