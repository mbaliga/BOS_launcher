package com.bos.sphere

import android.view.KeyEvent
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.bos.sphere.core.data.HomeLanding
import com.bos.sphere.core.data.LauncherStores
import com.bos.sphere.core.data.MediaInfo
import com.bos.sphere.core.data.MediaNow
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * NFR-13 / NFR-14 (wave 23): the whole-app TalkBack and semantics sweep. The real MainActivity is walked to Glance (controls shown), Launchpad, the
 * Recents panel, the drawer, Radar and Ground Control's top level, in portrait and landscape, and [A11yAudit] reads the merged semantics tree of
 * each: label, role, 48 dp, state, headings, pane titles. Robolectric only: how TalkBack speaks it and the real traversal order are device checks.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h720dp-xhdpi")
class A11ySweepTourTest : TourBase() {
    private val stores get() = LauncherStores.get(AppSmokeHarness.app)
    private val track = MediaInfo("Someday Out of The Blue", "Artist", "pkg.music", playing = true, canPrev = true, canNext = true)
    private val findings = ArrayList<A11yAudit.Finding>()
    private val small = ArrayList<String>()

    private fun start() {
        AppSmokeHarness.seedApps("Alpha", "Bravo", "Camera", "Delta")
        AppSmokeHarness.configureFresh { it }
        launch()
    }

    private fun roots() = rule.onAllNodes(isRoot()).fetchSemanticsNodes()
    private fun density() = AppSmokeHarness.app.resources.displayMetrics.density

    /** Audits the open surface in portrait and in landscape; returns the roots of the portrait pass for the heading and pane-title checks. */
    private fun sweep(surface: String, headings: Boolean = false, pane: Boolean = false) {
        findings += A11yAudit.audit("$surface/portrait", roots(), density(), A11yExceptions.all)
        small += A11yAudit.smallLayouts("$surface/portrait", roots(), density())
        if (headings) assertTrue("$surface: no heading is marked", A11yAudit.hasHeading(roots()))
        if (pane) assertTrue("$surface: the panel announces itself with a pane title", A11yAudit.hasPaneTitle(roots()))
        RuntimeEnvironment.setQualifiers("+w720dp-h360dp-land-xhdpi"); rule.waitForIdle(); settle(400)
        findings += A11yAudit.audit("$surface/landscape", roots(), density(), A11yExceptions.all)
        small += A11yAudit.smallLayouts("$surface/landscape", roots(), density())
        RuntimeEnvironment.setQualifiers("+w360dp-h720dp-port-xhdpi"); rule.waitForIdle(); settle(400)
    }

    @Test fun NFR_13_theWholeAppSweepEveryClickableHasALabelARoleAnd48dpAndThePanelsAnnounceThemselves() {
        start()
        MediaNow.publishForTest(track); settle()
        tapAt(0.5f, 0.72f); assertTrue(controlsShown()); sweep("glance")
        waitFor("controls out", 12_000) { !controlsShown() }
        swipeFromLeft(); check("launchpad", Surface.Launchpad); sweep("launchpad", pane = true); back()
        key(KeyEvent.KEYCODE_R, ctrl = true); check("recents", Surface.Recents); sweep("recents", headings = true, pane = true); back()
        key(KeyEvent.KEYCODE_D, ctrl = true); check("drawer", Surface.Drawer); sweep("drawer"); back()
        key(KeyEvent.KEYCODE_B); check("radar", Surface.Radar); sweep("radar"); key(KeyEvent.KEYCODE_R); key(KeyEvent.KEYCODE_A); sweep("radar typed"); back()
        key(KeyEvent.KEYCODE_COMMA, ctrl = true); check("ground control", Surface.Settings); sweep("ground control", headings = true)
        java.io.File(System.getProperty("user.dir"), "build/a11y-small-layouts.txt").apply { parentFile.mkdirs() }.writeText(small.joinToString("\n"))
        assertTrue("the sweep found:\n" + findings.joinToString("\n"), findings.isEmpty())
    }
}
