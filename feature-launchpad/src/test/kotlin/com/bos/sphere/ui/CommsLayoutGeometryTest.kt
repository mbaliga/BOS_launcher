package com.bos.sphere.ui

import android.graphics.Bitmap
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Wave 28 (commsLayout): the real Comms screen and menu panel, rendered at 411 dp x 909 dp and 352 dpi (one image pixel is one device pixel of the
 * owner's 904 px wide mock-ups), measured pixel by pixel against the table in docs/decisions/commsLayout.md (measured off the mock-ups with PIL):
 * every element lands within 2 px of the mock-up, text by its left edge and baseline (the app's own font has other glyph widths). Side-by-side
 * pictures go to build/screenshots/comms-geo-*-diff.png ([MockDiff.sideBySide]).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h909dp-352dpi", sdk = [35])
class CommsLayoutGeometryTest : CommsLayoutCases("geo") {
    private val pageBg = 0xFF0E121A.toInt()
    private val panelBg = 0xFF181818.toInt()
    private val failures = ArrayList<String>()

    /** [actual] within [tol] px of the mock-up's [expected]. */
    private fun near(what: String, expected: Int, actual: Int?, tol: Int = 2) {
        if (actual == null || Math.abs(expected - actual) > tol) failures += "$what: mock-up $expected, rendered $actual (tolerance $tol)"
    }
    private fun check() { assertTrue("elements off the mock-up:\n" + failures.joinToString("\n"), failures.isEmpty()) }

    private fun screen(): Bitmap {
        render(); rule.onNodeWithTag("hub.view.unread").performClick(); rule.waitForIdle()
        return frame().also { MockDiff.sideBySide(MockDiff.mock("comms-mock-screen.png"), it, File(outDir, "comms-geo-screen-diff.png")); File(outDir, "comms-geo-screen.png").outputStream().use { o -> it.compress(Bitmap.CompressFormat.PNG, 100, o) } }
    }

    @Test fun CML_01_headerLandsOnTheMockUp() {
        val b = screen()
        assertEquals("one image pixel per device pixel, as wide as the mock-up", 904, b.width)
        MockDiff.ink(b, 40, 60, 110, 125, pageBg)!!.let { near("menu glyph left", 70, it[0]); near("menu glyph right", 97, it[2]); near("menu glyph top", 80, it[1]); near("menu glyph bottom", 108, it[3]) }
        MockDiff.ink(b, 115, 60, 330, 125, pageBg)!!.let { near("title left", 129, it[0]); near("title top", 77, it[1]); near("title baseline", 104, it[3]) }
        MockDiff.ink(b, 40, 165, 400, 240, pageBg)!!.let { near("clock left", 73, it[0]); near("clock baseline", 219, it[3]); near("clock digit top", 183, it[1], 3) }
        check()
    }

    @Test fun CML_02_statusGlyphsLandOnTheMockUp() {
        val b = screen()
        // centres of the four inks: brightness, battery, signal, Wi-Fi (the sun sits 3 px higher than the rest)
        val cx = listOf(595, 656, 711, 777); val cy = listOf(198, 201, 201, 201); val names = listOf("brightness", "battery", "signal", "wifi")
        val regions = listOf(556 to 625, 632 to 684, 686 to 740, 744 to 810)
        for (i in 0 until 4) MockDiff.ink(b, regions[i].first, 165, regions[i].second, 240, pageBg)!!.let {
            near("${names[i]} centre x", cx[i], (it[0] + it[2]) / 2); near("${names[i]} centre y", cy[i], (it[1] + it[3]) / 2)
        }
        check()
    }

    @Test fun CML_03_tabsAndTheirHairlineLandOnTheMockUp() {
        val b = screen()
        val left = listOf(70, 174, 358, 489, 644); val ranges = listOf(60 to 150, 160 to 330, 340 to 470, 475 to 625, 630 to 830)
        for (i in 0 until 5) MockDiff.ink(b, ranges[i].first, 270, ranges[i].second, 325, pageBg, 25)!!.let {
            near("tab $i left", left[i], it[0]); near("tab $i cap top", 286, it[1]); near("tab $i baseline", 312, it[3])
        }
        val rows = MockDiff.lineRows(b, 100, 700, 320, 350, pageBg)
        near("tab hairline", 337, rows.firstOrNull()); assertTrue("one hairline of two pixels at most: $rows", rows.size <= 3)
        MockDiff.ink(b, 60, 330, 830, 345, pageBg, 8)!!.let { near("hairline left", 70, it[0]); near("hairline right", 800, it[2]) }
        check()
    }

    @Test fun CML_04_rowsLandOnTheMockUp() {
        val b = screen()
        val iconTop = listOf(374, 529, 684, 838)
        for (i in 0 until 4) {
            MockDiff.ink(b, 55, iconTop[i] - 20, 170, iconTop[i] + 100, pageBg, 30)!!.let {
                near("row $i icon left", 70, it[0]); near("row $i icon top", iconTop[i], it[1]); near("row $i icon right", 152, it[2]); near("row $i icon bottom", iconTop[i] + 83, it[3])
            }
        }
        val lines = MockDiff.lineRows(b, 100, 700, 480, 830, pageBg, 8)
        // each hairline is one or two rows; the first row of each run
        val tops = lines.filterIndexed { i, y -> i == 0 || y != lines[i - 1] + 1 }
        assertEquals("three row hairlines between the four rows: $lines", 3, tops.size)
        listOf(492, 647, 801).forEachIndexed { i, y -> near("row hairline $i", y, tops[i]) }
        MockDiff.ink(b, 60, tops[0], 830, tops[0] + 1, pageBg, 8)!!.let { near("row hairline left", 70, it[0]); near("row hairline right", 800, it[2]) }
        // row 1: Lemon / N.E.R.D & Rihanna / 17:34
        MockDiff.ink(b, 180, 375, 500, 425, pageBg)!!.let { near("title left", 191, it[0]); near("title top", 387, it[1]); near("title baseline", 413, it[3]) }
        MockDiff.ink(b, 180, 432, 500, 470, pageBg, 30)!!.let { near("second line left", 190, it[0]); near("second line top", 439, it[1]); near("second line baseline", 456, it[3]) }
        MockDiff.ink(b, 690, 425, 830, 470, pageBg, 30)!!.let { near("time right", 800, it[2]); near("time top", 435, it[1]); near("time baseline", 452, it[3]) }
        // row 2: Can we meet for Lunch? (the mock-up's rows are 155 px apart, its text 156: 3 px for the text of later rows)
        MockDiff.ink(b, 180, 525, 700, 580, pageBg)!!.let { near("row 2 title left", 190, it[0]); near("row 2 title baseline", 569, it[3], 3) }
        check()
    }

    @Test fun CML_05_theHomeCardSliverStartsWhereTheMockUpHasIt() {
        val b = screen()
        val cols = (800..903).filter { x -> b.getPixel(x, 1000) != pageBg }
        near("card edge", 843, cols.firstOrNull(), 3)
        check()
    }

    @Test fun CML_06_menuPanelLandsOnTheMockUp() {
        render(); openMenu()
        val b = frame()
        MockDiff.sideBySide(MockDiff.mock("comms-mock-menu.png"), b, File(outDir, "comms-geo-menu-diff.png"))
        File(outDir, "comms-geo-menu.png").outputStream().use { o -> b.compress(Bitmap.CompressFormat.PNG, 100, o) }
        val edge = (400..700).firstOrNull { x -> b.getPixel(x, 1500) != panelBg }
        near("panel width", 528, edge)
        MockDiff.ink(b, 60, 60, 300, 105, panelBg)!!.let { near("GROUP BY left", 72, it[0]); near("GROUP BY top", 73, it[1]); near("GROUP BY baseline", 91, it[3]) }
        val v = MockDiff.lineCols(b, 60, 500, 135, 150, panelBg, 8)
        near("grid divider 1", 208, v.firstOrNull { it in 190..230 }); near("grid divider 2", 347, v.firstOrNull { it in 330..370 })
        val h = MockDiff.lineRows(b, 75, 100, 120, 330, panelBg, 8)
        near("grid hairline", 223, h.firstOrNull())
        MockDiff.ink(b, 60, 120, 500, 330, panelBg, 8)!!.let { near("grid left", 71, it[0]); near("grid right", 485, it[2]); near("grid top", 132, it[1]); near("grid bottom", 315, it[3]) }
        MockDiff.ink(b, 60, 340, 300, 400, panelBg)!!.let { near("SHOW left", 72, it[0]); near("SHOW top", 369, it[1]); near("SHOW baseline", 387, it[3]) }
        // All Comms: the name's left edge and cap height, the count's right edge
        MockDiff.ink(b, 120, 420, 330, 475, panelBg)!!.let { near("All Comms left", 143, it[0]); near("All Comms cap top", 436, it[1]); near("All Comms baseline", 463, it[3]) }
        MockDiff.ink(b, 400, 420, 520, 475, panelBg, 12)!!.let { near("count right", 483, it[2]) }
        // the screen behind is pushed right by 551 px: its menu glyph (mock-up 620..648) and its title
        MockDiff.ink(b, 560, 60, 700, 125, pageBg)!!.let { near("shifted menu glyph left", 620, it[0]) }
        check()
    }
}
