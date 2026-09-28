package com.extrive.vigilex.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.extrive.vigilex.data.api.ApiResult
import com.extrive.vigilex.data.model.ProcessVideoRequest
import com.extrive.vigilex.data.repository.AssessmentRepository
import com.extrive.vigilex.ui.components.ErrorState
import com.extrive.vigilex.ui.components.OverlineLabel
import com.extrive.vigilex.ui.theme.BackgroundWhite
import com.extrive.vigilex.ui.theme.DividerColor
import com.extrive.vigilex.ui.theme.OnYellow
import com.extrive.vigilex.ui.theme.SurfaceSubtle
import com.extrive.vigilex.ui.theme.TextMuted
import com.extrive.vigilex.ui.theme.TextPrimary
import com.extrive.vigilex.ui.theme.TextSecondary
import com.extrive.vigilex.ui.theme.VigilExYellow
import kotlinx.coroutines.delay

private val analysisSteps = listOf(
    "Uploading video & detecting pose landmarks",
    "Tracking worker movement with ByteTrack",
    "Calculating RULA and REBA posture scores",
    "Persisting assessment into PostgreSQL"
)

/**
 * Loading/Processing screen between video capture and results.
 * Invokes backend multipart upload or server video processing endpoint
 * (YOLO11n-Pose -> ByteTrack -> RULA/REBA -> PostgreSQL) and displays execution progress.
 */
@Composable
fun AnalysisScreen(
    assessmentId: String,
    videoPath: String? = null,
    onAnalysisComplete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val assessmentRepository = remember { AssessmentRepository() }
    var completedSteps by remember { mutableIntStateOf(0) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var retryTrigger by remember { mutableIntStateOf(0) }

    val actualVideoPath = videoPath ?: "backend/tests/19832490-hd_1920_1080_25fps (1).mp4"
    val isDeviceUri = actualVideoPath.startsWith("content://") || actualVideoPath.startsWith("file://")

    LaunchedEffect(assessmentId, actualVideoPath, retryTrigger) {
        completedSteps = 0
        errorMessage = null

        // Step 1: Upload / Detect
        delay(300)
        completedSteps = 1

        // Step 2: Track
        delay(400)
        completedSteps = 2

        val result = if (isDeviceUri) {
            assessmentRepository.uploadVideo(context, assessmentId, actualVideoPath)
        } else {
            val request = ProcessVideoRequest(videoPath = actualVideoPath, strideHz = 25.0)
            assessmentRepository.processVideo(assessmentId, request)
        }

        when (result) {
            is ApiResult.Success -> {
                completedSteps = 3
                delay(300)
                completedSteps = 4
                delay(300)
                onAnalysisComplete()
            }
            is ApiResult.Error -> {
                errorMessage = result.message
            }
        }
    }

    val progress by animateFloatAsState(
        targetValue = completedSteps / analysisSteps.size.toFloat(),
        animationSpec = tween(durationMillis = 500),
        label = "analysisProgress"
    )

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = BackgroundWhite
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.Center
        ) {
            OverlineLabel(text = "Analysis")
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "Processing\nassessment",
                style = MaterialTheme.typography.displayMedium,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = if (isDeviceUri) "Uploading video & running YOLO11n-Pose analysis." else "Running YOLO11n-Pose & ByteTrack analysis.",
                style = MaterialTheme.typography.bodyLarge,
                color = TextSecondary
            )

            Spacer(modifier = Modifier.height(40.dp))

            if (errorMessage != null) {
                ErrorState(
                    message = errorMessage ?: "Processing failed",
                    onRetry = { retryTrigger++ }
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(DividerColor)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress.coerceIn(0.02f, 1f))
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(VigilExYellow)
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))

                analysisSteps.forEachIndexed { index, step ->
                    val done = index < completedSteps
                    val active = index == completedSteps
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(
                                    when {
                                        done -> VigilExYellow
                                        active -> TextPrimary
                                        else -> SurfaceSubtle
                                    }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (done) {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = null,
                                    tint = OnYellow,
                                    modifier = Modifier.size(14.dp)
                                )
                            } else if (active) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(VigilExYellow)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = step,
                            style = MaterialTheme.typography.bodyLarge,
                            color = when {
                                done || active -> TextPrimary
                                else -> TextMuted
                            }
                        )
                    }
                }
            }
        }
    }
}
