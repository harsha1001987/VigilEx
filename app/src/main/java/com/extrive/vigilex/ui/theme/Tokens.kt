package com.extrive.vigilex.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

object Space {
    val xxs = 4.dp
    val xs = 8.dp
    val sm = 12.dp
    val md = 16.dp
    val lg = 24.dp
    val xl = 32.dp
    val xxl = 48.dp
    val xxxl = 64.dp
    val huge = 96.dp
}

/** Fixed component dimensions, so controls stay the same size on every screen. */
object Size {
    /** Minimum touch target for anything tappable. */
    val touchTarget = 48.dp
    val button = 52.dp
    val headerBar = 56.dp
    val bottomBar = 64.dp
    val avatar = 36.dp
    val icon = 24.dp
    val iconSmall = 18.dp
}

object Radius {
    val sharp = RoundedCornerShape(2.dp)
    /** Tags and small markers. */
    val small = RoundedCornerShape(6.dp)
    /** Buttons, fields and menus. */
    val medium = RoundedCornerShape(12.dp)
    /** Cards and panels. */
    val card = RoundedCornerShape(14.dp)
    val large = RoundedCornerShape(20.dp)
    val pill = RoundedCornerShape(50)
}

object Border {
    val hairline = 1.dp
    val strong = 1.5.dp
}

/** One consistent motion language: calm, short, decelerating. */
object Motion {
    const val fast = 160
    const val base = 280
    const val slow = 450
    val standard = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val exit = CubicBezierEasing(0.3f, 0f, 1f, 1f)
}

/** Flat by default; elevation is used only to lift a dialog above content. */
object Elevation {
    val none = 0.dp
    val dialog = 6.dp
}
