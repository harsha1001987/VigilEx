package com.extrive.vigilex.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.extrive.vigilex.data.api.ApiResult
import com.extrive.vigilex.data.model.AreaDto
import com.extrive.vigilex.data.model.AssessmentCreateRequest
import com.extrive.vigilex.data.model.SiteDto
import com.extrive.vigilex.data.model.TaskDto
import com.extrive.vigilex.data.repository.AreaRepository
import com.extrive.vigilex.data.repository.AssessmentRepository
import com.extrive.vigilex.data.repository.SiteRepository
import com.extrive.vigilex.data.repository.TaskRepository
import com.extrive.vigilex.ui.components.BottomActionBar
import com.extrive.vigilex.ui.components.ErrorState
import com.extrive.vigilex.ui.components.LoadingState
import com.extrive.vigilex.ui.components.OverlineLabel
import com.extrive.vigilex.ui.components.PrimaryButton
import com.extrive.vigilex.ui.components.RowDivider
import com.extrive.vigilex.ui.components.ListContainer
import com.extrive.vigilex.ui.components.VigilExTextField
import com.extrive.vigilex.ui.components.VigilExTopBar
import com.extrive.vigilex.ui.state.UiState
import com.extrive.vigilex.ui.theme.BackgroundWhite
import com.extrive.vigilex.ui.theme.RiskRed
import com.extrive.vigilex.ui.theme.TextMuted
import com.extrive.vigilex.ui.theme.TextPrimary
import com.extrive.vigilex.ui.theme.TextSecondary
import kotlinx.coroutines.launch

@Composable
fun AssessmentSetupScreen(
    siteId: String,
    areaId: String,
    taskId: String,
    onBackClick: () -> Unit,
    onContinueToCapture: (assessmentId: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val siteRepository = remember { SiteRepository() }
    val areaRepository = remember { AreaRepository() }
    val taskRepository = remember { TaskRepository() }
    val assessmentRepository = remember { AssessmentRepository() }
    val scope = rememberCoroutineScope()

    var siteState by remember { mutableStateOf<UiState<SiteDto>>(UiState.Loading) }
    var areaState by remember { mutableStateOf<UiState<AreaDto>>(UiState.Loading) }
    var taskState by remember { mutableStateOf<UiState<TaskDto>>(UiState.Loading) }
    var retryTrigger by remember { mutableIntStateOf(0) }

    var load by remember { mutableStateOf("") }
    var workerRef by remember { mutableStateOf("") }
    var loadFieldError by remember { mutableStateOf<String?>(null) }
    var creationState by remember { mutableStateOf<UiState<Unit>?>(null) }

    LaunchedEffect(siteId, areaId, taskId, retryTrigger) {
        siteState = UiState.Loading
        areaState = UiState.Loading
        taskState = UiState.Loading

        siteState = when (val result = siteRepository.getSite(siteId)) {
            is ApiResult.Success -> UiState.Success(result.data)
            is ApiResult.Error -> UiState.Error(result.message)
        }
        areaState = when (val result = areaRepository.getArea(areaId)) {
            is ApiResult.Success -> UiState.Success(result.data)
            is ApiResult.Error -> UiState.Error(result.message)
        }
        taskState = when (val result = taskRepository.getTask(taskId)) {
            is ApiResult.Success -> UiState.Success(result.data)
            is ApiResult.Error -> UiState.Error(result.message)
        }
    }

    val site = (siteState as? UiState.Success)?.data
    val area = (areaState as? UiState.Success)?.data
    val task = (taskState as? UiState.Success)?.data
    val contextLoaded = site != null && area != null && task != null
    val contextError = (siteState as? UiState.Error)?.message
        ?: (areaState as? UiState.Error)?.message
        ?: (taskState as? UiState.Error)?.message
    val contextLoading = siteState is UiState.Loading || areaState is UiState.Loading || taskState is UiState.Loading

    fun submit() {
        val organizationId = site?.organizationId ?: return
        val trimmedLoad = load.trim()
        val loadValue = if (trimmedLoad.isEmpty()) null else trimmedLoad.toDoubleOrNull()
        if (trimmedLoad.isNotEmpty() && loadValue == null) {
            loadFieldError = "Enter a valid number, e.g. 15"
            return
        }
        loadFieldError = null

        val request = AssessmentCreateRequest(
            organizationId = organizationId,
            siteId = siteId,
            areaId = areaId,
            taskId = taskId,
            status = "draft",
            loadValue = loadValue,
            loadUnit = if (loadValue != null) "kg" else null,
            loadSource = if (loadValue != null) "prompted" else null,
            consentGiven = false
        )

        scope.launch {
            creationState = UiState.Loading
            when (val result = assessmentRepository.createAssessment(request)) {
                is ApiResult.Success -> {
                    creationState = UiState.Success(Unit)
                    onContinueToCapture(result.data.id)
                }
                is ApiResult.Error -> creationState = UiState.Error(result.message)
            }
        }
    }

    val isSubmitting = creationState is UiState.Loading

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = BackgroundWhite,
        bottomBar = {
            BottomActionBar {
                val creationError = (creationState as? UiState.Error)?.message
                if (creationError != null) {
                    Text(
                        text = creationError,
                        style = MaterialTheme.typography.bodySmall,
                        color = RiskRed,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
                PrimaryButton(
                    text = if (isSubmitting) "Creating assessment..." else "Continue to capture",
                    onClick = { submit() },
                    enabled = contextLoaded && !isSubmitting,
                    leadingIcon = if (isSubmitting) null else Icons.Outlined.Videocam
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
        ) {
            VigilExTopBar(onBackClick = onBackClick)

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
            ) {
                Spacer(modifier = Modifier.height(8.dp))

                Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                    OverlineLabel(text = "New assessment")
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Review setup",
                        style = MaterialTheme.typography.headlineLarge,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Confirm what you are about to assess before capturing video.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextSecondary
                    )
                }

                Spacer(modifier = Modifier.height(28.dp))

                when {
                    contextLoading -> LoadingState()

                    contextError != null -> ErrorState(
                        message = contextError,
                        onRetry = { retryTrigger++ }
                    )

                    else -> {
                        OverlineLabel(text = "Context", modifier = Modifier.padding(horizontal = 20.dp))
                        Spacer(modifier = Modifier.height(10.dp))
                        ListContainer(modifier = Modifier.padding(horizontal = 20.dp)) {
                            SetupRow(label = "Site", value = site?.name ?: "—", detail = site?.location)
                            RowDivider()
                            SetupRow(label = "Area", value = area?.name ?: "—")
                            RowDivider()
                            SetupRow(label = "Task", value = task?.name ?: "—", detail = task?.description)
                        }

                        Spacer(modifier = Modifier.height(28.dp))

                        OverlineLabel(text = "Worker", modifier = Modifier.padding(horizontal = 20.dp))
                        Spacer(modifier = Modifier.height(10.dp))
                        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                            VigilExTextField(
                                value = workerRef,
                                onValueChange = { workerRef = it },
                                placeholder = "Optional · pseudonymous reference"
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Leave blank to record this assessment anonymously.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextMuted
                            )
                        }

                        Spacer(modifier = Modifier.height(28.dp))

                        OverlineLabel(text = "Load", modifier = Modifier.padding(horizontal = 20.dp))
                        Spacer(modifier = Modifier.height(10.dp))
                        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                            VigilExTextField(
                                value = load,
                                onValueChange = {
                                    load = it
                                    loadFieldError = null
                                },
                                placeholder = "Not specified · e.g. 15 kg",
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = loadFieldError
                                    ?: "Required for NIOSH lifting analysis. REBA and RULA run without it.",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (loadFieldError != null) RiskRed else TextMuted
                            )
                        }

                        Spacer(modifier = Modifier.height(32.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun SetupRow(label: String, value: String, detail: String? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            modifier = Modifier.width(72.dp)
        )
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = androidx.compose.ui.Alignment.End
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary
            )
            if (!detail.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
            }
        }
    }
}
