package com.extrive.vigilex.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.extrive.vigilex.ui.theme.Border
import com.extrive.vigilex.ui.theme.HairlineStrong
import com.extrive.vigilex.ui.theme.Ink
import com.extrive.vigilex.ui.theme.InkMuted
import com.extrive.vigilex.ui.theme.InkSecondary
import com.extrive.vigilex.ui.theme.Radius
import com.extrive.vigilex.ui.theme.Red
import com.extrive.vigilex.ui.theme.RedInk
import com.extrive.vigilex.ui.theme.Size
import com.extrive.vigilex.ui.theme.Space
import com.extrive.vigilex.ui.theme.Surface
import com.extrive.vigilex.ui.theme.VxType

/**
 * Text field with a persistent label above an outlined box. Focus is shown by
 * a thicker charcoal outline (yellow is too light to see against white); an
 * error turns the outline red and is stated in words beneath, linked to the
 * field for screen readers.
 */
@Composable
fun VxTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    error: String? = null,
    supporting: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    onImeAction: () -> Unit = {},
    password: Boolean = false,
    enabled: Boolean = true,
    textStyle: TextStyle = VxType.body
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    var revealed by rememberSaveable { mutableStateOf(false) }

    val outline: Color = when {
        error != null -> Red
        focused -> Ink
        else -> HairlineStrong
    }
    val width = if (focused || error != null) Border.strong + 0.5.dp else Border.hairline

    Column(modifier.fillMaxWidth()) {
        Text(label, style = VxType.labelLarge, color = Ink)
        Spacer(Modifier.height(Space.xs))
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = Size.button)
                .clip(Radius.medium)
                .background(Surface)
                .border(width, outline, Radius.medium)
                .padding(start = Space.md, end = if (password) Space.xxs else Space.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                enabled = enabled,
                singleLine = true,
                textStyle = textStyle.copy(color = if (enabled) Ink else InkMuted),
                cursorBrush = SolidColor(Ink),
                interactionSource = interaction,
                visualTransformation = if (password && !revealed) PasswordVisualTransformation() else VisualTransformation.None,
                keyboardOptions = KeyboardOptions(
                    keyboardType = if (password) KeyboardType.Password else keyboardType,
                    imeAction = imeAction
                ),
                keyboardActions = KeyboardActions(onAny = { onImeAction() }),
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = Space.sm)
                    .semantics {
                        contentDescription = label
                        if (error != null) error(error)
                    }
            )
            if (password) {
                IconButton(onClick = { revealed = !revealed }) {
                    Icon(
                        if (revealed) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                        contentDescription = if (revealed) "Hide password" else "Show password",
                        tint = InkSecondary
                    )
                }
            }
        }
        when {
            error != null -> {
                Spacer(Modifier.height(Space.xs))
                Row(verticalAlignment = Alignment.Top) {
                    Icon(Icons.Outlined.ErrorOutline, contentDescription = null, tint = RedInk, modifier = Modifier.size(16.dp).padding(top = 2.dp))
                    Spacer(Modifier.width(Space.xxs))
                    Text(error, style = VxType.bodySmall, color = RedInk)
                }
            }
            supporting != null -> {
                Spacer(Modifier.height(Space.xs))
                Text(supporting, style = VxType.bodySmall, color = InkMuted)
            }
        }
    }
}

/** A group of settings rows on one card, separated by hairlines. */
@Composable
fun SettingsGroup(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    VxCard(modifier, contentPadding = PaddingValues(0.dp), content = content)
}

/**
 * One line of a settings list: optional icon, label with optional supporting
 * text, optional value, and a chevron when it leads somewhere. Rows without
 * an action are plain, non-focusable text.
 */
@Composable
fun SettingsRow(
    label: String,
    modifier: Modifier = Modifier,
    value: String? = null,
    supporting: String? = null,
    icon: ImageVector? = null,
    labelColor: Color = Ink,
    chevron: Boolean = true,
    divider: Boolean = true,
    onClick: (() -> Unit)? = null
) {
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
                .heightIn(min = 56.dp)
                .padding(horizontal = Space.md, vertical = Space.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = if (labelColor == Ink) InkSecondary else labelColor, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(Space.md))
            }
            Column(Modifier.weight(1f)) {
                Text(label, style = VxType.body, color = labelColor)
                supporting?.let { Text(it, style = VxType.bodySmall, color = InkMuted) }
            }
            if (value != null) {
                Spacer(Modifier.width(Space.md))
                Text(
                    value,
                    style = VxType.bodySmall,
                    color = InkMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
            }
            if (onClick != null && chevron) {
                Spacer(Modifier.width(Space.xs))
                Icon(
                    Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                    contentDescription = null,
                    tint = InkMuted
                )
            }
        }
        if (divider) Hairline(Modifier.padding(start = if (icon != null) Space.md + 22.dp + Space.md else Space.md))
    }
}
