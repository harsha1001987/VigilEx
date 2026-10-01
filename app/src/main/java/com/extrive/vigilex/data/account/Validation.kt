package com.extrive.vigilex.data.account

const val MIN_PASSWORD_LENGTH = 8

private val EMAIL = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]{2,}$")

/** Returns a message to show under the field, or null when the value is acceptable. */
fun emailError(email: String): String? = when {
    email.isBlank() -> "Enter your email address."
    !EMAIL.matches(email.trim()) -> "Enter a valid email address, such as name@company.com."
    else -> null
}

fun nameError(name: String): String? = if (name.isBlank()) "Enter your name." else null

/** Sign-in only checks presence; the length rule belongs to account creation. */
fun signInPasswordError(password: String): String? = if (password.isEmpty()) "Enter your password." else null

fun newPasswordError(password: String): String? = when {
    password.isEmpty() -> "Choose a password."
    password.length < MIN_PASSWORD_LENGTH -> "Use at least $MIN_PASSWORD_LENGTH characters."
    else -> null
}

fun confirmPasswordError(password: String, confirmation: String): String? = when {
    confirmation.isEmpty() -> "Enter the password again."
    confirmation != password -> "Passwords do not match."
    else -> null
}
