package com.extrive.vigilex.ui.components

import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.extrive.vigilex.data.insight.RegionFinding
import com.extrive.vigilex.data.insight.RegionStatus
import com.extrive.vigilex.data.model.BodyMetric
import com.extrive.vigilex.data.model.InterventionDto
import com.extrive.vigilex.ui.report.PdfPages
import com.extrive.vigilex.ui.theme.VigilExTheme
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The redesigned result findings and the report's page viewer. */
@RunWith(AndroidJUnit4::class)
class ResultsAndReportTest {

    @get:Rule
    val compose = createComposeRule()

    private fun finding(status: RegionStatus, maximum: Double?, average: Double?) = RegionFinding(
        metric = BodyMetric.TRUNK,
        status = status,
        average = average,
        maximum = maximum,
        framesMeasured = 37,
        framesFlagged = 37,
        peakFrameIndex = 4,
        title = "Pronounced trunk flexion",
        detail = "Observed consistently during the analyzed movement: 37 of 37 measured frames."
    )

    @Test
    fun findingStatesTitleStatusRegionAndExplanationInWords() {
        compose.setContent { VigilExTheme { WhatWeFound(listOf(finding(RegionStatus.ELEVATED, 57.4, 42.7))) } }
        compose.onNodeWithText("Pronounced trunk flexion", substring = true, useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("Elevated", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("Trunk · peak 57.4° · average 42.7°", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("37 of 37 measured frames", substring = true, useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun missingAnglesAreLeftOutRatherThanShownAsZero() {
        compose.setContent { VigilExTheme { WhatWeFound(listOf(finding(RegionStatus.NOT_MEASURED, null, null))) } }
        compose.onNodeWithText("Trunk", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("0.0°", substring = true, useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithText("Not measured", useUnmergedTree = true).assertIsDisplayed()
    }

    private fun action(title: String, area: String, status: String) = InterventionDto(
        area = area, priority = "high", status = status, title = title,
        observation = "Observed in 22 of 74 measured frames.", recommendation = "Recommendation for $title."
    )

    @Test
    fun recommendedActionsKeepTheBackendOrderUnderTheReportsSectionNames() {
        val actions = listOf(action("Protect the back", "back", "elevated"), action("Reduce neck bending", "neck", "attention"))
        compose.setContent { VigilExTheme { RecommendedActions(actions) } }
        compose.onNodeWithText("Priority action").assertIsDisplayed()
        compose.onNodeWithText("Protect the back").assertIsDisplayed()
        compose.onNodeWithText("Other actions").assertIsDisplayed()
        compose.onNodeWithText("Reduce neck bending").assertIsDisplayed()
    }

    @Test
    fun pdfPagesShowsTheFilesOwnPagesOneAtATime() {
        val file = twoPagePdf()
        compose.setContent { VigilExTheme { PdfPages(file, pageCount = 2) } }
        compose.waitUntil(5_000) {
            compose.onAllNodesWithContentDescriptionCount("Report page 1 of 2") == 1
        }
        compose.onNodeWithText("Page 1 of 2").assertIsDisplayed()
        compose.onNodeWithContentDescription("Previous page").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Next page").performClick()
        compose.onNodeWithText("Page 2 of 2").assertIsDisplayed()
        compose.onNodeWithContentDescription("Next page").assertIsNotEnabled()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithContentDescriptionCount("Report page 2 of 2") == 1
        }
    }

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onAllNodesWithContentDescriptionCount(text: String): Int =
        onAllNodes(androidx.compose.ui.test.hasContentDescription(text)).fetchSemanticsNodes().size

    /** A real two-page A4 PDF written with the platform's PdfDocument. */
    private fun twoPagePdf(): File {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.cacheDir, "test-report.pdf")
        val document = PdfDocument()
        repeat(2) { index ->
            val page = document.startPage(PdfDocument.PageInfo.Builder(595, 842, index + 1).create())
            page.canvas.drawText("Page ${index + 1}", 60f, 80f, Paint().apply { textSize = 24f })
            document.finishPage(page)
        }
        file.outputStream().use(document::writeTo)
        document.close()
        return file
    }
}
