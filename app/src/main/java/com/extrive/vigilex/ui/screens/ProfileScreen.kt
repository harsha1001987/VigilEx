package com.extrive.vigilex.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.extrive.vigilex.data.account.AccountSession
import com.extrive.vigilex.ui.components.Avatar
import com.extrive.vigilex.ui.components.ButtonRow
import com.extrive.vigilex.ui.components.PageContainer
import com.extrive.vigilex.ui.components.PageHeader
import com.extrive.vigilex.ui.components.PrimaryButton
import com.extrive.vigilex.ui.components.SecondaryButton
import com.extrive.vigilex.ui.components.SectionLabel
import com.extrive.vigilex.ui.components.SettingsRow
import com.extrive.vigilex.ui.theme.Ink
import com.extrive.vigilex.ui.theme.InkMuted
import com.extrive.vigilex.ui.theme.InkSecondary
import com.extrive.vigilex.ui.theme.Red
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

    PageContainer {
        PageHeader(eyebrow = "Account", title = "Profile", onBack = onBack)

        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(account, size = 72.dp, textStyle = VxType.sectionTitle)
            Spacer(Modifier.width(Space.lg))
            Column(Modifier.weight(1f)) {
                Text(
                    account?.name ?: "Not signed in",
                    style = VxType.sectionTitle,
                    color = Ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                account?.let {
                    Spacer(Modifier.height(Space.xxs))
                    Text(it.email, style = VxType.body, color = InkMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        Spacer(Modifier.height(Space.xxl))

        SectionLabel("Account")
        val current = account
        if (current != null) {
            SettingsRow("Name", value = current.name)
            SettingsRow("Email", value = current.email)
            Spacer(Modifier.height(Space.xl))
            SettingsRow("Sign out", labelColor = Red, onClick = onSignOut)
        } else {
            Text(
                "VigilEx works without an account. Your assessments are saved on this device.",
                style = VxType.body,
                color = InkSecondary,
                modifier = Modifier.widthIn(max = 520.dp)
            )
            Spacer(Modifier.height(Space.lg))
            SettingsRow("Name", value = "Not available")
            SettingsRow("Email", value = "Not available")
            Spacer(Modifier.height(Space.xl))
            ButtonRow {
                PrimaryButton(text = "Sign in", onClick = onSignIn)
                SecondaryButton(text = "Create account", onClick = onCreateAccount)
            }
        }
    }
}
