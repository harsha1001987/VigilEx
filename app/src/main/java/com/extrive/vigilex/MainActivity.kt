package com.extrive.vigilex

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.extrive.vigilex.data.account.AccountSession
import com.extrive.vigilex.data.account.AuthRepository
import com.extrive.vigilex.data.account.KeystoreSessionStore
import com.extrive.vigilex.data.settings.ServerSettings
import com.extrive.vigilex.data.store.AssessmentStore
import com.extrive.vigilex.navigation.VigilExNavGraph
import com.extrive.vigilex.ui.screens.SplashScreen
import com.extrive.vigilex.ui.studio.AssessmentStudio
import com.extrive.vigilex.ui.theme.Motion
import com.extrive.vigilex.ui.theme.VigilExTheme
import com.extrive.vigilex.ui.report.ReportService
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val SPLASH_HOLD_MS = 1100L

/** Outlives the activity: session checks and sign-out cleanup must finish across rotation. */
private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        )
        ServerSettings.init(this)
        AssessmentStore.init(this)
        AssessmentStudio.init(this)
        startSession()

        // Only a fresh launch shows the splash, not rotation or process restore.
        val coldStart = savedInstanceState == null

        setContent {
            VigilExTheme {
                var showSplash by rememberSaveable { mutableStateOf(coldStart) }
                LaunchedEffect(Unit) {
                    if (showSplash) {
                        delay(SPLASH_HOLD_MS)
                        showSplash = false
                    }
                }
                Box(Modifier.fillMaxSize()) {
                    VigilExNavGraph(navController = rememberNavController())
                    AnimatedVisibility(
                        visible = showSplash,
                        enter = EnterTransition.None,
                        exit = fadeOut(tween(Motion.slow, easing = Motion.exit))
                    ) {
                        SplashScreen()
                    }
                }
            }
        }
    }

    /**
     * Restores the saved session (expired ones are discarded) and confirms it
     * with the server in the background. Every sign-out removes the previous
     * account's data from this device: downloaded report PDFs, the video
     * links of its assessments and any analysis on screen.
     */
    private fun startSession() {
        if (AccountSession.onSignedOut == null) {
            val cacheDir = applicationContext.cacheDir
            AccountSession.onSignedOut = {
                AssessmentStudio.reset()
                appScope.launch {
                    File(cacheDir, ReportService.REPORT_DIR).deleteRecursively()
                    AssessmentStore.cleared()
                }
            }
            AccountSession.init(KeystoreSessionStore(this))
        }
        if (AccountSession.isSignedIn) appScope.launch { AuthRepository().refreshAccount() }
    }
}
