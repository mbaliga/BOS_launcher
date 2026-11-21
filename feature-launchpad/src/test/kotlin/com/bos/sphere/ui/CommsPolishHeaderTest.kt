package com.bos.sphere.ui

import android.graphics.Bitmap
import androidx.compose.ui.test.onNodeWithTag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Wave 30 (commsPolish): the menu glyph and the title of the Comms header share one vertical centre (owner: "the comms text and three bars are
 * misaligned"). Measured on the rendered frame at 411 dp x 909 dp and 352 dpi: the ink of the three bars and the ink of "Comms" (its capitals, no
 * ascenders or descenders) have the same vertical centre within 1 px and the glyph is as high as the capitals, at 100, 75 and 125 percent, in the light
 * look, mirrored (right-to-left) and at font scale 2. Pictures: build/screenshots/comms-header-*.png (the header crop at 3x).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h909dp-352dpi", sdk = [35])
class CommsPolishHeaderTest : CommsLayoutCases("polish") {
    private class Pair(val glyph: IntArray, val title: IntArray, val bmp: Bitmap)

    private fun measure(name: String, dark: Boolean = true, rtl: Boolean = false, scale: Int? = null, home: Boolean = false): Pair {
        render(dark = dark, rtl = rtl, home = home, scale = scale)
        val b = frame()
        val bg = b.getPixel(b.width / 2, 4)
        val menu = rule.onNodeWithTag("lp.menu").fetchSemanticsNode().boundsInRoot
        val title = rule.onNodeWithTag("lp.title").fetchSemanticsNode().boundsInRoot
        // the glyph's ink: inside the button's square; the title's ink: inside the text's box
        val gl = if (rtl) maxOf(menu.left.toInt(), title.right.toInt() + 2) else menu.left.toInt()
        val gr = if (rtl) menu.right.toInt() else minOf(menu.right.toInt(), title.left.toInt() - 2)
        val g = MockDiff.ink(b, gl, menu.top.toInt(), gr, menu.bottom.toInt(), bg, 90)!!
        val t = MockDiff.ink(b, title.left.toInt(), title.top.toInt() - 4, title.right.toInt(), title.bottom.toInt() + 4, bg, 90)!!
        val crop = Bitmap.createBitmap(b, 0, (menu.top.toInt() - 10).coerceAtLeast(0), b.width, (menu.height.toInt() + 20).coerceAtMost(b.height))
        File(outDir, "comms-header-$name.png").outputStream().use { Bitmap.createScaledBitmap(crop, crop.width, crop.height, false).compress(Bitmap.CompressFormat.PNG, 100, it) }
        println("HEADER $name glyph=${g.toList()} title=${t.toList()}")
        return Pair(g, t, b)
    }

    private fun aligned(what: String, p: Pair, capPx: Float) {
        val gc = (p.glyph[1] + p.glyph[3]) / 2f; val tc = (p.title[1] + p.title[3]) / 2f
        assertEquals("$what: vertical centre of the glyph ${p.glyph.toList()} vs the title ${p.title.toList()}", tc, gc, 1f)
        val gh = p.glyph[3] - p.glyph[1] + 1
        assertEquals("$what: the glyph is as high as the capitals ($capPx px)", capPx, gh.toFloat(), 1.5f)
    }

    private fun cap(scale: Float = 1f, font: Float = 1f) = CommsStyle.CAP_HEIGHT_EM * CommsStyle.TITLE_SP * 2.2f * scale * font

    @Test fun CMP_01_theMenuGlyphAndTheTitleShareOneVerticalCentreAt100Percent() = aligned("100%", measure("100"), cap())

    @Test fun CMP_01_theMenuGlyphAndTheTitleShareOneVerticalCentreAt75And125Percent() {
        aligned("75%", measure("75", scale = 75), cap(0.75f))
    }

    @Test fun CMP_01_theMenuGlyphAndTheTitleShareOneVerticalCentreAt125Percent() = aligned("125%", measure("125", scale = 125), cap(1.25f))

    @Test fun CMP_01_theMenuGlyphAndTheTitleShareOneVerticalCentreInTheLightLook() = aligned("light", measure("light", dark = false), cap())

    @Test fun CMP_01_theMenuGlyphAndTheTitleShareOneVerticalCentreMirrored() {
        val p = measure("rtl", rtl = true)
        aligned("rtl", p, cap())
        assertTrue("the glyph is at the right: ${p.glyph.toList()}", p.glyph[0] > p.bmp.width / 2)
    }

    @Test fun CMP_01_theGlyphFollowsTheTitleAtFontScale2() {
        RuntimeEnvironment.setFontScale(2f)
        aligned("font 2.0", measure("font2"), cap(font = 2f))
    }

    @Test fun CMP_02_theGapBetweenTheGlyphAndTheTitleStaysAsInTheMockUp() {
        val p = measure("gap", home = true)
        assertEquals("glyph left (mock-up 70 px)", 70f, p.glyph[0].toFloat(), 2f)
        assertEquals("title left (mock-up 129 px)", 129f, p.title[0].toFloat(), 2f)
    }
}
