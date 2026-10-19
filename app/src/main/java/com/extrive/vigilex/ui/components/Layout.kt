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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.extrive.vigilex.ui.theme.Hairline
import com.extrive.vigilex.ui.theme.Ink
import com.extrive.vigilex.ui.theme.InkMuted
import com.extrive.vigilex.ui.theme.Size
import com.extrive.vigilex.ui.theme.Space
import com.extrive.vigilex.ui.theme.VxType
import com.extrive.vigilex.ui.theme.Yellow

val ContentMaxWidth = 1120.dp

fun WindowClass.gutter(): Dp = when (this) {
    WindowClass.COMPACT -> 16.dp
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

/** Yellow brand square and "VigilEx": the compact identity used in headers and on sign-in. */
@Composable
fun Wordmark(modifier: Modifier = Modifier, color: Color = Ink) {
    Row(
        modifier.semantics(mergeDescendants = true) { contentDescription = "VigilEx" },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(12.dp)
                .background(Yellow, RoundedCornerShape(2.dp))
        )
        Spacer(Modifier.width(Space.xs))
        Text("VigilEx", style = VxType.wordmark, color = color)
    }
}

/**
 * The application header: identity, the account control, then the page title
 * and one line of context. On phones the wordmark sits in the top row (there
 * is no sidebar); sub-pages get a back action instead of the account control.
 */
@Composable
fun PageHeader(
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
                .height(Size.headerBar),
            verticalAlignment = Alignment.CenterVertically
        ) {
            when {
                // Offset so the arrow glyph, not its touch target, aligns with the gutter.
                onBack != null -> IconButton(onClick = onBack, modifier = Modifier.offset(x = (-12).dp)) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back", tint = Ink)
                }
                compact -> Wordmark()
            }
            Spacer(Modifier.weight(1f))
            trailing?.invoke(this)
            // Offset so the avatar, not its touch target, aligns with the gutter.
            if (onBack == null) AccountButton(Modifier.offset(x = 6.dp))
        }
        Spacer(Modifier.height(if (compact) Space.md else Space.xl))
        Text(
            text = title,
            style = if (compact) VxType.pageTitleCompact else VxType.pageTitle,
            color = Ink,
            modifier = Modifier.semantics { heading() }
        )
        if (supporting != null) {
            Spacer(Modifier.height(Space.xxs))
            Text(
                text = supporting,
                style = VxType.body,
                color = InkMuted,
                modifier = Modifier.widthIn(max = 560.dp)
            )
        }
        Spacer(Modifier.height(Space.lg))
    }
}

/**
 * Slim header for assessment views, where the first viewport belongs to the
 * result: back, the view's name and one line of metadata.
 */
@Composable
fun SubPageHeader(context: String, meta: String?, onBack: () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.height(Size.headerBar), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.offset(x = (-12).dp)) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back", tint = Ink)
            }
            Text(
                context,
                style = VxType.title,
                color = Ink,
                modifier = Modifier
                    .offset(x = (-4).dp)
                    .semantics { heading() }
            )
        }
        if (meta != null) {
            Text(meta, style = VxType.bodySmall, color = InkMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.height(Space.lg))
    }
}

/** Section heading, e.g. "Recent assessments", with an optional trailing link. */
@Composable
fun SectionLabel(
    text: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable RowScope.() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = Size.touchTarget)
            .padding(bottom = Space.xxs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text,
            style = VxType.sectionTitle,
            color = Ink,
            modifier = Modifier
                .weight(1f)
                .semantics { heading() }
        )
        trailing?.invoke(this)
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

/** Small dot used as a colour key next to a label (same mark as in tags). */
@Composable
fun KeySquare(color: Color, modifier: Modifier = Modifier, size: Dp = 8.dp) {
    Box(
        modifier
            .size(size)
            .background(color, CircleShape)
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
