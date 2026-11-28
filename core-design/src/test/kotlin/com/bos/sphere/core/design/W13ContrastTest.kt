package com.bos.sphere.core.design

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.bos.sphere.core.quality.Contrast
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * NFR-15 / NFR-13 (wave 13 audit): the screens of waves 8 to 12 draw 12 to 15 sp captions, hints and empty-state text in the dim ink
 * (edit card hint, "Empty page", Recent apps empty state, add carousel placeholder). That is normal-size text, so it needs 4.5 to 1,
 * not the 3 to 1 the older token test accepts, on the page background, the surface and the 14 percent dim tint the carousels use for chips.
 */
class W13ContrastTest {
    private fun ratio(fg: Color, bg: Color) = Contrast.ratio(fg.toArgb(), bg.toArgb())

    @Test fun NFR_15_dimInkIsReadableAsSmallTextOnTheBackgroundAndTheSurfaceOfEveryVariant() {
        for (v in ThemeVariant.entries) {
            val t = HyleColors.tokens(v)
            for ((name, bg) in listOf("background" to t.background, "surface" to t.surface)) {
                val r = ratio(t.inkDim, bg)
                assertTrue("$v dim ink on $name is ${"%.2f".format(r)}", r >= Contrast.AA_NORMAL)
            }
        }
    }

    /** Text that sits on the 14 percent dim tint of a chip or a field must use the normal ink: the dim ink on its own tint falls under 4.5 to 1 in the dark theme. */
    @Test fun NFR_15_normalInkIsReadableAsSmallTextOnTheChipTintOfEveryVariant() {
        for (v in ThemeVariant.entries) {
            val t = HyleColors.tokens(v)
            val tint = { base: Color -> Color(Contrast.blend(t.inkDim.toArgb(), base.toArgb(), 0.14)) }
            for ((name, bg) in listOf("chip on background" to tint(t.background), "chip on surface" to tint(t.surface))) {
                val r = ratio(t.ink, bg)
                assertTrue("$v ink on $name is ${"%.2f".format(r)}", r >= Contrast.AA_NORMAL)
            }
        }
    }
}
