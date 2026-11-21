package com.bos.sphere.feature.grid

import com.bos.sphere.core.quality.Contrast
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Wave 25 (owner item 5, PAL-18, NFR-15, NFR-05): the Radar's field, Clear X, Close and mic are black gradients, not glass. White text keeps 4.5:1 by
 * construction (black behind it), and none of the glass machinery is left.
 */
class RadarBlackW25Test {
    private fun sourceDir() = listOf("src/main/kotlin/com/bos/sphere/feature/grid", "feature-grid/src/main/kotlin/com/bos/sphere/feature/grid").map { File(it) }.first { it.exists() }
    private fun code(name: String) = File(sourceDir(), name).readLines().filterNot { it.trimStart().startsWith("//") || it.trimStart().startsWith("*") || it.trimStart().startsWith("/*") }.joinToString("\n")

    private val white = 0xFFFFFFFF.toInt()

    @Test
    fun NFR_15_whiteTextOnTheLeastOpaquePixelOfABlackSurfaceIsAboveFourPointFiveEvenOverPureWhite() {
        for (alpha in listOf(BlackSpec.TOP_ALPHA, BlackSpec.BOTTOM_ALPHA)) {
            // the surface is black at [alpha] over the brightest possible background: pure white
            val surface = Contrast.blend(0xFF000000.toInt(), white, alpha.toDouble())
            assertTrue("white text on black at $alpha over white: ${Contrast.ratio(white, surface)}", Contrast.ratio(white, surface) >= Contrast.AA_NORMAL)
            val hint = Contrast.blend(0xCCFFFFFF.toInt(), surface, 0.8)
            assertTrue("the 80 percent hint on it: ${Contrast.ratio(hint, surface)}", Contrast.ratio(hint, surface) >= Contrast.AA_NORMAL)
        }
        assertTrue("the gradient runs from more black at the top to less at the bottom", BlackSpec.TOP_ALPHA > BlackSpec.BOTTOM_ALPHA && BlackSpec.BOTTOM_ALPHA >= 0.6f)
    }

    @Test
    fun PAL_18_theBlackSurfaceDrawsOnlyBlackNoWhiteRimNoSheenNoGreyFill() {
        val glass = code("Glass.kt")
        assertFalse("no white anywhere in the surface brush", Regex("""Color\.White\.copy|Color\(0x[0-9A-Fa-f]{2}FFFFFF\)""").containsMatchIn(glass.substringAfter("fun Modifier.blackSurface").substringBefore("@Composable")))
        assertFalse("no hairline stroke", glass.contains("Stroke("))
        assertEquals("one vertical gradient and the solid pressed brush", 2, Regex("Brush\\.verticalGradient").findAll(glass).count())
    }

    @Test
    fun PAL_18_theGlassMachineryIsGone() {
        for (gone in listOf("com.bos.sphere.feature.grid.GlassLayers", "com.bos.sphere.feature.grid.GlassShade", "com.bos.sphere.feature.grid.GlassShades", "com.bos.sphere.feature.grid.GlassSpec", "com.bos.sphere.feature.grid.GlassButtonKt"))
            assertTrue("$gone is removed", runCatching { Class.forName(gone) }.isFailure)
        val names = listOf("Glass.kt", "SearchOverlay.kt", "SearchChrome.kt", "SearchLayouts.kt", "VoiceEntry.kt", "SignalBackground.kt", "LivingWallpapers.kt", "SignalViews.kt")
        for (n in names) for (word in listOf("GlassField", "GlassButton", "GlassSpec", "GlassShade", "rememberGlassShades", "GlassLayers", "GLASS_TOP_DP", ".glass(", "glass = true"))
            assertFalse("$n still mentions $word", code(n).contains(word))
        assertFalse("BackgroundConfig has no glass flag", BackgroundConfig::class.java.declaredFields.any { it.name == "glass" })
    }

    @Test
    fun NFR_05_noRenderEffectNoBlurNoBlurredCopyInTheRadarScreen() {
        val banned = listOf("RenderEffect", "BlurEffect", "Modifier.blur", ".blur(", "setRenderEffect", "RenderScript", "BlurMaskFilter", "createBlurEffect", "setBackdrop")
        for (name in listOf("Glass.kt", "SearchOverlay.kt", "SearchChrome.kt", "SearchLayouts.kt", "SignalBackground.kt", "LivingWallpapers.kt", "RadarFit.kt"))
            for (b in banned) assertFalse("$name uses $b", code(name).contains(b))
    }

    @Test
    fun NFR_13_theBlackButtonsKeepTheirFortyEightAndFiftySixDpTargets() {
        assertEquals(48f, BlackSpec.Target.value, 0f)
        assertEquals(56f, BlackSpec.BigButton.value, 0f)
    }
}
