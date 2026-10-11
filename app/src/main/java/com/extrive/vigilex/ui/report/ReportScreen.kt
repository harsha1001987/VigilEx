package com.extrive.vigilex.ui.report

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.extrive.vigilex.data.insight.Interpretation
import com.extrive.vigilex.data.insight.interpret
import com.extrive.vigilex.data.api.ApiResult
import com.extrive.vigilex.data.api.ErrorKind
import com.extrive.vigilex.data.model.AnalysisResult
import com.extrive.vigilex.data.model.AssessmentRecord
import com.extrive.vigilex.data.model.ReportContentDto
import com.extrive.vigilex.data.repository.AssessmentRepository
import com.extrive.vigilex.ui.components.BackendErrorState
import com.extrive.vigilex.ui.components.BottomActionBar
import com.extrive.vigilex.ui.components.ButtonRow
import com.extrive.vigilex.ui.components.ErrorState
import com.extrive.vigilex.ui.components.Hairline
import com.extrive.vigilex.ui.components.IndeterminateTrack
import com.extrive.vigilex.ui.components.LoadingState
import com.extrive.vigilex.ui.components.LocalWindowClass
import com.extrive.vigilex.ui.components.PageContainer
import com.extrive.vigilex.ui.components.PrimaryButton
import com.extrive.vigilex.ui.components.SecondaryButton
import com.extrive.vigilex.ui.components.SubPageHeader
import com.extrive.vigilex.ui.components.WindowClass
import com.extrive.vigilex.ui.format.formatDateTime
import com.extrive.vigilex.ui.theme.Green
import com.extrive.vigilex.ui.theme.Ink
import com.extrive.vigilex.ui.theme.InkMuted
import com.extrive.vigilex.ui.theme.InkSecondary
import com.extrive.vigilex.ui.theme.Motion
import com.extrive.vigilex.ui.theme.Red
import com.extrive.vigilex.ui.theme.Space
import com.extrive.vigilex.ui.theme.VxType
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Keeps the preparing state on screen long enough to read, so it never flashes. */
private const val MIN_PREPARING_MS = 700L

private enum class Stage { CHECKING, INTRO, PREPARING, READY, FAILED }

private sealed interface Source {
    data object Loading : Source
    data class Failed(val error: ApiResult.Error) : Source
    data class Ready(
        val record: AssessmentRecord,
        val result: AnalysisResult,
        val interpretation: Interpretation
    ) : Source
}

/**
 * Turns an assessment into a document: what the report contains, one action,
 * a calm preparing state, then the report itself with export and share.
 * The server generates the PDF from the stored assessment; the preview is
 * drawn from the same stored analysis.
 */
@Composable
fun ReportScreen(id: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val compact = LocalWindowClass.current == WindowClass.COMPACT
    val repository = remember { AssessmentRepository() }
    val service = remember { ReportService(context.applicationContext, repository) }

    var attempt by remember(id) { mutableIntStateOf(0) }
    var source by remember(id) { mutableStateOf<Source>(Source.Loading) }
    LaunchedEffect(id, attempt) {
        source = Source.Loading
        source = when (val detail = repository.detail(id)) {
            is ApiResult.Success -> Source.Ready(detail.data.record, detail.data.result, interpret(detail.data.result))
            is ApiResult.Error -> Source.Failed(detail)
        }
    }

    var stage by rememberSaveable(id) { mutableStateOf(Stage.CHECKING) }
    var reportPath by rememberSaveable(id) { mutableStateOf<String?>(null) }
    var pageCount by rememberSaveable(id) { mutableStateOf(0) }
    var failure by rememberSaveable(id) { mutableStateOf<Pair<String, String>?>(null) }
    var exportNote by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    // What the server built the PDF from; the preview shows exactly this.
    var content by remember(id) { mutableStateOf<ReportContentDto?>(null) }
    val reportFile = reportPath?.let(::File)?.takeIf { it.exists() }
    // A report from a previous session may have been cleared from the cache,
    // and its content is not kept across process restarts: fetch it again.
    if (stage == Stage.READY && (reportFile == null || content == null)) stage = Stage.CHECKING

    val ready = source as? Source.Ready
    val record = ready?.record

    // A report generated earlier is shown straight away (GET /assessments/{id}/report).
    LaunchedEffect(id, stage, ready != null) {
        if (stage != Stage.CHECKING || ready == null) return@LaunchedEffect
        val existing = service.existing(id)
        if (existing != null) {
            reportPath = existing.file.absolutePath
            pageCount = existing.pageCount
            content = existing.metadata.content
            stage = Stage.READY
        } else {
            stage = Stage.INTRO
        }
    }

    fun generate() {
        if (ready == null) return
        stage = Stage.PREPARING
        exportNote = null
        failure = null
        scope.launch {
            val started = System.currentTimeMillis()
            val outcome = service.generate(id)
            delay((MIN_PREPARING_MS - (System.currentTimeMillis() - started)).coerceAtLeast(0))
            when (outcome) {
                is ReportOutcome.Ready -> {
                    reportPath = outcome.report.file.absolutePath
                    pageCount = outcome.report.pageCount
                    content = outcome.report.metadata.content
                    stage = Stage.READY
                }
                is ReportOutcome.Failed -> {
                    failure = failureText(outcome)
                    stage = Stage.FAILED
                }
            }
        }
    }

    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        val file = reportFile ?: return@rememberLauncherForActivityResult
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val ok = copyTo(context, file, uri)
            exportNote = if (ok) "PDF saved." to true else "The PDF could not be saved. Try another location." to false
        }
    }
    val export = { reportFile?.let { exporter.launch(it.name) }; Unit }
    val share = {
        reportFile?.let { file ->
            if (!sharePdf(context, file)) exportNote = "No app on this device can share a PDF." to false
        }
        Unit
    }

    Box(Modifier.fillMaxSize()) {
        PageContainer {
            SubPageHeader(
                context = "Report",
                meta = record?.let { "${it.fileName} · ${formatDateTime(it.createdAtMillis)}" },
                onBack = onBack
            )
            val loading = source
            when {
                loading is Source.Loading -> LoadingState()
                loading is Source.Failed -> BackendErrorState(
                    loading.error,
                    onRetry = { attempt++ },
                    notFoundTitle = "Assessment not found",
                    notFoundMessage = "This assessment is not on the VigilEx server, so no report can be made from it."
                )
                ready != null && record != null -> AnimatedContent(
                    targetState = stage,
                    transitionSpec = { fadeIn(tween(Motion.base)) togetherWith fadeOut(tween(Motion.fast)) },
                    label = "report"
                ) { current ->
                    Column(Modifier.fillMaxWidth()) {
                        when (current) {
                            Stage.CHECKING -> LoadingState()
                            Stage.INTRO -> Intro(
                                sections = reportSections(record.primaryProduct != null),
                                onGenerate = ::generate
                            )
                            Stage.PREPARING -> Preparing()
                            Stage.FAILED -> ErrorState(
                                title = failure?.first ?: "Report not created",
                                message = failure?.second ?: "VigilEx could not create the report. Try again.",
                                actions = { PrimaryButton("Try again", onClick = ::generate) }
                            )
                            Stage.READY -> content?.let { built ->
                                Preview(
                                    pageCount = pageCount,
                                    showActions = !compact,
                                    note = exportNote,
                                    onExport = export,
                                    onShare = share
                                ) { ReportDocument(record, ready.result, ready.interpretation, built) }
                            } ?: LoadingState()
                        }
                    }
                }
            }
            if (compact && stage == Stage.READY) Spacer(Modifier.height(140.dp))
        }
        if (compact && stage == Stage.READY && ready != null) {
            BottomActionBar(Modifier.align(Alignment.BottomCenter)) {
                exportNote?.let { NoteText(it) }
                Row {
                    PrimaryButton("Export PDF", onClick = export, modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(Space.sm))
                    SecondaryButton("Share", onClick = share, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

private fun failureText(failure: ReportOutcome.Failed): Pair<String, String> {
    val error = failure.error
    val title = when {
        failure.generated -> "PDF not downloaded"
        error.kind == ErrorKind.UNREACHABLE -> "Server unavailable"
        error.kind == ErrorKind.TIMEOUT -> "Server not responding"
        else -> "Report not created"
    }
    val message = when {
        failure.generated -> "The report was created on the server, but the PDF could not be downloaded. ${error.message}"
        error.kind == ErrorKind.SERVER -> "The VigilEx server could not create the report. ${error.detail ?: "Try again."}"
        else -> error.message
    }
    return title to message
}

@Composable
private fun Intro(sections: List<String>, onGenerate: () -> Unit) {
    Column(Modifier.widthIn(max = 640.dp)) {
        Text("GENERATE REPORT", style = VxType.label, color = InkMuted)
        Spacer(Modifier.height(Space.sm))
        Text("Ergonomic assessment", style = VxType.pageTitle, color = Ink)
        Spacer(Modifier.height(Space.sm))
        Text(
            "Turn this assessment into a concise document you can keep, print or send.",
            style = VxType.body,
            color = InkSecondary
        )
        Spacer(Modifier.height(Space.xxl))
        Text("YOUR REPORT WILL INCLUDE", style = VxType.label, color = Ink)
        Spacer(Modifier.height(Space.xs))
        Hairline(color = Ink)
        sections.forEachIndexed { i, name ->
            Row(Modifier.padding(vertical = Space.md), verticalAlignment = Alignment.CenterVertically) {
                val mark = if (i == sections.lastIndex) "A" else "%02d".format(i + 1)
                Text(mark, style = VxType.mono, color = InkMuted, modifier = Modifier.width(48.dp))
                Text(name, style = VxType.title, color = Ink)
            }
            Hairline()
        }
        Spacer(Modifier.height(Space.xl))
        PrimaryButton("Generate report", onClick = onGenerate)
        Spacer(Modifier.height(Space.sm))
        Text("PDF · A4 · created by the VigilEx server", style = VxType.mono, color = InkMuted)
    }
}

@Composable
private fun Preparing() {
    Column(
        Modifier
            .widthIn(max = 560.dp)
            .padding(top = Space.xxl)
            .semantics { liveRegion = LiveRegionMode.Polite }
    ) {
        Text("ERGONOMIC ASSESSMENT", style = VxType.label, color = InkMuted)
        Spacer(Modifier.height(Space.sm))
        Text("Preparing report", style = VxType.riskHeadlineCompact, color = Ink)
        Spacer(Modifier.height(Space.lg))
        Text(
            "A concise report is being prepared from your assessment.",
            style = VxType.body,
            color = InkSecondary
        )
        Spacer(Modifier.height(Space.xl))
        IndeterminateTrack()
    }
}

@Composable
private fun Preview(
    pageCount: Int,
    showActions: Boolean,
    note: Pair<String, Boolean>?,
    onExport: () -> Unit,
    onShare: () -> Unit,
    document: @Composable () -> Unit
) {
    Text("REPORT PREVIEW", style = VxType.label, color = InkMuted)
    Spacer(Modifier.height(Space.xs))
    Row(verticalAlignment = Alignment.Bottom) {
        Text("Your report is ready", style = VxType.pageTitleCompact, color = Ink, modifier = Modifier.weight(1f))
        if (pageCount > 0) Text("PDF · $pageCount ${if (pageCount == 1) "page" else "pages"}", style = VxType.mono, color = InkMuted)
    }
    if (showActions) {
        Spacer(Modifier.height(Space.lg))
        ButtonRow {
            PrimaryButton("Export PDF", onClick = onExport)
            SecondaryButton("Share", onClick = onShare)
        }
        note?.let {
            Spacer(Modifier.height(Space.xs))
            NoteText(it)
        }
    }
    Spacer(Modifier.height(Space.xl))
    Box(Modifier.widthIn(max = 820.dp).fillMaxWidth()) { document() }
}

@Composable
private fun NoteText(note: Pair<String, Boolean>) {
    Text(
        note.first,
        style = VxType.bodySmall,
        color = if (note.second) Green else Red,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
    )
}

private suspend fun copyTo(context: Context, file: File, target: Uri): Boolean = withContext(Dispatchers.IO) {
    try {
        context.contentResolver.openOutputStream(target)?.use { out -> file.inputStream().use { it.copyTo(out) } } != null
    } catch (e: Exception) {
        false
    }
}

private fun sharePdf(context: Context, file: File): Boolean {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.reports", file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "application/pdf"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, "VigilEx ergonomic assessment")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    return try {
        context.startActivity(Intent.createChooser(send, "Share report"))
        true
    } catch (e: ActivityNotFoundException) {
        false
    }
}
