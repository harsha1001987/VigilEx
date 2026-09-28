package com.extrive.vigilex.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.extrive.vigilex.data.session.UserSession
import com.extrive.vigilex.ui.screens.AnalysisScreen
import com.extrive.vigilex.ui.screens.AreaSelectionScreen
import com.extrive.vigilex.ui.screens.AssessmentSetupScreen
import com.extrive.vigilex.ui.screens.CaptureScreen
import com.extrive.vigilex.ui.screens.HomeScreen
import com.extrive.vigilex.ui.screens.ResultsScreen
import com.extrive.vigilex.ui.screens.SignInScreen
import com.extrive.vigilex.ui.screens.SiteSelectionScreen
import com.extrive.vigilex.ui.screens.TaskSelectionScreen

sealed class Screen(val route: String) {
    object SignIn : Screen("sign_in")
    object Home : Screen("home")
    object SiteSelection : Screen("site_selection")
    object AreaSelection : Screen("area_selection/{siteId}") {
        fun createRoute(siteId: String) = "area_selection/$siteId"
    }
    object TaskSelection : Screen("task_selection/{siteId}/{areaId}") {
        fun createRoute(siteId: String, areaId: String) = "task_selection/$siteId/$areaId"
    }
    object AssessmentSetup : Screen("assessment_setup/{siteId}/{areaId}/{taskId}") {
        fun createRoute(siteId: String, areaId: String, taskId: String) =
            "assessment_setup/$siteId/$areaId/$taskId"
    }
    object Capture : Screen("capture/{assessmentId}") {
        fun createRoute(assessmentId: String) = "capture/$assessmentId"
    }
    object Analysis : Screen("analysis/{assessmentId}?videoPath={videoPath}") {
        fun createRoute(assessmentId: String, videoPath: String? = null) =
            if (!videoPath.isNull_or_empty()) "analysis/$assessmentId?videoPath=$videoPath" else "analysis/$assessmentId"
    }
    object Results : Screen("results/{assessmentId}") {
        fun createRoute(assessmentId: String) = "results/$assessmentId"
    }
}

private fun String?.isNull_or_empty() = this == null || this.isEmpty()

@Composable
fun VigilExNavGraph(navController: NavHostController) {
    NavHost(
        navController = navController,
        startDestination = Screen.SignIn.route
    ) {
        composable(Screen.SignIn.route) {
            SignInScreen(
                onSignInSuccess = { name ->
                    UserSession.userName = name
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.SignIn.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Home.route) {
            HomeScreen(
                onStartAssessmentClick = {
                    navController.navigate(Screen.SiteSelection.route)
                },
                onSignOut = {
                    UserSession.userName = ""
                    navController.navigate(Screen.SignIn.route) {
                        popUpTo(Screen.SignIn.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.SiteSelection.route) {
            SiteSelectionScreen(
                onBackClick = { navController.popBackStack() },
                onSiteSelected = { siteId ->
                    navController.navigate(Screen.AreaSelection.createRoute(siteId))
                }
            )
        }

        composable(
            route = Screen.AreaSelection.route,
            arguments = listOf(navArgument("siteId") { type = NavType.StringType })
        ) { backStackEntry ->
            val siteId = backStackEntry.arguments?.getString("siteId") ?: ""
            AreaSelectionScreen(
                siteId = siteId,
                onBackClick = { navController.popBackStack() },
                onAreaSelected = { areaId ->
                    navController.navigate(Screen.TaskSelection.createRoute(siteId, areaId))
                }
            )
        }

        composable(
            route = Screen.TaskSelection.route,
            arguments = listOf(
                navArgument("siteId") { type = NavType.StringType },
                navArgument("areaId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val siteId = backStackEntry.arguments?.getString("siteId") ?: ""
            val areaId = backStackEntry.arguments?.getString("areaId") ?: ""
            TaskSelectionScreen(
                areaId = areaId,
                onBackClick = { navController.popBackStack() },
                onTaskSelected = { taskId ->
                    navController.navigate(Screen.AssessmentSetup.createRoute(siteId, areaId, taskId))
                }
            )
        }

        composable(
            route = Screen.AssessmentSetup.route,
            arguments = listOf(
                navArgument("siteId") { type = NavType.StringType },
                navArgument("areaId") { type = NavType.StringType },
                navArgument("taskId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val siteId = backStackEntry.arguments?.getString("siteId") ?: ""
            val areaId = backStackEntry.arguments?.getString("areaId") ?: ""
            val taskId = backStackEntry.arguments?.getString("taskId") ?: ""
            AssessmentSetupScreen(
                siteId = siteId,
                areaId = areaId,
                taskId = taskId,
                onBackClick = { navController.popBackStack() },
                onContinueToCapture = { assessmentId ->
                    navController.navigate(Screen.Capture.createRoute(assessmentId))
                }
            )
        }

        composable(
            route = Screen.Capture.route,
            arguments = listOf(navArgument("assessmentId") { type = NavType.StringType })
        ) { backStackEntry ->
            val assessmentId = backStackEntry.arguments?.getString("assessmentId") ?: ""
            CaptureScreen(
                assessmentId = assessmentId,
                onBackClick = { navController.popBackStack() },
                onContinueToAnalysis = { videoPath ->
                    navController.navigate(Screen.Analysis.createRoute(assessmentId, videoPath))
                }
            )
        }

        composable(
            route = Screen.Analysis.route,
            arguments = listOf(
                navArgument("assessmentId") { type = NavType.StringType },
                navArgument("videoPath") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val assessmentId = backStackEntry.arguments?.getString("assessmentId") ?: ""
            val videoPath = backStackEntry.arguments?.getString("videoPath")
            AnalysisScreen(
                assessmentId = assessmentId,
                videoPath = videoPath,
                onAnalysisComplete = {
                    navController.navigate(Screen.Results.createRoute(assessmentId)) {
                        popUpTo(Screen.Analysis.route) { inclusive = true }
                    }
                }
            )
        }

        composable(
            route = Screen.Results.route,
            arguments = listOf(navArgument("assessmentId") { type = NavType.StringType })
        ) { backStackEntry ->
            val assessmentId = backStackEntry.arguments?.getString("assessmentId") ?: ""
            ResultsScreen(
                assessmentId = assessmentId,
                onBackClick = { navController.popBackStack() },
                onGenerateReport = { /* Report generation - not implemented yet */ },
                onDone = {
                    navController.popBackStack(Screen.Home.route, inclusive = false)
                }
            )
        }
    }
}
