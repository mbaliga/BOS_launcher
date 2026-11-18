package com.bos.sphere.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.foundation.background
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.bos.sphere.core.data.GroupMode
import com.bos.sphere.core.data.HubNotification
import com.bos.sphere.core.data.LaunchpadPrefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Wave 26 (launchpadPolish): the list runs edge to edge up to the home card, and a one-item group has no header of its own.
 * Screenshots: build/screenshots/launchpad-w26-*.png.
 */
@OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
abstract class LaunchpadEdgeW26Cases(private val orientation: String) : LaunchpadShots() {
    @Composable
    private fun Stage(items: List<HubNotification>, mode: GroupMode, rtl: Boolean) {
        CompositionLocalProvider(LocalLp provides LpDark, LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val density = androidx.compose.ui.platform.LocalDensity.current.density
                val geo = LaunchpadCardGeometry(maxWidth.value * density, maxHeight.value * density, CardEdge.forLaunchpad(rtl), density)
                Box(Modifier.fillMaxSize().launchpadBackdrop(LocalLp.current.bg, LocalLp.current.surface)) {
                    LaunchpadContent(
                        items = items, rules = emptyList(), prefs = LaunchpadPrefs(mode), readKeys = setOf("c1"), hasAccess = true, now = now,
                        onClose = {}, onOpenItem = {}, onOpenApp = {}, onDismissItem = {}, onClearAll = {}, onAddRule = {},
                        onSetDefaultMode = {}, onRequestAccess = {}, systemState = system, zone = zone,
                        endInset = (geo.sliverPx / density).dp,
                    )
                }
                Box(Modifier.fillMaxSize().homeCardLayer(geo) { 1f }.background(Color(0xFF111111)), contentAlignment = androidx.compose.ui.Alignment.Center) { Text("Home", color = Color.White) }
            }
        }
    }

    private fun render(mode: GroupMode, rtl: Boolean = false, items: List<HubNotification> = grouped, shot: String? = null) {
        rule.setContent { Stage(items, mode, rtl) }
        rule.waitForIdle()
        if (shot != null) shoot("launchpad-w26-$orientation-$shot")
    }

    private fun winW() = rule.activity.resources.displayMetrics.let { it.widthPixels / it.density }
    private fun tags(prefix: String): List<String> = rule.onAllNodes(SemanticsMatcher("tag $prefix") { it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith(prefix) == true })
        .fetchSemanticsNodes().map { it.config[SemanticsProperties.TestTag] }

    private fun assertEdgeToEdge(rtl: Boolean) {
        val w = winW()
        val geo = LaunchpadCardGeometry(w, 800f, CardEdge.forLaunchpad(rtl), 1f)
        val cardEdge = if (rtl) geo.sliverPx else w - geo.sliverPx // the card's inner edge
        val row = rule.onNodeWithTag("lp.row.m2").getUnclippedBoundsInRoot()
        val head = rule.onNodeWithTag("lp.group.${tags("lp.group.").first().removePrefix("lp.group.")}").getUnclippedBoundsInRoot()
        val time = rule.onNodeWithTag("lp.time.m2", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val sys = rule.onNodeWithTag("lp.sys.wifi").getUnclippedBoundsInRoot()
        if (!rtl) {
            assertEquals("row right edge is the card's left edge", cardEdge, row.right.value, 1f)
            assertEquals("section band right edge", cardEdge, head.right.value, 1f)
            assertEquals("time is 20 dp inside the row", row.right.value - 20f, time.right.value, 1.5f)
            assertTrue("switcher row stays inside the content", sys.right.value <= cardEdge + 0.5f)
        } else {
            assertEquals("row left edge is the card's right edge", cardEdge, row.left.value, 1f)
            assertEquals("section band left edge", cardEdge, head.left.value, 1f)
            assertTrue("time inside the row (${time.left.value} .. ${time.right.value}, row ${row.left.value}..${row.right.value})", time.right.value <= row.right.value - 19.5f && time.left.value >= row.left.value + 20f)
            assertTrue("switcher row stays inside the content", sys.left.value >= cardEdge - 0.5f)
        }
    }

    @Test fun STR_01_w26_rowsBandsAndDividersReachTheCardEdgeAndTheTimeIsNotClipped() {
        render(GroupMode.Sender, shot = "edge"); assertEdgeToEdge(rtl = false)
    }

    @Test fun STR_01_w26_rtlMirrorsTheEdgeToEdgeList() {
        render(GroupMode.Sender, rtl = true, shot = "edge-rtl"); assertEdgeToEdge(rtl = true)
    }

    @Test fun STR_02_w26_aSingleItemGroupHasNoHeaderAndAGroupOfTwoHasOneWithTheCount() {
        render(GroupMode.Sender, shot = "sender")
        val heads = tags("lp.group.")
        assertTrue("Mum has two items: $heads", heads.any { it.endsWith(":mum") })
        assertTrue("no header for the one-item groups: $heads", heads.none { it.endsWith(":ana") || it.contains("demo.mail") || it.contains("demo.cal") })
        assertTrue("Mum's header carries the count 2", rule.onAllNodes(androidx.compose.ui.test.hasText("2")).fetchSemanticsNodes().isNotEmpty())
        // The rows themselves are all there.
        assertTrue("rows stay", tags("lp.row.").contains("lp.row.m2"))
    }

    @Test fun STR_02_w26_flatHasNoGroupHeadersAtAll() {
        render(GroupMode.Flat, shot = "flat")
        assertEquals(emptyList<String>(), tags("lp.group."))
    }

    @Test fun STR_02_w26_timeKeepsItsDateBandEvenForOneItem() {
        render(GroupMode.Time, items = listOf(grouped[0]))
        assertEquals(listOf("lp.group.day:2024-03-10"), tags("lp.group."))
    }

    @Test fun STR_02_w26_appModeAndConversationModeFollowTheSameRule() {
        render(GroupMode.App)
        val heads = tags("lp.group.")
        assertTrue("Chat has two or more: $heads", heads.contains("lp.group.app:demo.chat"))
        assertTrue("Calendar and Mail have one each: $heads", heads.none { it.contains("demo.cal") || it.contains("demo.mail") })
    }
}
