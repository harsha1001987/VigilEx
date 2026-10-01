package com.extrive.vigilex.data.settings

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Normalises a user-typed server address into a Retrofit base URL, or returns
 * null if it is not a valid http(s) address. "192.168.1.20:8000" becomes
 * "http://192.168.1.20:8000/".
 */
fun normalizeServerUrl(input: String): String? {
    val trimmed = input.trim()
    if (trimmed.isEmpty()) return null
    val withScheme = if ("://" in trimmed) trimmed else "http://$trimmed"
    val url = withScheme.toHttpUrlOrNull() ?: return null
    if (url.scheme != "http" && url.scheme != "https") return null
    val text = url.toString()
    return if (text.endsWith("/")) text else "$text/"
}
