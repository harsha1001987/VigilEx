package com.extrive.vigilex.data.api

/**
 * Single place that defines where the backend lives during development.
 *
 * - Android Emulator: 10.0.2.2 is the emulator's alias for your computer's
 *   localhost (127.0.0.1), so this reaches a FastAPI server running on your machine.
 * - Physical phone: replace with your computer's LAN IP (e.g.
 *   "http://172.20.8.180:8000/"), make sure the phone and computer are on the
 *   same Wi-Fi network, allow the port through Windows Firewall, and add that
 *   IP to res/xml/network_security_config.xml.
 */
object ApiConfig {
    // 10.0.2.2 points to host machine's localhost (127.0.0.1) in Android Emulator.
    // Use "http://172.20.8.180:8000/" for physical phone on Wi-Fi.
    const val BASE_URL = "http://10.0.2.2:8000/"
}
