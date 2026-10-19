package com.extrive.vigilex.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.extrive.vigilex.ui.theme.Border
import com.extrive.vigilex.ui.theme.GoldInk
import com.extrive.vigilex.ui.theme.GoldTint
import com.extrive.vigilex.ui.theme.Hairline
import com.extrive.vigilex.ui.theme.Ink
import com.extrive.vigilex.ui.theme.InkMuted
import com.extrive.vigilex.ui.theme.InkSecondary
import com.extrive.vigilex.ui.theme.Motion
import com.extrive.vigilex.ui.theme.Radius
import com.extrive.vigilex.ui.theme.Red
import com.extrive.vigilex.ui.theme.RedInk
import com.extrive.vigilex.ui.theme.RedTint
import com.extrive.vigilex.ui.theme.Size
import com.extrive.vigilex.ui.theme.Space
import com.extrive.vigilex.ui.theme.Surface
import com.extrive.vigilex.ui.theme.SurfaceSunken
import com.extrive.vigilex.ui.theme.VxType

/**
 * A white surface with a hairline border, used for content that stands as
 * one unit (an assessment, a result, a group of settings). When [onClick] is
 * given the whole card is one button.
 */
@Composable
fun VxCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    contentPadding: PaddingValues = PaddingValues(Space.md),
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(Radius.card)
            .background(Surface)
            .border(Border.hairline, Hairline, Radius.card)
            .then(
                if (onClick != null) Modifier.clickable(role = Role.Button, onClickLabel = onClickLabel, onClick = onClick)
                else Modifier
            )
            .padding(contentPadding),
        content = content
    )
}

enum class CalloutTone { INFO, ATTENTION, ERROR }

private fun CalloutTone.background(): Color = when (this) {
    CalloutTone.INFO -> SurfaceSunken
    CalloutTone.ATTENTION -> GoldTint
    CalloutTone.ERROR -> RedTint
}

private fun CalloutTone.iconTint(): Color = when (this) {
    CalloutTone.INFO -> InkSecondary
    CalloutTone.ATTENTION -> GoldInk
    CalloutTone.ERROR -> RedInk
}

private fun CalloutTone.icon(): ImageVector = when (this) {
    CalloutTone.INFO -> Icons.Outlined.Info
    CalloutTone.ATTENTION -> Icons.Outlined.WarningAmber
    CalloutTone.ERROR -> Icons.Outlined.ErrorOutline
}

/**
 * Inline message tied to the content around it: information, something that
 * needs attention, or an error. The tone is stated by an icon and words as
 * well as colour. Optional [actions] sit under the text.
 */
@Composable
fun Callout(
    title: String?,
    body: String?,
    modifier: Modifier = Modifier,
    tone: CalloutTone = CalloutTone.INFO,
    actions: (@Composable () -> Unit)? = null
) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(Radius.medium)
            .background(tone.background())
            .padding(Space.md)
    ) {
        Icon(
            tone.icon(),
            contentDescription = when (tone) {
                CalloutTone.INFO -> null
                CalloutTone.ATTENTION -> "Attention"
                CalloutTone.ERROR -> "Error"
            },
            tint = tone.iconTint(),
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(Space.sm))
        Column(Modifier.weight(1f)) {
            title?.let { Text(it, style = VxType.labelLarge, color = Ink) }
            if (title != null && body != null) Spacer(Modifier.height(Space.xxs))
            body?.let { Text(it, style = VxType.bodySmall, color = Ink) }
            if (actions != null) {
                Spacer(Modifier.height(Space.xs))
                ButtonRow { actions() }
            }
        }
    }
}

/**
 * A titled section that can be collapsed, for long technical content. The
 * whole header is the toggle and announces whether it is expanded.
 * State survives rotation, keyed by [title].
 */
@Composable
fun ExpandableSection(
    title: String,
    modifier: Modifier = Modifier,
    summary: String? = null,
    initiallyExpanded: Boolean = false,
    expanded: Boolean? = null,
    onExpandedChange: ((Boolean) -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    var localExpanded by rememberSaveable(title) { mutableStateOf(initiallyExpanded) }
    val isExpanded = expanded ?: localExpanded
    val toggle: () -> Unit = {
        val next = !isExpanded
        if (expanded == null) localExpanded = next
        onExpandedChange?.invoke(next)
    }
    val rotation by animateFloatAsState(if (isExpanded) 180f else 0f, tween(Motion.fast), label = "chevron")

    Column(modifier.fillMaxWidth()) {
        Hairline()
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = Size.touchTarget + Space.xs)
                .clickable(role = Role.Button, onClick = toggle)
                .semantics { stateDescription = if (isExpanded) "Expanded" else "Collapsed" }
                .padding(vertical = Space.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = VxType.title, color = Ink, modifier = Modifier.semantics { heading() })
                if (summary != null) {
                    Spacer(Modifier.height(2.dp))
                    Text(summary, style = VxType.bodySmall, color = InkMuted)
                }
            }
            Spacer(Modifier.width(Space.sm))
            Icon(
                Icons.Outlined.KeyboardArrowDown,
                contentDescription = null,
                tint = InkSecondary,
                modifier = Modifier.rotate(rotation)
            )
        }
        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically(tween(Motion.base, easing = Motion.standard)) + fadeIn(tween(Motion.base)),
            exit = shrinkVertically(tween(Motion.fast)) + fadeOut(tween(Motion.fast))
        ) {
            Column(Modifier.fillMaxWidth().padding(top = Space.xs, bottom = Space.lg), content = content)
        }
    }
}

/**
 * The one confirmation dialog. [destructive] colours the confirm action red;
 * the dialog itself stays calm.
 */
@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = false,
    dismissLabel: String = "Cancel"
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surface,
        shape = Radius.card,
        title = { Text(title, style = VxType.sectionTitle, color = Ink) },
        text = { Text(message, style = VxType.body, color = InkSecondary) },
        confirmButton = {
            TextButton(onClick = onConfirm, modifier = Modifier.heightIn(min = Size.touchTarget)) {
                Text(confirmLabel, style = VxType.button, color = if (destructive) Red else Ink)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = Size.touchTarget)) {
                Text(dismissLabel, style = VxType.button, color = Ink)
            }
        }
    )
}

/** Information-only dialog with a single "Done". */
@Composable
fun InfoDialog(title: String, message: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surface,
        shape = Radius.card,
        title = { Text(title, style = VxType.sectionTitle, color = Ink) },
        text = { Text(message, style = VxType.body, color = InkSecondary) },
        confirmButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = Size.touchTarget)) {
                Text("Done", style = VxType.button, color = Ink)
            }
        }
    )
}
