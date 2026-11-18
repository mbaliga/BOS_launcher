package com.bos.sphere.feature.grid

import android.content.ComponentName
import android.graphics.drawable.ColorDrawable
import android.os.Process
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onNodeWithContentDescription
import com.bos.sphere.core.data.AppEntry
import com.bos.sphere.core.data.Corner
import com.bos.sphere.core.data.WheelGrouping
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * INP-19, CFG-15 (wave 29 review): the owner's "touch area" complaint. On every window shape, corner and font scale the hub's search button and every
 * ring action are fully on screen and no two of them overlap, so each has its own touch area.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WheelDiscTouchAreaW29Test {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val apps = (0 until 40).map { i -> AppEntry("demo.t$i", ComponentName("demo.t$i", "M"), "${'A' + (i % 20)}app$i", Process.myUserHandle()) { ColorDrawable(0xFF2F6BFF.toInt()) } }
    private val labels = (1..9).map { "Action $it" }

    private fun check(anchor: Corner, scale: Float, count: Int) {
        RuntimeEnvironment.setFontScale(scale)
        rule.mainClock.autoAdvance = false
        val actions = labels.take(count).map { PuckAction(it, Glyphs.Dial) {} }
        rule.setContent {
            Box(Modifier.fillMaxSize()) {
                WheelDrawer(true, anchor, apps, WheelGrouping.Letter, {}, emptyList(), {}, {}, {}, {}, actions = actions, modifier = Modifier.fillMaxSize())
            }
        }
        rule.mainClock.advanceTimeBy(2_000)
        val root = rule.onRoot().fetchSemanticsNode().boundsInRoot
        val rects = mutableListOf<Pair<String, Rect>>()
        rects += "Search apps" to rule.onNodeWithContentDescription("Search apps").fetchSemanticsNode().boundsInRoot
        val shown = labels.take(count).filter { rule.onAllNodesWithContentDescription(it).fetchSemanticsNodes().isNotEmpty() }
        for (l in shown) rects += l to rule.onNodeWithContentDescription(l).fetchSemanticsNode().boundsInRoot
        val d = rule.activity.resources.displayMetrics.density
        val min = minOf(root.width, root.height) / d
        val fit = com.bos.sphere.core.input.PuckLayout.compute(min, count).capacity
        assertTrue("every ring action that fits ($fit of $count) is on the disc, got ${shown} slots ${com.bos.sphere.core.input.PuckLayout.compute(min, count).slots}", shown.size == minOf(count, fit))
        val chips = WheelGrouping.entries.map { it.label }
        val chipRects = chips.map { it to rule.onNodeWithContentDescription(it).fetchSemanticsNode().boundsInRoot }
        for ((n, r) in chipRects) assertTrue("chip $n $r is inside the window $root", r.left >= root.left - 0.5f && r.top >= root.top - 0.5f && r.right <= root.right + 0.5f && r.bottom <= root.bottom + 0.5f)
        for (i in chipRects.indices) for (j in i + 1 until chipRects.size) assertTrue("chips ${chipRects[i].first} and ${chipRects[j].first} overlap", !chipRects[i].second.overlaps(chipRects[j].second))
        for ((n, r) in rects) assertTrue("$n $r is inside the window $root", r.left >= root.left - 0.5f && r.top >= root.top - 0.5f && r.right <= root.right + 0.5f && r.bottom <= root.bottom + 0.5f)
        for (i in rects.indices) for (j in i + 1 until rects.size) {
            val a = rects[i].second; val b = rects[j].second
            assertTrue("${rects[i].first} and ${rects[j].first} overlap: $a $b", !a.overlaps(b))
        }
    }

    /** The app icons of the spokes are separate touch areas too: none overlaps another, and each hub control is clear of every icon. */
    private fun appsClear(anchor: Corner) {
        rule.mainClock.autoAdvance = false
        val actions = labels.take(5).map { PuckAction(it, Glyphs.Dial) {} }
        rule.setContent { Box(Modifier.fillMaxSize()) { WheelDrawer(true, anchor, apps, WheelGrouping.Letter, {}, emptyList(), {}, {}, {}, {}, actions = actions, modifier = Modifier.fillMaxSize()) } }
        rule.mainClock.advanceTimeBy(2_000)
        val icons = apps.mapNotNull { a -> rule.onAllNodesWithContentDescription(a.label).fetchSemanticsNodes().firstOrNull()?.let { a.label to it.boundsInRoot } }
        assertTrue("some app icons are on the disc", icons.size >= 6)
        for (i in icons.indices) for (j in i + 1 until icons.size) assertTrue("${icons[i].first} and ${icons[j].first} overlap", !icons[i].second.overlaps(icons[j].second))
        val hub = (listOf("Search apps") + labels.take(5)).flatMap { l -> rule.onAllNodesWithContentDescription(l).fetchSemanticsNodes().map { l to it.boundsInRoot } }
        for ((hn, hr) in hub) for ((an, ar) in icons) assertTrue("$hn overlaps the app icon $an", !hr.overlaps(ar))
    }

    @Test @Config(sdk = [34], qualifiers = "w360dp-h640dp-port-xhdpi") fun INP_19_w29_appIconsAndHubControlsDoNotOverlapPortrait() = appsClear(Corner.BottomLeft)
    @Test @Config(sdk = [34], qualifiers = "w640dp-h360dp-land-xhdpi") fun INP_19_w29_appIconsAndHubControlsDoNotOverlapLandscape() = appsClear(Corner.BottomRight)

    @Test @Config(sdk = [34], qualifiers = "w360dp-h640dp-port-xhdpi") fun INP_19_w29_smallPortraitRight() = check(Corner.BottomRight, 1f, 9)
    @Test @Config(sdk = [34], qualifiers = "w360dp-h640dp-port-xhdpi") fun INP_19_w29_smallPortraitLeftBigFont() = check(Corner.BottomLeft, 2f, 9)
    @Test @Config(sdk = [34], qualifiers = "w640dp-h360dp-land-xhdpi") fun INP_19_w29_smallLandscapeRight() = check(Corner.BottomRight, 1f, 9)
    @Test @Config(sdk = [34], qualifiers = "w640dp-h360dp-land-xhdpi") fun INP_19_w29_smallLandscapeLeftBigFont() = check(Corner.BottomLeft, 2f, 9)
    @Test @Config(sdk = [34], qualifiers = "w640dp-h360dp-land-xhdpi") fun INP_19_w29_landscapeTopCorners() { check(Corner.TopLeft, 1f, 9) }
}
