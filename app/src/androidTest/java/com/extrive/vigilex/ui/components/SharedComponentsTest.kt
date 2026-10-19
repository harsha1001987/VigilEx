package com.extrive.vigilex.ui.components

import androidx.compose.material3.Text
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.extrive.vigilex.data.insight.RiskLevel
import com.extrive.vigilex.ui.theme.VigilExTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Behaviour of the shared components the redesigned screens rely on. */
@RunWith(AndroidJUnit4::class)
class SharedComponentsTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun expandableSectionHidesContentUntilOpened() {
        compose.setContent {
            VigilExTheme {
                ExpandableSection(title = "Methodology") { Text("Pose detected per frame") }
            }
        }
        compose.onNodeWithText("Pose detected per frame").assertDoesNotExist()
        compose.onNodeWithText("Methodology").performClick()
        compose.onNodeWithText("Pose detected per frame").assertIsDisplayed()
    }

    @Test
    fun riskTagStatesTheLevelInWords() {
        compose.setContent {
            VigilExTheme {
                RiskTag(RiskLevel.MODERATE)
                RiskTag(null)
            }
        }
        compose.onNodeWithText("Moderate risk").assertIsDisplayed()
        compose.onNodeWithText("Not measured").assertIsDisplayed()
    }

    @Test
    fun loadingPrimaryButtonKeepsItsLabelAndCannotBePressedAgain() {
        var clicks = 0
        compose.setContent {
            VigilExTheme {
                PrimaryButton("Sign in", onClick = { clicks++ }, loading = true)
            }
        }
        compose.onNodeWithText("Sign in").assertIsDisplayed()
        compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.Disabled)).assertIsNotEnabled()
        compose.onNodeWithText("Sign in").performClick()
        assertEquals(0, clicks)
    }
}
