package com.bos.sphere.feature.grid

import com.bos.sphere.core.data.BackgroundChoice
import com.bos.sphere.core.data.BgItem
import com.bos.sphere.core.data.RadarIntensity
import com.bos.sphere.core.quality.Contrast
import com.bos.sphere.core.signals.SignalState
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * NFR-15 / wave 21: text and icons on Radar's glass keep WCAG AA over every living wallpaper and effect pair at signal levels 0 and 4,
 * measured on the real painters' pixels in the places where the glass is (the field and Close at the top, the mic at the bottom centre),
 * in portrait and landscape and at both intensities. The glass is laid over each pixel at its strongest (brightest corner, pressed, plus
 * the sheen), so every other pixel is easier to read than what is checked here.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class RadarGlassContrastW21Test {
    /** The field carries text (4.5:1, the scope pill nested on it); Close and the mic carry only an icon (3:1, nothing nested). */
    private data class Zone(val name: String, val x0: Float, val x1: Float, val y0: Float, val y1: Float, val carries: GlassShade.Carries)

    private fun zones(wDp: Float, hDp: Float) = listOf(
        Zone("field", 16f, wDp - 12f - 56f, 8f, 60f, GlassShade.Carries.Text),
        Zone("pill", 16f, 16f + 150f, 8f, 60f, GlassShade.Carries.Label),
        Zone("close", wDp - 12f - 48f, wDp - 12f, 10f, 58f, GlassShade.Carries.Icon),
        Zone("mic", wDp / 2 - 28f, wDp / 2 + 28f, hDp - 64f, hDp - 8f, GlassShade.Carries.Icon),
    )

    private val textWhite = 0xFFFFFFFF.toInt()
    private val hint = 0xCCFFFFFF.toInt()

    /** Smallest contrast ratio of (text, placeholder) over the glass (with [shade] black under it) across the pixels inside [z] of a [w] x [h] render at [scale] px per dp. */
    private fun worst(px: IntArray, w: Int, h: Int, scale: Float, z: Zone, shade: Float): Pair<Double, Double> {
        var text = 100.0; var ph = 100.0
        for (y in (z.y0 * scale).toInt().coerceAtLeast(0) until (z.y1 * scale).toInt().coerceAtMost(h))
            for (x in (z.x0 * scale).toInt().coerceAtLeast(0) until (z.x1 * scale).toInt().coerceAtMost(w)) {
                val glass = GlassShade.glassOver(px[y * w + x] or (0xFF shl 24), shade, z.carries)
                text = minOf(text, Contrast.ratio(textWhite, glass))
                ph = minOf(ph, Contrast.ratio(Contrast.blend(hint, glass, 0.8), glass))
            }
        return text to ph
    }

    @Test
    fun NFR_15_glassTextHasAaOverEveryBackgroundPairAtLevels0And4() {
        val scale = 0.5f
        val all = BgItem.entries
        var minText = 100.0; var minHint = 100.0; var minIcon = 100.0; var where = ""; var whereIcon = ""
        var combos = 0; var shaded = 0; var maxShade = 0f
        var defaultShades = GlassShades()
        for (s1 in all) for (s2 in all) {
            val choice = BackgroundChoice(s1, s2)
            if (!choice.isValid) continue
            for (intensity in (if (choice == BackgroundChoice.DEFAULT) RadarIntensity.entries else listOf(RadarIntensity.Normal))) for ((wDp, hDp) in listOf(411 to 891, 891 to 411)) {
                val shades = GlassShade.compute(choice, intensity, wDp, hDp)
                if (choice == BackgroundChoice.DEFAULT && intensity == RadarIntensity.Normal && wDp == 411) defaultShades = shades
                if (shades.top > 0f || shades.bottom > 0f || shades.close > 0f) shaded++
                maxShade = maxOf(maxShade, shades.top, shades.bottom, shades.close)
                // Held-out frames (clock values the sampler did not use) at the two levels of the brief.
                for (level in intArrayOf(0, 4)) for (t in longArrayOf(ScopeMath.STILL_TIME_MS, 1_234L, 20_000L)) {
                    combos++
                    val px = GlassShade.render(choice, intensity, level, t, wDp, hDp, scale)
                    val w = (wDp * scale).toInt(); val h = (hDp * scale).toInt()
                    for (z in zones(wDp.toFloat(), hDp.toFloat())) {
                        val (tx, ph) = worst(px, w, h, scale, z, when (z.name) { "mic" -> shades.bottom; "close" -> shades.close; else -> shades.top })
                        if (z.carries != GlassShade.Carries.Icon) {
                            if (tx < minText) { minText = tx; where = "text ${choice.slot1}+${choice.slot2} $intensity L$level t=$t ${wDp}x$hDp ${z.name} shade=${shades.top}/${shades.close}/${shades.bottom}" }
                            if (z.carries == GlassShade.Carries.Text) minHint = minOf(minHint, ph)
                        } else if (tx < minIcon) { minIcon = tx; whereIcon = "icon ${choice.slot1}+${choice.slot2} $intensity L$level t=$t ${wDp}x$hDp ${z.name} shade=${shades.top}/${shades.close}/${shades.bottom}" }
                    }
                }
            }
        }
        println("RADAR-GLASS contrast: $combos frames; min field text ratio %.2f (%s); min placeholder ratio %.2f; min icon ratio %.2f (%s); %d background/size cases needed a shade, at most %.2f; default pair shades field=%.2f close=%.2f mic=%.2f".format(minText, where, minHint, minIcon, whereIcon, shaded, maxShade, defaultShades.top, defaultShades.close, defaultShades.bottom))
        assertTrue("glass text must be AA: %.2f at %s".format(minText, where), minText >= Contrast.AA_NORMAL - 0.05)
        assertTrue("glass placeholder must be AA: %.2f".format(minHint), minHint >= Contrast.AA_NORMAL - 0.05)
        assertTrue("glass icons (Close, mic) must be 3:1: %.2f at %s".format(minIcon, whereIcon), minIcon >= Contrast.AA_LARGE - 0.05)
        // Wave 22: the default pair (Spire + Breathing dots) no longer needs the smoky 0.37 / 0.40 shade: the glass is clear.
        assertTrue("the default pair keeps clear glass (a shade of at most 0.2) (field ${defaultShades.top}, close ${defaultShades.close}, mic ${defaultShades.bottom})", defaultShades.top <= 0.2f && defaultShades.close <= 0.2f && defaultShades.bottom <= 0.2f)
    }

    @Test
    fun NFR_15_aBrightBackgroundGetsAShadeAndADarkOneDoesNot() {
        assertTrue(GlassShade.shadeFor(intArrayOf(0xFF000000.toInt(), 0xFF101830.toInt())) == 0f)
        val s = GlassShade.shadeFor(intArrayOf(0xFFFFFFFF.toInt()))
        assertTrue("white needs a heavy shade, got $s", s > 0.5f)
        // An icon-only piece of glass (Close, the mic) needs less than text does over the same pixels.
        val bright = intArrayOf(0xFF6D9BFF.toInt())
        assertTrue("icon shade ${GlassShade.shadeFor(bright, GlassShade.Carries.Icon)} < text shade ${GlassShade.shadeFor(bright)}", GlassShade.shadeFor(bright, GlassShade.Carries.Icon) < GlassShade.shadeFor(bright))
        assertTrue(Contrast.ratio(textWhite, GlassShade.glassOver(0xFFFFFFFF.toInt(), s)) >= Contrast.AA_NORMAL - 0.02)
    }

    @Test
    fun NFR_15_worstGlassAlphaIsTheBrightestCornerPressedPlusTheSheen() {
        val a = GlassSpec.worstWhiteAlpha()
        assertTrue("about 0.2 to 0.4, got $a", a in 0.2f..0.4f)
        assertTrue(GlassSpec.worstWhiteAlpha(pressed = false) < a)
    }
}
