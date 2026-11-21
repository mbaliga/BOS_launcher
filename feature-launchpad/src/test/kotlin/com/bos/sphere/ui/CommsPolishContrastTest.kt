package com.bos.sphere.ui

import androidx.compose.ui.graphics.toArgb
import com.bos.sphere.core.design.WallpaperPalette
import org.junit.Assert.assertTrue
import org.junit.Test

/** Wave 30 (commsPolish): the muted read tokens and the accent time stay readable on the Comms page, dark and light (WCAG: 4.5 text, 3 secondary). */
class CommsPolishContrastTest {
    private val SECONDARY = 3.0
    private fun ratio(fg: androidx.compose.ui.graphics.Color, bg: androidx.compose.ui.graphics.Color) = WallpaperPalette.contrast(fg.toArgb(), bg.toArgb())

    private fun check(name: String, lp: LpColors) {
        val read = CommsRowTone.of(false, lp, BlueLight); val unread = CommsRowTone.of(true, lp, BlueLight)
        assertTrue("$name read headline ${ratio(read.title, lp.bg)}", ratio(read.title, lp.bg) >= CommsStyle.MIN_CONTRAST_TEXT)
        assertTrue("$name read second line ${ratio(read.sub, lp.bg)}", ratio(read.sub, lp.bg) >= SECONDARY)
        assertTrue("$name read time ${ratio(read.time, lp.bg)}", ratio(read.time, lp.bg) >= SECONDARY)
        assertTrue("$name unread headline", ratio(unread.title, lp.bg) >= CommsStyle.MIN_CONTRAST_TEXT)
        assertTrue("$name unread second line", ratio(unread.sub, lp.bg) >= SECONDARY)
        assertTrue("$name accent time ${ratio(unread.time, lp.bg)}", ratio(unread.time, lp.bg) >= CommsStyle.MIN_CONTRAST_TEXT)
        assertTrue("$name dot ${ratio(unreadDotColor(lp), lp.bg)}", ratio(unreadDotColor(lp), lp.bg) >= SECONDARY)
        // and the two states differ clearly: the read headline is dimmer than the unread one
        assertTrue("$name read vs unread headline", ratio(unread.title, read.title) >= 1.5)
    }

    @Test fun CMP_20_theMutedReadInkAndTheAccentTimeAreReadableOnTheDarkPage() = check("dark", LpDark)
    @Test fun CMP_20_theMutedReadInkAndTheAccentTimeAreReadableOnTheLightPage() = check("light", LpLight)
}
