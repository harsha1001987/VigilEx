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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.extrive.vigilex.data.api.ApiResult
import com.extrive.vigilex.data.api.ErrorKind
import com.extrive.vigilex.data.model.AssessmentRecord
import com.extrive.vigilex.data.repository.AssessmentRepository
import com.extrive.vigilex.ui.components.ArrowLink
import com.extrive.vigilex.ui.components.BackendErrorState
import com.extrive.vigilex.ui.components.BottomActionBar
import com.extrive.vigilex.ui.components.ButtonRow
import com.extrive.vigilex.ui.components.ErrorState
import com.extrive.vigilex.ui.components.SectionLabel
import com.extrive.vigilex.ui.components.Tag
import com.extrive.vigilex.ui.components.VxCard
import com.extrive.vigilex.ui.components.IndeterminateTrack
import com.extrive.vigilex.ui.components.LoadingState
import com.extrive.vigilex.ui.components.LocalWindowClass
import com.extrive.vigilex.ui.components.PageContainer
import com.extrive.vigilex.ui.components.PrimaryButton
import com.extrive.vigilex.ui.components.SecondaryButton
import com.extrive.vigilex.ui.components.SubPageHeader
import com.extrive.vigilex.ui.components.WindowClass
import com.extrive.vigilex.ui.format.formatRelativeDateTime
import com.extrive.vigilex.ui.theme.Green
import com.extrive.vigilex.ui.theme.Ink
import com.extrive.vigilex.ui.theme.InkMuted
import com.extrive.vigilex.ui.theme.InkSecondary
import com.extrive.vigilex.ui.theme.Motion
import com.extrive.vigilex.ui.theme.RedInk
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
    data class Ready(val record: AssessmentRecord) : Source
}

/**
 * Turns an assessment into a document: whether a report exists, one action to
 * generate it, a preparing state, then the PDF itself with export and share.
 * The server generates the PDF from the stored assessment; the preview shows
 * the downloaded file's own pages, so it is exactly what is exported.
 */
@Composable
fun ReportScreen(id: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val compact = LocalWindowClass.current == WindowClass.COMPACT
    val density = LocalDensity.current
    var barHeight by remember { mutableIntStateOf(0) }
    val repository = remember { AssessmentRepository() }
    val service = remember { ReportService(context.applicationContext, repository) }

    var attempt by remember(id) { mutableIntStateOf(0) }
    var source by remember(id) { mutableStateOf<Source>(Source.Loading) }
    LaunchedEffect(id, attempt) {
        source = Source.Loading
        source = when (val detail = repository.detail(id)) {
            is ApiResult.Success -> Source.Ready(detail.data.record)
            is ApiResult.Error -> Source.Failed(detail)
        }
    }

    var stage by rememberSaveable(id) { mutableStateOf(Stage.CHECKING) }
    var reportPath by rememberSaveable(id) { mutableStateOf<String?>(null) }
    var pageCount by rememberSaveable(id) { mutableStateOf(0) }
    var failure by rememberSaveable(id) { mutableStateOf<Pair<String, String>?>(null) }
    var exportNote by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    // True only while this screen's own generate request is running.
    var generating by remember(id) { mutableStateOf(false) }
    val reportFile = reportPath?.let(::File)?.takeIf { it.exists() }
    // A report from a previous session may have been cleared from the cache: fetch it again.
    if (stage == Stage.READY && reportFile == null) stage = Stage.CHECKING
    // Recreated while generating (the request ended with the old screen): ask the server what exists.
    if (stage == Stage.PREPARING && !generating) stage = Stage.CHECKING

    val ready = source as? Source.Ready
    val record = ready?.record

    // A report generated earlier is shown straight away (GET /assessments/{id}/report).
    LaunchedEffect(id, stage, ready != null) {
        if (stage != Stage.CHECKING || ready == null) return@LaunchedEffect
        val existing = service.existing(id)
        if (existing != null) {
            reportPath = existing.file.absolutePath
            pageCount = existing.pageCount
            stage = Stage.READY
        } else {
            stage = Stage.INTRO
        }
    }

    fun generate() {
        // One request at a time: repeated taps never start a second generation.
        if (ready == null || generating) return
        generating = true
        stage = Stage.PREPARING
        exportNote = null
        failure = null
        scope.launch {
            val started = System.currentTimeMillis()
            val outcome = service.generate(id)
            delay((MIN_PREPARING_MS - (System.currentTimeMillis() - started)).coerceAtLeast(0))
            generating = false
            when (outcome) {
                is ReportOutcome.Ready -> {
                    reportPath = outcome.report.file.absolutePath
                    pageCount = outcome.report.pageCount
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
            exportNote = if (ok) "PDF saved to the location you chose." to true else "The PDF could not be saved. Try another location." to false
        }
    }
    val export = { reportFile?.let { exporter.launch(it.name) }; Unit }
    val share = {
        reportFile?.let { file ->
            if (!sharePdf(context, file)) exportNote = "No app on this device can share a PDF." to false
        }
        Unit
    }
    val open = {
        reportFile?.let { file ->
            if (!openPdf(context, file)) exportNote = "No app on this device can open a PDF. Export it instead." to false
        }
        Unit
    }

    Box(Modifier.fillMaxSize()) {
        PageContainer {
            SubPageHeader(
                context = "PDF report",
                meta = record?.let { "${it.fileName} · ${formatRelativeDateTime(it.createdAtMillis)}" },
                onBack = onBack
            )
            val loading = source
            when {
                loading is Source.Loading -> LoadingState(label = "Loading assessment…")
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
                            Stage.CHECKING -> LoadingState(label = "Checking for an existing report…")
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
                            Stage.READY -> reportFile?.let { file ->
                                Preview(
                                    pageCount = pageCount,
                                    showActions = !compact,
                                    note = exportNote,
                                    onExport = export,
                                    onShare = share,
                                    onOpen = open
                                ) { PdfPages(file, pageCount) }
                            } ?: LoadingState()
                        }
                    }
                }
            }
            // Room for the action bar, however tall large text makes it.
            if (compact && stage == Stage.READY) Spacer(Modifier.height(with(density) { barHeight.toDp() } + Space.md))
        }
        if (compact && stage == Stage.READY && ready != null) {
            BottomActionBar(
                Modifier
                    .align(Alignment.BottomCenter)
                    .onSizeChanged { barHeight = it.height }
            ) {
                exportNote?.let { NoteText(it) }
                Row {
                    PrimaryButton("Export PDF", onClick = export, modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(Space.sm))
                    SecondaryButton("Share report", onClick = share, modifier = Modifier.weight(1f))
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
        Tag("No report yet", InkMuted, hollow = true, textColor = InkMuted)
        Spacer(Modifier.height(Space.sm))
        Text("Generate a PDF report", style = VxType.pageTitleCompact, color = Ink)
        Spacer(Modifier.height(Space.xxs))
        Text(
            "No report has been made for this assessment yet. The VigilEx server builds an A4 PDF from the " +
                "saved assessment, which you can then export, print or share.",
            style = VxType.body,
            color = InkSecondary
        )
        Spacer(Modifier.height(Space.lg))
        PrimaryButton("Generate PDF report", onClick = onGenerate, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(Space.xl))
        SectionLabel("The report includes")
        VxCard {
            sections.forEachIndexed { i, name ->
                Row(Modifier.padding(vertical = Space.xs), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (i == sections.lastIndex) "Appendix" else "${i + 1}", style = VxType.label, color = InkMuted, modifier = Modifier.width(64.dp))
                    Text(name, style = VxType.body, color = Ink)
                }
            }
        }
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
        Tag("Generating", Ink)
        Spacer(Modifier.height(Space.sm))
        Text("Generating report", style = VxType.pageTitleCompact, color = Ink)
        Spacer(Modifier.height(Space.xxs))
        Text(
            "The server is building the PDF from the stored assessment, then it is downloaded to this device.",
            style = VxType.body,
            color = InkSecondary
        )
        Spacer(Modifier.height(Space.lg))
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
    onOpen: () -> Unit,
    document: @Composable () -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Tag("Ready", Ink)
        if (pageCount > 0) {
            Spacer(Modifier.width(Space.sm))
            Text("PDF · $pageCount ${if (pageCount == 1) "page" else "pages"}", style = VxType.bodySmall, color = InkMuted)
        }
    }
    Spacer(Modifier.height(Space.xs))
    Text("PDF report ready", style = VxType.pageTitleCompact, color = Ink)
    Text(
        "These are the PDF's own pages. Export saves a copy to a location you choose; Share sends it to another app.",
        style = VxType.bodySmall,
        color = InkSecondary
    )
    ArrowLink("Open in a PDF app", onClick = onOpen)
    if (showActions) {
        Spacer(Modifier.height(Space.sm))
        ButtonRow {
            PrimaryButton("Export PDF", onClick = onExport)
            SecondaryButton("Share report", onClick = onShare)
        }
        note?.let {
            Spacer(Modifier.height(Space.xs))
            NoteText(it)
        }
    }
    Spacer(Modifier.height(Space.lg))
    Box(Modifier.widthIn(max = 720.dp).fillMaxWidth()) { document() }
}

@Composable
private fun NoteText(note: Pair<String, Boolean>) {
    Text(
        note.first,
        style = VxType.bodySmall,
        color = if (note.second) Green else RedInk,
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

/** Opens the PDF in an installed viewer through the same FileProvider URI used for sharing. */
private fun openPdf(context: Context, file: File): Boolean {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.reports", file)
    val view = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "application/pdf")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    return try {
        context.startActivity(view)
        true
    } catch (e: ActivityNotFoundException) {
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
