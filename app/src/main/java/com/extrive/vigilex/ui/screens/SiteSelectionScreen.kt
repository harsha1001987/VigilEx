package com.extrive.vigilex.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.extrive.vigilex.data.api.ApiResult
import com.extrive.vigilex.data.model.SiteDto
import com.extrive.vigilex.data.repository.SiteRepository
import com.extrive.vigilex.ui.components.BottomActionBar
import com.extrive.vigilex.ui.components.EmptyState
import com.extrive.vigilex.ui.components.ErrorState
import com.extrive.vigilex.ui.components.ListContainer
import com.extrive.vigilex.ui.components.LoadingState
import com.extrive.vigilex.ui.components.OverlineLabel
import com.extrive.vigilex.ui.components.PrimaryButton
import com.extrive.vigilex.ui.components.SelectionRow
import com.extrive.vigilex.ui.components.StepHeader
import com.extrive.vigilex.ui.components.VigilExTextField
import com.extrive.vigilex.ui.components.VigilExTopBar
import com.extrive.vigilex.ui.state.UiState
import com.extrive.vigilex.ui.theme.BackgroundWhite

@Composable
fun SiteSelectionScreen(
    onBackClick: () -> Unit,
    onSiteSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val repository = remember { SiteRepository() }
    var uiState by remember { mutableStateOf<UiState<List<SiteDto>>>(UiState.Loading) }
    var retryTrigger by remember { mutableIntStateOf(0) }

    var searchQuery by remember { mutableStateOf("") }
    var selectedSiteId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(retryTrigger) {
        uiState = UiState.Loading
        uiState = when (val result = repository.getSites()) {
            is ApiResult.Success -> UiState.Success(result.data)
            is ApiResult.Error -> UiState.Error(result.message)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = BackgroundWhite,
        bottomBar = {
            BottomActionBar {
                PrimaryButton(
                    text = "Continue",
                    onClick = { selectedSiteId?.let { onSiteSelected(it) } },
                    enabled = selectedSiteId != null
                )
            }
        }
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
                Spacer(modifier = Modifier.height(8.dp))

                StepHeader(
                    step = 1,
                    totalSteps = 3,
                    eyebrow = "Select site",
                    title = "Where are you assessing?",
                    subtitle = "Choose the facility this task belongs to."
                )

                Spacer(modifier = Modifier.height(24.dp))

                VigilExTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = "Search sites",
                    leadingIcon = Icons.Outlined.Search,
                    filled = true,
                    modifier = Modifier.padding(horizontal = 20.dp)
                )

                Spacer(modifier = Modifier.height(28.dp))

                when (val state = uiState) {
                    is UiState.Loading -> LoadingState()

                    is UiState.Error -> ErrorState(
                        message = state.message,
                        onRetry = { retryTrigger++ }
                    )

                    is UiState.Success -> {
                        val filteredSites = state.data.filter { site ->
                            searchQuery.isBlank() ||
                                site.name.contains(searchQuery, ignoreCase = true) ||
                                (site.location?.contains(searchQuery, ignoreCase = true) == true)
                        }

                        OverlineLabel(
                            text = if (searchQuery.isBlank()) "All sites" else "Results",
                            modifier = Modifier.padding(horizontal = 20.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        if (state.data.isEmpty()) {
                            EmptyState(message = "No sites have been added yet.")
                        } else if (filteredSites.isEmpty()) {
                            EmptyState(message = "No sites match \"$searchQuery\".")
                        } else {
                            ListContainer(modifier = Modifier.padding(horizontal = 20.dp)) {
                                filteredSites.forEachIndexed { index, site ->
                                    SelectionRow(
                                        title = site.name,
                                        subtitle = site.location.orEmpty(),
                                        isSelected = selectedSiteId == site.id,
                                        onClick = { selectedSiteId = site.id },
                                        showDivider = index < filteredSites.lastIndex
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}
