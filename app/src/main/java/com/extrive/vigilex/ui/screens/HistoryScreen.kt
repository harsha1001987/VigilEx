package com.extrive.vigilex.ui.screens

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.extrive.vigilex.data.store.AssessmentStore
import com.extrive.vigilex.ui.components.AssessmentList
import com.extrive.vigilex.ui.components.EmptyState
import com.extrive.vigilex.ui.components.LoadingState
import com.extrive.vigilex.ui.components.PageContainer
import com.extrive.vigilex.ui.components.PageHeader
import com.extrive.vigilex.ui.theme.InkMuted
import com.extrive.vigilex.ui.theme.Space
import com.extrive.vigilex.ui.theme.VxType

@Composable
fun HistoryScreen(
    onOpenAssessment: (String) -> Unit,
    onStartAssessment: () -> Unit
) {
    val records by AssessmentStore.records.collectAsState()
    val loaded by AssessmentStore.loaded.collectAsState()

    PageContainer {
        PageHeader(
            eyebrow = "History",
            title = "Assessment history",
            supporting = "Completed video assessments saved on this device."
        )
        when {
            !loaded -> LoadingState()
            records.isEmpty() -> EmptyState(
                title = "No assessments yet",
                body = "Completed assessments will appear here.",
                actionLabel = "Start assessment",
                onAction = onStartAssessment
            )
            else -> {
                Text(
                    if (records.size == 1) "1 assessment" else "${records.size} assessments",
                    style = VxType.mono,
                    color = InkMuted
                )
                Spacer(Modifier.height(Space.md))
                AssessmentList(records, onOpen = { onOpenAssessment(it.id) })
            }
        }
    }
}
