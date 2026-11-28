package com.bos.sphere.feature.grid

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.TextLayoutResult
import com.bos.sphere.core.data.ClockFormat
import com.bos.sphere.core.data.ClockSize
import com.bos.sphere.core.data.ClockStyle
import com.bos.sphere.core.data.LauncherPrefs
import com.bos.sphere.core.data.MediaInfo
import com.bos.sphere.core.design.HyleTheme
import com.bos.sphere.core.design.L10n
import com.bos.sphere.core.design.ReducedMotionTesting
import com.bos.sphere.core.design.ThemeVariant
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Wave 41 (glanceClock): the pure rules of the big Glance clock. */
class GlanceClockRulesW41Test {
    @Test fun GFX_41_wordClockReadsTheTimeInEnglishWords() {
        val cases = listOf(
            Triple(12, 0, "It's twelve o'clock"), Triple(0, 0, "It's twelve o'clock"), Triple(12, 30, "It's half past twelve"), Triple(6, 10, "It's ten past six"),
            Triple(6, 15, "It's quarter past six"), Triple(6, 45, "It's quarter to seven"), Triple(12, 55, "It's five to one"), Triple(11, 35, "It's twenty-five to twelve"),
            Triple(23, 50, "It's ten to twelve"), Triple(13, 5, "It's five past one"), Triple(9, 20, "It's twenty past nine"), Triple(3, 29, "It's twenty-five past three"),
            Triple(18, 59, "It's five to seven"), Triple(10, 0, "It's ten o'clock"),
        )
        for ((h, m, text) in cases) assertEquals("$h:$m", text, ClockText.wordsEnglish(h, m))
    }

    @Test fun GFX_41_everyMinuteOfADayHasWordsStartingWithItsAndNoBlankHour() {
        for (h in 0..23) for (m in 0..59) { val w = ClockText.wordsEnglish(h, m); assertTrue("$h:$m $w", w.startsWith("It's ") && w.length > 12) }
    }

    @Test fun GFX_41_wordClockFallsBackToBigDigitalOutsideEnglish() {
        assertEquals(ClockStyle.Word, ClockText.effectiveStyle(ClockStyle.Word, "en"))
        for (l in listOf("de", "fr", "ja", "ar")) assertEquals(l, ClockStyle.BigDigital, ClockText.effectiveStyle(ClockStyle.Word, l))
        assertEquals(ClockStyle.AnalogMinimal, ClockText.effectiveStyle(ClockStyle.AnalogMinimal, "de"))
    }

    @Test fun GFX_41_twelveOrTwentyFourHourDigits() {
        assertEquals("07", ClockText.hoursText(7, true)); assertEquals("7", ClockText.hoursText(7, false)); assertEquals("12", ClockText.hoursText(0, false)); assertEquals("12", ClockText.hoursText(12, false))
        assertEquals("1", ClockText.hoursText(13, false)); assertEquals("00", ClockText.hoursText(0, true)); assertEquals("05", ClockText.minutesText(5))
        assertTrue(ClockText.is24(ClockFormat.Hour24, false)); assertFalse(ClockText.is24(ClockFormat.Hour12, true)); assertTrue(ClockText.is24(ClockFormat.System, true)); assertFalse(ClockText.is24(ClockFormat.System, false))
        assertEquals("h:mm", ClockText.timePattern(ClockFormat.Hour12, true)); assertEquals("HH:mm", ClockText.timePattern(ClockFormat.System, true))
    }

    @Test fun GFX_41_handAnglesAreClockwiseFromTwelve() {
        val (h3, m0) = ClockText.handAngles(3, 0)
        assertEquals(Math.PI.toFloat() / 2f, h3, 1e-4f); assertEquals(0f, m0, 1e-4f)
        val (h6, m30) = ClockText.handAngles(18, 30)
        assertEquals(Math.PI.toFloat() * (6.5f / 6f), h6, 1e-3f); assertEquals(Math.PI.toFloat(), m30, 1e-4f)
    }

    @Test fun GFX_41_theFitNeverExceedsTheMaximumAndShrinksUntilItFits() {
        assertEquals(100f, ClockText.fit(100f, 20f) { true }, 0f)
        val s = ClockText.fit(100f, 20f) { it <= 57f }
        assertTrue(s in 56.5f..57f)
        assertEquals("nothing fits: the minimum", 20f, ClockText.fit(100f, 20f) { false }, 0.1f)
    }

    @Test fun GFX_41_sizesAreSmallMediumLargeAndLargeIsTheDefault() {
        assertEquals(listOf(ClockSize.Small, ClockSize.Medium, ClockSize.Large), ClockSize.entries)
        assertTrue(ClockSize.Small.factor < ClockSize.Medium.factor && ClockSize.Medium.factor < ClockSize.Large.factor)
        assertEquals(1f, ClockSize.Large.factor, 0f); assertEquals(ClockSize.Large, LauncherPrefs().glanceClockSize)
    }

    @Test fun GFX_41_theSixStylesAreThere() {
        assertEquals(listOf("BigDigital", "BigDigitalLine", "AnalogClassic", "AnalogMinimal", "Word", "Off"), ClockStyle.entries.map { it.name })
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GlanceClockW41UiTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val outDir = File(System.getProperty("user.dir"), "build/screenshots").apply { mkdirs() }

    @After fun reset() {
        Snapshot.withMutableSnapshot { HyleTheme.apply(ThemeVariant.Dark, 0xFF8E7BFF) }
        ReducedMotionTesting.force(null)
        L10n.setLanguage("")
        runCatching { LauncherInput.focus.nodes().map { it.id }.forEach(LauncherInput.focus::unregister) }
    }

    private val media = MediaInfo("Someday Out of The Blue", "Artist", "pkg.music", playing = true, canPrev = true, canNext = true)
    private var taps = 0

    private val cur = androidx.compose.runtime.mutableStateOf(LauncherPrefs())
    private val curDark = androidx.compose.runtime.mutableStateOf(true)
    private var shown = false

    /** First call sets the content; later calls swap the prefs (Compose allows one setContent per test). */
    private fun show(prefs: LauncherPrefs, dark: Boolean = true, m: MediaInfo? = media) {
        Snapshot.withMutableSnapshot { cur.value = prefs; curDark.value = dark }
        if (shown) return
        shown = true
        rule.setContent {
            GlanceHomeForTest(cur.value, m, emptyList(), emptyMap(), true, { _, _ -> }, {}, {}, {}, Modifier.fillMaxSize(), dark = curDark.value, onClockTap = { taps++ })
        }
    }

    private fun text(tag: String): String {
        val results = ArrayList<TextLayoutResult>()
        rule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
        return results.first().layoutInput.text.text
    }

    private fun count(tag: String) = rule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().size
    private fun root() = rule.onRoot().fetchSemanticsNode().boundsInRoot

    private fun snap(): Bitmap {
        rule.mainClock.advanceTimeBy(300)
        val view = rule.activity.findViewById<android.view.View>(android.R.id.content)
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread { android.graphics.Canvas(bmp).also { it.drawColor(android.graphics.Color.BLACK); view.draw(it) } }
        return bmp
    }

    /** The pixels of the clock node (the whole view is drawn, then cropped to the node's bounds). */
    private fun clockPixels(): Bitmap {
        val b = rule.onNodeWithTag("glance.clock", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val full = snap()
        val l = b.left.toInt().coerceIn(0, full.width - 1); val t = b.top.toInt().coerceIn(0, full.height - 1)
        return Bitmap.createBitmap(full, l, t, b.width.toInt().coerceIn(1, full.width - l), b.height.toInt().coerceIn(1, full.height - t))
    }

    private fun shoot(name: String) {
        val bmp = snap()
        File(outDir, "glance_clock_$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun lum(c: Int): Double {
        fun ch(v: Int): Double { val x = v / 255.0; return if (x <= 0.03928) x / 12.92 else Math.pow((x + 0.055) / 1.055, 2.4) }
        return 0.2126 * ch(android.graphics.Color.red(c)) + 0.7152 * ch(android.graphics.Color.green(c)) + 0.0722 * ch(android.graphics.Color.blue(c))
    }
    private fun contrast(a: Int, b: Int): Double { val l1 = lum(a); val l2 = lum(b); return (maxOf(l1, l2) + 0.05) / (minOf(l1, l2) + 0.05) }

    /** The pixels of the clock node that differ from the surface; asserts there are some, that they contrast with the surface, and the node is inside the page. */
    private fun assertDrawnAndLegible(dark: Boolean) {
        val surface = if (dark) android.graphics.Color.BLACK else 0xFFF3F3F6.toInt()
        val b = rule.onNodeWithTag("glance.clock", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val r = root()
        assertTrue("inside the page: $b in $r", b.left >= r.left - 1f && b.right <= r.right + 1f && b.top >= r.top - 1f && b.bottom <= r.bottom + 1f)
        val bmp = clockPixels()
        var ink = 0; var best = 1.0
        for (y in 0 until bmp.height step 2) for (x in 0 until bmp.width step 2) {
            val p = bmp.getPixel(x, y)
            if (android.graphics.Color.alpha(p) > 200 && contrast(p, surface) > 4.5) ink++
            best = maxOf(best, contrast(p, surface))
        }
        assertTrue("some pixels are the clock's ink (dark=$dark): $ink", ink > 20)
        assertTrue("the ink reads on the surface: $best", best >= 7.0)
    }

    private val port = "w411dp-h891dp-port-xhdpi"

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun GFX_41_everyStyleDrawsInsideThePageAndReadsOnDarkAndLight() {
        for (style in listOf(ClockStyle.BigDigital, ClockStyle.BigDigitalLine, ClockStyle.AnalogClassic, ClockStyle.AnalogMinimal, ClockStyle.Word)) for (dark in listOf(true, false)) {
            show(LauncherPrefs(clockStyle = style), dark)
            rule.waitForIdle()
            assertEquals("$style exists", 1, count("glance.clock"))
            assertDrawnAndLegible(dark)
        }
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun GFX_41_bigDigitalIsHoursOverMinutesWithAThinAccentLineBetween() {
        show(LauncherPrefs(clockStyle = ClockStyle.BigDigital, clockFormat = ClockFormat.Hour24)); rule.waitForIdle()
        assertTrue(text("glance.clock.hours").matches(Regex("\\d\\d"))); assertTrue(text("glance.clock.minutes").matches(Regex("\\d\\d")))
        val h = rule.onNodeWithTag("glance.clock.hours", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val l = rule.onNodeWithTag("glance.clock.line", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val m = rule.onNodeWithTag("glance.clock.minutes", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue("hours, then the line, then minutes", h.bottom <= l.top + 1f && l.bottom <= m.top + 1f)
        val d = rule.activity.resources.displayMetrics.density
        assertTrue("the line is thin", l.height / d <= 3f)
        // the digits are big: much taller than the 14 sp music line
        assertTrue("big numerals", h.height / d > 60f)
        shoot("bigdigital_dark_portrait")
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun GFX_41_singleLineIsHHmmAndTwelveHourHasNoLeadingZero() {
        show(LauncherPrefs(clockStyle = ClockStyle.BigDigitalLine, clockFormat = ClockFormat.Hour24)); rule.waitForIdle()
        assertTrue(text("glance.clock.line.text").matches(Regex("\\d\\d:\\d\\d")))
        shoot("bigdigital_line_dark_portrait")
        show(LauncherPrefs(clockStyle = ClockStyle.BigDigitalLine, clockFormat = ClockFormat.Hour12)); rule.waitForIdle()
        assertTrue(text("glance.clock.line.text").matches(Regex("(1[0-2]|[1-9]):\\d\\d")))
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun GFX_41_analogStylesDrawADialAndNoDigits() {
        show(LauncherPrefs(clockStyle = ClockStyle.AnalogClassic)); rule.waitForIdle()
        assertEquals(1, count("glance.clock.analogClassic")); assertEquals(0, count("glance.clock.hours")); assertEquals(0, count("glance.clock.analogMinimal"))
        val b = rule.onNodeWithTag("glance.clock.analogClassic", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val d = rule.activity.resources.displayMetrics.density
        assertTrue("a large dial: ${b.width / d} dp", b.width / d >= 200f && b.width == b.height)
        shoot("analog_classic_dark_portrait")
        show(LauncherPrefs(clockStyle = ClockStyle.AnalogMinimal)); rule.waitForIdle()
        assertEquals(1, count("glance.clock.analogMinimal")); assertEquals(0, count("glance.clock.analogClassic"))
        shoot("analog_minimal_dark_portrait")
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun GFX_41_wordClockShowsWordsInEnglishAndBigDigitalInOtherLanguages() {
        show(LauncherPrefs(clockStyle = ClockStyle.Word)); rule.waitForIdle()
        assertTrue(text("glance.clock.word").startsWith("It's "))
        shoot("word_dark_portrait")
        L10n.setLanguage("de")
        show(LauncherPrefs(clockStyle = ClockStyle.Word)); rule.waitForIdle()
        assertEquals(0, count("glance.clock.word")); assertEquals(1, count("glance.clock.hours"))
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun GFX_41_offDrawsNoClockAndTheDateIsOptional() {
        show(LauncherPrefs(clockStyle = ClockStyle.Off)); rule.waitForIdle()
        assertEquals(0, count("glance.clock")); assertEquals(0, count("glance.clock.area"))
        show(LauncherPrefs(clockStyle = ClockStyle.BigDigital)); rule.waitForIdle()
        assertEquals("the date is off by default", 0, count("glance.clock.date"))
        show(LauncherPrefs(clockStyle = ClockStyle.BigDigital, glanceClockDate = true)); rule.waitForIdle()
        assertEquals(1, count("glance.clock.date")); assertTrue(text("glance.clock.date").isNotBlank())
        val c = rule.onNodeWithTag("glance.clock.minutes", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val dt = rule.onNodeWithTag("glance.clock.date", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue("the date is under the clock", dt.top >= c.bottom - 1f)
        shoot("bigdigital_date_dark_portrait")
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun GFX_41_sizeOptionsScaleTheClockAndLargeIsTheBiggest() {
        val heights = ClockSize.entries.map { size ->
            show(LauncherPrefs(clockStyle = ClockStyle.BigDigital, glanceClockSize = size)); rule.waitForIdle()
            val h = rule.onNodeWithTag("glance.clock.hours", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.height
            h
        }
        assertTrue("small < medium < large: $heights", heights[0] < heights[1] && heights[1] < heights[2])
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi", fontScale = 2f)
    fun GFX_41_aLargeFontScaleNeverOverflowsThePage() {
        for (style in listOf(ClockStyle.BigDigital, ClockStyle.BigDigitalLine, ClockStyle.Word)) {
            show(LauncherPrefs(clockStyle = style, glanceClockDate = true)); rule.waitForIdle()
            val b = rule.onNodeWithTag("glance.clock", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            val r = root()
            assertTrue("$style fits at font scale 2: $b in $r", b.left >= 0f && b.right <= r.right && b.bottom <= r.bottom)
        }
    }

    @Test @Config(sdk = [35], qualifiers = "w891dp-h411dp-land-xhdpi")
    fun GFX_41_landscapeScalesTheClockToFit() {
        for (style in listOf(ClockStyle.BigDigital, ClockStyle.BigDigitalLine, ClockStyle.AnalogClassic, ClockStyle.Word)) {
            show(LauncherPrefs(clockStyle = style, glanceClockDate = true)); rule.waitForIdle()
            val b = rule.onNodeWithTag("glance.clock", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            val r = root()
            assertTrue("$style in landscape: $b in $r", b.left >= 0f && b.right <= r.right && b.top >= 0f && b.bottom <= r.bottom * 0.75f)
            if (style == ClockStyle.BigDigital) shoot("bigdigital_dark_landscape")
            if (style == ClockStyle.AnalogClassic) shoot("analog_classic_dark_landscape")
        }
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun GFX_41_theClockIsHorizontallyCentredAboveTheMiddleAndTheMusicLineIsAboveIt() {
        show(LauncherPrefs(clockStyle = ClockStyle.BigDigital)); rule.waitForIdle()
        val c = rule.onNodeWithTag("glance.clock", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val r = root()
        assertEquals(r.center.x, c.center.x, 2f)
        assertTrue("upper-middle: centre at ${c.center.y} of ${r.height}", c.center.y in r.height * 0.15f..r.height * 0.5f)
        val music = rule.onNodeWithTag("glance.title.static", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue("the music line is the top line, above the clock", music.bottom <= c.top + 1f)
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun GFX_41_aTapOnTheClockOpensTheClockAppAndTheAccentOptionChangesTheInk() {
        taps = 0
        show(LauncherPrefs(clockStyle = ClockStyle.BigDigital)); rule.waitForIdle()
        rule.onNodeWithTag("glance.clock", useUnmergedTree = true).performClick()
        assertEquals(1, taps)
        val white = clockPixels()
        fun brightest(b: Bitmap): Int { var best = 0; for (y in 0 until b.height step 2) for (x in 0 until b.width step 2) { val p = b.getPixel(x, y); if (lum(p) > lum(best)) best = p }; return best }
        assertTrue("automatic ink on dark is near white", android.graphics.Color.red(brightest(white)) > 200 && android.graphics.Color.blue(brightest(white)) > 200)
        show(LauncherPrefs(clockStyle = ClockStyle.BigDigital, glanceClockAccent = true)); rule.waitForIdle()
        val acc = clockPixels()
        val p = brightest(acc)
        assertTrue("the accent ink is violet, not white: ${Integer.toHexString(p)}", android.graphics.Color.red(p) < 200 || android.graphics.Color.green(p) < 180)
    }
}
