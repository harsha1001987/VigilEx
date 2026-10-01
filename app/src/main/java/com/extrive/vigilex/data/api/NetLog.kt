package com.extrive.vigilex.data.api

import android.util.Log

/**
 * Development log for the analysis request (tag "VigilExNet", visible in
 * Logcat). Enabled only in debuggable builds; never logs video contents.
 */
object NetLog {
    private const val TAG = "VigilExNet"

    @Volatile var enabled = true

    fun i(message: String) {
        if (!enabled) return
        try {
            Log.i(TAG, message)
        } catch (e: RuntimeException) {
            // JVM unit tests have no android.util.Log.
            println("$TAG: $message")
        }
    }
}
