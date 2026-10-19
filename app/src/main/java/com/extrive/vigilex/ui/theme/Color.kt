package com.extrive.vigilex.ui.theme

import androidx.compose.ui.graphics.Color

/*
 * Contrast ratios (WCAG 2.x) are noted against WarmWhite unless stated.
 * Body text needs 4.5:1; large text and non-text marks need 3:1.
 */

// Brand
/** Brand and primary-action fill. Never text on light surfaces (1.5:1); Charcoal on it is 11.3:1. */
val Yellow = Color(0xFFF4C928)
/** The original launch splash field; matches @color/vigilex_yellow (window and system splash). Splash only. */
val SplashYellow = Color(0xFFFFD21F)
/** Pure black, reserved for video and camera backgrounds. */
val Black = Color(0xFF0A0A0A)
/** Structure and text: 16.7:1. */
val Charcoal = Color(0xFF171717)
val White = Color(0xFFFFFFFF)
val WarmWhite = Color(0xFFF7F7F4)
/** Supporting accent for marks and rules only (2.9:1); use GoldInk for text. */
val Gold = Color(0xFFB68A2C)
/** High risk, errors and destructive actions: 4.8:1. */
val Red = Color(0xFFC93636)
val Green = Color(0xFF2E7D32)

// Ink (text on light surfaces)
val Ink = Charcoal
/** 7.0:1. */
val InkSecondary = Color(0xFF55554F)
/** Metadata and secondary text: 4.8:1 (5.1:1 on Surface, 4.5:1 on SurfaceSunken). */
val InkMuted = Color(0xFF6E6E68)
/** Disabled text and decorative glyphs only; not readable text. */
val InkFaint = Color(0xFFB9B9B2)

// Ink on dark surfaces (navigation)
val InkOnDark = White
/** 7.1:1 on Charcoal. */
val InkOnDarkMuted = Color(0xFFA3A39D)

// Structure
val Canvas = WarmWhite
val Surface = White
/** Soft neutral: tags, selected rows and secondary panels. InkMuted on it is 4.5:1. */
val SurfaceSunken = Color(0xFFEFEEE8)
/** Card borders and dividers. */
val Hairline = Color(0xFFE4E3DD)
/** Field outlines and secondary button borders: visible against both canvas and white (3:1 non-text needs Ink focus). */
val HairlineStrong = Color(0xFFCBCBC4)

/** Gold darkened for text on light surfaces: 5.8:1, 5.4:1 on GoldTint. */
val GoldInk = Color(0xFF7A5C14)
val VeryHighRed = Color(0xFF8E1B1B)
/** Red for small text on RedTint: 5.8:1 (Red itself is 4.3:1 there). */
val RedInk = Color(0xFFA82A2A)

// Tints used behind semantic text; never used alone as decoration.
val RedTint = Color(0xFFF9E7E6)
val GoldTint = Color(0xFFF6EFD6)
val GreenTint = Color(0xFFE5F0E5)
