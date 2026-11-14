package com.bos.sphere.core.design

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance

/** Palette family of the launcher UI. The integrator maps its own theme setting onto this. */
enum class ThemeVariant { Dark, Amoled, Light }

/**
 * Live theme state. Everything in [HyleColors] reads these snapshot states, so any composable
 * that touches a colour recomposes when [apply] changes the theme.
 */
object HyleTheme {
    var variant: ThemeVariant by mutableStateOf(ThemeVariant.Dark)
        private set
    var accent: Color by mutableStateOf(Color(0xFF8E7BFF))
        private set

    /** Switch palette and accent ([accentArgb] is 0xAARRGGBB; a zero alpha is treated as opaque). */
    fun apply(variant: ThemeVariant, accentArgb: Long) {
        this.variant = variant
        val c = Color(accentArgb.toInt())
        this.accent = if (c.alpha == 0f) c.copy(alpha = 1f) else c
    }

    val isLight: Boolean get() = variant == ThemeVariant.Light
}

/**
 * Hyle / Aarso palette (build brief §4). Every property is a live getter over [HyleTheme], so
 * call sites stay unchanged and recompose on theme change. Dark equals the original brand tokens.
 */
object HyleColors {
    // Accent
    val Violet: Color get() = HyleTheme.accent
    val VioletSoft: Color
        get() = if (HyleTheme.isLight) lerp(HyleTheme.accent, Color.Black, 0.25f)
        else lerp(HyleTheme.accent, Color.White, 0.57f)

    /** Readable text/icon colour to put on top of [Violet]. */
    val OnAccent: Color get() = if (HyleTheme.accent.luminance() > 0.45f) Color(0xFF0A0B10) else Color.White

    // Substrate
    val Background: Color
        get() = when (HyleTheme.variant) {
            ThemeVariant.Dark -> Color(0xFF0A0B10)
            ThemeVariant.Amoled -> Color(0xFF000000)
            ThemeVariant.Light -> Color(0xFFF3F4F8)
        }
    val Surface: Color
        get() = when (HyleTheme.variant) {
            ThemeVariant.Dark -> Color(0xFF161922)
            ThemeVariant.Amoled -> Color(0xFF0B0C10)
            ThemeVariant.Light -> Color(0xFFFFFFFF)
        }
    val SurfaceTranslucent: Color // glass panels (~0.86 alpha)
        get() = when (HyleTheme.variant) {
            ThemeVariant.Dark -> Color(0xCC12141C)
            ThemeVariant.Amoled -> Color(0xDD040508)
            ThemeVariant.Light -> Color(0xDDFFFFFF)
        }

    // Ink
    val Ink: Color
        get() = if (HyleTheme.isLight) Color(0xFF2A2E3A) else Color(0xFFC9CDD6)
    val InkBright: Color
        get() = if (HyleTheme.isLight) Color(0xFF0E1018) else Color(0xFFE7E9F0)
    val InkDim: Color
        get() = if (HyleTheme.isLight) Color(0xFF666C7C) else Color(0xFF7D8496)

    // Hairlines / inset highlights (key light = upper-left)
    val Hairline: Color
        get() = if (HyleTheme.isLight) Color(0x1F000000) else Color(0x1AFFFFFF)
    val InsetHighlight: Color
        get() = if (HyleTheme.isLight) Color(0x99FFFFFF) else Color(0x2EFFFFFF)

    /**
     * Provenance system (§4): surfaces and affordances tint by where computation happens.
     * On-device = warm radium glow; cloud = alien cyan.
     */
    val ProvenanceOnDevice: Color
        get() = if (HyleTheme.isLight) Color(0xFF3E9E1F) else Color(0xFF8CFF6A) // warm radium
    val ProvenanceCloud: Color
        get() = if (HyleTheme.isLight) Color(0xFF0090A8) else Color(0xFF42E8FF) // alien cyan
}

/** Where a piece of computation ran, used to tint provenance-aware surfaces. Colours are read lazily. */
enum class Provenance {
    OnDevice,
    Cloud;

    val color: Color
        get() = when (this) {
            OnDevice -> HyleColors.ProvenanceOnDevice
            Cloud -> HyleColors.ProvenanceCloud
        }
}
