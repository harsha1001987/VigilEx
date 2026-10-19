package com.extrive.vigilex.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.extrive.vigilex.ui.theme.Border
import com.extrive.vigilex.ui.theme.Canvas
import com.extrive.vigilex.ui.theme.HairlineStrong
import com.extrive.vigilex.ui.theme.Ink
import com.extrive.vigilex.ui.theme.InkFaint
import com.extrive.vigilex.ui.theme.InkMuted
import com.extrive.vigilex.ui.theme.Radius
import com.extrive.vigilex.ui.theme.RedInk
import com.extrive.vigilex.ui.theme.Size
import com.extrive.vigilex.ui.theme.Space
import com.extrive.vigilex.ui.theme.Surface
import com.extrive.vigilex.ui.theme.SurfaceSunken
import com.extrive.vigilex.ui.theme.VxType
import com.extrive.vigilex.ui.theme.Yellow

/**
 * The one yellow action on a screen. While [loading], the label stays (so the
 * button keeps its meaning for screen readers) and a small spinner replaces
 * the icon; the button is disabled so a request cannot be sent twice.
 */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    loading: Boolean = false
) {
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        modifier = modifier
            .heightIn(min = Size.button)
            .defaultMinSize(minWidth = 120.dp),
        shape = Radius.medium,
        colors = ButtonDefaults.buttonColors(
            containerColor = Yellow,
            contentColor = Ink,
            disabledContainerColor = if (loading) Yellow else SurfaceSunken,
            disabledContentColor = if (loading) Ink else InkMuted
        ),
        elevation = null,
        contentPadding = PaddingValues(horizontal = Space.lg, vertical = Space.sm)
    ) {
        ButtonContent(text, icon, loading)
    }
}

/** A visible but quieter action: white, outlined, charcoal text. */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    destructive: Boolean = false,
    icon: ImageVector? = null,
    loading: Boolean = false
) {
    val color = if (destructive) RedInk else Ink
    OutlinedButton(
        onClick = onClick,
        enabled = enabled && !loading,
        modifier = modifier
            .heightIn(min = Size.button)
            .defaultMinSize(minWidth = 120.dp),
        shape = Radius.medium,
        border = BorderStroke(Border.hairline, if (enabled) (if (destructive) color else HairlineStrong) else InkFaint),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = Surface,
            contentColor = color,
            disabledContainerColor = Surface,
            disabledContentColor = InkMuted
        ),
        contentPadding = PaddingValues(horizontal = Space.lg, vertical = Space.sm)
    ) {
        ButtonContent(text, icon, loading)
    }
}

@Composable
private fun ButtonContent(text: String, icon: ImageVector?, loading: Boolean) {
    when {
        loading -> {
            CircularProgressIndicator(
                color = Ink,
                trackColor = Color.Transparent,
                strokeWidth = 2.dp,
                modifier = Modifier.size(Size.iconSmall)
            )
            Spacer(Modifier.width(Space.xs))
        }
        icon != null -> {
            Icon(icon, contentDescription = null, modifier = Modifier.size(Size.icon - 2.dp))
            Spacer(Modifier.width(Space.xs))
        }
    }
    Text(text, style = VxType.button, maxLines = 2, overflow = TextOverflow.Ellipsis)
}

/** Inline navigation link, e.g. "View all →". Text only, 48 dp touch target. */
@Composable
fun ArrowLink(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = Ink,
    showArrow: Boolean = true
) {
    TextButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = Size.touchTarget),
        shape = Radius.medium,
        colors = ButtonDefaults.textButtonColors(contentColor = color, disabledContentColor = InkMuted),
        contentPadding = PaddingValues(horizontal = Space.xs, vertical = Space.xs)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text, style = VxType.link)
            if (showArrow) {
                Spacer(Modifier.width(Space.xxs))
                Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
fun ButtonRow(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    FlowRow(
        modifier = modifier.padding(top = Space.xs),
        horizontalArrangement = Arrangement.spacedBy(Space.sm),
        verticalArrangement = Arrangement.spacedBy(Space.sm)
    ) { content() }
}

/**
 * Sticky action at the bottom of a phone screen: canvas-coloured, a hairline
 * above, one or two full-width actions. Content scrolls underneath.
 */
@Composable
fun BottomActionBar(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .background(Canvas)
    ) {
        Hairline()
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = LocalWindowClass.current.gutter(), vertical = Space.sm),
            verticalArrangement = Arrangement.spacedBy(Space.xs)
        ) { content() }
    }
}
