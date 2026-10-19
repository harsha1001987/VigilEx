package com.extrive.vigilex.ui.components

import android.app.Activity
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.getValue
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.font.FontWeight
import com.extrive.vigilex.ui.theme.Ink
import com.extrive.vigilex.ui.theme.InkMuted
import com.extrive.vigilex.ui.theme.Radius
import com.extrive.vigilex.ui.theme.Surface
import com.extrive.vigilex.ui.theme.SurfaceSunken
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.core.view.WindowCompat
import com.extrive.vigilex.navigation.TopLevel
import com.extrive.vigilex.ui.theme.Canvas
import com.extrive.vigilex.ui.theme.Motion
import com.extrive.vigilex.ui.theme.Size
import com.extrive.vigilex.ui.theme.Space
import com.extrive.vigilex.ui.theme.VxType
import com.extrive.vigilex.ui.theme.Yellow

enum class WindowClass { COMPACT, MEDIUM, EXPANDED }

val LocalWindowClass = staticCompositionLocalOf { WindowClass.COMPACT }

/**
 * Root layout. Phones get a bottom bar, tablets a rail, large screens a
 * sidebar; all three are white with a hairline edge and a yellow mark on the
 * current destination.
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
                    isAppearanceLightNavigationBars = true
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
        targetValue = if (active) Ink else InkMuted,
        animationSpec = tween(Motion.fast),
        label = "navColor"
    )
    return color
}

@Composable
private fun BottomBar(current: TopLevel?, analysisRunning: Boolean, onNavigate: (TopLevel) -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Surface)
            .windowInsetsPadding(WindowInsets.navigationBars)
    ) {
        Hairline()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = Size.bottomBar)
                .selectableGroup()
        ) {
            TopLevel.entries.forEach { item ->
                val active = item == current
                val color = navColor(active)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = Size.bottomBar)
                        .navItemSemantics(item.label, active, item == TopLevel.ASSESSMENT && analysisRunning) { onNavigate(item) },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .width(28.dp)
                            .height(3.dp)
                            .background(if (active) Yellow else Color.Transparent, RoundedCornerShape(bottomStart = 2.dp, bottomEnd = 2.dp))
                    )
                    Spacer(Modifier.height(Space.xs))
                    Box {
                        Icon(item.icon, contentDescription = null, tint = color, modifier = Modifier.size(Size.icon))
                        if (item == TopLevel.ASSESSMENT && analysisRunning) RunningDot(Modifier.align(Alignment.TopEnd))
                    }
                    Spacer(Modifier.height(Space.xxs))
                    Text(
                        item.label,
                        style = VxType.label.copy(fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium),
                        color = color,
                        maxLines = 1
                    )
                    Spacer(Modifier.height(Space.xs))
                }
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
    Row(Modifier.fillMaxHeight()) {
        Column(
            modifier = Modifier
                .width(92.dp)
                .fillMaxHeight()
                .background(Surface)
                .padding(vertical = Space.lg)
                .selectableGroup(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                Modifier
                    .size(14.dp)
                    .background(Yellow, RoundedCornerShape(2.dp))
                    .semantics { contentDescription = "VigilEx" }
            )
            Spacer(Modifier.height(Space.xxl))
            TopLevel.entries.forEach { item ->
                RailItem(
                    label = item.label,
                    icon = item.icon,
                    active = item == current,
                    showDot = item == TopLevel.ASSESSMENT && analysisRunning,
                    onClick = { onNavigate(item) }
                )
                Spacer(Modifier.height(Space.sm))
            }
        }
        VerticalHairline(Modifier.fillMaxHeight())
    }
}

@Composable
private fun RailItem(label: String, icon: ImageVector, active: Boolean, showDot: Boolean, onClick: () -> Unit) {
    val color = navColor(active)
    Column(
        modifier = Modifier
            .width(76.dp)
            .navItemSemantics(label, active, showDot, onClick)
            .padding(vertical = Space.xs),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(if (active) Yellow else Color.Transparent)
                .padding(horizontal = 18.dp, vertical = 4.dp)
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(Size.icon))
            if (showDot) RunningDot(Modifier.align(Alignment.TopEnd))
        }
        Spacer(Modifier.height(Space.xxs))
        Text(label, style = VxType.label, color = color, maxLines = 1)
    }
}

@Composable
private fun Sidebar(
    current: TopLevel?,
    analysisRunning: Boolean,
    onNavigate: (TopLevel) -> Unit
) {
    Row(Modifier.fillMaxHeight()) {
        Column(
            modifier = Modifier
                .width(240.dp)
                .fillMaxHeight()
                .background(Surface)
                .padding(vertical = Space.lg)
                .selectableGroup()
        ) {
            Wordmark(Modifier.padding(horizontal = Space.lg))
            Spacer(Modifier.height(Space.xxl))
            TopLevel.entries.forEach { item ->
                SidebarItem(
                    item.label,
                    item.icon,
                    item == current,
                    item == TopLevel.ASSESSMENT && analysisRunning
                ) { onNavigate(item) }
            }
        }
        VerticalHairline(Modifier.fillMaxHeight())
    }
}

@Composable
private fun SidebarItem(label: String, icon: ImageVector, active: Boolean, showDot: Boolean, onClick: () -> Unit) {
    val textColor = navColor(active)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Space.sm, vertical = 2.dp)
            .clip(Radius.medium)
            .background(if (active) SurfaceSunken else Color.Transparent)
            .heightIn(min = Size.touchTarget)
            .navItemSemantics(label, active, showDot, onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .width(3.dp)
                .height(20.dp)
                .background(if (active) Yellow else Color.Transparent)
        )
        Spacer(Modifier.width(Space.sm))
        Icon(icon, contentDescription = null, tint = textColor, modifier = Modifier.size(Size.icon - 2.dp))
        Spacer(Modifier.width(Space.sm))
        Text(label, style = VxType.title.copy(fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal), color = textColor)
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

/**
 * One tappable, selectable destination. The label is announced once with its
 * selected state, and "analysis in progress" when the running dot shows.
 */
@Composable
private fun Modifier.navItemSemantics(label: String, active: Boolean, running: Boolean, onClick: () -> Unit): Modifier {
    val interaction = remember { MutableInteractionSource() }
    return this
        .selectable(
            selected = active,
            interactionSource = interaction,
            indication = ripple(color = Ink),
            role = Role.Tab,
            onClick = onClick
        )
        .clearAndSetSemantics {
            contentDescription = if (running) "$label, analysis in progress" else label
            selected = active
            role = Role.Tab
            onClick(label = label) { onClick(); true }
        }
}

