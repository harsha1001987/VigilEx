package com.extrive.vigilex.ui.report

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.extrive.vigilex.ui.components.LoadingState
import com.extrive.vigilex.ui.theme.HairlineStrong
import com.extrive.vigilex.ui.theme.Ink
import com.extrive.vigilex.ui.theme.InkMuted
import com.extrive.vigilex.ui.theme.Radius
import com.extrive.vigilex.ui.theme.Space
import com.extrive.vigilex.ui.theme.VxType
import com.extrive.vigilex.ui.theme.White
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A4 portrait, used for the placeholder until the page has been drawn. */
private const val A4_ASPECT = 210f / 297f

/** The longest side a preview page is drawn at; enough to read on a phone, bounded in memory. */
private const val MAX_RENDER_PX = 1600

/**
 * The downloaded PDF itself, one page at a time, so the preview is exactly
 * what is exported or shared. Only the visible page is held in memory.
 */
@Composable
fun PdfPages(file: File, pageCount: Int, modifier: Modifier = Modifier) {
    var page by rememberSaveable(file.path) { mutableIntStateOf(0) }
    var bitmap by remember(file.path) { mutableStateOf<ImageBitmap?>(null) }
    var failed by remember(file.path) { mutableStateOf(false) }

    BoxWithConstraints(modifier.fillMaxWidth()) {
        val widthPx = with(LocalDensity.current) { maxWidth.toPx() }.toInt().coerceIn(320, MAX_RENDER_PX)
        LaunchedEffect(file.path, page, widthPx) {
            bitmap = null
            val drawn = renderPage(file, page, widthPx)
            failed = drawn == null
            bitmap = drawn?.asImageBitmap()
        }

        Column(Modifier.fillMaxWidth()) {
            val pageModifier = Modifier
                .fillMaxWidth()
                .clip(Radius.small)
                .border(1.dp, HairlineStrong, Radius.small)
                .background(White)
            val shown = bitmap
            when {
                shown != null -> Image(
                    bitmap = shown,
                    contentDescription = "Report page ${page + 1} of $pageCount",
                    modifier = pageModifier.aspectRatio(shown.width.toFloat() / shown.height)
                )
                failed -> Box(pageModifier.aspectRatio(A4_ASPECT), contentAlignment = Alignment.Center) {
                    Text(
                        "This page could not be shown here. Export or share the PDF to open it in another app.",
                        style = VxType.bodySmall,
                        color = InkMuted,
                        modifier = Modifier.width(240.dp)
                    )
                }
                else -> Box(pageModifier.aspectRatio(A4_ASPECT), contentAlignment = Alignment.Center) {
                    LoadingState(label = "Drawing page ${page + 1}…")
                }
            }
            if (pageCount > 1) {
                Spacer(Modifier.height(Space.xs))
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    IconButton(onClick = { page-- }, enabled = page > 0) {
                        Icon(
                            Icons.AutoMirrored.Outlined.KeyboardArrowLeft,
                            contentDescription = "Previous page",
                            tint = if (page > 0) Ink else HairlineStrong
                        )
                    }
                    Text(
                        "Page ${page + 1} of $pageCount",
                        style = VxType.bodySmall,
                        color = Ink,
                        modifier = Modifier.semantics { contentDescription = "Showing page ${page + 1} of $pageCount" }
                    )
                    IconButton(onClick = { page++ }, enabled = page < pageCount - 1) {
                        Icon(
                            Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                            contentDescription = "Next page",
                            tint = if (page < pageCount - 1) Ink else HairlineStrong
                        )
                    }
                }
            }
        }
    }
}

/** Draws one page on white at [widthPx] wide; null if the file can't be read. */
private suspend fun renderPage(file: File, index: Int, widthPx: Int): Bitmap? = withContext(Dispatchers.IO) {
    try {
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
            PdfRenderer(fd).use { renderer ->
                if (index !in 0 until renderer.pageCount) return@withContext null
                renderer.openPage(index).use { page ->
                    val height = (widthPx.toLong() * page.height / page.width).toInt().coerceAtLeast(1)
                    Bitmap.createBitmap(widthPx, height, Bitmap.Config.ARGB_8888).also {
                        it.eraseColor(AndroidColor.WHITE)
                        page.render(it, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    }
                }
            }
        }
    } catch (e: Exception) {
        null
    }
}
