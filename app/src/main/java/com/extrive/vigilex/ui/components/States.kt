package com.extrive.vigilex.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.extrive.vigilex.ui.theme.Hairline
import com.extrive.vigilex.ui.theme.Ink
import com.extrive.vigilex.ui.theme.InkSecondary
import com.extrive.vigilex.ui.theme.Red
import com.extrive.vigilex.ui.theme.Space
import com.extrive.vigilex.ui.theme.VxType
import com.extrive.vigilex.ui.theme.Yellow

/**
 * Intentional empty state: a statement, one sentence of guidance and at most
 * one action. Left-aligned on the page grid rather than floating in a card.
 */
@Composable
fun EmptyState(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    StatementBlock(
        marker = Yellow,
        title = title,
        body = body,
        modifier = modifier
    ) {
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(Space.lg))
            PrimaryButton(text = actionLabel, onClick = onAction)
        }
    }
}

@Composable
fun ErrorState(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actions: (@Composable () -> Unit)? = null
) {
    StatementBlock(
        marker = Red,
        title = title,
        body = message,
        modifier = modifier.semantics { liveRegion = LiveRegionMode.Polite }
    ) {
        if (actions != null) {
            Spacer(Modifier.height(Space.lg))
            ButtonRow { actions() }
        }
    }
}

@Composable
private fun StatementBlock(
    marker: Color,
    title: String,
    body: String,
    modifier: Modifier,
    footer: @Composable () -> Unit
) {
    Column(modifier.fillMaxWidth()) {
        Hairline(color = Hairline)
        Spacer(Modifier.height(Space.xl))
        Row(verticalAlignment = Alignment.CenterVertically) {
            KeySquare(marker, size = 10.dp)
            Spacer(Modifier.width(Space.sm))
            Text(title.uppercase(), style = VxType.labelLarge, color = Ink)
        }
        Spacer(Modifier.height(Space.sm))
        Text(
            text = body,
            style = VxType.body,
            color = InkSecondary,
            modifier = Modifier.widthIn(max = 520.dp)
        )
        footer()
        Spacer(Modifier.height(Space.xl))
    }
}

@Composable
fun LoadingState(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .padding(vertical = Space.xxl),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(
            color = Ink,
            trackColor = Hairline,
            strokeWidth = 2.dp,
            modifier = Modifier.size(28.dp)
        )
    }
}
