package com.extrive.vigilex.data.session

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Holds the current user's name in memory for the duration of the app session.
 * Set once during Sign In and read by any screen (Home, Profile, etc.) that
 * needs to display the current user.
 */
object UserSession {
    var userName by mutableStateOf("")
}
