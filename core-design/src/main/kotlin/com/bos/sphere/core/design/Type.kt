package com.bos.sphere.core.design

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * Hyle type ramp (§4): light weights for the clock hero / focal labels; tabular figures
 * for time and temperatures. Uses the platform default family for now — a bespoke face can
 * be dropped in here later without touching call sites.
 */
val HyleTypography = Typography(
    // Clock hero (portrait) — very light, large.
    displayLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Thin,
        fontSize = 76.sp,
        letterSpacing = 0.01.em,
    ),
    // Widget-tile heroes (clock / calendar number).
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Light,
        fontSize = 27.sp,
        letterSpacing = 0.02.em,
    ),
    // App labels on the equator.
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        letterSpacing = 0.02.em,
    ),
    // Chrome / overline labels (weather, edge bars).
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp,
        letterSpacing = 0.14.em,
    ),
)
