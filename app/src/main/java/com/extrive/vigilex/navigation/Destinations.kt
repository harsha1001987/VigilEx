package com.extrive.vigilex.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.ui.graphics.vector.ImageVector

const val DEEP_LINK_BASE = "vigilex://app"

/** The four primary sections. Order is the order shown in navigation. */
enum class TopLevel(val route: String, val label: String, val icon: ImageVector) {
    OVERVIEW("overview", "Overview", Icons.Outlined.Dashboard),
    ASSESSMENT("assessment", "Assessment", Icons.Outlined.Videocam),
    HISTORY("history", "History", Icons.Outlined.History),
    REPORTS("reports", "Reports", Icons.Outlined.BarChart)
}

object Routes {
    const val DETAIL = "assessment/{id}"
    const val REPORT = "assessment/{id}/report"
    const val PROFILE = "profile"
    const val SETTINGS = "settings"
    const val DEVELOPER = "settings/developer"

    const val SIGN_IN = "sign-in"
    const val CREATE_ACCOUNT = "create-account"
    const val FORGOT_PASSWORD = "forgot-password"

    /** Full-screen routes drawn without the navigation shell. */
    val auth = setOf(SIGN_IN, CREATE_ACCOUNT, FORGOT_PASSWORD)

    fun detail(id: String) = "assessment/$id"
    fun report(id: String) = "assessment/$id/report"
}

/** Which primary section a route belongs to, for the navigation's active state. */
fun topLevelFor(route: String?): TopLevel? = when {
    route == null -> null
    route == Routes.DETAIL || route == Routes.REPORT -> TopLevel.HISTORY
    else -> TopLevel.entries.firstOrNull { it.route == route }
}
