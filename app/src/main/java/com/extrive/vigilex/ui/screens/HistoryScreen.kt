package com.extrive.vigilex.ui.screens

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
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
import com.extrive.vigilex.data.api.ApiResult
import com.extrive.vigilex.data.repository.AssessmentRepository
import com.extrive.vigilex.data.repository.HistoryPage
import com.extrive.vigilex.data.store.AssessmentStore
import com.extrive.vigilex.ui.components.AssessmentList
import com.extrive.vigilex.ui.components.BackendErrorState
import com.extrive.vigilex.ui.components.Callout
import com.extrive.vigilex.ui.components.CalloutTone
import com.extrive.vigilex.ui.components.EmptyState
import com.extrive.vigilex.ui.components.LoadingState
import com.extrive.vigilex.ui.components.PageContainer
import com.extrive.vigilex.ui.components.PageHeader
import com.extrive.vigilex.ui.components.SecondaryButton
import com.extrive.vigilex.ui.state.UiState
import com.extrive.vigilex.ui.theme.InkMuted
import com.extrive.vigilex.ui.theme.Space
import com.extrive.vigilex.ui.theme.VxType
import kotlinx.coroutines.launch

@Composable
fun HistoryScreen(
    onOpenAssessment: (String) -> Unit,
    onStartAssessment: () -> Unit
) {
    val repository = remember { AssessmentRepository() }
    val scope = rememberCoroutineScope()
    val revision by AssessmentStore.revision.collectAsState()
    var attempt by remember { mutableIntStateOf(0) }
    var state by remember { mutableStateOf<UiState<HistoryPage>>(UiState.Loading) }
    var error by remember { mutableStateOf<ApiResult.Error?>(null) }
    var loadingMore by remember { mutableStateOf(false) }
    var moreError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(revision, attempt) {
        if (state !is UiState.Success) state = UiState.Loading
        when (val result = repository.history()) {
            is ApiResult.Success -> { state = UiState.Success(result.data); error = null }
            is ApiResult.Error -> { state = UiState.Error(result.message); error = result }
        }
    }

    fun loadMore(current: HistoryPage) {
        loadingMore = true
        moreError = null
        scope.launch {
            when (val next = repository.history(offset = current.records.size)) {
                is ApiResult.Success -> state = UiState.Success(
                    HistoryPage(current.records + next.data.records, next.data.total, 0)
                )
                is ApiResult.Error -> moreError = next.message
            }
            loadingMore = false
        }
    }

    PageContainer {
        PageHeader(
            title = "History",
            supporting = "Completed assessments saved to your account, newest first."
        )
        when (val current = state) {
            UiState.Loading -> LoadingState(label = "Loading history…")
            is UiState.Error -> error?.let { BackendErrorState(it, onRetry = { attempt++ }) }
            is UiState.Success -> {
                val page = current.data
                if (page.records.isEmpty()) {
                    EmptyState(
                        title = "No assessments yet",
                        body = "Completed assessments will appear here, newest first.",
                        actionLabel = "Start assessment",
                        onAction = onStartAssessment
                    )
                } else {
                    Text(
                        if (page.total == 1) "1 assessment" else "${page.total} assessments",
                        style = VxType.bodySmall,
                        color = InkMuted
                    )
                    Spacer(Modifier.height(Space.sm))
                    AssessmentList(page.records, onOpen = { onOpenAssessment(it.id) })
                    if (page.hasMore) {
                        Spacer(Modifier.height(Space.lg))
                        SecondaryButton(
                            text = "Load more",
                            onClick = { loadMore(page) },
                            loading = loadingMore,
                            modifier = Modifier.fillMaxWidth()
                        )
                        moreError?.let {
                            Spacer(Modifier.height(Space.xs))
                            Callout(title = "More assessments could not be loaded", body = it, tone = CalloutTone.ERROR)
                        }
                    }
                }
            }
        }
    }
}
