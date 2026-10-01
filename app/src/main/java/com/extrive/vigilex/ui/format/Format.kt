package com.extrive.vigilex.ui.format

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

const val EMPTY_VALUE = "—"

fun formatScore(score: Int?): String = score?.toString()?.padStart(2, '0') ?: EMPTY_VALUE

fun formatScale(max: Int): String = max.toString().padStart(2, '0')

fun formatDegrees(value: Double?): String =
    value?.let { String.format(Locale.US, "%.1f°", it) } ?: EMPTY_VALUE

fun formatDegreesNumber(value: Double?): String =
    value?.let { String.format(Locale.US, "%.1f", it) } ?: EMPTY_VALUE

fun formatPercent(fraction: Double?): String =
    fraction?.let { "${(it * 100).roundToInt()}%" } ?: EMPTY_VALUE

fun formatSeconds(seconds: Double?): String = when {
    seconds == null -> EMPTY_VALUE
    seconds < 60 -> String.format(Locale.US, "%.1f s", seconds)
    else -> {
        val total = seconds.roundToInt()
        "%d:%02d".format(Locale.US, total / 60, total % 60)
    }
}

fun formatElapsed(millis: Long): String {
    val total = (millis / 1000).toInt()
    return "%d:%02d".format(Locale.US, total / 60, total % 60)
}

fun formatBytes(bytes: Long?): String = when {
    bytes == null || bytes < 0 -> EMPTY_VALUE
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> String.format(Locale.US, "%.0f KB", bytes / 1024.0)
    bytes < 1024L * 1024 * 1024 -> String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024))
    else -> String.format(Locale.US, "%.2f GB", bytes / (1024.0 * 1024 * 1024))
}

fun formatResolution(width: Int?, height: Int?): String =
    if (width != null && height != null && width > 0 && height > 0) "$width × $height" else EMPTY_VALUE

fun formatFps(fps: Double?): String =
    fps?.let { if (it % 1.0 == 0.0) "${it.toInt()} fps" else String.format(Locale.US, "%.2f fps", it) }
        ?: EMPTY_VALUE

fun formatDate(millis: Long): String =
    SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(millis))

fun formatDateTime(millis: Long): String =
    SimpleDateFormat("d MMM yyyy · HH:mm", Locale.getDefault()).format(Date(millis))

fun formatShortDate(millis: Long): String =
    SimpleDateFormat("d MMM", Locale.getDefault()).format(Date(millis))
