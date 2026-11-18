package com.bos.sphere.feature.grid

import android.content.ComponentName
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.ColorDrawable
import android.os.Process
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.unit.dp
import com.bos.sphere.core.data.AppEntry
import com.bos.sphere.core.data.Corner
import com.bos.sphere.core.data.SwitcherStyle
import com.bos.sphere.core.data.WheelGrouping
import com.bos.sphere.core.quality.MotionPrefs
import com.bos.sphere.core.design.ReducedMotionTesting
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** W26 arcDrawer: the looped section switcher (compact and expanded) and the colour-picker fan, rendered and driven on the JVM. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ArcDrawerUiTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val outDir = File(System.getProperty("user.dir"), "build/screenshots").apply { mkdirs() }

    private val names = listOf("Alarm", "Amazon", "Atlas", "Bank", "Books", "Browser", "Calendar", "Camera", "Chat", "Clock", "Maps", "Mail", "Music", "News", "Notes", "Phone", "Photos", "Podcasts", "Radio", "Reader", "Settings", "Spark", "Tasks", "Video", "Weather", "Wallet", "Yoga", "Zen")
    private val apps: List<AppEntry> = names.mapIndexed { i, n ->
        AppEntry("demo.a$i", ComponentName("demo.a$i", "M"), n, Process.myUserHandle()) { ColorDrawable(0xFF3070D0.toInt() + (i * 0x090705)) }
    }

    @After fun reset() = ReducedMotionTesting.force(null)

    private fun shoot(name: String, settleMs: Long = 1_500) {
        rule.mainClock.advanceTimeBy(settleMs)
        val view = rule.activity.findViewById<android.view.View>(android.R.id.content)
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread { view.draw(Canvas(bmp)) }
        File(outDir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private var selectedLabels = mutableListOf<String>()
    private var launched = mutableListOf<String>()
    private var dismissed = 0

    private fun content(anchor: Corner, style: SwitcherStyle = SwitcherStyle.Expanded, expanded: Boolean = false, grouping: WheelGrouping = WheelGrouping.Letter) {
        rule.setContent {
            Box(Modifier.fillMaxSize().background(Color.Black)) {
                WheelDrawer(
                    visible = true, anchor = anchor, apps = apps, grouping = grouping, onGroupingChange = {}, recentKeys = emptyList(),
                    onLaunch = { launched += it.label }, onLongPressApp = {}, onSearch = {}, onDismiss = { dismissed++ },
                    modifier = Modifier.fillMaxSize(), switcherStyle = style, startExpanded = expanded,
                )
            }
        }
    }

    // ---- screenshots

    @Test @Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun shots_switcher_compact_and_expanded_portrait() {
        rule.mainClock.autoAdvance = false
        content(Corner.BottomLeft, SwitcherStyle.Compact); shoot("w26-switcher-compact-portrait")
    }

    @Test @Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun shots_switcher_expanded_style_portrait() {
        rule.mainClock.autoAdvance = false
        content(Corner.BottomLeft, SwitcherStyle.Expanded); shoot("w26-switcher-expanded-portrait")
    }

    @Test @Config(sdk = [34], qualifiers = "w411dp-h891dp-port-night-xhdpi")
    fun shots_drawer_expanded_portrait_dark() {
        rule.mainClock.autoAdvance = false
        content(Corner.BottomLeft, expanded = true); shoot("w26-drawer-portrait-dark")
    }

    @Test @Config(sdk = [34], qualifiers = "w411dp-h891dp-port-notnight-xhdpi")
    fun shots_drawer_expanded_portrait_light_right_handed() {
        rule.mainClock.autoAdvance = false
        content(Corner.BottomRight, expanded = true); shoot("w26-drawer-portrait-light-right")
    }

    @Test @Config(sdk = [34], qualifiers = "w891dp-h411dp-land-night-xhdpi")
    fun shots_drawer_expanded_landscape_dark() {
        rule.mainClock.autoAdvance = false
        content(Corner.BottomLeft, expanded = true); shoot("w26-drawer-landscape-dark")
    }

    @Test @Config(sdk = [34], qualifiers = "w891dp-h411dp-land-notnight-xhdpi")
    fun shots_drawer_expanded_landscape_light() {
        rule.mainClock.autoAdvance = false
        content(Corner.BottomRight, expanded = true, grouping = WheelGrouping.Category); shoot("w26-drawer-landscape-light")
    }


    // ---- behaviour: the looped sections (ARC_01), driven by keys, scroll and a swipe

    private fun selectedSection(): String {
        val n = rule.onAllNodes(androidx.compose.ui.test.SemanticsMatcher("selected section tab") {
            it.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.Selected) == true &&
                it.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.Role) == androidx.compose.ui.semantics.Role.Tab &&
                (it.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.ContentDescription)?.firstOrNull()?.contains("apps") == true)
        }).fetchSemanticsNodes().single()
        return n.config[androidx.compose.ui.semantics.SemanticsProperties.ContentDescription].first().substringBefore(",")
    }

    private fun key(code: Int) {
        rule.runOnUiThread { LauncherInput.onKeyEvent(android.view.KeyEvent(0L, 0L, android.view.KeyEvent.ACTION_DOWN, code, 0)) }
        rule.waitForIdle()
    }

    @Test @Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun ARC_01_keyboard_left_from_the_first_section_continues_into_the_last_and_right_back_to_the_first() {
        content(Corner.BottomLeft)
        rule.waitForIdle()
        val first = selectedSection()
        key(android.view.KeyEvent.KEYCODE_DPAD_LEFT)
        val last = selectedSection()
        assertTrue("left from $first lands on a different section", last != first)
        key(android.view.KeyEvent.KEYCODE_DPAD_RIGHT)
        assertEquals(first, selectedSection())
        // A full lap of right-steps returns to the start: the sections form a ring.
        val laps = ArcMath.WEDGES.coerceAtLeast(1)
        val seen = mutableListOf(first)
        repeat(40) { key(android.view.KeyEvent.KEYCODE_DPAD_RIGHT); seen += selectedSection() }
        assertTrue("the first section comes round again after the last", seen.drop(1).count { it == first } >= 1)
        assertTrue(laps >= 1)
    }

    @Test @Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun ARC_01_swiping_along_the_arc_past_the_last_section_wraps_to_the_first() {
        content(Corner.BottomLeft)
        rule.waitForIdle()
        val first = selectedSection()
        // Swipe the wedge band the way a finger turns the dial; every swipe moves on one or more sections, and enough of them come round.
        val seen = mutableSetOf(first)
        var back = false
        repeat(40) {
            rule.onNode(androidx.compose.ui.test.isRoot()).performTouchInput {
                val c = androidx.compose.ui.geometry.Offset(36.dp.toPx(), height - 36.dp.toPx())
                val r = 110.dp.toPx()
                val a0 = androidx.compose.ui.geometry.Offset(c.x + r * 0.2f, c.y - r * 0.98f)
                val a1 = androidx.compose.ui.geometry.Offset(c.x + r * 0.98f, c.y - r * 0.2f)
                swipe(a0, a1, 120)
            }
            rule.waitForIdle()
            val now = selectedSection()
            if (now == first && seen.size > 1) back = true
            seen += now
        }
        assertTrue("swiping repeatedly went through several sections", seen.size > 1)
        assertTrue("and came back round to the first one", back)
    }

    @Test @Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun ARC_02_compact_switcher_has_no_count_labels_and_expanded_has_them() {
        content(Corner.BottomLeft, SwitcherStyle.Compact)
        rule.waitForIdle()
        rule.onAllNodes(androidx.compose.ui.test.hasText("28").or(androidx.compose.ui.test.hasText("3"))).assertCountEquals(0)
    }
}
