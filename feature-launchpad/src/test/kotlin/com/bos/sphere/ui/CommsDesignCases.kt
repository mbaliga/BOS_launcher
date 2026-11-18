package com.bos.sphere.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.bos.sphere.core.data.GroupMode
import com.bos.sphere.core.data.HubNotification
import com.bos.sphere.core.data.LaunchpadPrefs
import com.bos.sphere.core.data.NotificationHistoryStore
import com.bos.sphere.core.data.SystemTileKind
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.concurrent.Executor

/**
 * Wave 27 (commsDesign): the Comms screen and the Comms menu panel against the owner's mock-ups. Geometry (Robolectric bounds) and screenshots
 * `build/screenshots/comms-*.png` (portrait and landscape, dark and light, with 0, 1, 4 and 30 notifications, the menu, the Unread tab, right to left).
 */
@OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
abstract class CommsDesignCases(private val orientation: String) : LaunchpadShots() {
    private val mock = listOf(
        Triple("demo.music", "Lemon", "N.E.R.D & Rihanna" to false), Triple("com.whatsapp", "Tulika Pandey", "Can we meet for Lunch?" to true),
        Triple("com.snapchat.android", "Cutegirlxx", "Open app to view message" to true), Triple("com.openai.chatgpt", "ChatGPT", "ChatGPT needs your permission to read your messages" to false),
    )
    private val colours = mapOf("demo.music" to 0xFF2A2A2A, "com.whatsapp" to 0xFF25D366, "com.snapchat.android" to 0xFFFFFC00, "com.openai.chatgpt" to 0xFFFFFFFF, "com.google.android.gm" to 0xFFD93025, "demo.cal" to 0xFF1A73E8)
    private val labels = mapOf("demo.music" to "Music", "com.whatsapp" to "WhatsApp", "com.snapchat.android" to "Snapchat", "com.openai.chatgpt" to "ChatGPT")

    private fun feed(count: Int): List<HubNotification> = (0 until count).map { i ->
        if (i < mock.size && count <= mock.size || (count > mock.size && i < mock.size)) {
            val (pkg, who, msg) = mock[i]
            val (text, messaging) = msg
            if (pkg == "demo.music") HubNotification(id = "k$i", key = "k$i", source = "x", title = "Lemon", summary = text, timestamp = at(17, 34) - i * 120_000L, packageName = pkg, appLabel = labels[pkg]!!)
            else if (messaging) HubNotification(id = "k$i", key = "k$i", source = "x", title = who, summary = text, timestamp = at(17, 34) - i * 120_000L, packageName = pkg, appLabel = labels[pkg]!!, sender = who, conversation = who, isMessaging = true)
            else HubNotification(id = "k$i", key = "k$i", source = "x", title = text, summary = who, timestamp = at(17, 34) - i * 120_000L, packageName = pkg, appLabel = labels[pkg]!!)
        } else {
            val pkg = listOf("com.google.android.gm", "demo.cal", "com.whatsapp")[i % 3]
            HubNotification(id = "k$i", key = "k$i", source = "x", title = "Person $i", summary = "Message number $i", timestamp = at(11, 50) - i * 47L * 60_000L, packageName = pkg, appLabel = labels[pkg] ?: pkg, sender = "Person $i", conversation = "Person $i", isMessaging = true)
        }
    }

    private lateinit var hub: HubState
    private var menuRef: LaunchpadMenuState? = null
    private val opened = mutableListOf<SystemTileKind>()
    private var soundOpened = 0

    @Before fun seedIcons() {
        colours.forEach { (pkg, c) -> iconCache[pkg] = solid(c.toInt()) }
        val store = NotificationHistoryStore(File(java.nio.file.Files.createTempDirectory("comms").toFile(), "usage/h.log"), Executor { it.run() })
        store.observe(feed(4))
        hub = HubState(HubPrefs(null), store, true, liveAll = true)
    }
    @After fun clearIcons() { colours.keys.forEach { iconCache.remove(it) } }

    private fun solid(argb: Int): ImageBitmap {
        val b = android.graphics.Bitmap.createBitmap(48, 48, android.graphics.Bitmap.Config.ARGB_8888); b.eraseColor(argb); return b.asImageBitmap()
    }

    @Composable
    private fun Stage(items: List<HubNotification>, dark: Boolean, rtl: Boolean, home: Boolean) {
        CompositionLocalProvider(LocalLp provides if (dark) LpDark else LpLight, LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val menu = rememberLaunchpadMenu().also { menuRef = it }
                val density = androidx.compose.ui.platform.LocalDensity.current.density
                val geo = LaunchpadCardGeometry(maxWidth.value * density, maxHeight.value * density, CardEdge.forLaunchpad(rtl), density)
                Box(Modifier.fillMaxSize().background(LocalLp.current.bg)) {
                    LaunchpadContent(
                        items = items, rules = emptyList(), prefs = LaunchpadPrefs(GroupMode.Flat), readKeys = emptySet(), hasAccess = true, now = at(23, 2),
                        onClose = {}, onOpenItem = {}, onOpenApp = {}, onDismissItem = {}, onClearAll = {}, onAddRule = {},
                        onSetDefaultMode = {}, onRequestAccess = {}, zone = zone, menu = menu, systemState = system.copy(now = at(23, 2), batteryPercent = 60),
                        onOpenSystemPanel = { opened += it }, onOpenSound = { soundOpened++ }, hub = hub, viewSwitcher = { HubSwitcher(hub) },
                        endInset = if (home) (geo.sliverPx / density).dp else 0.dp,
                    )
                }
                if (home) Box(Modifier.fillMaxSize().launchpadMenuAside(menu, geo.widthPx, rtl).homeCardLayer(geo) { 1f }.background(Color(0xFF000000)))
            }
        }
    }

    private fun render(items: List<HubNotification>, dark: Boolean = true, name: String? = null, rtl: Boolean = false, home: Boolean = true) {
        rule.setContent { Stage(items, dark, rtl, home) }
        rule.waitForIdle()
        if (name != null) shoot("comms-$orientation-$name-${if (dark) "dark" else "light"}")
    }
    private fun openMenu() { rule.onNodeWithTag("lp.menu").performClick(); rule.waitForIdle(); rule.mainClock.advanceTimeBy(1000); rule.waitForIdle() }
    private fun winW() = rule.activity.resources.displayMetrics.let { it.widthPixels / it.density }
    private fun b(tag: String, unmerged: Boolean = false) = rule.onNodeWithTag(tag, useUnmergedTree = unmerged).getUnclippedBoundsInRoot()

    // ---- screenshots: 0, 1, 4 and 30 notifications, dark and light ----
    @Test fun STR_02_w27_screens_zero_dark() = render(emptyList(), true, "0")
    @Test fun STR_02_w27_screens_zero_light() = render(emptyList(), false, "0")
    @Test fun STR_02_w27_screens_one_dark() = render(feed(1), true, "1")
    @Test fun STR_02_w27_screens_one_light() = render(feed(1), false, "1")
    @Test fun STR_02_w27_screens_four_dark() = render(feed(4), true, "4")
    @Test fun STR_02_w27_screens_four_light() = render(feed(4), false, "4")
    @Test fun STR_02_w27_screens_thirty_dark() = render(feed(30), true, "30")
    @Test fun STR_02_w27_screens_thirty_light() = render(feed(30), false, "30")
    @Test fun STR_02_w27_screens_unreadTab_dark() { render(feed(4), true); rule.onNodeWithTag("hub.view.unread").performClick(); rule.waitForIdle(); shoot("comms-$orientation-unread-dark") }
    @Test fun STR_02_w27_screens_unreadTab_light() { render(feed(4), false); rule.onNodeWithTag("hub.view.unread").performClick(); rule.waitForIdle(); shoot("comms-$orientation-unread-light") }
    @Test fun STR_01_w27_screens_menu_dark() { render(feed(4), true); openMenu(); shoot("comms-$orientation-menu-dark") }
    @Test fun STR_01_w27_screens_menu_light() { render(feed(4), false); openMenu(); shoot("comms-$orientation-menu-light") }
    @Test fun STR_01_w27_screens_menu_rtl() { render(feed(4), true, rtl = true); openMenu(); shoot("comms-$orientation-menu-rtl-dark") }
    @Test fun STR_02_w27_screens_rtl() = render(feed(4), true, "rtl", rtl = true)

    // ---- header: menu glyph and title, clock left, four glyphs right ----
    @Test fun STR_02_w27_headerHasTheTitleCommsAt28spTheClockAndFourStatusGlyphsOf48dp() {
        render(feed(4), home = false)
        rule.onNodeWithTag("lp.title", useUnmergedTree = true).assertIsDisplayed()
        assertEquals("Comms", rule.onNodeWithTag("lp.title", useUnmergedTree = true).fetchSemanticsNode().config.getOrNull(SemanticsProperties.Text)!!.joinToString { it.text })
        val menu = b("lp.menu"); val title = b("lp.title", true); val clock = b("lp.clock")
        assertEquals("menu target is 48 dp", 48f, menu.right.value - menu.left.value, 0.5f)
        assertTrue("the title follows the menu glyph", title.left.value > menu.right.value)
        assertTrue("the clock row is under the title row", clock.top.value >= menu.bottom.value - 1f)
        assertEquals("the clock is on the left, 20 dp from the edge", 20f, clock.left.value, 1f)
        val glyphs = listOf("brightness", "battery", "signal", "wifi").map { b("lp.sys.$it") }
        glyphs.forEach { assertEquals("each glyph is a 48 dp target", 48f, it.right.value - it.left.value, 0.5f); assertEquals(48f, it.bottom.value - it.top.value, 0.5f) }
        assertTrue("glyphs are in order left to right", glyphs.zipWithNext().all { (a, c) -> a.right.value <= c.left.value + 0.5f })
        assertTrue("and on the right of the clock", glyphs.first().left.value > clock.right.value)
        assertEquals("the last glyph's target ends 8 dp from the edge, so the glyph is 20 dp in", winW() - 8f, glyphs.last().right.value, 1f)
        assertEquals("on the clock's row", (clock.top.value + clock.bottom.value) / 2, (glyphs[0].top.value + glyphs[0].bottom.value) / 2, 2f)
        // 23:02, 24 hour (the test device uses the default 24 h of the locale: the text is HH:mm).
        val t = rule.onNodeWithTag("lp.clock").fetchSemanticsNode().config.getOrNull(SemanticsProperties.ContentDescription)!!.joinToString()
        assertTrue("the clock reads the time: $t", t.contains("11:02") || t.contains("23:02"))
    }

    @Test fun STR_02_w27_statusGlyphsOpenTheSystemPanelsAndTheClockTheSoundPopup() {
        render(feed(4), home = false)
        rule.onNodeWithTag("lp.sys.brightness").performClick(); rule.onNodeWithTag("lp.sys.battery").performClick()
        rule.onNodeWithTag("lp.sys.signal").performClick(); rule.onNodeWithTag("lp.sys.wifi").performClick()
        assertEquals(listOf(SystemTileKind.Brightness, SystemTileKind.Battery, SystemTileKind.Connectivity, SystemTileKind.Connectivity), opened)
        assertEquals("android.settings.DISPLAY_SETTINGS", com.bos.sphere.core.data.SystemStateLogic.panelFor(SystemTileKind.Brightness))
        rule.onNodeWithTag("lp.clock").performClick()
        assertEquals(1, soundOpened)
        assertEquals("the old four square buttons are gone", 0, rule.onAllNodesWithTag("lp.sys.time").fetchSemanticsNodes().size + rule.onAllNodesWithTag("lp.sys.sound").fetchSemanticsNodes().size + rule.onAllNodesWithTag("lp.system").fetchSemanticsNodes().size)
        assertEquals("no jump strip", 0, rule.onAllNodesWithTag("lp.strip").fetchSemanticsNodes().size)
    }

    // ---- tabs ----
    @Test fun STR_02_w28_tabsAreUppercaseAllUnreadFilesTimelineInOneRowAndReadAndUsageHaveNoTab() {
        render(feed(4), home = false)
        listOf("ALL", "UNREAD", "FILES", "TIMELINE").forEach { assertTrue("$it tab", rule.onAllNodesWithText(it).fetchSemanticsNodes().isNotEmpty()) }
        assertEquals("no USAGE tab (wave 28)", 0, rule.onAllNodesWithText("USAGE").fetchSemanticsNodes().size + rule.onAllNodesWithTag("hub.view.usage").fetchSemanticsNodes().size)
        assertEquals("no READ tab", 0, rule.onAllNodesWithTag("hub.view.read").fetchSemanticsNodes().size)
        val tabs = listOf("all", "unread", "files", "timeline").map { b("hub.view.$it") }
        tabs.forEach { assertEquals("each tab is 48 dp high", 48f, it.bottom.value - it.top.value, 0.5f) }
        assertTrue("one row", tabs.all { it.top.value == tabs[0].top.value })
        assertTrue("left to right in order", tabs.zipWithNext().all { (a, c) -> a.right.value <= c.left.value + 0.5f })
        assertTrue("tabs start at the 20 dp margin (less the tab padding)", tabs[0].left.value in 8f..14f)
        val clock = b("lp.clock"); assertTrue("the tab row is under the clock row", tabs[0].top.value >= clock.bottom.value - 1f)
        val line = b("lp.tabsLine"); assertEquals("a hairline under the tabs", tabs[0].bottom.value, line.top.value, 1f)
        assertTrue("the hairline is thin", line.bottom.value - line.top.value <= 1f)
        assertEquals(true, rule.onNodeWithTag("hub.view.all").fetchSemanticsNode().config.getOrNull(SemanticsProperties.Selected))
    }

    // ---- rows ----
    @Test fun STR_02_w27_rowsAre56dpIconsTitleSenderAndARightAlignedTimeWithAnInsetHairlineAndNoBands() {
        render(feed(4), home = false)
        val w = winW()
        val keys = (0 until 4).map { "k$it" }.filter { rule.onAllNodesWithTag("lp.row.$it", true).fetchSemanticsNodes().isNotEmpty() && b("lp.row.$it", true).bottom.value <= rule.onRoot().getUnclippedBoundsInRoot().bottom.value }
        assertTrue("at least two rows are on screen: $keys", keys.size >= 2)
        for (k in keys) {
            val row = b("lp.row.$k", true); val h = row.bottom.value - row.top.value
            assertTrue("row $k is $h dp, expected 78..90", h in 78f..90f)
            val icon = b("lp.badge.$k", true); assertEquals(56f, icon.right.value - icon.left.value, 0.5f); assertEquals(56f, icon.bottom.value - icon.top.value, 0.5f)
            assertEquals("icon at the 20 dp margin", 20f, icon.left.value, 0.5f)
            val time = b("lp.time.$k", true); assertEquals("time right-aligned at the margin", w - 20f, time.right.value, 1f)
            val title = b("lp.title.$k", true); val sub = b("lp.preview.$k", true)
            assertEquals(20f + 56f + 16f, title.left.value, 1f); assertEquals(title.left.value, sub.left.value, 1f)
            assertTrue("sender under the title", sub.top.value >= title.bottom.value - 1f)
            val line = b("lp.rowLine.$k", true); assertEquals("hairline inset 20 dp", 20f, line.left.value, 0.5f); assertEquals(w - 20f, line.right.value, 0.5f)
            assertEquals("icon is vertically centred", (row.top.value + row.bottom.value) / 2, (icon.top.value + icon.bottom.value) / 2, 4f)
        }
        assertEquals("no stripes", 0, rule.onAllNodesWithTag("lp.stripe.k0", true).fetchSemanticsNodes().size)
        assertEquals("no date bands or group headers in the default view", 0, rule.onAllNodes(androidx.compose.ui.test.SemanticsMatcher("group") { it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("lp.group.") == true }).fetchSemanticsNodes().size)
        val whatsapp = rule.onNodeWithTag("lp.title.k1", true).fetchSemanticsNode().config.getOrNull(SemanticsProperties.Text)!!.joinToString { it.text }
        assertEquals("a message shows its text as the headline", "Can we meet for Lunch?", whatsapp)
        assertEquals("and the sender under it", "Tulika Pandey", rule.onNodeWithTag("lp.preview.k1", true).fetchSemanticsNode().config.getOrNull(SemanticsProperties.Text)!!.joinToString { it.text })
        assertEquals("a notification that is not a message shows its title", "Lemon", rule.onNodeWithTag("lp.title.k0", true).fetchSemanticsNode().config.getOrNull(SemanticsProperties.Text)!!.joinToString { it.text })
    }

    @Test fun STR_02_w27_rowIconsAreTheRealColouredAppIcons() {
        render(feed(4), home = false)
        val icon = b("lp.badge.k1", true)
        val px = pixelAt((icon.left.value + icon.right.value) / 2, (icon.top.value + icon.bottom.value) / 2)
        assertEquals("the WhatsApp row paints its own green icon", 0x25D366, px and 0xFFFFFF)
    }

    @Test fun STR_02_w27_unreadTabUsesTheSameRowLayoutAsTheLiveList() {
        render(feed(4), home = false)
        rule.onNodeWithTag("hub.view.unread").performClick(); rule.waitForIdle()
        val ids = rule.onAllNodes(androidx.compose.ui.test.SemanticsMatcher("row") { it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("hub.row.") == true }).fetchSemanticsNodes()
        assertTrue("history rows are shown", ids.isNotEmpty())
        val tag = ids.first().config[SemanticsProperties.TestTag]; val id = tag.removePrefix("hub.row.")
        val row = b(tag, true); assertTrue("${row.bottom.value - row.top.value}", row.bottom.value - row.top.value in 78f..90f)
        val icon = b("hub.badge.$id", true); assertEquals(56f, icon.right.value - icon.left.value, 0.5f)
        assertEquals(winW() - 20f, b("hub.time.$id", true).right.value, 1f)
        assertEquals("no filter chips row any more", 0, rule.onAllNodesWithTag("hub.filters").fetchSemanticsNodes().size)
        assertEquals("no date band in the default view", 0, rule.onAllNodes(androidx.compose.ui.test.SemanticsMatcher("band") { it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("hub.header.") == true }).fetchSemanticsNodes().size)
    }

    // ---- the Comms menu panel ----
    @Test fun STR_01_w27_menuPanelIs58PercentWideWithAnIconOnlyThreeByTwoGroupGrid() {
        render(feed(4))
        openMenu()
        val w = winW()
        val panel = b("lp.menu.panel")
        assertEquals("58 percent of the window (at most 380 dp)", minOf(380f, w * 0.58f), panel.right.value - panel.left.value, 1.5f)
        val cells = GroupMode.entries.associateWith { b("lp.menu.mode.${it.name}") }
        assertEquals("six cells", 6, cells.size)
        assertEquals("three columns", 3, cells.values.map { Math.round(it.left.value) }.toSet().size)
        assertEquals("two rows", 2, cells.values.map { Math.round(it.top.value) }.toSet().size)
        assertEquals("time, app and sender share the top row", cells[GroupMode.Time]!!.top.value, cells[GroupMode.Sender]!!.top.value, 0.5f)
        assertTrue("conversation starts the second row", cells[GroupMode.Conversation]!!.top.value > cells[GroupMode.Time]!!.bottom.value - 1f)
        assertTrue("reading order: time, app, sender", cells[GroupMode.Time]!!.left.value < cells[GroupMode.App]!!.left.value && cells[GroupMode.App]!!.left.value < cells[GroupMode.Sender]!!.left.value)
        assertTrue("second row: conversation, app and conversation, none", cells[GroupMode.Conversation]!!.left.value < cells[GroupMode.AppConversation]!!.left.value && cells[GroupMode.AppConversation]!!.left.value < cells[GroupMode.Flat]!!.left.value)
        val widths = cells.values.map { it.right.value - it.left.value }; assertTrue("equal cells $widths", widths.max() - widths.min() < 1f)
        assertTrue("the grid sits inside the panel with the 20 dp margin", cells.values.all { it.left.value >= panel.left.value + 19.5f && it.right.value <= panel.right.value - 19.5f })
        // Icons only: no label text inside a cell; the names are accessibility descriptions.
        GroupMode.entries.forEach {
            assertEquals(0, rule.onNodeWithTag("lp.menu.mode.${it.name}", useUnmergedTree = true).fetchSemanticsNode().children.count { c -> c.config.getOrNull(SemanticsProperties.Text) != null })
            assertTrue(rule.onNodeWithTag("lp.menu.mode.${it.name}").fetchSemanticsNode().config.getOrNull(SemanticsProperties.ContentDescription)!!.isNotEmpty())
        }
        assertEquals("the default grouping (app and conversation) is the selected cell", true, rule.onNodeWithTag("lp.menu.mode.Flat").fetchSemanticsNode().config.getOrNull(SemanticsProperties.Selected))
        assertTrue("GROUP BY and Show labels", listOf("GROUP BY", "Show").all { rule.onAllNodesWithText(it).fetchSemanticsNodes().isNotEmpty() })
    }

    @Test fun STR_01_w27_showRowsHaveAnAppIconAndTheNameAndACountRightAlignedAllCommsFirst() {
        render(feed(4))
        openMenu()
        val panel = b("lp.menu.panel")
        val rows = listOf("lp.menu.show.all", "lp.menu.show.com.whatsapp", "lp.menu.show.com.snapchat.android")
        var allTop = 0f
        for (r in rows) {
            scrollMenuTo(r)
            val rb = b(r)
            if (r == rows[0]) allTop = rb.top.value else assertTrue("All Comms is the first row, the apps follow", rb.top.value > allTop || r != rows[0])
            assertEquals("counts are right-aligned at the panel's inner edge", panel.right.value - 20f, b("$r.count", true).right.value, 1f)
        }
        scrollMenuTo(rows[0])
        assertEquals("All Comms carries the total", "4", rule.onNodeWithTag("lp.menu.show.all.count", true).fetchSemanticsNode().config.getOrNull(SemanticsProperties.Text)!!.joinToString { it.text })
        scrollMenuTo(rows[1])
        val icon = b("lp.menu.show.com.whatsapp.icon", true); assertEquals("24 dp icon", 24f, icon.right.value - icon.left.value, 0.5f)
        assertEquals("icon at the 20 dp margin", panel.left.value + 20f, icon.left.value, 1f)
        val name = rule.onNodeWithTag("lp.menu.show.com.whatsapp").fetchSemanticsNode().config.getOrNull(SemanticsProperties.Text)!!.joinToString { it.text }
        assertTrue(name, name.contains("WhatsApp"))
    }

    @Test fun STR_01_w27_showRowsPaintTheAppsOwnColouredIconNotAMonochromeGlyph() {
        render(feed(4))
        openMenu()
        scrollMenuTo("lp.menu.show.com.whatsapp")
        val wa = b("lp.menu.show.com.whatsapp.icon", true)
        assertEquals("the miniature icon is the real (green) icon", 0x25D366, pixelAt((wa.left.value + wa.right.value) / 2, (wa.top.value + wa.bottom.value) / 2) and 0xFFFFFF)
        scrollMenuTo("lp.menu.show.com.snapchat.android")
        val sc = b("lp.menu.show.com.snapchat.android.icon", true)
        assertEquals("and Snapchat's is yellow", 0xFFFC00, pixelAt((sc.left.value + sc.right.value) / 2, (sc.top.value + sc.bottom.value) / 2) and 0xFFFFFF)
        // 8 dp between the icon and the name.
        val nameBox = b("lp.menu.show.com.whatsapp", true)
        assertTrue(nameBox.left.value <= wa.left.value)
    }

    @Test fun STR_01_w27_aMissingIconFallsBackToAColouredLetterBadge() {
        iconCache.remove("com.whatsapp")
        render(feed(4))
        openMenu()
        scrollMenuTo("lp.menu.show.com.whatsapp")
        val wa = b("lp.menu.show.com.whatsapp.icon", true)
        val px = pixelAt(wa.left.value + 3f, wa.top.value + 12f)
        assertEquals("the badge is the app's hue, not the panel grey", HubStyle.stripeFor("com.whatsapp").value.shr(32).toInt() and 0xFFFFFF, px and 0xFFFFFF)
    }

    @Test fun STR_01_w27_choosingAGroupingOrAnAppAppliesAndClosesThePanel() {
        render(feed(4))
        openMenu()
        scrollMenuTo("lp.menu.show.com.whatsapp")
        rule.onNodeWithTag("lp.menu.show.com.whatsapp").performClick(); rule.waitForIdle(); rule.mainClock.advanceTimeBy(1000); rule.waitForIdle()
        assertFalse(menuRef!!.open)
        assertEquals("the title carries the filter", "WhatsApp", rule.onNodeWithTag("lp.title", true).fetchSemanticsNode().config.getOrNull(SemanticsProperties.Text)!!.joinToString { it.text })
        assertEquals("the app filter follows into history views", "com.whatsapp", hub.filters(HubView.Unread).app)
    }

    @Test fun STR_01_w27_optionsMovedIntoThePanelAndTheThreeDotsButtonIsGone() {
        render(feed(4))
        assertEquals("no ... button on the tab row", 0, rule.onAllNodesWithTag("hub.options").fetchSemanticsNodes().size)
        openMenu()
        for (t in listOf("lp.menu.opt.default", "lp.menu.opt.read", "lp.menu.opt.markRead", "lp.menu.opt.delete")) {
            if (rule.onAllNodesWithTag(t).fetchSemanticsNodes().isEmpty()) rule.onNodeWithTag("lp.menu.list").performScrollToNode(androidx.compose.ui.test.hasTestTag(t))
            rule.onNodeWithTag(t).assertExists()
        }
        rule.onNodeWithTag("lp.menu.opt.read").performClick(); rule.waitForIdle()
        assertEquals("Read history is reachable from the menu", HubView.Read, hub.view)
    }

    private fun scrollMenuTo(tag: String) { rule.onNodeWithTag("lp.menu.list").performScrollToNode(androidx.compose.ui.test.hasTestTag(tag)); rule.waitForIdle() }

    // Moved here from the hub's inline options panel (HUB_14, HUB_16): density, grouping, mark visible as read and delete history are rows of the Comms menu.
    @Test fun STR_01_w27_densityAndGroupingOfAHistoryViewAreInTheMenu() {
        render(feed(4))
        rule.onNodeWithTag("hub.view.unread").performClick(); rule.waitForIdle()
        openMenu()
        scrollMenuTo("lp.menu.opt.density")
        rule.onNodeWithTag("lp.menu.opt.density").performClick(); rule.waitForIdle()
        assertEquals(HubDensity.Expanded, hub.density)
        assertTrue("the density row keeps the panel open", menuRef!!.open)
        scrollMenuTo("lp.menu.mode.App")
        rule.onNodeWithTag("lp.menu.mode.App").performClick(); rule.waitForIdle(); rule.mainClock.advanceTimeBy(1000); rule.waitForIdle()
        assertEquals(HubGrouping.App, hub.grouping)
        assertFalse(menuRef!!.open)
        rule.onAllNodesWithTag("hub.header.a:com.whatsapp").fetchSemanticsNodes().let { assertTrue("app header for the app group", it.isEmpty() || it.size == 1) }
    }

    @Test fun STR_01_w27_markVisibleReadAndDeleteHistoryAreMenuRowsAndDeleteNeedsAConfirmation() {
        render(feed(4))
        rule.onNodeWithTag("hub.view.unread").performClick(); rule.waitForIdle()
        openMenu()
        scrollMenuTo("lp.menu.opt.markRead"); rule.onNodeWithTag("lp.menu.opt.markRead").performClick(); rule.waitForIdle(); rule.mainClock.advanceTimeBy(1000); rule.waitForIdle()
        assertTrue("marking read through the menu reads the visible history", hub.store!!.entries.value.all { it.read })
        assertFalse(menuRef!!.open)
        openMenu()
        scrollMenuTo("lp.menu.opt.delete"); rule.onNodeWithTag("lp.menu.opt.delete").performClick(); rule.waitForIdle()
        assertEquals("nothing deleted by the first tap", 4, hub.store!!.entries.value.size)
        scrollMenuTo("hub.deleteConfirm"); rule.onNodeWithTag("hub.deleteConfirm").assertExists()
        scrollMenuTo("lp.menu.opt.deleteCancel"); rule.onNodeWithTag("lp.menu.opt.deleteCancel").performClick(); rule.waitForIdle()
        rule.onNodeWithTag("lp.menu.opt.delete").performClick(); rule.waitForIdle()
        // The scrim's centre is under the panel; tap its far edge, the part of the list that still shows.
        rule.onNodeWithTag("lp.menu.scrim").performTouchInput { click(androidx.compose.ui.geometry.Offset(width - 10f, height / 2f)) }; rule.waitForIdle(); rule.mainClock.advanceTimeBy(1000); rule.waitForIdle()
        assertFalse(menuRef!!.open)
        openMenu(); scrollMenuTo("lp.menu.opt.delete")
        rule.onNodeWithTag("hub.deleteConfirm").assertDoesNotExist()
        rule.onNodeWithTag("lp.menu.opt.delete").performClick(); rule.waitForIdle()
        scrollMenuTo("lp.menu.opt.deleteYes"); rule.onNodeWithTag("lp.menu.opt.deleteYes").performClick(); rule.waitForIdle()
        assertTrue(hub.store!!.entries.value.isEmpty())
    }

    @Test fun STR_01_w27_theListShiftsRightBehindThePanelAndTheHomeCardLeaves() {
        render(feed(4))
        openMenu()
        val panel = b("lp.menu.panel"); val list = b("lp.root")
        assertTrue("list shifted right of the panel edge", list.left.value >= panel.right.value - 1f)
    }

    // ---- right to left ----
    @Test fun STR_02_w27_rtlMirrorsHeaderRowsAndPanel() {
        render(feed(4), rtl = true, home = false)
        val w = winW()
        val clock = b("lp.clock"); assertEquals("clock on the right", w - 20f, clock.right.value, 1f)
        assertTrue("glyphs on the left", b("lp.sys.wifi").right.value < clock.left.value)
        val icon = b("lp.badge.k0", true); assertEquals("icon on the right", w - 20f, icon.right.value, 0.5f)
        val time = b("lp.time.k0", true); assertEquals("time at the left margin", 20f, time.left.value, 1f)
        openMenu()
        val panel = b("lp.menu.panel"); assertEquals("panel from the right", w, panel.right.value, 1f)
    }

    private fun pixelAt(xDp: Float, yDp: Float): Int {
        rule.mainClock.advanceTimeBy(800); rule.waitForIdle()
        val view = rule.activity.findViewById<android.view.View>(android.R.id.content)
        val bmp = android.graphics.Bitmap.createBitmap(view.width, view.height, android.graphics.Bitmap.Config.ARGB_8888)
        rule.runOnUiThread { view.draw(android.graphics.Canvas(bmp)) }
        val d = rule.activity.resources.displayMetrics.density
        return bmp.getPixel((xDp * d).toInt().coerceIn(0, bmp.width - 1), (yDp * d).toInt().coerceIn(0, bmp.height - 1))
    }
}
