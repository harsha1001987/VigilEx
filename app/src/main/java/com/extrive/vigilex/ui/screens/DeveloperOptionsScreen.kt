package com.extrive.vigilex.ui.screens

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.extrive.vigilex.data.repository.AnalysisRepository
import com.extrive.vigilex.data.settings.ServerSettings
import com.extrive.vigilex.data.settings.normalizeServerUrl
import com.extrive.vigilex.ui.components.ButtonRow
import com.extrive.vigilex.ui.components.PageContainer
import com.extrive.vigilex.ui.components.PageHeader
import com.extrive.vigilex.ui.components.PrimaryButton
import com.extrive.vigilex.ui.components.SecondaryButton
import com.extrive.vigilex.ui.components.SectionLabel
import com.extrive.vigilex.ui.components.VxTextField
import com.extrive.vigilex.ui.theme.Green
import com.extrive.vigilex.ui.theme.InkMuted
import com.extrive.vigilex.ui.theme.InkSecondary
import com.extrive.vigilex.ui.theme.Red
import com.extrive.vigilex.ui.theme.Space
import com.extrive.vigilex.ui.theme.VxType
import kotlinx.coroutines.launch

private enum class ConnectionCheck { IDLE, CHECKING, OK, FAILED }

/**
 * Developer-only network configuration: which VigilEx server the app sends
 * videos to. Reached from Settings only in debug builds or once unlocked.
 */
@Composable
fun DeveloperOptionsScreen(onBack: () -> Unit) {
    val serverUrl by ServerSettings.serverUrl.collectAsState()
    val scope = rememberCoroutineScope()
    val repository = remember { AnalysisRepository() }

    var input by rememberSaveable(serverUrl) { mutableStateOf(serverUrl) }
    var inputError by remember { mutableStateOf<String?>(null) }
    var check by remember { mutableStateOf(ConnectionCheck.IDLE) }

    fun validated(): String? {
        val normalized = normalizeServerUrl(input)
        inputError = if (normalized == null) "Enter an address such as http://192.168.1.20:8000" else null
        return normalized
    }

    fun save() {
        val normalized = validated() ?: return
        input = normalized
        ServerSettings.setServerUrl(normalized)
    }

    PageContainer {
        PageHeader(eyebrow = "Settings", title = "Developer options", onBack = onBack)

        SectionLabel("Analysis server")
        Text(
            "The server that receives videos for analysis. Emulator: http://10.0.2.2:8000. Phone on USB: " +
                "http://127.0.0.1:8000, forwarded to the computer by debug builds (adb reverse). Phone on " +
                "Wi-Fi only: the computer's network address, e.g. http://192.168.1.20:8000.",
            style = VxType.bodySmall,
            color = InkSecondary,
            modifier = Modifier.widthIn(max = 560.dp)
        )
        Spacer(Modifier.height(Space.lg))
        VxTextField(
            label = "Server address",
            value = input,
            onValueChange = {
                input = it
                inputError = null
                check = ConnectionCheck.IDLE
            },
            error = inputError,
            keyboardType = KeyboardType.Uri,
            imeAction = ImeAction.Done,
            onImeAction = { save() },
            textStyle = VxType.metricSmall
        )
        Spacer(Modifier.height(Space.xs))
        if (inputError == null) {
            when {
                check == ConnectionCheck.CHECKING -> Text("Checking…", style = VxType.bodySmall, color = InkMuted)
                check == ConnectionCheck.OK -> Text("Server reachable.", style = VxType.bodySmall, color = Green)
                check == ConnectionCheck.FAILED -> Text(
                    "No response from this address. Check that the server is running and reachable from this device.",
                    style = VxType.bodySmall,
                    color = Red
                )
                input != serverUrl -> Text("Not saved.", style = VxType.bodySmall, color = InkMuted)
                else -> Text("Saved.", style = VxType.bodySmall, color = InkMuted)
            }
        }
        Spacer(Modifier.height(Space.lg))
        ButtonRow {
            PrimaryButton(text = "Save", onClick = { save() }, enabled = input != serverUrl)
            SecondaryButton(
                text = "Test connection",
                enabled = check != ConnectionCheck.CHECKING,
                onClick = {
                    // Tests the typed address without saving it.
                    val url = validated() ?: return@SecondaryButton
                    check = ConnectionCheck.CHECKING
                    scope.launch {
                        check = if (repository.isServerReachable(url)) ConnectionCheck.OK else ConnectionCheck.FAILED
                    }
                }
            )
            if (serverUrl != ServerSettings.defaultUrl) {
                SecondaryButton(text = "Use default", onClick = {
                    ServerSettings.resetServerUrl()
                    input = ServerSettings.defaultUrl
                    check = ConnectionCheck.IDLE
                })
            }
        }
    }
}
