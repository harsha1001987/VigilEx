package com.extrive.vigilex.ui.state

/** Simple loading/success/error state for a screen backed by one API call. */
sealed class UiState<out T> {
    object Loading : UiState<Nothing>()
    data class Success<T>(val data: T) : UiState<T>()
    data class Error(val message: String) : UiState<Nothing>()
}
