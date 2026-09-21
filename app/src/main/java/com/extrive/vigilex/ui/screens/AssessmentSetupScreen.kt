package com.extrive.vigilex.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.extrive.vigilex.data.mock.MockData
import com.extrive.vigilex.ui.components.VigilExButton
import com.extrive.vigilex.ui.components.VigilExTextField
import com.extrive.vigilex.ui.components.VigilExTopBar
import com.extrive.vigilex.ui.theme.BackgroundWhite
import com.extrive.vigilex.ui.theme.DividerColor
import com.extrive.vigilex.ui.theme.SurfaceSubtle
import com.extrive.vigilex.ui.theme.TextMuted
import com.extrive.vigilex.ui.theme.TextPrimary
import com.extrive.vigilex.ui.theme.TextSecondary

@Composable
fun AssessmentSetupScreen(
    siteId: String,
    areaId: String,
    taskId: String,
    onBackClick: () -> Unit,
    onContinueToCapture: () -> Unit,
    modifier: Modifier = Modifier
) {
    val site = MockData.sites.find { it.id == siteId }
    val area = MockData.areas.find { it.id == areaId }
    val task = MockData.tasks.find { it.id == taskId }

    var load by remember { mutableStateOf("") }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = BackgroundWhite
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            VigilExTopBar(onBackClick = onBackClick)

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .padding(top = 8.dp, bottom = 24.dp)
                ) {
                    Text(
                        text = "Assessment setup",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onBackground,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Review your selection before continuing.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }

                // Summary card
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(SurfaceSubtle)
                ) {
                    SetupRow(label = "Site", value = site?.name ?: "—")
                    HorizontalDivider(color = DividerColor, thickness = 1.dp, modifier = Modifier.padding(horizontal = 20.dp))
                    SetupRow(label = "Area", value = area?.name ?: "—")
                    HorizontalDivider(color = DividerColor, thickness = 1.dp, modifier = Modifier.padding(horizontal = 20.dp))
                    SetupRow(label = "Task", value = task?.name ?: "—")
                    HorizontalDivider(color = DividerColor, thickness = 1.dp, modifier = Modifier.padding(horizontal = 20.dp))
                    SetupRow(label = "Worker", value = "Worker 001")
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Load input
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                ) {
                    Text(
                        text = "Load (optional)",
                        style = MaterialTheme.typography.labelLarge,
                        color = TextSecondary,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    VigilExTextField(
                        value = load,
                        onValueChange = { load = it },
                        placeholder = "e.g. 15 kg"
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Required for NIOSH lift equation analysis.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            ) {
                HorizontalDivider(color = DividerColor, thickness = 1.dp, modifier = Modifier.padding(bottom = 16.dp))
                VigilExButton(
                    text = "Continue to capture",
                    onClick = onContinueToCapture
                )
            }
        }
    }
}

@Composable
private fun SetupRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = TextPrimary,
            fontWeight = FontWeight.Medium
        )
    }
}
