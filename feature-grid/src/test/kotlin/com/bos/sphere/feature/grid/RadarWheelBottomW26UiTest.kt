package com.bos.sphere.feature.grid

import android.content.ComponentName
import android.os.Process
import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import com.bos.sphere.core.data.AppEntry
import com.bos.sphere.core.data.LauncherPrefs
import com.bos.sphere.core.data.SearchLayout
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Wave 26 (audit of wave 25): the category wheel's lowest label (ACTIONS) must not reach into the result strip or the mic bar at 360x640 with font 2.0.
 * Every label the wheel draws visibly (its box) ends above the strip (typed Orbit) and above the mic.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class RadarWheelBottomW26UiTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    @Before fun english() { com.bos.sphere.core.design.L10n.setLanguage("en") }

    private val apps = listOf("Fylz", "Fylzer", "Filez", "Files").map { n -> AppEntry("demo.$n", ComponentName("demo.$n", "demo.$n.Main"), n, Process.myUserHandle()) { null } }

    private fun check(layout: SearchLayout, query: String, font: Float, keyboard: Float = 0f, cat: com.bos.sphere.core.data.SearchCategory? = null) {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            val d = androidx.compose.ui.platform.LocalDensity.current
            androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(d.density, font)) {
                SearchScreen(
                    apps = apps, recent = apps, prefs = LauncherPrefs(searchMotion = false, searchLayout = layout, searchLayoutPinned = true), contacts = null,
                    onLaunch = {}, onLongPressApp = {}, onOpenIntent = {}, onRequestContactsPermission = {}, onDismiss = {},
                    hub = null, onOpenHit = null, onRunAction = {}, onRequestFilePermission = {}, sounds = null, initialQuery = query, initialCategory = cat,
                    launchHistory = RadarTestHabits.history(apps), learnedOverride = emptyMap(), nowMillis = { RadarTestHabits.NOW }, voiceAvailable = true,
                )
            }
        }
        rule.mainClock.advanceTimeBy(1_500)
        if (keyboard > 0f) {
            val h = rule.onRoot().fetchSemanticsNode().boundsInRoot.height
            rule.runOnUiThread {
                val w = androidx.core.view.WindowInsetsCompat.Builder()
                    .setInsets(androidx.core.view.WindowInsetsCompat.Type.ime(), androidx.core.graphics.Insets.of(0, 0, 0, (h * keyboard).toInt()))
                    .setVisible(androidx.core.view.WindowInsetsCompat.Type.ime(), true).build()
                androidx.core.view.ViewCompat.dispatchApplyWindowInsets(rule.activity.window.decorView, w)
            }
            rule.mainClock.advanceTimeBy(500)
        }
        run {
            rule.mainClock.advanceTimeBy(900)
            val view = rule.activity.findViewById<android.view.View>(android.R.id.content)
            val bmp = android.graphics.Bitmap.createBitmap(view.width, view.height, android.graphics.Bitmap.Config.ARGB_8888)
            rule.runOnUiThread { view.draw(android.graphics.Canvas(bmp)) }
            val dir = java.io.File(System.getProperty("user.dir"), "build/screenshots").apply { mkdirs() }
            java.io.File(dir, "w26-wheel-$layout-${if (query.isEmpty()) "idle" else "typed"}-f$font-kb$keyboard-${cat ?: "all"}.png").outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        }
        val wheel = rule.onAllNodes(hasTestTag(RADAR_WHEEL_TAG)).fetchSemanticsNodes().first().boundsInRoot
        val mic = rule.onAllNodes(hasTestTag(RADAR_MIC_TAG)).fetchSemanticsNodes().first().boundsInRoot
        val strip = rule.onAllNodes(hasTestTag(RADAR_STRIP_TAG)).fetchSemanticsNodes().firstOrNull()?.boundsInRoot
        val d = rule.activity.resources.displayMetrics.density
        val where = "kb=$keyboard $layout '$query' font=$font wheel=$wheel strip=$strip mic=$mic dp=${wheel.bottom / d}"
        assertTrue("the wheel reaches into the strip: $where", strip == null || wheel.bottom <= strip.top + 0.5f)
        assertTrue("the wheel reaches into the mic bar: $where", wheel.bottom <= mic.top + 0.5f || mic.center.y < wheel.top)
        // every label whose centre is inside the wheel's box is drawn whole inside it
        val labels = rule.onAllNodes(androidx.compose.ui.test.SemanticsMatcher("wheel label") { n ->
            n.config.getOrNull(SemanticsProperties.Text) != null && generateSequence(n) { it.parent }.any { it.config.getOrNull(SemanticsProperties.TestTag) == RADAR_WHEEL_TAG }
        }, useUnmergedTree = true).fetchSemanticsNodes()
        // no label is cut off at its right end (font 2.0 turned EVERYTHING into EVERYTHIN)
        labels.forEach { n ->
            val r = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
            n.config.getOrNull(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult)?.action?.invoke(r)
            r.forEach { assertTrue("the wheel label ${n.config.getOrNull(SemanticsProperties.Text)} is cut off at font=$font (line right ${r.firstOrNull()?.getLineRight(0)} > ${r.firstOrNull()?.size?.width}): $where", it.getLineEnd(0, true) == it.layoutInput.text.length && it.getLineRight(0) <= it.size.width + 1f) }
        }
        labels.filter { it.boundsInRoot.center.y in wheel.top..wheel.bottom }.forEach {
            assertTrue("a visible label ${it.config.getOrNull(SemanticsProperties.Text)} sticks out of the wheel: ${it.boundsInRoot} in $where", it.boundsInRoot.bottom <= wheel.bottom + 0.5f && it.boundsInRoot.top >= wheel.top - 0.5f)
        }
    }

    @Test @Config(sdk = [35], qualifiers = "w360dp-h640dp-port-night-xhdpi") fun PAL_18_wheelStaysAboveTheStrip_orbitTyped_360x640_font2() = check(SearchLayout.Orbit, "fy", 2f)
    @Test @Config(sdk = [35], qualifiers = "w360dp-h640dp-port-night-xhdpi") fun PAL_18_wheelStaysAboveTheStrip_orbitIdle_360x640_font2() = check(SearchLayout.Orbit, "", 2f)
    @Test @Config(sdk = [35], qualifiers = "w360dp-h640dp-port-night-xhdpi") fun PAL_18_wheelStaysAboveTheMic_filmstrip_360x640_font2() = check(SearchLayout.Filmstrip, "", 2f)
    @Test @Config(sdk = [35], qualifiers = "w360dp-h640dp-port-night-xhdpi") fun PAL_18_wheelStaysAboveTheStrip_orbitTyped_360x640_font2_keyboard() = check(SearchLayout.Orbit, "fy", 2f, 0.4f)
    @Test @Config(sdk = [35], qualifiers = "w360dp-h640dp-port-night-xhdpi") fun PAL_18_wheelStaysAboveTheStrip_orbitIdle_360x640_font2_keyboard() = check(SearchLayout.Orbit, "", 2f, 0.4f)
    @Test @Config(sdk = [35], qualifiers = "w360dp-h640dp-port-night-xhdpi") fun PAL_18_wheelStaysAboveTheStrip_filmstrip_360x640_font2_keyboard() = check(SearchLayout.Filmstrip, "", 2f, 0.4f)
    @Test @Config(sdk = [35], qualifiers = "w360dp-h640dp-port-night-xhdpi") fun PAL_18_wheelStaysAboveTheStrip_orbitTyped_360x640_font1() = check(SearchLayout.Orbit, "fy", 1f)
    @Test @Config(sdk = [35], qualifiers = "w320dp-h480dp-port-night-xhdpi") fun PAL_18_wheelStaysAboveTheStrip_orbitTyped_320x480_font2() = check(SearchLayout.Orbit, "fy", 2f)
    @Test @Config(sdk = [35], qualifiers = "w640dp-h360dp-land-night-xhdpi") fun PAL_18_wheelStaysAboveTheStrip_orbitTyped_640x360_font2() = check(SearchLayout.Orbit, "fy", 2f)
    @Test @Config(sdk = [35], qualifiers = "w360dp-h640dp-port-night-xhdpi") fun PAL_18_wheelStaysAboveTheStrip_actionsSelected_360x640_font2() = check(SearchLayout.Orbit, "fy", 2f, cat = com.bos.sphere.core.data.SearchCategory.Actions)
    @Test @Config(sdk = [35], qualifiers = "w360dp-h640dp-port-night-xhdpi") fun PAL_18_wheelStaysAboveTheStrip_filesSelected_360x640_font2() = check(SearchLayout.Orbit, "fy", 2f, cat = com.bos.sphere.core.data.SearchCategory.Files)
    @Test @Config(sdk = [35], qualifiers = "w360dp-h640dp-port-night-xhdpi") fun PAL_18_wheelStaysAboveTheMic_actionsSelected_idle_360x640_font2() = check(SearchLayout.Orbit, "", 2f, cat = com.bos.sphere.core.data.SearchCategory.Actions)
}
