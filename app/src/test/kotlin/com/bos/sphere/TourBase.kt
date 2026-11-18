package com.bos.sphere

import android.view.KeyEvent
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.click
import androidx.compose.ui.test.down
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.moveBy
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.up
import androidx.test.core.app.ActivityScenario
import com.bos.sphere.core.data.MediaNow
import com.bos.sphere.feature.grid.LauncherInput
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.robolectric.RuntimeEnvironment
import org.robolectric.shadows.ShadowLooper

/**
 * The shared base of the whole-app TOUR suites (wave 22, stream tourSmoke). One real [MainActivity] under Robolectric, driven with
 * touch, keys, Back and rotations, and after every step the same questions: did anything throw, which surface is shown, is the
 * activity alive, is the Compose clock idle. Not a device run (see docs/decisions/tourSmoke.md for what this cannot cover).
 */
abstract class TourBase {
    @get:Rule val rule = createEmptyComposeRule()
    protected var scenario: ActivityScenario<MainActivity>? = null

    @Before fun tourFreshProcess() { AppSmokeHarness.resetProcess() }
    @After fun tourCloseApp() {
        if (scenario != null) { rule.waitForIdle(); assertFalse("a test left a global Compose snapshot write pending", Snapshot.current.hasPendingChanges()) }
        scenario?.close(); scenario = null
        MediaNow.publishForTest(null)
        com.bos.sphere.core.data.NotificationProvider.publishForTest(emptyList())
        com.bos.sphere.core.data.PendingIntentLauncher.sender = realSender
        AppSmokeHarness.resetProcess()
    }
    private val realSender = com.bos.sphere.core.data.PendingIntentLauncher.sender

    protected fun launch(): ActivityScenario<MainActivity> = AppSmokeHarness.launch().also { scenario = it; rule.waitForIdle(); settle() }
    protected val sc: ActivityScenario<MainActivity> get() = scenario!!

    protected fun settle(ms: Long = 1500) {
        ShadowLooper.idleMainLooper(); Snapshot.sendApplyNotifications(); rule.mainClock.advanceTimeBy(ms); ShadowLooper.idleMainLooper(); rule.mainClock.advanceTimeBy(ms)
    }

    // ---- surfaces
    protected fun tagShown(t: String) = rule.onAllNodesWithTag(t, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    protected fun descShown(d: String, substring: Boolean = false) = rule.onAllNodesWithContentDescription(d, substring = substring, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    protected fun textShown(t: String) = rule.onAllNodesWithText(t, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    protected fun launchpadShown() = tagShown("lp.card") && tagShown("lp.root")
    protected fun recentsShown() = tagShown("recents.panel")
    protected fun radarShown() = descShown("Speak to search")
    protected fun drawerShown() = LauncherInput.focus.nodes().any { it.id.startsWith("drawer.") }
    protected fun settingsShown() = descShown("Close settings")
    protected fun quickSheetShown() = tagShown("quick.glance")
    /** Wave 26: Glance is the surface on screen (its deck dots are drawn only there); otherwise the pages are. */
    protected fun glanceShown() = tagShown("glance.deckdots")
    protected fun editShown() = descShown("Page carousel")
    protected fun controlsShown() = descShown("Next")

    enum class Surface { Home, Launchpad, Recents, Radar, Drawer, Settings, QuickSheet, Edit }

    /** Every surface that is open right now (a healthy state has at most one of the full-screen ones). */
    protected fun open(): Set<Surface> = buildSet {
        if (launchpadShown()) add(Surface.Launchpad)
        if (recentsShown()) add(Surface.Recents)
        if (radarShown()) add(Surface.Radar)
        if (drawerShown()) add(Surface.Drawer)
        if (settingsShown()) add(Surface.Settings)
        if (quickSheetShown()) add(Surface.QuickSheet)
        if (editShown()) add(Surface.Edit)
        if (isEmpty()) add(Surface.Home)
    }

    /** The invariants after every step: alive, one surface at most (the quick sheet may sit over the home), clock idle, a focus owner exists. */
    protected fun check(step: String, expect: Surface? = null) {
        var state: androidx.lifecycle.Lifecycle.State? = null
        sc.onActivity { state = it.lifecycle.currentState }
        assertTrue("$step: the activity is alive ($state)", state != null && state!!.isAtLeast(androidx.lifecycle.Lifecycle.State.CREATED))
        val o = open()
        val fullScreen = o - Surface.QuickSheet - Surface.Edit
        assertTrue("$step: more than one surface open: $o", fullScreen.size <= 1)
        if (expect != null) assertTrue("$step: expected $expect, shown $o", expect in o)
        Snapshot.sendApplyNotifications(); rule.waitForIdle()
        assertFalse("$step: a global snapshot write is pending", Snapshot.current.hasPendingChanges())
        assertTrue("$step: a focus graph exists", LauncherInput.focus.nodes().isNotEmpty())
    }

    // ---- input
    protected fun key(code: Int, ctrl: Boolean = false, shift: Boolean = false): Boolean { val h = AppSmokeHarness.press(sc, code, ctrl = ctrl, shift = shift); settle(400); return h }
    /** The Home button (a new MAIN/HOME intent): home returns to what it starts on, Glance when it is on. */
    protected fun pressHome() { sc.onActivity { androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().callActivityOnNewIntent(it, android.content.Intent(android.content.Intent.ACTION_MAIN).addCategory(android.content.Intent.CATEGORY_HOME)) }; settle() }
    protected fun back() { AppSmokeHarness.back(sc); settle() }
    protected fun swipeFromLeft(y: Float = 0.5f) { rule.onRoot().performTouchInput { down(Offset(6f, height * y)); moveBy(Offset(60f, 0f)); moveBy(Offset(200f, 0f)); moveBy(Offset(300f, 0f)); up() }; settle() }
    protected fun swipeFromRight(y: Float = 0.5f) { rule.onRoot().performTouchInput { down(Offset(width - 6f, height * y)); moveBy(Offset(-60f, 0f)); moveBy(Offset(-200f, 0f)); moveBy(Offset(-300f, 0f)); up() }; settle() }
    protected fun tapAt(fx: Float, fy: Float) { rule.onRoot().performTouchInput { click(Offset(width * fx, height * fy)) }; settle(300) }
    protected fun longPressAt(fx: Float, fy: Float) { rule.onRoot().performTouchInput { longClick(Offset(width * fx, height * fy)) }; settle(300) }

    /** A configuration change at the current step: land, then back to portrait; the same surface must be there after each. */
    protected fun rotateAndCheck(step: String) {
        val before = open()
        var a: MainActivity? = null; sc.onActivity { a = it }
        RuntimeEnvironment.setQualifiers("+w720dp-h360dp-land-xhdpi"); rule.waitForIdle(); settle(400)
        var b: MainActivity? = null; sc.onActivity { b = it }
        assertTrue("$step: the activity handles the rotation itself", a === b)
        assertEquals("$step: the surface survives landscape", before, open())
        RuntimeEnvironment.setQualifiers("+w360dp-h720dp-port-xhdpi"); rule.waitForIdle(); settle(400)
        assertEquals("$step: the surface survives the return to portrait", before, open())
        check(step)
    }

    /** Closes a modal bottom sheet (a dialog window: the Activity's Back dispatcher is not what it listens to) by tapping its scrim, as a finger would. */
    protected fun closeSheet() {
        rule.onAllNodes(androidx.compose.ui.test.isRoot()).onLast().performTouchInput { click(Offset(width * 0.5f, 12f)) }
        settle()
    }

    protected fun waitFor(what: String, ms: Int = 6000, cond: () -> Boolean) {
        var left = ms
        while (!cond() && left > 0) { Thread.sleep(40); settle(250); left -= 250 } // real time passes too: a restore runs on an IO thread
        assertTrue("never happened: $what", cond())
    }

    companion object { const val ESC = KeyEvent.KEYCODE_ESCAPE }
}
