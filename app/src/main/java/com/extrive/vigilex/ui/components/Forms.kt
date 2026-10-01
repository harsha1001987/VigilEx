package com.extrive.vigilex.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
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
import com.extrive.vigilex.ui.theme.Ink
import com.extrive.vigilex.ui.theme.InkFaint
import com.extrive.vigilex.ui.theme.InkMuted
import com.extrive.vigilex.ui.theme.InkSecondary
import com.extrive.vigilex.ui.theme.Red
import com.extrive.vigilex.ui.theme.Space
import com.extrive.vigilex.ui.theme.VxType
import com.extrive.vigilex.ui.theme.Yellow

/**
 * Swiss form field: small tracked label, text on an underline. The rule turns
 * yellow on focus and red on error; the error is also stated in words.
 */
@Composable
fun VxTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    error: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    onImeAction: () -> Unit = {},
    password: Boolean = false,
    textStyle: TextStyle = VxType.body
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    var revealed by rememberSaveable { mutableStateOf(false) }

    Column(modifier.fillMaxWidth()) {
        Text(label.uppercase(), style = VxType.label, color = InkMuted)
        Row(verticalAlignment = Alignment.CenterVertically) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = textStyle.copy(color = Ink),
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
                    .heightIn(min = 48.dp)
                    .padding(vertical = Space.sm)
                    .semantics {
                        contentDescription = label
                        if (error != null) error(error)
                    }
            )
            if (password) {
                TextButton(onClick = { revealed = !revealed }) {
                    Text(if (revealed) "HIDE" else "SHOW", style = VxType.label, color = InkSecondary)
                }
            }
        }
        val rule: Color = when {
            error != null -> Red
            focused -> Yellow
            else -> Ink
        }
        Hairline(Modifier.height(if (focused || error != null) Border.strong else Border.hairline), color = rule)
        if (error != null) {
            Spacer(Modifier.height(Space.xs))
            Text(error, style = VxType.bodySmall, color = Red)
        }
    }
}

/**
 * One line of a settings list: label, optional value, and a chevron when it
 * leads somewhere. Rows without an action are plain, non-focusable text.
 */
@Composable
fun SettingsRow(
    label: String,
    modifier: Modifier = Modifier,
    value: String? = null,
    labelColor: Color = Ink,
    chevron: Boolean = true,
    onClick: (() -> Unit)? = null
) {
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
                .heightIn(min = 56.dp)
                .padding(vertical = Space.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, style = VxType.body, color = labelColor, modifier = Modifier.weight(1f))
            if (value != null) {
                Spacer(Modifier.width(Space.md))
                Text(
                    value,
                    style = VxType.body,
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
                    tint = InkFaint
                )
            }
        }
        Hairline()
    }
}
