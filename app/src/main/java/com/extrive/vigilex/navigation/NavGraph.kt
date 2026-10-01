package com.extrive.vigilex.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import com.extrive.vigilex.data.account.AccountSession
import com.extrive.vigilex.ui.components.AccountActions
import com.extrive.vigilex.ui.components.AppShell
import com.extrive.vigilex.ui.components.LocalAccountActions
import com.extrive.vigilex.ui.report.ReportScreen
import com.extrive.vigilex.ui.screens.AssessmentDetailScreen
import com.extrive.vigilex.ui.screens.AssessmentStudioScreen
import com.extrive.vigilex.ui.screens.CreateAccountScreen
import com.extrive.vigilex.ui.screens.DeveloperOptionsScreen
import com.extrive.vigilex.ui.screens.ForgotPasswordScreen
import com.extrive.vigilex.ui.screens.HistoryScreen
import com.extrive.vigilex.ui.screens.OverviewScreen
import com.extrive.vigilex.ui.screens.ProfileScreen
import com.extrive.vigilex.ui.screens.ReportsScreen
import com.extrive.vigilex.ui.screens.SettingsScreen
import com.extrive.vigilex.ui.screens.SignInScreen
import com.extrive.vigilex.ui.studio.AssessmentStudio
import com.extrive.vigilex.ui.studio.StudioState
import com.extrive.vigilex.ui.theme.Motion

/** Switches primary section, keeping each section's own back stack and scroll state. */
fun NavHostController.navigateTopLevel(destination: TopLevel) {
    navigate(destination.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/** Leaves the auth flow for the application, dropping the auth screens from the back stack. */
private fun NavHostController.enterApp() {
    navigate(TopLevel.OVERVIEW.route) {
        popUpTo(graph.findStartDestination().id)
        launchSingleTop = true
    }
}

/** Goes back, or to [fallback] when a deep link opened this screen with nothing behind it. */
private fun NavHostController.back(fallback: () -> Unit) {
    if (!popBackStack()) fallback()
}

/** Swaps one auth screen for another so Sign in ⇄ Create account never piles up. */
private fun NavHostController.switchAuth(from: String, to: String) {
    navigate(to) {
        popUpTo(from) { inclusive = true }
        launchSingleTop = true
    }
}

private fun NavGraphBuilder.screen(route: String, content: @Composable (NavBackStackEntry) -> Unit) {
    composable(route, deepLinks = listOf(navDeepLink { uriPattern = "$DEEP_LINK_BASE/$route" })) { content(it) }
}

@Composable
fun VigilExNavGraph(navController: NavHostController) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val route = backStackEntry?.destination?.route
    val studio by AssessmentStudio.state.collectAsState()

    val openDetail = { id: String -> navController.navigate(Routes.detail(id)) }
    val startAssessment = { navController.navigateTopLevel(TopLevel.ASSESSMENT) }
    val openProfile = { navController.navigate(Routes.PROFILE) { launchSingleTop = true } }
    val openSignIn = { navController.navigate(Routes.SIGN_IN) { launchSingleTop = true } }
    val openCreateAccount = { navController.navigate(Routes.CREATE_ACCOUNT) { launchSingleTop = true } }
    val openDeveloper = { navController.navigate(Routes.DEVELOPER) { launchSingleTop = true } }
    val signOut = {
        AccountSession.signOut()
        navController.enterApp()
    }
    val toOverview = { navController.navigateTopLevel(TopLevel.OVERVIEW) }

    val accountActions = remember(navController) {
        AccountActions(
            openProfile = openProfile,
            openSettings = { navController.navigate(Routes.SETTINGS) { launchSingleTop = true } },
            openSignIn = openSignIn,
            signOut = signOut
        )
    }

    CompositionLocalProvider(LocalAccountActions provides accountActions) {
        AppShell(
            current = topLevelFor(route),
            analysisRunning = studio is StudioState.Analyzing,
            onNavigate = navController::navigateTopLevel,
            showNavigation = route !in Routes.auth
        ) {
            NavHost(
                navController = navController,
                startDestination = TopLevel.OVERVIEW.route,
                enterTransition = { fadeIn(tween(Motion.base, easing = Motion.standard)) },
                exitTransition = { fadeOut(tween(Motion.fast)) },
                popEnterTransition = { fadeIn(tween(Motion.base, easing = Motion.standard)) },
                popExitTransition = { fadeOut(tween(Motion.fast)) }
            ) {
                // ---- Primary sections
                screen(TopLevel.OVERVIEW.route) {
                    OverviewScreen(
                        onStartAssessment = startAssessment,
                        onOpenAssessment = openDetail,
                        onOpenHistory = { navController.navigateTopLevel(TopLevel.HISTORY) }
                    )
                }
                screen(TopLevel.ASSESSMENT.route) {
                    AssessmentStudioScreen(onOpenDiagnostic = openDetail, onOpenDeveloperOptions = openDeveloper)
                }
                screen(TopLevel.HISTORY.route) {
                    HistoryScreen(onOpenAssessment = openDetail, onStartAssessment = startAssessment)
                }
                screen(TopLevel.REPORTS.route) {
                    ReportsScreen(onStartAssessment = startAssessment)
                }

                // ---- Assessment detail and its report
                composable(
                    Routes.DETAIL,
                    arguments = listOf(navArgument("id") { type = NavType.StringType }),
                    deepLinks = listOf(navDeepLink { uriPattern = "$DEEP_LINK_BASE/${Routes.DETAIL}" })
                ) { entry ->
                    val id = entry.arguments?.getString("id").orEmpty()
                    AssessmentDetailScreen(
                        id = id,
                        onBack = { navController.back { navController.navigateTopLevel(TopLevel.HISTORY) } },
                        onGenerateReport = { navController.navigate(Routes.report(id)) { launchSingleTop = true } }
                    )
                }
                composable(
                    Routes.REPORT,
                    arguments = listOf(navArgument("id") { type = NavType.StringType }),
                    deepLinks = listOf(navDeepLink { uriPattern = "$DEEP_LINK_BASE/${Routes.REPORT}" })
                ) { entry ->
                    val id = entry.arguments?.getString("id").orEmpty()
                    ReportScreen(id = id, onBack = { navController.back { navController.navigate(Routes.detail(id)) } })
                }

                // ---- Account area (reached from the header's account menu)
                screen(Routes.PROFILE) {
                    ProfileScreen(
                        onBack = { navController.back(toOverview) },
                        onSignIn = openSignIn,
                        onCreateAccount = openCreateAccount,
                        onSignOut = signOut
                    )
                }
                screen(Routes.SETTINGS) {
                    SettingsScreen(
                        onBack = { navController.back(toOverview) },
                        onOpenProfile = openProfile,
                        onOpenDeveloperOptions = openDeveloper
                    )
                }
                screen(Routes.DEVELOPER) {
                    DeveloperOptionsScreen(onBack = { navController.back(toOverview) })
                }

                // ---- Authentication (full screen, no navigation)
                screen(Routes.SIGN_IN) {
                    SignInScreen(
                        onClose = { navController.back(toOverview) },
                        onSignedIn = navController::enterApp,
                        onCreateAccount = { navController.switchAuth(Routes.SIGN_IN, Routes.CREATE_ACCOUNT) },
                        onForgotPassword = { navController.navigate(Routes.FORGOT_PASSWORD) { launchSingleTop = true } }
                    )
                }
                screen(Routes.CREATE_ACCOUNT) {
                    CreateAccountScreen(
                        onClose = { navController.back(toOverview) },
                        onCreated = navController::enterApp,
                        onSignIn = { navController.switchAuth(Routes.CREATE_ACCOUNT, Routes.SIGN_IN) }
                    )
                }
                screen(Routes.FORGOT_PASSWORD) {
                    ForgotPasswordScreen(
                        onBackToSignIn = {
                            val cameFromSignIn = navController.previousBackStackEntry?.destination?.route == Routes.SIGN_IN
                            if (cameFromSignIn) navController.popBackStack()
                            else navController.switchAuth(Routes.FORGOT_PASSWORD, Routes.SIGN_IN)
                        }
                    )
                }
            }
        }
    }
}
