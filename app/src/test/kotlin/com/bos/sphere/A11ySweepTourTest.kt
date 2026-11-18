package com.bos.sphere

import android.view.KeyEvent
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import com.bos.sphere.core.data.LauncherStores
import com.bos.sphere.core.data.MediaInfo
import com.bos.sphere.core.data.MediaNow
import org.junit.Assert.assertFalse
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
    private fun sweep(surface: String, headings: Boolean = false, pane: String? = null) {
        findings += A11yAudit.audit("$surface/portrait", roots(), density(), A11yExceptions.all)
        small += A11yAudit.smallLayouts("$surface/portrait", roots(), density())
        if (headings) assertTrue("$surface: no heading is marked", A11yAudit.hasHeading(roots()))
        if (pane != null) assertTrue("$surface: the panel announces itself with the pane title \"$pane\"", A11yAudit.hasPaneTitle(roots(), pane))
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
        swipeFromLeft(); check("launchpad", Surface.Launchpad); sweep("launchpad", pane = "Launchpad"); back()
        key(KeyEvent.KEYCODE_R, ctrl = true); check("recents", Surface.Recents); sweep("recents", headings = true, pane = "Recent apps"); back()
        key(KeyEvent.KEYCODE_D, ctrl = true); check("drawer", Surface.Drawer); sweep("drawer"); back()
        key(KeyEvent.KEYCODE_B); check("radar", Surface.Radar); sweep("radar"); key(KeyEvent.KEYCODE_R); key(KeyEvent.KEYCODE_A); sweep("radar typed"); back()
        key(KeyEvent.KEYCODE_COMMA, ctrl = true); check("ground control", Surface.Settings); sweep("ground control", headings = true)
        java.io.File(System.getProperty("user.dir"), "build/a11y-small-layouts.txt").apply { parentFile.mkdirs() }.writeText(small.joinToString("\n"))
        assertTrue("the sweep found:\n" + findings.joinToString("\n"), findings.isEmpty())
    }

    /**
     * Wave 26 (audit of wave 25): the surfaces wave 25 added: the Launchpad side panel, the hub's switcher, options and history rows on a non-default
     * view (with a filter set and a row expanded), and the hub's Files view. Same rules as the whole-app sweep: label, role, 48 dp, state.
     */
    @Test fun NFR_13_wave25SurfacesSweepThePanelMenuAndTheHubViews() {
        val dir = java.nio.file.Files.createTempDirectory("a11yhub").toFile()
        val file = java.io.File(dir, "usage/h.log").apply { parentFile.mkdirs() }
        val t = System.currentTimeMillis()
        file.writeText(listOf(
            com.bos.sphere.core.data.HistoryEntry("k1|1", "k1", "demo.chat", "Chat", "Mum", "Mum", "Are you coming on Sunday?", "", t - 60_000, read = false),
            com.bos.sphere.core.data.HistoryEntry("k2|2", "k2", "demo.mail", "Mail", "", "Invoice", "Payment received", "", t - 3_600_000, read = false),
            com.bos.sphere.core.data.HistoryEntry("k3|3", "k3", "demo.chat", "Chat", "Team", "Team", "Build is green", "", t - 7_200_000, read = true),
        ).joinToString("\n") { com.bos.sphere.core.data.NotificationHistoryCodec.encode(it) } + "\n")
        com.bos.sphere.core.data.NotificationHistory.setForTest(com.bos.sphere.core.data.NotificationHistoryStore(file, java.util.concurrent.Executor { it.run() }))
        try {
            start()
            swipeFromLeft(); check("launchpad", Surface.Launchpad)
            rule.onNodeWithContentDescription("Notification menu", useUnmergedTree = true).performClick(); settle()
            assertTrue("the side panel is open", tagShown("lp.menu.panel")); sweep("launchpad menu panel")
            rule.onNodeWithTag("lp.menu.close", useUnmergedTree = true).performClick(); settle(); assertFalse(tagShown("lp.menu.panel"))
            rule.onNodeWithTag("hub.view.unread", useUnmergedTree = true).performClick(); settle()
            assertTrue("the Unread history shows", tagShown("hub.body")); sweep("hub unread")
            rule.onNodeWithTag("hub.f.today", useUnmergedTree = true).performClick(); settle()
            rule.onAllNodes(androidx.compose.ui.test.hasTestTag("hub.list"), useUnmergedTree = true).fetchSemanticsNodes()
            sweep("hub unread, Today filter set")
            rule.onNodeWithTag("hub.options", useUnmergedTree = true).performClick(); settle()
            assertTrue("the options panel shows", tagShown("hub.optionsPanel")); sweep("hub options")
            rule.onNodeWithTag("hub.deleteHistory", useUnmergedTree = true).performClick(); settle()
            assertTrue("the delete confirmation shows", tagShown("hub.deleteConfirm")); sweep("hub delete confirmation")
            rule.onNodeWithTag("hub.view.files", useUnmergedTree = true).performClick(); settle()
            sweep("hub files")
            rule.onNodeWithTag("hub.view.usage", useUnmergedTree = true).performClick(); settle(); sweep("hub usage")
            rule.onNodeWithTag("hub.view.timeline", useUnmergedTree = true).performClick(); settle(); sweep("hub timeline")
            java.io.File(System.getProperty("user.dir"), "build/a11y-small-layouts-w25.txt").apply { parentFile.mkdirs() }.writeText(small.joinToString("\n"))
            assertTrue("hub controls narrower than 48 dp (their hit areas would share space with a neighbour):\n" + small.filter { "tag=hub." in it }.joinToString("\n"), small.none { "tag=hub." in it })
            assertTrue("the wave 25 sweep found:\n" + findings.joinToString("\n"), findings.isEmpty())
        } finally { com.bos.sphere.core.data.NotificationHistory.setForTest(null) }
    }
}
