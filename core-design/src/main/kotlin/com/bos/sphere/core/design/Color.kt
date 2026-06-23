package com.bos.sphere.core.design

import androidx.compose.ui.graphics.Color

/**
 * Hyle / Aarso palette (build brief §4). These are the fixed brand tokens; dynamic
 * Material You / Monet color (added at M1+) layers on top of, not instead of, the violet
 * accent and provenance hues which are part of the identity.
 */
object HyleColors {
    // Accent (violet)
    val Violet = Color(0xFF8E7BFF)
    val VioletSoft = Color(0xFFCFC6FF)

    // Substrate
    val Background = Color(0xFF0A0B10)
    val Surface = Color(0xFF161922)
    val SurfaceTranslucent = Color(0xCC12141C) // glass panels (~0.86 alpha)

    // Ink
    val Ink = Color(0xFFC9CDD6)
    val InkBright = Color(0xFFE7E9F0)
    val InkDim = Color(0xFF7D8496)

    // Hairlines / inset highlights (key light = upper-left)
    val Hairline = Color(0x1AFFFFFF)
    val InsetHighlight = Color(0x2EFFFFFF)

    /**
     * Provenance system (§4): surfaces and affordances tint by where computation happens.
     * On-device = warm radium glow; cloud = alien cyan.
     */
    val ProvenanceOnDevice = Color(0xFF8CFF6A) // warm radium
    val ProvenanceCloud = Color(0xFF42E8FF)    // alien cyan
}

/** Where a piece of computation ran, used to tint provenance-aware surfaces. */
enum class Provenance(val color: Color) {
    OnDevice(HyleColors.ProvenanceOnDevice),
    Cloud(HyleColors.ProvenanceCloud),
}
