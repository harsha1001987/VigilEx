package com.extrive.vigilex.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.extrive.vigilex.ui.theme.Black
import com.extrive.vigilex.ui.theme.Border
import com.extrive.vigilex.ui.theme.Canvas
import com.extrive.vigilex.ui.theme.Ink
import com.extrive.vigilex.ui.theme.InkFaint
import com.extrive.vigilex.ui.theme.InkMuted
import com.extrive.vigilex.ui.theme.Radius
import com.extrive.vigilex.ui.theme.Red
import com.extrive.vigilex.ui.theme.Space
import com.extrive.vigilex.ui.theme.SurfaceSunken
import com.extrive.vigilex.ui.theme.VxType
import com.extrive.vigilex.ui.theme.Yellow

private val ButtonHeight = 52.dp

/** The one yellow action on a screen. */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .height(ButtonHeight)
            .defaultMinSize(minWidth = 160.dp),
        shape = Radius.small,
        colors = ButtonDefaults.buttonColors(
            containerColor = Yellow,
            contentColor = Black,
            disabledContainerColor = SurfaceSunken,
            disabledContentColor = InkFaint
        ),
        elevation = null,
        contentPadding = PaddingValues(horizontal = Space.lg)
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(Space.xs))
        }
        Text(text.uppercase(), style = VxType.labelLarge)
    }
}

@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    destructive: Boolean = false,
    icon: ImageVector? = null
) {
    val color = if (destructive) Red else Ink
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .height(ButtonHeight)
            .defaultMinSize(minWidth = 140.dp),
        shape = Radius.small,
        border = BorderStroke(Border.hairline, if (enabled) color else InkFaint),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = color,
            disabledContentColor = InkFaint
        ),
        contentPadding = PaddingValues(horizontal = Space.lg)
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(Space.xs))
        }
        Text(text.uppercase(), style = VxType.labelLarge)
    }
}

/** Inline navigation link: label and arrow, no container. */
@Composable
fun ArrowLink(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = Ink
) {
    TextButton(
        onClick = onClick,
        modifier = modifier,
        shape = Radius.small,
        colors = ButtonDefaults.textButtonColors(contentColor = color, disabledContentColor = InkMuted),
        contentPadding = PaddingValues(horizontal = 0.dp, vertical = Space.xs)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text.uppercase(), style = VxType.labelLarge)
            Spacer(Modifier.width(Space.xs))
            Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
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
