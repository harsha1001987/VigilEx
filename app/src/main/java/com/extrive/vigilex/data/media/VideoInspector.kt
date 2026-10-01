package com.extrive.vigilex.data.media

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Extensions accepted by POST /api/v1/analyze-video (cv_analysis.ALLOWED_VIDEO_EXTENSIONS). */
val ACCEPTED_VIDEO_EXTENSIONS = listOf("mp4", "mov", "avi", "3gp", "mkv", "webm")

data class SelectedVideo(
    val uri: Uri,
    val displayName: String,
    val sizeBytes: Long?,
    val mimeType: String?,
    val durationMs: Long?,
    /** Display size, i.e. already swapped for 90°/270° rotated recordings. */
    val width: Int?,
    val height: Int?
)

sealed interface InspectionResult {
    data class Valid(val video: SelectedVideo) : InspectionResult
    data class Invalid(val message: String) : InspectionResult
}

object VideoInspector {

    /** Reads name/size/type and validates that the file is a playable, supported video. */
    suspend fun inspect(context: Context, uri: Uri): InspectionResult = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        var name: String? = null
        var size: Long? = null
        try {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (nameIndex >= 0) name = cursor.getString(nameIndex)
                        if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) size = cursor.getLong(sizeIndex)
                    }
                }
        } catch (e: Exception) {
            return@withContext InspectionResult.Invalid("This file could not be opened.")
        }

        val mime = resolver.getType(uri)
        val displayName = name ?: uri.lastPathSegment ?: "video"
        val extension = displayName.substringAfterLast('.', "").lowercase()
        val supported = extension in ACCEPTED_VIDEO_EXTENSIONS || mime?.startsWith("video/") == true
        if (!supported) {
            return@withContext InspectionResult.Invalid(
                "This file is not a supported video. Choose an MP4, MOV, AVI, 3GP, MKV or WEBM file."
            )
        }
        if (size == 0L) {
            return@withContext InspectionResult.Invalid("This video file is empty.")
        }

        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            val duration = retriever.extract(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
            val rawWidth = retriever.extract(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull()
            val rawHeight = retriever.extract(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull()
            val rotation = retriever.extract(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            if (rawWidth == null || rawHeight == null) {
                return@withContext InspectionResult.Invalid("This file does not contain a readable video track.")
            }
            val rotated = rotation == 90 || rotation == 270
            InspectionResult.Valid(
                SelectedVideo(
                    uri = uri,
                    displayName = displayName,
                    sizeBytes = size,
                    mimeType = mime,
                    durationMs = duration,
                    width = if (rotated) rawHeight else rawWidth,
                    height = if (rotated) rawWidth else rawHeight
                )
            )
        } catch (e: Exception) {
            InspectionResult.Invalid("This file could not be read as a video.")
        } finally {
            retriever.release()
        }
    }

    /** True if this app can still open the video (URI permission may be gone after reinstall). */
    suspend fun isReadable(context: Context, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openInputStream(uri)?.use { true } ?: false
        } catch (e: Exception) {
            false
        }
    }

    /** Frame nearest to `seconds`, scaled down to at most `maxWidth` pixels wide. */
    suspend fun frameAt(context: Context, uri: Uri, seconds: Double, maxWidth: Int = 1280): Bitmap? =
        withContext(Dispatchers.IO) {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, uri)
                val timeUs = (seconds * 1_000_000).toLong()
                val option = MediaMetadataRetriever.OPTION_CLOSEST
                val width = retriever.extract(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull()
                val height = retriever.extract(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1 &&
                    width != null && height != null && width > maxWidth
                ) {
                    val scaledHeight = (height * (maxWidth.toFloat() / width)).toInt()
                    retriever.getScaledFrameAtTime(timeUs, option, maxWidth, scaledHeight)
                } else {
                    retriever.getFrameAtTime(timeUs, option)
                }
            } catch (e: Exception) {
                null
            } finally {
                retriever.release()
            }
        }

    private fun MediaMetadataRetriever.extract(key: Int): String? = extractMetadata(key)
}
