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

    // --- 3D (wave 56): the classic glass shelf. Geometry in dp; the shelf is drawn in a canonical frame (see DockStyleDraw.shelf).
    /** How deep the shelf is (floor plus lip), measured from the dock's edge inwards. */
    const val SHELF_DEPTH_DP = 30f
    /** The darker front face. */
    const val LIP_DP = 6f
    /** How far the back edge is pulled in at each end, as a fraction of the floor's depth: the perspective. */
    const val BACK_INSET = 0.42f
    /** Extra room a slot gets on the shelf side in 3D, so the reflection has floor to fall on (the rail's padding on that side is dropped by the same amount, so the rail is no taller). */
    const val SLOT_EXTRA_DP = 10f
    /** The slot margin around the icon. */
    const val SLOT_MARGIN_DP = (SLOT_DP - ICON_DP) / 2f
    const val REFLECTION_ALPHA = 0.42f
    const val REFLECTION_GAP_DP = 1f
    /** How much of the mirrored icon shows on the floor: the room from the icon's foot to the lip. */
    const val REFLECT_DP = SLOT_MARGIN_DP + SLOT_EXTRA_DP - LIP_DP - REFLECTION_GAP_DP

    class Shelf(
        /** The floor's glass, at the back edge and at the front. */
        val floorBack: Int, val floorFront: Int,
        /** The lip's face, top and bottom. */
        val lipTop: Int, val lipBottom: Int,
        /** The bright bevel along the back edge, on the slanted ends, and where the floor turns into the lip; the lip's lower edge. */
        val topLine: Int, val sideLine: Int, val junction: Int, val lipEdge: Int,
        val glow: Int, val innerShadow: Int,
        /** The shelf's own shadow below it, and the oval where an icon stands. */
        val shadow: Int, val iconShadow: Int,
    )

    fun shelf(dark: Boolean): Shelf = if (dark) Shelf(
        floorBack = 0x70DCE4FF, floorFront = 0x30AEBBF0, lipTop = 0x70161A2C, lipBottom = 0xA00A0C16.toInt(),
        topLine = 0xE6FFFFFF.toInt(), sideLine = 0x80FFFFFF.toInt(), junction = 0x66FFFFFF, lipEdge = 0x55FFFFFF,
        glow = 0x40FFFFFF, innerShadow = 0x40000000, shadow = 0x55000000, iconShadow = 0xAA000000.toInt(),
    ) else Shelf(
        floorBack = 0xD8FFFFFF.toInt(), floorFront = 0x80F0F3FA.toInt(), lipTop = 0x889AA2B8.toInt(), lipBottom = 0xB0646C86.toInt(),
        topLine = 0xFFFFFFFF.toInt(), sideLine = 0xCCFFFFFF.toInt(), junction = 0xAAFFFFFF.toInt(), lipEdge = 0x66FFFFFF,
        glow = 0x66FFFFFF, innerShadow = 0x26000000, shadow = 0x40000000, iconShadow = 0x77000000,
    )

    /** The cross-axis padding a rail drops on the shelf side in 3D (dp): 10 dp became slot room. */
    fun shelfSidePad(base: Float): Float = (base - SLOT_EXTRA_DP).coerceAtLeast(0f)

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

    // --- WCAG maths (the same as core-quality's Contrast, kept here so the dock module needs no extra dependency)
    private fun channel(c: Int): Double { val s = c / 255.0; return if (s <= 0.03928) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4) }
    fun luminance(argb: Int): Double = 0.2126 * channel((argb shr 16) and 0xFF) + 0.7152 * channel((argb shr 8) and 0xFF) + 0.0722 * channel(argb and 0xFF)
    fun ratio(a: Int, b: Int): Double { val la = luminance(a); val lb = luminance(b); return (max(la, lb) + 0.05) / (min(la, lb) + 0.05) }
    fun blend(fg: Int, bg: Int, alpha: Double): Int {
        fun mix(s: Int) = Math.round(((fg shr s) and 0xFF) * alpha + ((bg shr s) and 0xFF) * (1 - alpha)).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (mix(16) shl 16) or (mix(8) shl 8) or mix(0)
    }
}
