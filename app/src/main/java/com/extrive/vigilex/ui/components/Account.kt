package com.extrive.vigilex.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.extrive.vigilex.data.account.Account
import com.extrive.vigilex.data.account.AccountSession
import com.extrive.vigilex.ui.theme.Border
import com.extrive.vigilex.ui.theme.HairlineStrong
import com.extrive.vigilex.ui.theme.Ink
import com.extrive.vigilex.ui.theme.InkMuted
import com.extrive.vigilex.ui.theme.InkSecondary
import com.extrive.vigilex.ui.theme.Radius
import com.extrive.vigilex.ui.theme.Space
import com.extrive.vigilex.ui.theme.Surface
import com.extrive.vigilex.ui.theme.VxType
import com.extrive.vigilex.ui.theme.Yellow

/** Destinations reachable from the account control, provided once by the nav graph. */
class AccountActions(
    val openProfile: () -> Unit,
    val openSettings: () -> Unit,
    val openSignIn: () -> Unit,
    val signOut: () -> Unit
)

val LocalAccountActions = staticCompositionLocalOf<AccountActions?> { null }

/** Initials on yellow when signed in; a neutral outline figure otherwise. */
@Composable
fun Avatar(account: Account?, size: Dp, modifier: Modifier = Modifier, textStyle: TextStyle = VxType.labelLarge) {
    val initials = account?.name
        ?.split(" ")
        ?.filter { it.isNotBlank() }
        ?.take(2)
        ?.joinToString("") { it.first().uppercase() }
        .orEmpty()
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .then(
                if (initials.isNotEmpty()) Modifier.background(Yellow)
                else Modifier.background(Surface).border(Border.hairline, HairlineStrong, CircleShape)
            ),
        contentAlignment = Alignment.Center
    ) {
        if (initials.isNotEmpty()) {
            Text(initials, style = textStyle, color = Ink)
        } else {
            Icon(Icons.Outlined.Person, contentDescription = null, tint = InkSecondary, modifier = Modifier.size(size * 0.55f))
        }
    }
}

/** Header account control: opens Profile, Settings and Sign out (or Sign in). */
@Composable
fun AccountButton(modifier: Modifier = Modifier) {
    val actions = LocalAccountActions.current ?: return
    val account by AccountSession.current.collectAsState()
    var open by remember { mutableStateOf(false) }

    Box(modifier) {
        IconButton(
            onClick = { open = true },
            modifier = Modifier.semantics { contentDescription = "Account menu" }
        ) {
            Avatar(account, size = 32.dp)
        }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            containerColor = Surface,
            shape = Radius.small,
            tonalElevation = 0.dp,
            modifier = Modifier.widthIn(min = 220.dp)
        ) {
            Column(Modifier.padding(horizontal = Space.md, vertical = Space.xs)) {
                Text(
                    account?.name ?: "Not signed in",
                    style = VxType.title,
                    color = Ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                account?.let {
                    Text(it.email, style = VxType.bodySmall, color = InkMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Hairline(Modifier.padding(vertical = Space.xxs))
            MenuItem("Profile") { open = false; actions.openProfile() }
            MenuItem("Settings") { open = false; actions.openSettings() }
            Hairline(Modifier.padding(vertical = Space.xxs))
            if (account != null) {
                MenuItem("Sign out") { open = false; actions.signOut() }
            } else {
                MenuItem("Sign in") { open = false; actions.openSignIn() }
            }
        }
    }
}

@Composable
private fun MenuItem(label: String, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label, style = VxType.body, color = Ink) },
        onClick = onClick
    )
}
