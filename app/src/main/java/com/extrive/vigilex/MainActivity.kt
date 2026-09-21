package com.extrive.vigilex

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.navigation.compose.rememberNavController
import com.extrive.vigilex.navigation.VigilExNavGraph
import com.extrive.vigilex.ui.theme.VigilExTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            VigilExTheme {
                val navController = rememberNavController()
                VigilExNavGraph(navController = navController)
            }
        }
    }
}
