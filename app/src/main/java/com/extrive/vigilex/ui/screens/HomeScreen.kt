package com.extrive.vigilex.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Apartment
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Logout
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.extrive.vigilex.R
import com.extrive.vigilex.data.mock.AssessmentStatus
import com.extrive.vigilex.data.mock.MockData
import com.extrive.vigilex.data.mock.Report
import com.extrive.vigilex.data.session.UserSession
import com.extrive.vigilex.ui.components.AssessmentCard
import com.extrive.vigilex.ui.components.HomeTab
import com.extrive.vigilex.ui.components.ListContainer
import com.extrive.vigilex.ui.components.MetricCard
import com.extrive.vigilex.ui.components.OverlineLabel
import com.extrive.vigilex.ui.components.ProfileHeader
import com.extrive.vigilex.ui.components.RiskBadge
import com.extrive.vigilex.ui.components.RowDivider
import com.extrive.vigilex.ui.components.SectionHeader
import com.extrive.vigilex.ui.components.VigilExBottomNavigation
import com.extrive.vigilex.ui.theme.BackgroundWhite
import com.extrive.vigilex.ui.theme.BorderSubtle
import com.extrive.vigilex.ui.theme.DividerColor
import com.extrive.vigilex.ui.theme.OnYellow
import com.extrive.vigilex.ui.theme.RiskOrange
import com.extrive.vigilex.ui.theme.RiskRed
import com.extrive.vigilex.ui.theme.SurfaceSubtle
import com.extrive.vigilex.ui.theme.TextMuted
import com.extrive.vigilex.ui.theme.TextPrimary
import com.extrive.vigilex.ui.theme.TextSecondary
import com.extrive.vigilex.ui.theme.VigilExYellow
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen(
    onStartAssessmentClick: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTab by rememberSaveable { mutableStateOf(HomeTab.HOME) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = BackgroundWhite,
        bottomBar = {
            VigilExBottomNavigation(
                selected = selectedTab,
                onSelect = { selectedTab = it }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (selectedTab) {
                HomeTab.HOME -> HomeTabContent(
                    onStartAssessmentClick = onStartAssessmentClick,
                    onSeeAllAssessments = { selectedTab = HomeTab.ASSESSMENTS }
                )
                HomeTab.ASSESSMENTS -> AssessmentsTabContent()
                HomeTab.REPORTS -> ReportsTabContent()
                HomeTab.PROFILE -> ProfileTabContent(onSignOut = onSignOut)
            }
        }
    }
}

// ------------------------------------------------------------------ Home tab

private fun timeOfDayGreeting(): String {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    return when {
        hour < 12 -> "Good morning"
        hour < 17 -> "Good afternoon"
        else -> "Good evening"
    }
}

@Composable
private fun HomeTabContent(
    onStartAssessmentClick: () -> Unit,
    onSeeAllAssessments: () -> Unit
) {
    val userName = UserSession.userName
    val today = remember { SimpleDateFormat("EEE, d MMM", Locale.getDefault()).format(Date()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 18.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = painterResource(id = R.drawable.vigilex_logo),
                contentDescription = "VigilEx",
                modifier = Modifier.height(22.dp),
                contentScale = ContentScale.Fit
            )
            Text(
                text = today,
                style = MaterialTheme.typography.labelMedium,
                color = TextMuted
            )
        }

        // Greeting
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(top = 12.dp, bottom = 28.dp)
        ) {
            Text(
                text = if (userName.isNotBlank()) "${timeOfDayGreeting()},\n$userName" else timeOfDayGreeting(),
                style = MaterialTheme.typography.displaySmall,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "Here's your safety overview.",
                style = MaterialTheme.typography.bodyLarge,
                color = TextSecondary
            )
        }

        // Primary CTA
        StartAssessmentCard(
            onClick = onStartAssessmentClick,
            modifier = Modifier.padding(horizontal = 20.dp)
        )

        Spacer(modifier = Modifier.height(28.dp))

        // Summary metrics
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            MetricCard(
                label = "Assessments",
                value = MockData.metrics.totalAssessments.toString(),
                caption = "This month",
                modifier = Modifier.weight(1f)
            )
            MetricCard(
                label = "Moderate",
                value = MockData.metrics.moderateRisk.toString().padStart(2, '0'),
                valueColor = RiskOrange,
                caption = "Risk flagged",
                modifier = Modifier.weight(1f)
            )
            MetricCard(
                label = "Reports",
                value = MockData.metrics.reports.toString(),
                caption = "Generated",
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Needs attention
        SectionHeader(
            title = "Needs attention",
            modifier = Modifier.padding(horizontal = 20.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))
        AttentionCard(modifier = Modifier.padding(horizontal = 20.dp))

        Spacer(modifier = Modifier.height(32.dp))

        // Recent activity
        SectionHeader(
            title = "Recent activity",
            actionText = "See all",
            onActionClick = onSeeAllAssessments,
            modifier = Modifier.padding(horizontal = 20.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))
        ListContainer(modifier = Modifier.padding(horizontal = 20.dp)) {
            MockData.recentAssessments.forEachIndexed { index, assessment ->
                AssessmentCard(assessment = assessment)
                if (index < MockData.recentAssessments.lastIndex) RowDivider(startIndent = 74.dp)
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun StartAssessmentCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(VigilExYellow)
            .clickable(onClick = onClick)
            .padding(22.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            OverlineLabel(text = "New", color = OnYellow.copy(alpha = 0.6f))
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Start new assessment",
                style = MaterialTheme.typography.headlineSmall,
                color = OnYellow
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Capture a task, extract posture and score risk in minutes.",
                style = MaterialTheme.typography.bodyMedium,
                color = OnYellow.copy(alpha = 0.72f)
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(TextPrimary),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
                contentDescription = null,
                tint = VigilExYellow,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
private fun AttentionCard(modifier: Modifier = Modifier) {
    val item = MockData.attentionAssessment
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(BackgroundWhite)
            .border(1.dp, BorderSubtle, RoundedCornerShape(16.dp))
            .padding(20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                RiskBadge(level = item.riskLevel, showSuffix = true)
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "${item.taskName} — ${item.areaName}",
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${item.siteName} · ${item.timestamp}",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = item.score.toString(),
                    style = MaterialTheme.typography.displaySmall,
                    color = RiskRed
                )
                OverlineLabel(text = "Score")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider(color = DividerColor, thickness = 1.dp)
        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = "Shoulder elevation above 90° for most of the work cycle. Review before the next shift.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary
        )
        Spacer(modifier = Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Review assessment",
                style = MaterialTheme.typography.labelLarge,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                imageVector = Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = TextPrimary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

// ----------------------------------------------------------- Assessments tab

private enum class AssessmentFilter(val label: String) {
    ALL("All"), REVIEW("Review"), COMPLETED("Completed"), DRAFT("Drafts")
}

@Composable
private fun AssessmentsTabContent() {
    var filter by remember { mutableStateOf(AssessmentFilter.ALL) }

    val items = when (filter) {
        AssessmentFilter.ALL -> MockData.assessments
        AssessmentFilter.REVIEW -> MockData.assessments.filter { it.status == AssessmentStatus.REVIEW_REQUIRED }
        AssessmentFilter.COMPLETED -> MockData.assessments.filter { it.status == AssessmentStatus.COMPLETED }
        AssessmentFilter.DRAFT -> MockData.assessments.filter { it.status == AssessmentStatus.DRAFT }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        TabHeader(
            title = "Assessments",
            subtitle = "${MockData.assessments.size} recorded across ${MockData.sites.size} sites"
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AssessmentFilter.entries.forEach { option ->
                FilterChip(
                    label = option.label,
                    selected = option == filter,
                    onClick = { filter = option }
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        if (items.isEmpty()) {
            EmptyState(
                title = "Nothing here yet",
                body = "Assessments matching this filter will appear here."
            )
        } else {
            ListContainer(modifier = Modifier.padding(horizontal = 20.dp)) {
                items.forEachIndexed { index, assessment ->
                    AssessmentCard(assessment = assessment)
                    if (index < items.lastIndex) RowDivider(startIndent = 74.dp)
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun FilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        color = if (selected) OnYellow else TextSecondary,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) VigilExYellow else SurfaceSubtle)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp)
    )
}

// --------------------------------------------------------------- Reports tab

@Composable
private fun ReportsTabContent() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        TabHeader(
            title = "Reports",
            subtitle = "${MockData.reports.size} generated · shared with site leads"
        )

        ListContainer(modifier = Modifier.padding(horizontal = 20.dp)) {
            MockData.reports.forEachIndexed { index, report ->
                ReportRow(report = report)
                if (index < MockData.reports.lastIndex) RowDivider(startIndent = 74.dp)
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun ReportRow(report: Report) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(SurfaceSubtle),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.Description,
                contentDescription = null,
                tint = TextSecondary,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = report.title,
                style = MaterialTheme.typography.titleSmall,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = "${report.siteName} · ${report.assessmentCount} assessments · ${report.generatedAt}",
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        RiskBadge(level = report.overallRisk)
    }
}

// --------------------------------------------------------------- Profile tab

@Composable
private fun ProfileTabContent(onSignOut: () -> Unit) {
    var notificationsEnabled by remember { mutableStateOf(true) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(modifier = Modifier.height(28.dp))

        ProfileHeader(
            name = UserSession.userName,
            role = "Ergonomic assessor",
            modifier = Modifier.padding(horizontal = 20.dp)
        )

        Spacer(modifier = Modifier.height(32.dp))

        OverlineLabel(text = "Identity", modifier = Modifier.padding(horizontal = 20.dp))
        Spacer(modifier = Modifier.height(10.dp))
        ListContainer(modifier = Modifier.padding(horizontal = 20.dp)) {
            SettingsRow(icon = Icons.Outlined.Badge, title = "Employee ID", value = "EMP-0421")
            RowDivider(startIndent = 60.dp)
            SettingsRow(icon = Icons.Outlined.Apartment, title = "Organization", value = "Extrive Industries")
            RowDivider(startIndent = 60.dp)
            SettingsRow(icon = Icons.Outlined.LocationOn, title = "Primary site", value = "Assembly Plant")
        }

        Spacer(modifier = Modifier.height(28.dp))

        OverlineLabel(text = "Settings", modifier = Modifier.padding(horizontal = 20.dp))
        Spacer(modifier = Modifier.height(10.dp))
        ListContainer(modifier = Modifier.padding(horizontal = 20.dp)) {
            SettingsRow(icon = Icons.Outlined.Tune, title = "Preferences", showChevron = true)
            RowDivider(startIndent = 60.dp)
            SettingsRow(
                icon = Icons.Outlined.Notifications,
                title = "Notifications",
                trailing = {
                    Switch(
                        checked = notificationsEnabled,
                        onCheckedChange = { notificationsEnabled = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = OnYellow,
                            checkedTrackColor = VigilExYellow,
                            uncheckedThumbColor = BackgroundWhite,
                            uncheckedTrackColor = DividerColor,
                            uncheckedBorderColor = DividerColor
                        )
                    )
                }
            )
        }

        Spacer(modifier = Modifier.height(28.dp))

        ListContainer(modifier = Modifier.padding(horizontal = 20.dp)) {
            SettingsRow(
                icon = Icons.Outlined.Logout,
                title = "Sign out",
                tint = RiskRed,
                onClick = onSignOut
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "VigilEx 1.0 · Extrive Innovations",
            style = MaterialTheme.typography.bodySmall,
            color = TextMuted,
            modifier = Modifier.padding(horizontal = 20.dp)
        )

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    value: String? = null,
    tint: Color = TextPrimary,
    showChevron: Boolean = false,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .height(60.dp)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (tint == TextPrimary) TextSecondary else tint,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(22.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = tint,
            modifier = Modifier.weight(1f)
        )
        if (value != null) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
        }
        if (trailing != null) trailing()
        if (showChevron) {
            Icon(
                imageVector = Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = TextMuted,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

// ------------------------------------------------------------------- Shared

@Composable
private fun TabHeader(title: String, subtitle: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(top = 28.dp, bottom = 24.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.displaySmall,
            color = TextPrimary
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary
        )
    }
}

@Composable
private fun EmptyState(title: String, body: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceSubtle)
            .padding(horizontal = 20.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(VigilExYellow)
        )
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = TextPrimary
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = body,
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary
        )
    }
}
