package com.extrive.vigilex.ui.components

import android.app.Activity
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.extrive.vigilex.navigation.TopLevel
import com.extrive.vigilex.ui.theme.Black
import com.extrive.vigilex.ui.theme.Canvas
import com.extrive.vigilex.ui.theme.InkOnDark
import com.extrive.vigilex.ui.theme.InkOnDarkMuted
import com.extrive.vigilex.ui.theme.Motion
import com.extrive.vigilex.ui.theme.Space
import com.extrive.vigilex.ui.theme.VxType
import com.extrive.vigilex.ui.theme.Yellow

enum class WindowClass { COMPACT, MEDIUM, EXPANDED }

val LocalWindowClass = staticCompositionLocalOf { WindowClass.COMPACT }

/**
 * Root layout. Phones get a bottom bar, tablets a rail, large screens a
 * sidebar; the navigation surface is black so the content canvas stays calm.
 * All three carry the same four destinations; Profile and Settings live in
 * the header's account menu.
 */
@Composable
fun AppShell(
    current: TopLevel?,
    analysisRunning: Boolean,
    onNavigate: (TopLevel) -> Unit,
    showNavigation: Boolean = true,
    content: @Composable () -> Unit
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Canvas)
    ) {
        val windowClass = when {
            maxWidth < 600.dp -> WindowClass.COMPACT
            maxWidth < 1000.dp -> WindowClass.MEDIUM
            else -> WindowClass.EXPANDED
        }

        val view = LocalView.current
        if (!view.isInEditMode) {
            SideEffect {
                val window = (view.context as Activity).window
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = true
                    // The compact bottom bar is black, so its system buttons must be light.
                    isAppearanceLightNavigationBars = !showNavigation || windowClass != WindowClass.COMPACT
                }
            }
        }

        CompositionLocalProvider(LocalWindowClass provides windowClass) {
            // Sign-in and account screens hide the navigation. The layout keeps its
            // structure so the NavHost inside `content` is never recreated.
            when (windowClass) {
                WindowClass.COMPACT -> Column(Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .windowInsetsPadding(WindowInsets.statusBars)
                            .then(if (showNavigation) Modifier else Modifier.windowInsetsPadding(WindowInsets.navigationBars))
                    ) { content() }
                    if (showNavigation) BottomBar(current, analysisRunning, onNavigate)
                }

                WindowClass.MEDIUM -> Row(
                    Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                ) {
                    if (showNavigation) Rail(current, analysisRunning, onNavigate)
                    Box(Modifier.weight(1f).fillMaxHeight()) { content() }
                }

                WindowClass.EXPANDED -> Row(
                    Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                ) {
                    if (showNavigation) Sidebar(current, analysisRunning, onNavigate)
                    Box(Modifier.weight(1f).fillMaxHeight()) { content() }
                }
            }
        }
    }
}

@Composable
private fun navColor(active: Boolean): Color {
    val color by animateColorAsState(
        targetValue = if (active) Yellow else InkOnDarkMuted,
        animationSpec = tween(Motion.fast),
        label = "navColor"
    )
    return color
}

@Composable
private fun BottomBar(current: TopLevel?, analysisRunning: Boolean, onNavigate: (TopLevel) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Black)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .height(64.dp)
    ) {
        TopLevel.entries.forEach { item ->
            val active = item == current
            val color = navColor(active)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .navItemSemantics(active) { onNavigate(item) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(20.dp)
                        .height(2.dp)
                        .background(if (active) Yellow else Color.Transparent)
                )
                Spacer(Modifier.height(Space.xs))
                Box {
                    Icon(item.icon, contentDescription = null, tint = color, modifier = Modifier.size(22.dp))
                    if (item == TopLevel.ASSESSMENT && analysisRunning) RunningDot(Modifier.align(Alignment.TopEnd))
                }
                Spacer(Modifier.height(Space.xxs))
                Text(item.label.uppercase(), style = VxType.label.copy(letterSpacing = 0.8.sp), color = color)
            }
        }
    }
}

@Composable
private fun Rail(
    current: TopLevel?,
    analysisRunning: Boolean,
    onNavigate: (TopLevel) -> Unit
) {
    Column(
        modifier = Modifier
            .width(92.dp)
            .fillMaxHeight()
            .background(Black)
            .padding(vertical = Space.lg),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("VX", style = VxType.wordmark, color = InkOnDark)
        Spacer(Modifier.height(Space.xxl))
        TopLevel.entries.forEach { item ->
            RailItem(
                label = item.label,
                icon = item.icon,
                active = item == current,
                showDot = item == TopLevel.ASSESSMENT && analysisRunning,
                onClick = { onNavigate(item) }
            )
            Spacer(Modifier.height(Space.md))
        }
    }
}

@Composable
private fun RailItem(label: String, icon: ImageVector, active: Boolean, showDot: Boolean, onClick: () -> Unit) {
    val color = navColor(active)
    Column(
        modifier = Modifier
            .width(76.dp)
            .navItemSemantics(active, onClick)
            .padding(vertical = Space.xs),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
            if (showDot) RunningDot(Modifier.align(Alignment.TopEnd))
        }
        Spacer(Modifier.height(6.dp))
        Text(label.uppercase(), style = VxType.label.copy(letterSpacing = 0.8.sp), color = color)
    }
}

@Composable
private fun Sidebar(
    current: TopLevel?,
    analysisRunning: Boolean,
    onNavigate: (TopLevel) -> Unit
) {
    Column(
        modifier = Modifier
            .width(240.dp)
            .fillMaxHeight()
            .background(Black)
            .padding(vertical = Space.xl)
    ) {
        Text(
            "VIGILEX",
            style = VxType.wordmark,
            color = InkOnDark,
            modifier = Modifier.padding(horizontal = Space.lg)
        )
        Spacer(Modifier.height(Space.xxxl))
        TopLevel.entries.forEach { item ->
            SidebarItem(item.label, item == current, item == TopLevel.ASSESSMENT && analysisRunning) { onNavigate(item) }
        }
    }
}

@Composable
private fun SidebarItem(label: String, active: Boolean, showDot: Boolean, onClick: () -> Unit) {
    val textColor by animateColorAsState(
        targetValue = if (active) InkOnDark else InkOnDarkMuted,
        animationSpec = tween(Motion.fast),
        label = "sidebarText"
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .navItemSemantics(active, onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .width(3.dp)
                .height(20.dp)
                .background(if (active) Yellow else Color.Transparent)
        )
        Spacer(Modifier.width(Space.lg - 3.dp))
        Text(label, style = VxType.title, color = textColor)
        if (showDot) {
            Spacer(Modifier.width(Space.xs))
            RunningDot()
        }
    }
}

/** Marks that an analysis is in progress; it reflects real workflow state only. */
@Composable
private fun RunningDot(modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(7.dp)
            .clip(CircleShape)
            .background(Yellow)
    )
}

@Composable
private fun Modifier.navItemSemantics(active: Boolean, onClick: () -> Unit): Modifier {
    val interaction = remember { MutableInteractionSource() }
    return this
        .semantics { selected = active }
        .clickable(
            interactionSource = interaction,
            indication = ripple(color = Yellow),
            role = Role.Tab,
            onClick = onClick
        )
}

