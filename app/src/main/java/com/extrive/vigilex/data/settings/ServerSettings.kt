package com.extrive.vigilex.data.settings

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import androidx.core.content.edit
import com.extrive.vigilex.data.api.ApiConfig
import com.extrive.vigilex.data.api.NetLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The VigilEx server address, editable under Developer options so a physical
 * phone can reach a LAN server. Not a user-facing setting.
 */
object ServerSettings {
    private const val PREFS = "vigilex_settings"
    private const val KEY_SERVER_URL = "server_url"
    private const val KEY_DEVELOPER = "developer_options"

    private lateinit var prefs: SharedPreferences
    private val _serverUrl = MutableStateFlow(ApiConfig.EMULATOR_BASE_URL)
    val serverUrl: StateFlow<String> = _serverUrl.asStateFlow()

    private val _developerOptions = MutableStateFlow(false)
    /** Debug builds always show Developer options; release builds only once unlocked from About. */
    val developerOptions: StateFlow<Boolean> = _developerOptions.asStateFlow()

    val defaultUrl: String get() = ApiConfig.defaultBaseUrl

    fun init(context: Context) {
        if (::prefs.isInitialized) return
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val saved = prefs.getString(KEY_SERVER_URL, null)
        // The emulator alias cannot work on a physical device; a saved copy of it is dropped.
        val unusable = saved == ApiConfig.EMULATOR_BASE_URL && ApiConfig.defaultBaseUrl != ApiConfig.EMULATOR_BASE_URL
        if (unusable) prefs.edit { remove(KEY_SERVER_URL) }
        _serverUrl.value = saved?.takeUnless { unusable } ?: ApiConfig.defaultBaseUrl
        val debuggable = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        _developerOptions.value = debuggable || prefs.getBoolean(KEY_DEVELOPER, false)
        NetLog.enabled = debuggable
    }

    fun setServerUrl(url: String) {
        prefs.edit { putString(KEY_SERVER_URL, url) }
        _serverUrl.value = url
    }

    fun resetServerUrl() {
        prefs.edit { remove(KEY_SERVER_URL) }
        _serverUrl.value = ApiConfig.defaultBaseUrl
    }

    fun enableDeveloperOptions() {
        prefs.edit { putBoolean(KEY_DEVELOPER, true) }
        _developerOptions.value = true
    }
}
