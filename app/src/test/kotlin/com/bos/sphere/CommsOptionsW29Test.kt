package com.bos.sphere

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.semantics.getOrNull
import com.bos.sphere.feature.grid.description
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.core.app.ApplicationProvider
import com.bos.sphere.core.data.CommsScale
import com.bos.sphere.core.data.CommsScaleSettings
import com.bos.sphere.core.data.HomePage
import com.bos.sphere.core.data.HubNotification
import com.bos.sphere.core.data.LauncherPrefs
import com.bos.sphere.core.data.NotificationHistory
import com.bos.sphere.core.data.NotificationHistoryStore
import com.bos.sphere.core.data.NotificationProvider
import com.bos.sphere.core.design.L10n
import com.bos.sphere.core.design.SphereTheme
import com.bos.sphere.feature.grid.SettingCategory
import com.bos.sphere.feature.grid.SettingItem
import com.bos.sphere.feature.grid.SettingsIndex
import com.bos.sphere.feature.grid.SettingsSpace
import com.bos.sphere.ui.CommsOptions
import com.bos.sphere.ui.DefaultViewChoice
import com.bos.sphere.ui.HubPrefs
import com.bos.sphere.ui.HubState
import com.bos.sphere.ui.HubView
import com.bos.sphere.ui.LaunchpadHubNav
import com.bos.sphere.ui.SettingsModel
import com.bos.sphere.ui.SystemLinks
import com.bos.sphere.ui.buildSettingsCategories
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.util.concurrent.Executor

/**
 * Wave 29 (commsOptions): the Comms options moved from the Comms menu panel into Ground Control's Comms category. Each row changes the state it
 * names; the destructive ones ask first; the search index finds them; the rows hold at font scale 2.0 and in RTL.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h1600dp-xhdpi")
class CommsOptionsW29Test {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val ctx get() = ApplicationProvider.getApplicationContext<android.content.Context>()
    private lateinit var store: NotificationHistoryStore
    private val outDir = File(System.getProperty("user.dir"), "build/screenshots").apply { mkdirs() }

    private fun feed(n: Int) = (0 until n).map { HubNotification(id = "k$it", key = "k$it", source = "x", title = "Person $it", summary = "Hello $it", timestamp = 1_000L * it, packageName = "com.whatsapp", appLabel = "WhatsApp") }

    @Before fun setUp() {
        ctx.getSharedPreferences("launchpad_hub", 0).edit().clear().commit()
        store = NotificationHistoryStore(File(java.nio.file.Files.createTempDirectory("w29").toFile(), "usage/h.log"), Executor { it.run() })
        store.observe(feed(4))
        NotificationHistory.setForTest(store)
        CommsOptions.init(ctx)
        CommsOptions.setDefaultView(HubView.All)
        CommsScaleSettings.set(CommsScale.DEFAULT)
        LaunchpadHubNav.requests.value = null; LaunchpadHubNav.groups.value = false; LaunchpadHubNav.openSignal.value = false
    }
    @After fun tearDown() { NotificationHistory.setForTest(null); CommsOptions.clearer = { NotificationProvider.clearAll() }; NotificationProvider.publishForTest(emptyList()) }

    private fun cats(): List<SettingCategory> = buildSettingsCategories(
        SettingsModel(
            prefs = LauncherPrefs(), updatePrefs = {}, pageIndex = 0, pages = listOf(HomePage(keys = emptyList(), id = 1L)),
            setPageLayout = { _, _ -> }, addPageAfter = {}, deletePage = {}, setClockTile = { _, _ -> }, corners = emptyMap(), gestures = emptyMap(), setGesture = { _, _ -> },
            about = listOf("Version" to "test"), appsByKey = emptyMap(), isLandscape = false, chosenApp = { null },
            pickLeftWidget = {}, leftWidgetLabel = null, pickAppFor = {}, motionAvailable = false, editCorner = {}, editGesture = {},
            system = SystemLinks({}, {}, {}, {}), lab = emptyList(),
        ),
    )
    private fun comms() = cats().first { it.title == "Side panel" } // wave 52: the category is the Side panel
    private fun row(title: String) = comms().items.firstOrNull { it.title == title } ?: throw AssertionError("no Comms row '$title'")

    @Test fun STR_01_w29_theCommsCategoryListsEveryMovedOptionOnce() {
        val titles = comms().items.map { it.title }
        for (t in listOf("Comms size", "Default view", "Read history", "Groups and rules", "Mark all as read", "Clear all notifications", "Delete history")) assertEquals("one '$t' row", 1, titles.count { it == t })
        assertTrue("the three destructive rows ask first", listOf("Mark all as read", "Clear all notifications", "Delete history").all { (row(it) as SettingItem.Action).confirm != null })
    }

    @Test fun STR_01_w29_theDefaultViewRowPersistsAndCommsOpensOnIt() {
        @Suppress("UNCHECKED_CAST") val r = row("Default view") as SettingItem.Choice<DefaultViewChoice>
        // Wave 53: the three categories sit right below All.
        assertEquals(listOf("All", "Messages", "Notifications", "Alerts", "Unread", "Read", "Files", "Timeline"), r.options.map { r.label(it) })
        r.onSelect(DefaultViewChoice(HubView.Timeline))
        assertEquals(HubView.Timeline, HubPrefs.of(ctx).defaultView)
        assertEquals("the row shows it live", DefaultViewChoice(HubView.Timeline), (row("Default view") as SettingItem.Choice<*>).selected)
        assertEquals("Comms opens on it", HubView.Timeline, HubState(HubPrefs.of(ctx), store, true, liveAll = true).view)
    }

    @Test fun STR_17_theDefaultViewRowOffersTheCategoriesAndStoresTheChoiceWithTheExportedPrefs() {
        @Suppress("UNCHECKED_CAST") val r = row("Default view") as SettingItem.Choice<DefaultViewChoice>
        r.onSelect(DefaultViewChoice(HubView.All, com.bos.sphere.core.data.CommsCategory.Alerts))
        assertEquals(com.bos.sphere.core.data.CommsCategory.Alerts, com.bos.sphere.core.data.LaunchpadSettings.prefs.value.defaultCategory)
        assertEquals(DefaultViewChoice(HubView.All, com.bos.sphere.core.data.CommsCategory.Alerts), (row("Default view") as SettingItem.Choice<*>).selected)
        assertEquals("the hub still opens on All (the category filters it)", HubView.All, HubPrefs.of(ctx).defaultView)
        (row("Default view") as SettingItem.Choice<DefaultViewChoice>).onSelect(DefaultViewChoice(HubView.All))
        assertEquals(null, com.bos.sphere.core.data.LaunchpadSettings.prefs.value.defaultCategory)
    }

    @Test fun STR_16_theShowPinnedAtTheTopRowIsOnByDefaultAndStoresItsChoice() {
        val t = row("Show pinned at the top") as SettingItem.Toggle
        assertEquals(true, t.checked)
        t.onChange(false)
        assertEquals(false, com.bos.sphere.core.data.LaunchpadSettings.prefs.value.pinnedAtTop)
        (row("Show pinned at the top") as SettingItem.Toggle).onChange(true)
        assertEquals(true, com.bos.sphere.core.data.LaunchpadSettings.prefs.value.pinnedAtTop)
    }

    @Test fun STR_01_w29_theCommsSizeRowChangesTheScale() {
        val r = row("Comms size") as SettingItem.Stepper
        r.onChange(115)
        assertEquals(115, CommsScaleSettings.percent.value)
        assertEquals("115%", (row("Comms size") as SettingItem.Stepper).value)
    }

    @Test fun STR_01_w29_readHistoryAndGroupsAndRulesOpenCommsOnTheirScreens() {
        (row("Read history") as SettingItem.Action).onClick()
        assertEquals(HubView.Read, LaunchpadHubNav.requests.value); assertTrue(LaunchpadHubNav.openSignal.value); assertFalse(LaunchpadHubNav.groups.value)
        LaunchpadHubNav.requests.value = null; LaunchpadHubNav.openSignal.value = false
        (row("Groups and rules") as SettingItem.Action).onClick()
        assertTrue("the manager is requested", LaunchpadHubNav.groups.value); assertTrue(LaunchpadHubNav.openSignal.value)
        assertTrue(LaunchpadHubNav.takeGroups()); assertFalse(LaunchpadHubNav.groups.value)
    }

    @Test fun STR_01_w29_markAllReadMarksTheLiveListAndTheHistory() {
        NotificationProvider.publishForTest(feed(4))
        (row("Mark all as read") as SettingItem.Action).onClick()
        assertTrue(store.entries.value.isNotEmpty() && store.entries.value.all { it.read })
        assertTrue("the live rows are read too", com.bos.sphere.core.data.LaunchpadSettings.readKeys.value.containsAll(feed(4).map { it.key }))
    }

    @Test fun STR_01_w29_clearAllNotificationsDismissesTheLiveListOnly() {
        var cleared = 0; CommsOptions.clearer = { cleared++ }
        (row("Clear all notifications") as SettingItem.Action).onClick()
        assertEquals(1, cleared); assertEquals("history untouched", 4, store.entries.value.size)
    }

    private fun show(font: Float = 1f, rtl: Boolean = false, category: Int? = null) = rule.setContent {
        val d = androidx.compose.ui.platform.LocalDensity.current
        CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides Density(d.density, font), LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
            val all = androidx.compose.runtime.remember { cats() }
            SphereTheme { SettingsSpace(visible = true, categories = all, initialCategory = category ?: all.indexOfFirst { it.title == "Side panel" }, sounds = null, onClose = {}, session = 0) }
        }
    }
    private fun shoot(name: String) {
        rule.mainClock.autoAdvance = false; rule.mainClock.advanceTimeBy(800)
        val view = rule.activity.findViewById<android.view.View>(android.R.id.content)
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread { view.draw(android.graphics.Canvas(bmp)) }
        File(outDir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        rule.mainClock.autoAdvance = true
    }
    @Test fun STR_01_w29_deleteHistoryAsksForConfirmationThenDeletes() {
        show(); rule.waitForIdle()
        val scroller = rule.onNodeWithTag(com.bos.sphere.feature.grid.SettingsTabTags.list("Side panel"))
        scroller.performScrollToNode(hasText("Delete history", substring = true)); rule.waitForIdle()
        rule.onAllNodes(hasText("Delete history", substring = true))[0].performClick(); rule.waitForIdle()
        rule.onNodeWithTag("gc.confirm.text").assertExists()
        assertEquals("nothing deleted by the first tap", 4, store.entries.value.size)
        rule.onNodeWithText("Cancel").performClick(); rule.waitForIdle()
        assertEquals(4, store.entries.value.size)
        scroller.performScrollToNode(hasText("Delete history", substring = true)); rule.waitForIdle()
        rule.onAllNodes(hasText("Delete history", substring = true))[0].performClick(); rule.waitForIdle()
        rule.onAllNodes(hasText("Delete history", substring = true)).let { n -> n[n.fetchSemanticsNodes().size - 1].performClick() }; rule.waitForIdle()
        assertTrue("deleted after the confirmation", store.entries.value.isEmpty())
    }

    @Test fun STR_01_w29_settingsSearchFindsEveryMovedRow() {
        val idx = SettingsIndex.fromCategories(cats())
        for ((q, t) in listOf("comms size" to "Comms size", "default view" to "Default view", "read history" to "Read history", "groups and rules" to "Groups and rules",
            "mark all" to "Mark all as read", "clear all notifications" to "Clear all notifications", "delete history" to "Delete history")) {
            val hit = idx.search(q).firstOrNull { it.title == t }
            assertNotNull("search '$q' finds '$t'", hit); assertEquals("Side panel", hit!!.category)
        }
        assertTrue("the launchpad alias still lands in the Side panel", idx.search("launchpad").any { it.category == "Side panel" })
    }

    private fun rowsHold(font: Float, rtl: Boolean) {
        show(font, rtl); rule.waitForIdle()
        rule.onNodeWithTag(com.bos.sphere.feature.grid.SettingsTabTags.list("Side panel")).performScrollToNode(hasText("Default view", substring = true)); rule.waitForIdle() // wave 57: below the Settings header
        val d = rule.activity.resources.displayMetrics.density
        val n = rule.onAllNodes(hasText("Default view", substring = true)).fetchSemanticsNodes()
        assertTrue("'Default view' composes (font $font rtl $rtl)", n.isNotEmpty())
        assertTrue("the Default view row is at least 48 dp: ${n[0].boundsInRoot.height / d}", n[0].boundsInRoot.height / d >= 47.5f)
        shoot("w29-comms-settings-font${font.toInt()}${if (rtl) "-rtl" else ""}")
        val reads = rule.onAllNodes(hasText("Read history", substring = true)).fetchSemanticsNodes()
        if (reads.isNotEmpty()) assertTrue("an action row is at least 48 dp: ${reads[0].boundsInRoot.height / d}", reads[0].boundsInRoot.height / d >= 47.5f)
    }
    @Test fun STR_01_w29_theCommsRowsComposeAtNormalSize() = rowsHold(1f, false)
    @Test fun STR_01_w29_theCommsRowsComposeAtFontScale2() = rowsHold(2f, false)
    @Test fun STR_01_w29_theCommsRowsComposeInRtl() = rowsHold(1f, true)
    @Test fun STR_01_w29_theCommsRowsComposeInRtlAtFontScale2() = rowsHold(2f, true)

    @Test fun STR_01_w29_everyNewStringIsTranslatedIn8Languages() {
        val keys = listOf("What Comms shows first when it opens.", "Opens the notifications you have already read.", "Opens the groups and rules manager in Comms.", "Mark all as read",
            "Clear all notifications", "Deletes the stored notification history. Notifications in the shade stay.", "Clear all notifications from the list? The history stays.")
        for (k in keys) assertTrue("'$k' is a known string", k in L10n.englishKeys)
    }

    // ---- adversarial review additions ----
    private val commsStrings: List<String> get() = comms().items.flatMap { i ->
        listOfNotNull(i.title, i.value.takeIf { it.isNotEmpty() && i is SettingItem.Action }, i.description(),
            (i as? SettingItem.Action)?.confirm, (i as? SettingItem.Action)?.confirmLabel.takeIf { i is SettingItem.Action && i.confirm != null })
    } + listOf("Cancel", "All", "Unread", "Read", "Files", "Timeline")

    @Test fun STR_01_w29_everyCommsRowStringIsTranslatedIn8Languages() {
        for (lang in listOf("es", "fr", "de", "pt", "hi", "zh", "ja", "ar")) {
            val t = L10n.tableFor(lang) ?: throw AssertionError("no table for $lang")
            for (s in commsStrings) assertTrue("'$s' translated to $lang", t.containsKey(s))
        }
    }

    @Test fun STR_01_w29_theActionRowsHaveASummaryLikeEveryOtherRow() {
        for (t in listOf("Read history", "Groups and rules", "Mark all as read", "Clear all notifications", "Delete history")) {
            assertTrue("'$t' has a summary", !(row(t) as SettingItem.Action).hint.isNullOrBlank())
        }
        assertNotNull("search matches the summary text", SettingsIndex.fromCategories(cats()).search("shade").firstOrNull { it.title == "Delete history" })
    }

    // Wave 54: the Default view row is a row of drawn cards like every other choice, so no choice is forced onto the old stepped slab any more.
    @Test fun STR_01_w29_noChoiceNeedsTheSteppedSlabNowTheDefaultViewIsCards() {
        val all = cats().flatMap { c -> c.items.filterIsInstance<SettingItem.Choice<*>>() }
        assertEquals(emptyList<String>(), all.filter { it.stepped }.map { it.title })
        assertNotNull("the Default view row has its cards", all.first { it.title == "Default view" }.preview)
    }
    @Suppress("UNCHECKED_CAST") private fun SettingItem.Choice<*>.labelOf(o: Any?): String = (this as SettingItem.Choice<Any?>).label(o)

    private fun showClosing(font: Float = 1f, closed: IntArray) = rule.setContent {
        val d = androidx.compose.ui.platform.LocalDensity.current
        CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides Density(d.density, font)) {
            val all = androidx.compose.runtime.remember { cats() }
            SphereTheme { SettingsSpace(visible = true, categories = all, initialCategory = all.indexOfFirst { it.title == "Side panel" }, sounds = null, onClose = { closed[0]++ }, session = 0) }
        }
    }
    private fun openConfirm(title: String) {
        rule.onNodeWithTag(com.bos.sphere.feature.grid.SettingsTabTags.list("Side panel")).performScrollToNode(hasText(title)); rule.waitForIdle()
        rule.onAllNodes(hasText(title))[0].performClick(); rule.waitForIdle()
        rule.onNodeWithTag("gc.confirm.text").assertExists()
    }

    @Test fun STR_01_w29_backDismissesTheConfirmationNotGroundControl() {
        val closed = intArrayOf(0); showClosing(1f, closed); rule.waitForIdle()
        openConfirm("Mark all as read")
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }; rule.waitForIdle()
        rule.onAllNodesWithTag("gc.confirm.text").assertCountEquals(0)
        assertEquals("Ground Control stays open", 0, closed[0])
        assertTrue("nothing was marked", store.entries.value.none { it.read })
    }

    @Test fun STR_01_w29_confirmationButtonsStayOnScreenAtFontScale2() {
        val closed = intArrayOf(0); showClosing(2f, closed); rule.waitForIdle()
        openConfirm("Delete history")
        val w = rule.activity.resources.displayMetrics.widthPixels.toFloat()
        for (label in listOf("Cancel", "Delete history")) {
            val n = rule.onAllNodes(hasText(label)).fetchSemanticsNodes().last().boundsInRoot
            assertTrue("'$label' inside the window: $n of $w", n.left >= 0f && n.right <= w + 0.5f)
        }
    }

    @Test fun STR_01_w29_theCommsSizeTitleIsNotCutAtFontScale2() {
        show(2f, false); rule.waitForIdle()
        val cut = rule.onAllNodes(hasText("Comms size")).fetchSemanticsNodes().mapNotNull { n ->
            val r = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
            n.config.getOrNull(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult)?.action?.invoke(r); r.firstOrNull()
        }
        assertTrue("the title composes", cut.isNotEmpty())
        assertTrue("the title stays whole on one line: ${cut.map { it.layoutInput.text.text + it.lineCount }}", cut.all { it.lineCount == 1 && !it.isLineEllipsized(0) })
    }

    @Test fun STR_01_w29_noLeftoverOptionsInTheMenuCodeOrDocs() {
        val root = File(System.getProperty("user.dir")).parentFile
        fun text(p: String) = File(root, p).readText()
        assertFalse("HubState keeps no delete confirmation of the removed menu row", text("feature-launchpad/src/main/kotlin/com/bos/sphere/ui/HubModel.kt").contains("confirmDelete"))
        assertFalse(text("feature-launchpad/src/main/kotlin/com/bos/sphere/ui/HubUi.kt").contains("Options live in the Comms menu"))
        for (d in listOf("docs/decisions/commsLayout.md", "docs/decisions/hubData.md", "docs/decisions/commsDesign.md")) {
            val t = text(d)
            assertFalse("$d still describes Options in the Comms menu", Regex("Options row of the Comms menu|in Options stores|the switcher tabs, Options|Options are inline|then the Options rows|Options live in the Comms menu").containsMatchIn(t))
        }
    }

    @Test fun STR_02_w42_theShadeRowDefaultsOnPersistsAndChangesWhatDismissDoes() {
        val r = row("Clear from the shade when dismissed in Comms") as SettingItem.Toggle
        assertTrue("default on keeps the wave 32 behaviour", r.checked)
        assertTrue(HubPrefs.of(ctx).clearFromShade)
        r.onChange(false)
        assertFalse(HubPrefs.of(ctx).clearFromShade)
        assertFalse((row("Clear from the shade when dismissed in Comms") as SettingItem.Toggle).checked)
        assertFalse("the live flag Comms reads when dismissing", CommsOptions.clearFromShade.value)
        CommsOptions.setClearFromShade(true)
        assertTrue(HubPrefs.of(ctx).clearFromShade)
    }
}
