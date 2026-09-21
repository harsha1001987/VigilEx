package com.extrive.vigilex.data.api

/**
 * Single place that defines where the backend lives during development.
 *
 * - Android Emulator: 10.0.2.2 is the emulator's alias for your computer's
 *   localhost, so this reaches a FastAPI server running on your machine.
 * - Physical phone: replace with your computer's LAN IP (e.g.
 *   "http://192.168.1.23:8000/"), make sure the phone and computer are on the
 *   same Wi-Fi network, allow the port through Windows Firewall, and add that
 *   IP to res/xml/network_security_config.xml.
 */
object ApiConfig {
    const val BASE_URL = "http://10.0.2.2:8000/"
}
