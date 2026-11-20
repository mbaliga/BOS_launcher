package com.bos.sphere.feature.dock

import com.bos.sphere.core.data.DockStyle
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Wave 39 (dockThemes): the numbers behind the three dock styles, pure so a JVM test can check them. Colours are 0xAARRGGBB ints. The Standard
 * style keeps the existing pill (HyleColors surface and hairline) and has no tokens here. Nothing in a style animates.
 */
object DockStyleTokens {
    const val CORNER_DP = 22f
    /** The slot and icon the rail already uses; a style never changes a touch target. */
    const val SLOT_DP = 52f
    const val ICON_DP = 40f

    // --- Transparent: a dark soft halo behind each icon and glyph, so they stay readable on light or busy wallpapers.
    const val HALO_ALPHA = 0.66f
    /** The halo's middle stop: where (as a fraction of its radius) it has fallen to [HALO_MID_ALPHA] of the centre's alpha, before it fades to nothing at the rim. */
    const val HALO_MID_AT = 0.8f
    const val HALO_MID_ALPHA = 0.75f
    /** Halo radius as a fraction of the icon edge; 0.64 of 40 dp is 25.6 dp, inside the 52 dp slot, so the slot never cuts it. */
    const val HALO_RADIUS = 0.64f
    const val GLYPH = 0xFFFFFFFF.toInt()
    const val HALO = 0xFF000000.toInt()
    /** WCAG 1.4.11: an icon or glyph needs 3:1 against what is behind it. */
    const val MIN_CONTRAST = 3.0

    // --- 3D: a raised icon with its own small shadow and a faint reflection on the shelf.
    const val ICON_RAISE_DP = 4f
    const val REFLECTION_ALPHA = 0.3f
    const val REFLECTION_GAP_DP = 1f
    /** How much of the mirrored icon shows under the raised one: the slot's margin below the icon, less the gap; the fade runs over all of it. */
    const val REFLECTION_VISIBLE_DP = (SLOT_DP - ICON_DP) / 2f + ICON_RAISE_DP - REFLECTION_GAP_DP
    const val CONTACT_LAYERS = 6
    const val CONTACT_DP = 7f

    class Slab(
        val faceTop: Int, val faceBottom: Int,
        /** The bright line along the top edge, fading out by [HIGHLIGHT_REACH]. */
        val highlight: Int,
        val bevelLight: Int, val bevelDark: Int,
        val innerShadow: Int,
        /** Total opacity of the contact shadow beneath the shelf (the layers add up to about this). */
        val contactShadow: Int,
        val iconShadow: Int,
    )
    const val HIGHLIGHT_REACH = 0.45f

    fun slab(dark: Boolean): Slab = if (dark) Slab(
        faceTop = 0xFF3A3F50.toInt(), faceBottom = 0xFF1B1E29.toInt(), highlight = 0x66FFFFFF, bevelLight = 0x55FFFFFF, bevelDark = 0xAA000000.toInt(),
        innerShadow = 0x40000000, contactShadow = 0x66000000, iconShadow = 0x99000000.toInt(),
    ) else Slab(
        faceTop = 0xFFFAFBFE.toInt(), faceBottom = 0xFFD3D7E3.toInt(), highlight = 0xCCFFFFFF.toInt(), bevelLight = 0xFFFFFFFF.toInt(), bevelDark = 0x66000000,
        innerShadow = 0x22000000, contactShadow = 0x44000000, iconShadow = 0x66000000,
    )

    fun hasSurface(style: DockStyle): Boolean = style != DockStyle.Transparent

    /** Where an icon's edge sits on the halo, as a fraction of its radius: the weakest place an icon's own outline is read against the halo. */
    const val ICON_EDGE_AT = (ICON_DP / 2f) / (ICON_DP * HALO_RADIUS)

    /** The halo's opacity at [f] of its radius (0 at the centre, 1 at the rim); the drawing uses the same stops. */
    fun haloAlphaAt(f: Float): Float {
        val x = f.coerceIn(0f, 1f)
        return if (x <= HALO_MID_AT) HALO_ALPHA * (1f + (HALO_MID_ALPHA - 1f) * x / HALO_MID_AT) else HALO_ALPHA * HALO_MID_ALPHA * (1f - (x - HALO_MID_AT) / (1f - HALO_MID_AT))
    }

    /** What the wallpaper looks like at an icon's edge with the halo over it: the backdrop a white or bright icon outline is read against. */
    fun iconEdgeBackdrop(wallpaper: Int): Int = blend(HALO, wallpaper, haloAlphaAt(ICON_EDGE_AT).toDouble())

    /** What an icon or glyph is drawn over in the Transparent style: the wallpaper under the halo's centre. */
    fun haloBackdrop(wallpaper: Int): Int = blend(HALO, wallpaper, HALO_ALPHA.toDouble())

    /** Contrast of a white glyph over [wallpaper] with its (solid, [HALO_ALPHA]) halo behind it: the home glyph, the plus ring, the divider. */
    fun glyphContrastOnHalo(wallpaper: Int): Double = ratio(GLYPH, haloBackdrop(wallpaper))

    /** Contrast of a white icon outline against the wallpaper at the icon's edge, with the halo over it. */
    fun iconEdgeContrast(wallpaper: Int): Double = ratio(GLYPH, iconEdgeBackdrop(wallpaper))

    /** The unit direction the 3D shelf's contact shadow falls: down for the bottom dock, towards the screen's inside for a side dock. */
    fun shadowDirection(edge: DockEdge): Pair<Float, Float> = when (edge) {
        DockEdge.Bottom -> 0f to 1f
        DockEdge.Left -> 1f to 0.35f
        DockEdge.Right -> -1f to 0.35f
    }

    /** Opacity of layer [i] (0-based) of the contact shadow: they add up to [Slab.contactShadow]'s alpha and fade outwards. */
    fun contactLayerAlpha(i: Int, total: Float): Float = total / CONTACT_LAYERS * (1f - i.toFloat() / (CONTACT_LAYERS + 1)) * 1.4f

    // --- WCAG maths (the same as core-quality's Contrast, kept here so the dock module needs no extra dependency)
    private fun channel(c: Int): Double { val s = c / 255.0; return if (s <= 0.03928) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4) }
    fun luminance(argb: Int): Double = 0.2126 * channel((argb shr 16) and 0xFF) + 0.7152 * channel((argb shr 8) and 0xFF) + 0.0722 * channel(argb and 0xFF)
    fun ratio(a: Int, b: Int): Double { val la = luminance(a); val lb = luminance(b); return (max(la, lb) + 0.05) / (min(la, lb) + 0.05) }
    fun blend(fg: Int, bg: Int, alpha: Double): Int {
        fun mix(s: Int) = Math.round(((fg shr s) and 0xFF) * alpha + ((bg shr s) and 0xFF) * (1 - alpha)).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (mix(16) shl 16) or (mix(8) shl 8) or mix(0)
    }
}
