package com.extrive.vigilex.ui.screens

import android.content.Context
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Text
import com.extrive.vigilex.ui.components.Callout
import com.extrive.vigilex.ui.components.CalloutTone
import com.extrive.vigilex.ui.components.ConfirmDialog
import com.extrive.vigilex.ui.components.InfoDialog
import com.extrive.vigilex.ui.components.SettingsGroup
import com.extrive.vigilex.ui.format.EMPTY_VALUE
import com.extrive.vigilex.ui.theme.RedInk
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.extrive.vigilex.data.api.ApiResult
import com.extrive.vigilex.data.repository.AssessmentRepository
import com.extrive.vigilex.data.settings.ServerSettings
import com.extrive.vigilex.data.store.AssessmentStore
import com.extrive.vigilex.ui.components.PageContainer
import com.extrive.vigilex.ui.components.PageHeader
import com.extrive.vigilex.ui.components.SectionLabel
import com.extrive.vigilex.ui.components.SettingsRow
import com.extrive.vigilex.ui.theme.InkMuted
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
    val repository = remember { AssessmentRepository() }
    val revision by AssessmentStore.revision.collectAsState()
    // Completed assessments on the server; null until known or when the server is unreachable.
    var savedCount by remember { mutableStateOf<Int?>(null) }
    var clearing by remember { mutableStateOf(false) }
    var clearNote by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(revision) {
        savedCount = (repository.history(limit = 1) as? ApiResult.Success)?.data?.total
    }
    val developer by ServerSettings.developerOptions.collectAsState()
    val context = LocalContext.current
    val version = remember { appVersion(context) }
    val scope = rememberCoroutineScope()

    var confirmClear by remember { mutableStateOf(false) }
    var showPrivacy by remember { mutableStateOf(false) }
    var versionTaps by remember { mutableIntStateOf(0) }

    PageContainer {
        PageHeader(title = "Settings", onBack = onBack)

        SectionLabel("Account")
        SettingsGroup {
            val current = account
            SettingsRow(
                "Profile",
                supporting = current?.email ?: "Not signed in",
                icon = Icons.Outlined.Person,
                divider = false,
                onClick = onOpenProfile
            )
        }
        Spacer(Modifier.height(Space.lg))

        SectionLabel("Data")
        val canClear = (savedCount ?: 0) > 0 && !clearing
        SettingsGroup {
            SettingsRow(
                "Saved assessments",
                icon = Icons.Outlined.Inventory2,
                value = savedCount?.toString() ?: EMPTY_VALUE
            )
            SettingsRow(
                if (clearing) "Clearing assessment history…" else "Clear assessment history",
                supporting = "Deletes every assessment and report on your account",
                icon = Icons.Outlined.DeleteOutline,
                labelColor = if (canClear) RedInk else InkMuted,
                chevron = false,
                divider = false,
                onClick = if (canClear) ({ confirmClear = true }) else null
            )
        }
        clearNote?.let {
            Spacer(Modifier.height(Space.xs))
            Callout(title = "Not everything was deleted", body = it, tone = CalloutTone.ERROR)
        }
        Spacer(Modifier.height(Space.xs))
        Text(
            "Assessments are saved to your account on the VigilEx server; Overview, History and Reports are built " +
                "from them. Videos on this device stay in your gallery or files.",
            style = VxType.bodySmall,
            color = InkMuted,
            modifier = Modifier.widthIn(max = 560.dp)
        )
        Spacer(Modifier.height(Space.lg))

        SectionLabel("About")
        SettingsGroup {
            SettingsRow(
                "VigilEx",
                icon = Icons.Outlined.Info,
                value = version?.let { "Version $it" },
                chevron = false,
                // Release builds unlock Developer options with repeated taps, as Android does.
                onClick = if (developer || version == null) null else ({
                    versionTaps++
                    if (versionTaps >= DEVELOPER_TAPS) ServerSettings.enableDeveloperOptions()
                })
            )
            SettingsRow("Privacy", icon = Icons.Outlined.Shield, divider = developer, onClick = { showPrivacy = true })
            if (developer) {
                SettingsRow(
                    "Developer options",
                    supporting = "Analysis server address",
                    icon = Icons.Outlined.Code,
                    divider = false,
                    onClick = onOpenDeveloperOptions
                )
            }
        }
    }

    if (confirmClear) {
        ConfirmDialog(
            title = "Delete all assessments?",
            message = "Every assessment and report on your account is permanently deleted. This cannot be undone.",
            confirmLabel = "Delete all",
            destructive = true,
            onDismiss = { confirmClear = false },
            onConfirm = {
                confirmClear = false
                clearing = true
                clearNote = null
                scope.launch {
                    clearNote = clearAll(repository)
                    clearing = false
                }
            }
        )
    }

    if (showPrivacy) {
        InfoDialog(
            title = "Privacy",
            message = "When you analyze a video, VigilEx uploads it to the VigilEx server you are signed in to, " +
                "which measures posture and calculates RULA and REBA. The assessment is saved to your account on " +
                "that server. You can delete an assessment from its page, or all of them here in Settings. " +
                "The video on this device is not changed. Signing out removes downloaded reports from this device.",
            onDismiss = { showPrivacy = false }
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

/** Deletes every completed assessment on the server. Returns a message when not all could be deleted. */
private suspend fun clearAll(repository: AssessmentRepository): String? {
    val records = when (val all = repository.allCompleted()) {
        is ApiResult.Success -> all.data
        is ApiResult.Error -> return "Nothing was deleted. ${all.message}"
    }
    val failed = records.count { repository.delete(it.id) is ApiResult.Error }
    if (failed == 0) AssessmentStore.cleared() else AssessmentStore.changed()
    return if (failed == 0) null else "$failed of ${records.size} assessments could not be deleted. Try again."
}
