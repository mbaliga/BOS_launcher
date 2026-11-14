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
/** The substrate, ink and hairline colours of one [ThemeVariant]. */
class HyleTokens(
    val background: Color, val surface: Color, val surfaceTranslucent: Color,
    val ink: Color, val inkBright: Color, val inkDim: Color, val hairline: Color,
)

object HyleColors {
    // Accent
    val Violet: Color get() = HyleTheme.accent
    val VioletSoft: Color
        get() = if (HyleTheme.isLight) lerp(HyleTheme.accent, Color.Black, 0.25f)
        else lerp(HyleTheme.accent, Color.White, 0.57f)

    /** Readable text/icon colour to put on top of [Violet]. */
    val OnAccent: Color get() = if (HyleTheme.accent.luminance() > 0.45f) Color(0xFF0A0B10) else Color.White

    // Substrate, ink and hairlines: one table per variant, read live below and by surfaces that choose their own
    // light or dark (Launchpad), so no surface keeps a private palette (CFG-10).
    fun tokens(variant: ThemeVariant): HyleTokens = when (variant) {
        ThemeVariant.Dark -> HyleTokens(
            background = Color(0xFF0A0B10), surface = Color(0xFF161922), surfaceTranslucent = Color(0xCC12141C),
            ink = Color(0xFFC9CDD6), inkBright = Color(0xFFE7E9F0), inkDim = Color(0xFF7D8496), hairline = Color(0x1AFFFFFF),
        )
        ThemeVariant.Amoled -> HyleTokens(
            background = Color(0xFF000000), surface = Color(0xFF0B0C10), surfaceTranslucent = Color(0xDD040508),
            ink = Color(0xFFC9CDD6), inkBright = Color(0xFFE7E9F0), inkDim = Color(0xFF7D8496), hairline = Color(0x1AFFFFFF),
        )
        ThemeVariant.Light -> HyleTokens(
            background = Color(0xFFF3F4F8), surface = Color(0xFFFFFFFF), surfaceTranslucent = Color(0xDDFFFFFF),
            ink = Color(0xFF2A2E3A), inkBright = Color(0xFF0E1018), inkDim = Color(0xFF666C7C), hairline = Color(0x1F000000),
        )
    }

    val Background: Color get() = tokens(HyleTheme.variant).background
    val Surface: Color get() = tokens(HyleTheme.variant).surface
    val SurfaceTranslucent: Color get() = tokens(HyleTheme.variant).surfaceTranslucent // glass panels (~0.86 alpha)

    // Ink
    val Ink: Color get() = tokens(HyleTheme.variant).ink
    val InkBright: Color get() = tokens(HyleTheme.variant).inkBright
    val InkDim: Color get() = tokens(HyleTheme.variant).inkDim

    // Hairlines / inset highlights (key light = upper-left)
    val Hairline: Color get() = tokens(HyleTheme.variant).hairline
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
