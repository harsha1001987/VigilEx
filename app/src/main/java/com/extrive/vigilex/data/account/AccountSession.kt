package com.extrive.vigilex.data.account

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class Account(val name: String, val email: String)

/**
 * The signed-in account, if any. The VigilEx backend has no authentication
 * endpoints yet, so nothing can sign in and [current] stays null; the app
 * works fully without an account, keeping assessments on this device.
 */
object AccountSession {
    /** False until the backend supports accounts; auth screens say so instead of faking success. */
    const val AUTH_AVAILABLE = false

    private val _current = MutableStateFlow<Account?>(null)
    val current: StateFlow<Account?> = _current.asStateFlow()

    fun signOut() {
        _current.value = null
    }
}
