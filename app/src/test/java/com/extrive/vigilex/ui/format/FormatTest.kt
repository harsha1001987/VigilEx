package com.extrive.vigilex.ui.format

import com.extrive.vigilex.data.settings.normalizeServerUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FormatTest {

    @Test
    fun `scores are two digits and missing values use a dash`() {
        assertEquals("04", formatScore(4))
        assertEquals("12", formatScore(12))
        assertEquals(EMPTY_VALUE, formatScore(null))
        assertEquals("07", formatScale(7))
        assertEquals("15", formatScale(15))
    }

    @Test
    fun `angles, percentages and durations`() {
        assertEquals("78.2°", formatDegrees(78.23962962962962))
        assertEquals("107.2", formatDegreesNumber(107.15))
        assertEquals(EMPTY_VALUE, formatDegrees(null))
        assertEquals("100%", formatPercent(1.0))
        assertEquals("5.8 s", formatSeconds(5.76))
        assertEquals("1:05", formatSeconds(65.2))
        assertEquals("0:42", formatElapsed(42_300))
    }

    @Test
    fun `sizes and resolution`() {
        assertEquals("512 B", formatBytes(512))
        assertEquals("30.0 MB", formatBytes(30L * 1024 * 1024))
        assertEquals(EMPTY_VALUE, formatBytes(null))
        assertEquals("1280 × 720", formatResolution(1280, 720))
        assertEquals(EMPTY_VALUE, formatResolution(null, 720))
        assertEquals("25 fps", formatFps(25.0))
        assertEquals("29.97 fps", formatFps(29.97))
    }

    @Test
    fun `server addresses are normalised into base URLs`() {
        assertEquals("http://192.168.1.20:8000/", normalizeServerUrl("192.168.1.20:8000"))
        assertEquals("http://10.0.2.2:8000/", normalizeServerUrl(" http://10.0.2.2:8000 "))
        assertEquals("https://vigilex.example/api/", normalizeServerUrl("https://vigilex.example/api"))
        assertNull(normalizeServerUrl(""))
        assertNull(normalizeServerUrl("ftp://host"))
        assertNull(normalizeServerUrl("not a url"))
    }
}
