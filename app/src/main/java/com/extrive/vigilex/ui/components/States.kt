package com.extrive.vigilex.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.extrive.vigilex.data.api.ApiResult
import com.extrive.vigilex.data.api.ErrorKind
import com.extrive.vigilex.ui.theme.Hairline
import com.extrive.vigilex.ui.theme.Ink
import com.extrive.vigilex.ui.theme.InkMuted
import com.extrive.vigilex.ui.theme.InkSecondary
import com.extrive.vigilex.ui.theme.RedInk
import com.extrive.vigilex.ui.theme.Space
import com.extrive.vigilex.ui.theme.VxType

/**
 * Intentional empty state: an icon, a statement, one sentence of guidance and
 * at most one action, on a card so it reads as a deliberate state.
 */
@Composable
fun EmptyState(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    icon: ImageVector = Icons.Outlined.Inbox
) {
    StatementBlock(
        icon = icon,
        iconTint = InkSecondary,
        title = title,
        body = body,
        modifier = modifier
    ) {
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(Space.md))
            PrimaryButton(text = actionLabel, onClick = onAction)
        }
    }
}

/** A failure that stops this part of the screen from working, with what to do next. */
@Composable
fun ErrorState(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actions: (@Composable () -> Unit)? = null
) {
    StatementBlock(
        icon = Icons.Outlined.ErrorOutline,
        iconTint = RedInk,
        title = title,
        body = message,
        modifier = modifier.semantics { liveRegion = LiveRegionMode.Polite }
    ) {
        if (actions != null) {
            Spacer(Modifier.height(Space.md))
            ButtonRow { actions() }
        }
    }
}

/** Title for a failed backend call, specific to what failed. */
fun ApiResult.Error.title(notFound: String = "Not found"): String = when (kind) {
    ErrorKind.UNREACHABLE -> "Server unavailable"
    ErrorKind.TIMEOUT -> "Server not responding"
    ErrorKind.NOT_FOUND -> notFound
    ErrorKind.REJECTED -> "Request not accepted"
    ErrorKind.UNAUTHORIZED -> "Signed out"
    ErrorKind.CONFLICT -> "Already exists"
    ErrorKind.RATE_LIMITED -> "Too many attempts"
    ErrorKind.SERVER -> "Server error"
    ErrorKind.BAD_RESPONSE -> "Unexpected response"
    ErrorKind.OTHER -> "Something went wrong"
}

/**
 * A failed backend call. Not-found is final (no retry); every other failure
 * can be retried. [notFoundMessage] replaces the server's wording for 404s.
 */
@Composable
fun BackendErrorState(
    error: ApiResult.Error,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    notFoundTitle: String = "Not found",
    notFoundMessage: String? = null
) {
    val notFound = error.kind == ErrorKind.NOT_FOUND
    ErrorState(
        title = error.title(notFoundTitle),
        message = if (notFound && notFoundMessage != null) notFoundMessage else error.message,
        modifier = modifier,
        actions = if (notFound) null else ({ PrimaryButton("Try again", onClick = onRetry) })
    )
}

@Composable
private fun StatementBlock(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    body: String,
    modifier: Modifier,
    footer: @Composable () -> Unit
) {
    VxCard(modifier, contentPadding = PaddingValues(Space.lg)) {
        Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(28.dp))
        Spacer(Modifier.height(Space.sm))
        Text(title, style = VxType.sectionTitle, color = Ink, modifier = Modifier.semantics { heading() })
        Spacer(Modifier.height(Space.xxs))
        Text(
            text = body,
            style = VxType.body,
            color = InkSecondary,
            modifier = Modifier.widthIn(max = 520.dp)
        )
        footer()
    }
}

/** Real loading only: a small spinner and, when given, what is loading. */
@Composable
fun LoadingState(modifier: Modifier = Modifier, label: String = "Loading…") {
    Row(
        modifier
            .fillMaxWidth()
            .padding(vertical = Space.xl)
            .semantics(mergeDescendants = true) { contentDescription = label },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircularProgressIndicator(
            color = Ink,
            trackColor = Hairline,
            strokeWidth = 2.dp,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(Space.sm))
        Text(label, style = VxType.bodySmall, color = InkMuted)
    }
}
