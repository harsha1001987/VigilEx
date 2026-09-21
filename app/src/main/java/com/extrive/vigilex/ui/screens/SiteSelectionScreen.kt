package com.extrive.vigilex.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.unit.dp
import com.extrive.vigilex.data.mock.MockData
import com.extrive.vigilex.ui.components.SelectionRow
import com.extrive.vigilex.ui.components.VigilExButton
import com.extrive.vigilex.ui.components.VigilExTextField
import com.extrive.vigilex.ui.components.VigilExTopBar
import com.extrive.vigilex.ui.theme.BackgroundWhite
import com.extrive.vigilex.ui.theme.DividerColor
import com.extrive.vigilex.ui.theme.SurfaceSubtle
import com.extrive.vigilex.ui.theme.TextMuted
import com.extrive.vigilex.ui.theme.TextSecondary

@Composable
fun SiteSelectionScreen(
    onBackClick: () -> Unit,
    onSiteSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedSiteId by remember { mutableStateOf<String?>(null) }

    val filteredSites = remember(searchQuery) {
        if (searchQuery.isBlank()) MockData.sites
        else MockData.sites.filter {
            it.name.contains(searchQuery, ignoreCase = true) ||
            it.subtitle.contains(searchQuery, ignoreCase = true)
        }
    }

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
                        .padding(top = 8.dp, bottom = 20.dp)
                ) {
                    Text(
                        text = "Select a site",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onBackground,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Default,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Where are you performing this assessment?",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                ) {
                    VigilExTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = "Search sites"
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (filteredSites.isEmpty()) {
                    Text(
                        text = "No sites found",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextMuted,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(SurfaceSubtle)
                    ) {
                        filteredSites.forEachIndexed { index, site ->
                            SelectionRow(
                                title = site.name,
                                subtitle = site.subtitle,
                                isSelected = selectedSiteId == site.id,
                                onClick = { selectedSiteId = site.id },
                                showDivider = index < filteredSites.size - 1
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }

            // Continue button
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            ) {
                HorizontalDivider(color = DividerColor, thickness = 1.dp, modifier = Modifier.padding(bottom = 16.dp))
                VigilExButton(
                    text = "Continue",
                    onClick = { selectedSiteId?.let { onSiteSelected(it) } },
                    enabled = selectedSiteId != null
                )
            }
        }
    }
}
