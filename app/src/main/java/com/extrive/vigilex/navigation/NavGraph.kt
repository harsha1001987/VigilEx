package com.extrive.vigilex.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.navigation.NavType
import com.extrive.vigilex.ui.screens.AreaSelectionScreen
import com.extrive.vigilex.ui.screens.AssessmentSetupScreen
import com.extrive.vigilex.ui.screens.CaptureScreen
import com.extrive.vigilex.ui.screens.HomeScreen
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
    object Capture : Screen("capture")
}

@Composable
fun VigilExNavGraph(navController: NavHostController) {
    NavHost(
        navController = navController,
        startDestination = Screen.SignIn.route
    ) {
        composable(Screen.SignIn.route) {
            SignInScreen(
                onSignInSuccess = {
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
                onContinueToCapture = { navController.navigate(Screen.Capture.route) }
            )
        }

        composable(Screen.Capture.route) {
            CaptureScreen(
                onBackClick = { navController.popBackStack() }
            )
        }
    }
}
