package com.extrive.vigilex.data.api

import android.os.Build

/**
 * Where the development backend lives. The default depends on where the app runs:
 *
 * - Android Emulator: 10.0.2.2 is the emulator's alias for the computer's
 *   localhost, so http://10.0.2.2:8000/ reaches FastAPI on the computer.
 * - Physical phone over USB: 10.0.2.2 does not exist on a real device. Debug
 *   builds run `adb reverse tcp:8000 tcp:8000` (see app/build.gradle.kts), which
 *   makes http://127.0.0.1:8000/ on the phone forward to port 8000 on the computer.
 * - Physical phone over Wi-Fi only: set the computer's LAN address, e.g.
 *   http://192.168.1.20:8000/, in Settings → Developer options. The phone and
 *   computer must be on the same network and port 8000 must be allowed through
 *   the Windows firewall.
 */
object ApiConfig {
    const val PORT = 8000
    const val EMULATOR_BASE_URL = "http://10.0.2.2:$PORT/"
    const val USB_REVERSE_BASE_URL = "http://127.0.0.1:$PORT/"
    const val ANALYZE_VIDEO_PATH = "api/v1/analyze-video"
    const val VIDEO_PART_NAME = "file"

    /** Default analysis server for this device, used until one is set in Developer options. */
    val defaultBaseUrl: String by lazy { if (isEmulator()) EMULATOR_BASE_URL else USB_REVERSE_BASE_URL }

    private fun isEmulator(): Boolean =
        Build.FINGERPRINT.startsWith("generic") ||
            Build.FINGERPRINT.contains("emulator") ||
            Build.HARDWARE in setOf("goldfish", "ranchu") ||
            Build.PRODUCT.startsWith("sdk") ||
            Build.PRODUCT.contains("emulator") ||
            Build.MODEL.contains("Android SDK built for")
}
