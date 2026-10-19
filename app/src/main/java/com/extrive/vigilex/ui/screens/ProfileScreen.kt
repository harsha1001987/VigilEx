package com.extrive.vigilex.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.extrive.vigilex.data.account.AccountSession
import com.extrive.vigilex.ui.components.Avatar
import com.extrive.vigilex.ui.components.ConfirmDialog
import com.extrive.vigilex.ui.components.EmptyState
import com.extrive.vigilex.ui.components.PageContainer
import com.extrive.vigilex.ui.components.PageHeader
import com.extrive.vigilex.ui.components.PrimaryButton
import com.extrive.vigilex.ui.components.SecondaryButton
import com.extrive.vigilex.ui.components.SettingsGroup
import com.extrive.vigilex.ui.components.SettingsRow
import com.extrive.vigilex.ui.components.VxCard
import com.extrive.vigilex.ui.theme.Ink
import com.extrive.vigilex.ui.theme.InkMuted
import com.extrive.vigilex.ui.theme.RedInk
import com.extrive.vigilex.ui.theme.Space
import com.extrive.vigilex.ui.theme.VxType

@Composable
fun ProfileScreen(
    onBack: () -> Unit,
    onSignIn: () -> Unit,
    onCreateAccount: () -> Unit,
    onSignOut: () -> Unit
) {
    val account by AccountSession.current.collectAsState()
    var confirmSignOut by remember { mutableStateOf(false) }

    PageContainer {
        PageHeader(title = "Profile", onBack = onBack)

        val current = account
        if (current != null) {
            // Name and email are shown once, here.
            VxCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Avatar(current, size = 56.dp, textStyle = VxType.sectionTitle)
                    Spacer(Modifier.width(Space.md))
                    Column(Modifier.weight(1f)) {
                        Text(current.name, style = VxType.sectionTitle, color = Ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(current.email, style = VxType.bodySmall, color = InkMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            Spacer(Modifier.height(Space.sm))
            Text(
                "Your assessments and reports are saved to this account on the VigilEx server. Signing out " +
                    "removes downloaded reports and the assessment on screen from this device.",
                style = VxType.bodySmall,
                color = InkMuted,
                modifier = Modifier.widthIn(max = 520.dp)
            )
            Spacer(Modifier.height(Space.lg))
            SettingsGroup {
                SettingsRow(
                    "Sign out",
                    icon = Icons.AutoMirrored.Outlined.Logout,
                    labelColor = RedInk,
                    chevron = false,
                    divider = false,
                    onClick = { confirmSignOut = true }
                )
            }
        } else {
            EmptyState(
                title = "Not signed in",
                body = "Sign in to see your assessments. They are saved to your account on the VigilEx server."
            )
            Spacer(Modifier.height(Space.md))
            PrimaryButton(text = "Sign in", onClick = onSignIn, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(Space.sm))
            SecondaryButton(text = "Create account", onClick = onCreateAccount, modifier = Modifier.fillMaxWidth())
        }
    }

    if (confirmSignOut) {
        ConfirmDialog(
            title = "Sign out?",
            message = "Downloaded reports are removed from this device. Your assessments stay saved to your account.",
            confirmLabel = "Sign out",
            onDismiss = { confirmSignOut = false },
            onConfirm = {
                confirmSignOut = false
                onSignOut()
            }
        )
    }
}
