package com.extrive.vigilex.ui.screens

import android.content.Context
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.extrive.vigilex.data.account.AccountSession
import com.extrive.vigilex.data.settings.ServerSettings
import com.extrive.vigilex.data.store.AssessmentStore
import com.extrive.vigilex.ui.components.PageContainer
import com.extrive.vigilex.ui.components.PageHeader
import com.extrive.vigilex.ui.components.SectionLabel
import com.extrive.vigilex.ui.components.SettingsRow
import com.extrive.vigilex.ui.theme.Ink
import com.extrive.vigilex.ui.theme.InkFaint
import com.extrive.vigilex.ui.theme.InkMuted
import com.extrive.vigilex.ui.theme.InkSecondary
import com.extrive.vigilex.ui.theme.Red
import com.extrive.vigilex.ui.theme.Space
import com.extrive.vigilex.ui.theme.VxType
import kotlinx.coroutines.launch

/** Taps on the version row that unlock Developer options in a release build. */
private const val DEVELOPER_TAPS = 7

/*
 * Only settings that work are shown. There is no Appearance section because
 * VigilEx has a single light theme; add it when a dark theme exists.
 */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenDeveloperOptions: () -> Unit
) {
    val account by AccountSession.current.collectAsState()
    val records by AssessmentStore.records.collectAsState()
    val loaded by AssessmentStore.loaded.collectAsState()
    val developer by ServerSettings.developerOptions.collectAsState()
    val context = LocalContext.current
    val version = remember { appVersion(context) }
    val scope = rememberCoroutineScope()

    var confirmClear by remember { mutableStateOf(false) }
    var showPrivacy by remember { mutableStateOf(false) }
    var versionTaps by remember { mutableIntStateOf(0) }

    PageContainer {
        PageHeader(eyebrow = "VigilEx", title = "Settings", onBack = onBack)

        SectionLabel("Account")
        val current = account
        if (current != null) {
            SettingsRow("Name", value = current.name)
            SettingsRow("Email", value = current.email)
            SettingsRow("Profile", onClick = onOpenProfile)
        } else {
            SettingsRow("Profile", value = "Not signed in", onClick = onOpenProfile)
        }
        Spacer(Modifier.height(Space.xxl))

        SectionLabel("Data")
        SettingsRow(
            "Saved assessments",
            value = if (loaded) records.size.toString() else null
        )
        SettingsRow(
            "Clear assessment history",
            labelColor = if (records.isNotEmpty()) Red else InkFaint,
            onClick = if (records.isNotEmpty()) ({ confirmClear = true }) else null
        )
        Spacer(Modifier.height(Space.sm))
        Text(
            "Assessments are saved on this device. Overview, History and Reports are built from them. " +
                "Your videos stay in your gallery or files.",
            style = VxType.bodySmall,
            color = InkMuted,
            modifier = Modifier.widthIn(max = 560.dp)
        )
        Spacer(Modifier.height(Space.xxl))

        SectionLabel("About")
        SettingsRow(
            "VigilEx",
            value = version?.let { "Version $it" },
            chevron = false,
            // Release builds unlock Developer options with repeated taps, as Android does.
            onClick = if (developer || version == null) null else ({
                versionTaps++
                if (versionTaps >= DEVELOPER_TAPS) ServerSettings.enableDeveloperOptions()
            })
        )
        SettingsRow("Privacy", onClick = { showPrivacy = true })
        SettingsRow("Terms", value = "Not available")

        if (developer) {
            Spacer(Modifier.height(Space.xxl))
            SettingsRow("Developer options", labelColor = InkMuted, onClick = onOpenDeveloperOptions)
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Delete all assessments?", style = VxType.sectionTitle) },
            text = { Text("This cannot be undone.", style = VxType.body, color = InkSecondary) },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    scope.launch { AssessmentStore.clear() }
                }) { Text("DELETE", style = VxType.labelLarge, color = Red) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) {
                    Text("CANCEL", style = VxType.labelLarge, color = Ink)
                }
            }
        )
    }

    if (showPrivacy) {
        AlertDialog(
            onDismissRequest = { showPrivacy = false },
            title = { Text("Privacy", style = VxType.sectionTitle) },
            text = {
                Text(
                    "When you analyze a video, VigilEx uploads it to the VigilEx analysis service to measure " +
                        "posture and calculate RULA and REBA. The assessment result is saved on this device " +
                        "and can be deleted at any time from Settings. The original video is not changed.",
                    style = VxType.body,
                    color = InkSecondary
                )
            },
            confirmButton = {
                TextButton(onClick = { showPrivacy = false }) {
                    Text("DONE", style = VxType.labelLarge, color = Ink)
                }
            }
        )
    }
}

/** The installed versionName, or null if it cannot be read; never a made-up value. */
private fun appVersion(context: Context): String? = try {
    @Suppress("DEPRECATION")
    context.packageManager.getPackageInfo(context.packageName, 0).versionName
} catch (e: Exception) {
    null
}
