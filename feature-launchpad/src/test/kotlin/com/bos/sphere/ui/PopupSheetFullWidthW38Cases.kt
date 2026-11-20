package com.bos.sphere.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.bos.sphere.core.data.GroupMode
import com.bos.sphere.core.data.HubNotification
import com.bos.sphere.core.data.LaunchpadPrefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Wave 38 (popupsHidden): the Comms popups span the whole window. The stage is what the app builds in Comms-open state: the panel under a
 * shrunken home card pushed to the far edge (drawn after the panel), with the full-window sheet layer on top. Screenshots go to
 * build/screenshots/popups-w38-*.png (compare with connectivity-ref.png and the Sound mock-up).
 */
@OptIn(ExperimentalTestApi::class)
abstract class PopupSheetFullWidthW38Cases(private val orientation: String) : LaunchpadShots() {
    private val sound = SoundSheetState()
    private val conn = ConnectivitySheetState()
    private val host = PopupSheetHost()

    private fun items() = (0 until 6).map { i ->
        HubNotification(id = "k$i", key = "k$i", source = "x", title = "Person $i", summary = "Message number $i", timestamp = at(11, 50) - i * 47L * 60_000L,
            packageName = "demo.chat", appLabel = "Chat", sender = "Person $i", conversation = "Person $i", isMessaging = true)
    }

    private fun stage(rtl: Boolean, hosted: Boolean) {
        rule.setContent {
            CompositionLocalProvider(LocalLp provides LpDark, LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    val density = androidx.compose.ui.platform.LocalDensity.current.density
                    val geo = LaunchpadCardGeometry(maxWidth.value * density, maxHeight.value * density, CardEdge.forLaunchpad(rtl), density)
                    val menu = rememberLaunchpadMenu()
                    Box(Modifier.fillMaxSize().background(LocalLp.current.bg)) {
                        LaunchpadContent(
                            items = items(), rules = emptyList(), prefs = LaunchpadPrefs(GroupMode.Flat), readKeys = emptySet(), hasAccess = true, now = at(23, 2),
                            onClose = {}, onOpenItem = {}, onOpenApp = {}, onDismissItem = {}, onClearAll = {}, onAddRule = {},
                            onSetDefaultMode = {}, onRequestAccess = {}, zone = zone, menu = menu, systemState = system.copy(now = at(23, 2), batteryPercent = 60),
                            soundSheet = sound, connectivitySheet = conn, sheetHost = if (hosted) host else null,
                            endInset = (geo.sliverPx / density).dp,
                        )
                    }
                    // The shrunken home card, drawn after the panel like the app does.
                    Box(Modifier.fillMaxSize().homeCardLayer(geo) { 1f }.background(Color(0xFF000000)))
                    if (hosted) host.Layer()
                }
            }
        }
        rule.waitForIdle()
    }

    private fun open(which: String) {
        rule.runOnUiThread { if (which == "snd") sound.show() else conn.show() }
        rule.mainClock.advanceTimeBy(700); rule.waitForIdle()
    }
    private fun b(tag: String) = rule.onNodeWithTag(tag, useUnmergedTree = true).getUnclippedBoundsInRoot()
    private fun root() = rule.onRoot().getUnclippedBoundsInRoot()

    @Test fun STR_02_w38_soundSheetSpansTheWholeWindowOverTheHomeCard() {
        stage(rtl = false, hosted = true); open("snd")
        val r = root(); val sheet = b("snd.sheet"); val scrim = b("snd.scrim")
        assertEquals("sheet starts at the window's left edge", 0f, sheet.left.value, 0.5f)
        assertEquals("sheet ends at the window's right edge", r.right.value, sheet.right.value, 0.5f)
        assertEquals("scrim covers the whole width", r.right.value, scrim.right.value - scrim.left.value, 0.5f)
        for (k in listOf("media", "notifications", "alarms", "vibration")) {
            val s = b("snd.slider.$k")
            assertEquals("$k slider: 32 dp left margin", 32f, s.left.value, 0.5f)
            assertEquals("$k slider: the same 32 dp right margin", 32f, r.right.value - s.right.value, 0.5f)
        }
        shoot("popups-w38-$orientation-sound")
    }

    @Test fun STR_02_w38_connectivitySheetSpansTheWholeWindowOverTheHomeCard() {
        stage(rtl = false, hosted = true); open("conn")
        val r = root(); val sheet = b("cn.sheet")
        assertEquals(0f, sheet.left.value, 0.5f)
        assertEquals(r.right.value, sheet.right.value, 0.5f)
        rule.onNodeWithTag("cn.done", useUnmergedTree = true).performScrollTo()
        val done = b("cn.done"); val sw = b("cn.wifi")
        assertTrue("Done ends inside the window with the margin: ${done.right.value} of ${r.right.value}", r.right.value - done.right.value in 31.5f..32.5f)
        assertTrue("the first switch row also ends 32 dp from the right edge", r.right.value - sw.right.value in 31.5f..32.5f)
        assertEquals("equal left and right margins on the rows", sw.left.value, r.right.value - sw.right.value, 0.5f)
        val tune = rule.onAllNodes(androidx.compose.ui.test.hasTestTag("cn.tune"), useUnmergedTree = true).fetchSemanticsNodes()
        if (tune.isNotEmpty()) assertEquals("the tune button ends at the margin too", 32f, r.right.value - b("cn.tune").right.value, 0.5f)
        shoot("popups-w38-$orientation-connectivity")
    }

    @Test fun STR_02_w38_withoutTheHostTheSheetIsOnlyAsWideAsThePanel() {
        stage(rtl = false, hosted = false); open("conn")
        assertTrue("the in-panel fallback keeps the old narrower sheet (this is the bug the host fixes)", b("cn.sheet").right.value < root().right.value - 1f)
    }

    @Test fun STR_02_w38_rtlAndBackStillWork() {
        stage(rtl = true, hosted = true); open("snd")
        val r = root(); val sheet = b("snd.sheet")
        assertEquals(0f, sheet.left.value, 0.5f); assertEquals(r.right.value, sheet.right.value, 0.5f)
        shoot("popups-w38-$orientation-sound-rtl")
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }; rule.waitForIdle()
        assertEquals("Back closes the sheet and clears the host layer", false, sound.open)
        assertEquals(0, rule.onAllNodesWithTagCount("snd.sheet"))
    }

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onAllNodesWithTagCount(t: String) =
        onAllNodes(androidx.compose.ui.test.hasTestTag(t), useUnmergedTree = true).fetchSemanticsNodes().size
}
