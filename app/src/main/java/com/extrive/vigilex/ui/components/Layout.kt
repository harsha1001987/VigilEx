package com.extrive.vigilex.ui.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.extrive.vigilex.ui.theme.Hairline
import com.extrive.vigilex.ui.theme.Ink
import com.extrive.vigilex.ui.theme.InkMuted
import com.extrive.vigilex.ui.theme.InkSecondary
import com.extrive.vigilex.ui.theme.Space
import com.extrive.vigilex.ui.theme.VxType

val ContentMaxWidth = 1120.dp

fun WindowClass.gutter(): Dp = when (this) {
    WindowClass.COMPACT -> 20.dp
    WindowClass.MEDIUM -> 40.dp
    WindowClass.EXPANDED -> 56.dp
}

/** Scrollable page body, centred, with a max width and window-aware gutters. */
@Composable
fun PageContainer(
    modifier: Modifier = Modifier,
    scrollState: ScrollState = rememberScrollState(),
    content: @Composable ColumnScope.() -> Unit
) {
    val windowClass = LocalWindowClass.current
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .widthIn(max = ContentMaxWidth)
                .fillMaxWidth()
                .padding(horizontal = windowClass.gutter())
                .padding(bottom = Space.xxxl),
            content = content
        )
    }
}

/**
 * The application header: identity, page context and the account control.
 * On phones the wordmark sits in the top row (there is no sidebar); sub-pages
 * get a back action instead of the account control.
 */
@Composable
fun PageHeader(
    eyebrow: String,
    title: String,
    supporting: String? = null,
    onBack: (() -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null
) {
    val compact = LocalWindowClass.current == WindowClass.COMPACT
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            when {
                // Offset so the arrow glyph, not its touch target, aligns with the gutter.
                onBack != null -> IconButton(onClick = onBack, modifier = Modifier.offset(x = (-12).dp)) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back", tint = Ink)
                }
                compact -> Text("VIGILEX", style = VxType.wordmark, color = Ink)
            }
            Spacer(Modifier.weight(1f))
            trailing?.invoke(this)
            // Offset so the avatar, not its touch target, aligns with the gutter.
            if (onBack == null) AccountButton(Modifier.offset(x = 8.dp))
        }
        Spacer(Modifier.height(if (compact) Space.lg else Space.xxl))
        Text(eyebrow.uppercase(), style = VxType.label, color = InkMuted)
        Spacer(Modifier.height(Space.sm))
        Text(
            text = title,
            style = if (compact) VxType.pageTitleCompact else VxType.pageTitle,
            color = Ink
        )
        if (supporting != null) {
            Spacer(Modifier.height(Space.sm))
            Text(
                text = supporting,
                style = VxType.body,
                color = InkSecondary,
                modifier = Modifier.widthIn(max = 560.dp)
            )
        }
        Spacer(Modifier.height(if (compact) Space.xl else Space.xxl))
    }
}

/**
 * Slim header for assessment views, where the first viewport belongs to the
 * result: back, a small context label and one line of metadata.
 */
@Composable
fun SubPageHeader(context: String, meta: String?, onBack: () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.height(56.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.offset(x = (-12).dp)) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back", tint = Ink)
            }
            Text(context.uppercase(), style = VxType.label, color = Ink)
        }
        if (meta != null) {
            Text(meta, style = VxType.bodySmall, color = InkMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.height(Space.xl))
    }
}

/** Swiss section opener: a strong rule, then a small tracked label. */
@Composable
fun SectionLabel(
    text: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable RowScope.() -> Unit)? = null
) {
    Column(modifier.fillMaxWidth()) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Ink)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Space.sm, bottom = Space.lg),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text.uppercase(), style = VxType.label, color = Ink, modifier = Modifier.weight(1f))
            trailing?.invoke(this)
        }
    }
}

@Composable
fun Hairline(modifier: Modifier = Modifier, color: Color = Hairline) {
    Box(
        modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(color)
    )
}

@Composable
fun VerticalHairline(modifier: Modifier = Modifier, color: Color = Hairline) {
    Box(
        modifier
            .width(1.dp)
            .background(color)
    )
}

/** Small square used as a colour key next to a label. */
@Composable
fun KeySquare(color: Color, modifier: Modifier = Modifier, size: Dp = 8.dp) {
    Box(
        modifier
            .size(size)
            .background(color)
    )
}

/** Lays children in `columns` equal columns, wrapping into rows. */
@Composable
fun <T> GridRows(
    items: List<T>,
    columns: Int,
    modifier: Modifier = Modifier,
    horizontalGap: Dp = Space.lg,
    verticalGap: Dp = Space.xl,
    cell: @Composable (T) -> Unit
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(verticalGap)) {
        items.chunked(columns).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(horizontalGap)) {
                row.forEach { item -> Box(Modifier.weight(1f)) { cell(item) } }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
fun FullScreenCentered(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        content = content
    )
}
