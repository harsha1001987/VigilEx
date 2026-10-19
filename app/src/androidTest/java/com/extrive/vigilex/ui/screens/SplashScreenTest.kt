package com.extrive.vigilex.ui.screens

import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.extrive.vigilex.ui.theme.SplashYellow
import com.extrive.vigilex.ui.theme.VigilExTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The original launch splash: a yellow field with the black VIGILEX wordmark, nothing else. */
@RunWith(AndroidJUnit4::class)
class SplashScreenTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun showsTheOriginalWordmarkOnTheOriginalYellow() {
        compose.setContent { VigilExTheme { SplashScreen() } }
        compose.onNodeWithText("VIGILEX", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithContentDescription("VigilEx").assertIsDisplayed()
        // Same value as @color/vigilex_yellow, so window, system splash and this screen match.
        assertEquals(0xFFFFD21F.toInt(), SplashYellow.toArgb())
    }
}
