package com.extrive.vigilex.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.extrive.vigilex.ui.theme.Black
import com.extrive.vigilex.ui.theme.VxType
import com.extrive.vigilex.ui.theme.Yellow

/** Yellow field, black wordmark. Nothing else. */
@Composable
fun SplashScreen(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxSize()
            .background(Yellow)
            .semantics { contentDescription = "VigilEx" },
        contentAlignment = Alignment.Center
    ) {
        // Tracking adds space after the last letter too; offset it so the word is optically centred.
        Text(
            "VIGILEX",
            style = VxType.splash,
            color = Black,
            modifier = Modifier.padding(start = VxType.splash.letterSpacing.value.dp)
        )
    }
}
