package com.extrive.vigilex.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.extrive.vigilex.data.model.TaskDto
import com.extrive.vigilex.data.repository.TaskRepository
import com.extrive.vigilex.ui.components.BottomActionBar
import com.extrive.vigilex.ui.components.EmptyState
import com.extrive.vigilex.ui.components.ErrorState
import com.extrive.vigilex.ui.components.ListContainer
import com.extrive.vigilex.ui.components.LoadingState
import com.extrive.vigilex.ui.components.OverlineLabel
import com.extrive.vigilex.ui.components.PrimaryButton
import com.extrive.vigilex.ui.components.SelectionRow
import com.extrive.vigilex.ui.components.StepHeader
import com.extrive.vigilex.ui.components.VigilExTopBar
import com.extrive.vigilex.ui.state.UiState
import com.extrive.vigilex.ui.theme.BackgroundWhite

@Composable
fun TaskSelectionScreen(
    areaId: String,
    onBackClick: () -> Unit,
    onTaskSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val repository = remember { TaskRepository() }
    var uiState by remember { mutableStateOf<UiState<List<TaskDto>>>(UiState.Loading) }
    var retryTrigger by remember { mutableIntStateOf(0) }
    var selectedTaskId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(areaId, retryTrigger) {
        uiState = UiState.Loading
        uiState = when (val result = repository.getTasks(areaId)) {
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
                    onClick = { selectedTaskId?.let { onTaskSelected(it) } },
                    enabled = selectedTaskId != null
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
                    step = 3,
                    totalSteps = 3,
                    eyebrow = "Select task",
                    title = "What is being done?",
                    subtitle = "Select the task you will observe and record."
                )

                Spacer(modifier = Modifier.height(28.dp))

                when (val state = uiState) {
                    is UiState.Loading -> LoadingState()

                    is UiState.Error -> ErrorState(
                        message = state.message,
                        onRetry = { retryTrigger++ }
                    )

                    is UiState.Success -> {
                        OverlineLabel(text = "Tasks", modifier = Modifier.padding(horizontal = 20.dp))
                        Spacer(modifier = Modifier.height(10.dp))

                        if (state.data.isEmpty()) {
                            EmptyState(message = "This area has no tasks yet.")
                        } else {
                            ListContainer(modifier = Modifier.padding(horizontal = 20.dp)) {
                                state.data.forEachIndexed { index, task ->
                                    SelectionRow(
                                        title = task.name,
                                        subtitle = task.description.orEmpty(),
                                        isSelected = selectedTaskId == task.id,
                                        onClick = { selectedTaskId = task.id },
                                        showDivider = index < state.data.lastIndex
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
