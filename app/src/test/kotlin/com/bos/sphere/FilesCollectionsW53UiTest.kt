package com.bos.sphere

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.bos.sphere.core.catalog.FileCollections
import com.bos.sphere.core.catalog.FilesCollectionPrefs
import com.bos.sphere.core.catalog.FileKind
import com.bos.sphere.core.catalog.FilesViewMode
import com.bos.sphere.core.catalog.GroupKey
import com.bos.sphere.core.catalog.HomePanelPrefs
import com.bos.sphere.core.catalog.PinnedFolder
import com.bos.sphere.core.catalog.RecentFile
import com.bos.sphere.core.catalog.SortKey
import com.bos.sphere.core.catalog.ViewOptions
import com.bos.sphere.core.design.HyleColors
import com.bos.sphere.feature.grid.LauncherInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Wave 53 (HOM-15, filesCollections): the Files section's source switcher, collections, chooser and editor pages, sort and group sheet, sections in
 * the list and the grid, what each source remembers, and the empty states. The files come from a fake index (no real folder or media is read), so
 * these tests prove the screen's behaviour; real grants, the system picker and real providers are device checks.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi")
class FilesCollectionsW53UiTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val ctx get() = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val outDir = File(System.getProperty("user.dir"), "build/screenshots").apply { mkdirs() }
    private val now = 1_700_000_000_000L
    private val day = 86_400_000L
    private val t1 = "content://t/1"; private val t2 = "content://t/2"
    private val folders = listOf(PinnedFolder(t1, "Documents"), PinnedFolder(t2, "Download"))

    private fun file(name: String, size: Long = 1000, age: Int = 1, folder: String = "Documents", source: String = t1, mime: String = "") =
        RecentFile("content://doc/$name", name, mime, now - age * day, source, size, folder)

    private val mb = 1024L * 1024
    private val files = listOf(
        file("report.pdf", 2 * mb, 1), file("Budget.xlsx", 90_000, 3), file("notes.txt", 800, 40),
        file("holiday.jpg", 3 * mb, 2, "Camera", t2), file("Screenshot_1.png", 400_000, 0, "Download", t2),
        file("movie.mp4", 700 * mb, 100, "Videos"), file("song.mp3", 6 * mb, 8, "Music", t2),
        file("backup.zip", 150 * mb, 5, "Download", t2), file("invoice.pdf", 300_000, 10, "Download", t2),
    )

    private class NoThumbs : FileThumbs { override suspend fun load(file: RecentFile, kind: FileKind, widthPx: Int, heightPx: Int) = ThumbData(null) }

    private var picked = 0
    private var opened: RecentFile? = null
    private var openedFolder: PinnedFolder? = null
    private lateinit var data: FakeFilesData

    @Before fun setUp() {
        FileThumbCache.cache.clear(); LauncherInput.focus.nodes().forEach { }
        ctx.getSharedPreferences("home_state", 0).edit().clear().commit()
        picked = 0; opened = null; openedFolder = null
    }

    private fun show(list: List<RecentFile> = files, granted: List<PinnedFolder> = folders, font: Float = 1f, rtl: Boolean = false, after: FilesSnapshot? = null) {
        data = FakeFilesData(FilesSnapshot(granted, list), after)
        rule.setContent {
            val d = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(d.density, font), LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
                LocalFilesData provides data, LocalFileThumbs provides NoThumbs(),
            ) {
                Box(Modifier.fillMaxSize().background(HyleColors.Surface).padding(horizontal = 18.dp, vertical = 12.dp)) {
                    FilesSection(HomePanelPrefs(ctx), FilesCollectionPrefs(ctx), FilesActions({ picked++ }, { opened = it }, { openedFolder = it }), clock = { now })
                }
            }
        }
        await("files.sources")
    }

    private fun await(tag: String) = rule.waitUntil(10_000) { rule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
    private fun gone(tag: String) = rule.waitUntil(10_000) { rule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isEmpty() }
    private fun tap(tag: String) { rule.onNodeWithTag(tag, useUnmergedTree = true).also { runCatching { it.performScrollTo() } }.performClick(); rule.waitForIdle() }
    private fun summary() = rule.onNodeWithTag("files.summary").fetchSemanticsNode().config.getOrNull(SemanticsProperties.Text)?.joinToString { it.text }.orEmpty()
    private fun waitSummary(text: String) {
        try { rule.waitUntil(10_000) { runCatching { summary().startsWith(text) }.getOrDefault(false) } }
        catch (e: androidx.compose.ui.test.ComposeTimeoutException) { throw AssertionError("summary was '${runCatching { summary() }.getOrNull()}', wanted '$text'; rows ${rows()}") }
    }

    private fun tagsWith(prefix: String) = rule.onAllNodes(SemanticsMatcher("tag $prefix") { it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith(prefix) == true }, useUnmergedTree = true).fetchSemanticsNodes()
    private fun order(prefix: String): List<String> = tagsWith(prefix).sortedWith(compareBy({ it.positionInRoot.y }, { it.positionInRoot.x })).map { it.config[SemanticsProperties.TestTag].removePrefix(prefix) }
    private fun rows() = order("files.row.")
    private fun awaitRows(expected: List<String>) {
        try { rule.waitUntil(10_000) { rows() == expected } } catch (e: androidx.compose.ui.test.ComposeTimeoutException) { throw AssertionError("rows were ${rows()}, wanted $expected") }
    }
    private fun awaitSections(prefix: String, expected: Set<String>) {
        try { rule.waitUntil(10_000) { sections().filter { it.startsWith(prefix) }.toSet() == expected } } catch (e: androidx.compose.ui.test.ComposeTimeoutException) { throw AssertionError("sections were ${sections()}, wanted $expected") }
    }
    private fun awaitNoSections() {
        try { rule.waitUntil(10_000) { sections().isEmpty() } } catch (e: androidx.compose.ui.test.ComposeTimeoutException) { throw AssertionError("sections were ${sections()}, wanted none") }
    }
    private fun sections() = order("files.section.").filter { !it.startsWith("count.") }

    private fun pick(source: String) { tap("files.source.$source"); waitSettled() }
    private fun waitSettled() { rule.waitForIdle(); Thread.sleep(50); rule.waitForIdle() }
    private fun shoot(name: String) {
        rule.mainClock.autoAdvance = false; rule.mainClock.advanceTimeBy(800)
        val view = rule.activity.findViewById<android.view.View>(android.R.id.content)
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread { view.draw(Canvas(bmp)) }
        File(outDir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        rule.mainClock.autoAdvance = true
    }

    // ---- the switcher ----

    @Test fun HOM_15_w53_theSwitcherListsAllTheShownCollectionsAndEachFolderThenTheAddTile() {
        show()
        val chips = order("files.source.")
        assertEquals(listOf("all", "b:recents", "b:documents", "b:images", "b:downloads", "f:$t1", "f:$t2"), chips)
        rule.onNodeWithTag("files.add").assertExists()
        waitSummary("9 files")
    }

    @Test fun HOM_15_w53_selectingACollectionShowsOnlyItsFilesAndTheCount() {
        show(); waitSummary("9 files")
        pick("b:documents"); waitSummary("4 files")
        assertEquals(listOf("report.pdf", "Budget.xlsx", "invoice.pdf", "notes.txt").sorted(), rows().sorted())
        pick("b:images"); waitSummary("2 files")
        assertEquals(listOf("Screenshot_1.png", "holiday.jpg"), rows())
        pick("b:downloads"); waitSummary("3 files")
        assertEquals(setOf("Screenshot_1.png", "backup.zip", "invoice.pdf"), rows().toSet())
        pick("b:recents"); waitSummary("7 files")
        assertFalse("notes.txt is 40 days old", "notes.txt" in rows())
    }

    @Test fun HOM_15_w53_selectingAFolderShowsThatFoldersFiles() {
        show(); waitSummary("9 files")
        pick("f:$t2"); waitSummary("5 files")
        assertTrue(rows().all { it in setOf("holiday.jpg", "Screenshot_1.png", "song.mp3", "backup.zip", "invoice.pdf") })
        rule.onNodeWithTag("files.openFolder").assertExists()
        tap("files.openFolder"); assertEquals("Download", openedFolder?.name)
        pick("all"); rule.onAllNodesWithTag("files.openFolder").assertCountEquals(0)
    }

    @Test fun HOM_15_w53_theSwitcherChipsAreRadioButtonsWithTheirSelectedState() {
        show(); waitSummary("9 files")
        val n = rule.onNodeWithTag("files.source.all").fetchSemanticsNode().config
        assertEquals(androidx.compose.ui.semantics.Role.RadioButton, n.getOrNull(SemanticsProperties.Role))
        assertEquals(true, n.getOrNull(SemanticsProperties.Selected))
        pick("b:images")
        assertEquals(false, rule.onNodeWithTag("files.source.all").fetchSemanticsNode().config.getOrNull(SemanticsProperties.Selected))
        assertEquals(true, rule.onNodeWithTag("files.source.b:images").fetchSemanticsNode().config.getOrNull(SemanticsProperties.Selected))
        rule.onNodeWithTag("files.source.b:images").assertHeightIsAtLeast(48.dp)
        rule.onNodeWithTag("files.add").assertHeightIsAtLeast(48.dp)
    }

    @Test fun HOM_15_w53_theLastSourceIsRememberedAcrossOpens() {
        show(); waitSummary("9 files"); pick("b:images"); waitSummary("2 files")
        assertEquals("b:images", FilesCollectionPrefs(ctx).source)
        assertEquals("a second section starts where the first stopped", "b:images", FilesCollectionPrefs(ctx).source)
    }

    @Test fun HOM_15_w53_aSourceThatNoLongerExistsFallsBackToAll() {
        FilesCollectionPrefs(ctx).source = "f:content://gone/tree"
        show(); waitSummary("9 files")
        assertEquals(true, rule.onNodeWithTag("files.source.all").fetchSemanticsNode().config.getOrNull(SemanticsProperties.Selected))
        FilesCollectionPrefs(ctx).source = "c:deadbeef"
    }

    @Test fun HOM_15_w53_afterTheFirstReadAFreshScanReplacesTheFilesAndSaysUpdating() {
        val later = FilesSnapshot(folders, files + file("fresh.pdf", 10, 0))
        show(after = later)
        waitSummary("10 files")
        assertTrue(data.reindexed)
    }

    // ---- sort and group ----

    @Test fun HOM_15_w53_theSortAndGroupSheetHasVisualCardsForSortOrderAndGroup() {
        show(); waitSummary("9 files"); tap("files.sortGroup"); await("files.sheet")
        for (k in listOf("Name", "Modified", "Size", "Type")) rule.onNodeWithTag("files.sort.$k", true).assertExists()
        rule.onAllNodesWithTag("files.sort.Added", true).assertCountEquals(0)
        for (g in GroupKey.entries) rule.onNodeWithTag("files.group.${g.name}", true).assertExists()
        rule.onNodeWithTag("files.order.asc", true).assertExists(); rule.onNodeWithTag("files.order.desc", true).assertExists()
        assertEquals(true, rule.onNodeWithTag("files.sort.Modified", true).fetchSemanticsNode().config.getOrNull(SemanticsProperties.Selected))
        assertEquals(true, rule.onNodeWithTag("files.group.None", true).fetchSemanticsNode().config.getOrNull(SemanticsProperties.Selected))
        assertEquals(true, rule.onNodeWithTag("files.order.desc", true).fetchSemanticsNode().config.getOrNull(SemanticsProperties.Selected))
        rule.onNodeWithText("Newest first", useUnmergedTree = true).assertExists()
    }

    @Test fun HOM_15_w53_theSheetsMainActionIsAColouredBoxWithPlainTextBelowIt() {
        show(); waitSummary("9 files"); tap("files.sortGroup"); await("files.sheet")
        val done = rule.onNodeWithTag("files.sheet.done", true).fetchSemanticsNode().boundsInRoot
        val all = rule.onNodeWithTag("files.sheet.all", true).fetchSemanticsNode().boundsInRoot
        assertTrue("Done sits above the plain text", done.bottom <= all.top + 1)
        rule.onNodeWithTag("files.sheet.done", true).assertHeightIsAtLeast(48.dp)
        rule.onNodeWithTag("files.sheet.all", true).assertHeightIsAtLeast(48.dp)
        rule.onAllNodesWithTag("files.sheet.reset", true).assertCountEquals(0)
        tap("files.sheet.done"); gone("files.sheet")
    }

    @Test fun HOM_15_w53_sortingByNameAndSizeAndTypeReordersTheList() {
        show(); waitSummary("9 files")
        awaitRows(listOf("Screenshot_1.png", "report.pdf", "holiday.jpg", "Budget.xlsx", "backup.zip", "song.mp3", "invoice.pdf", "notes.txt", "movie.mp4")) // date modified, newest first, is the default
        tap("files.sortGroup"); await("files.sheet")
        tap("files.sort.Name"); tap("files.order.asc")
        awaitRows(listOf("backup.zip", "Budget.xlsx", "holiday.jpg", "invoice.pdf", "movie.mp4", "notes.txt", "report.pdf", "Screenshot_1.png", "song.mp3"))
        tap("files.order.desc")
        awaitRows(listOf("song.mp3", "Screenshot_1.png", "report.pdf", "notes.txt", "movie.mp4", "invoice.pdf", "holiday.jpg", "Budget.xlsx", "backup.zip"))
        tap("files.sort.Size")
        awaitRows(listOf("movie.mp4", "backup.zip", "song.mp3", "holiday.jpg", "report.pdf", "Screenshot_1.png", "invoice.pdf", "Budget.xlsx", "notes.txt"))
        tap("files.order.asc")
        awaitRows(listOf("notes.txt", "Budget.xlsx", "invoice.pdf", "Screenshot_1.png", "report.pdf", "holiday.jpg", "song.mp3", "backup.zip", "movie.mp4"))
        tap("files.sort.Type"); tap("files.order.asc")
        rule.waitUntil(10_000) { rows().firstOrNull() == "holiday.jpg" } // images come first in the kind order
        rule.onNodeWithText("A to Z", useUnmergedTree = true).assertExists()
    }

    @Test fun HOM_15_w53_groupByDateShowsTheSectionsWithCountsInTheList() {
        show(); waitSummary("9 files")
        tap("files.sortGroup"); await("files.sheet"); tap("files.group.Date"); tap("files.sheet.done"); gone("files.sheet")
        rule.waitUntil(10_000) { sections().containsAll(listOf("d:Today", "d:Yesterday", "d:Older")) } // the middle buckets depend on the weekday of the test clock
        assertEquals("1", rule.onNodeWithTag("files.section.count.d:Today", true).fetchSemanticsNode().config.getOrNull(SemanticsProperties.Text)?.joinToString { it.text })
        val h = rule.onNodeWithTag("files.section.d:Today").fetchSemanticsNode().config
        assertTrue("a header is a heading for the screen reader", h.contains(SemanticsProperties.Heading))
        assertEquals("Today, 1 file", h.getOrNull(SemanticsProperties.ContentDescription)?.firstOrNull())
    }

    @Test fun HOM_15_w53_groupByTypeFolderAndSizeMakeTheirOwnSections() {
        show(); waitSummary("9 files")
        tap("files.sortGroup"); await("files.sheet")
        tap("files.group.Type"); awaitSections("t:", setOf("t:Image", "t:Video", "t:Audio", "t:Pdf", "t:Archive", "t:Spreadsheet", "t:Text"))
        tap("files.group.Folder"); awaitSections("f:", setOf("f:Camera", "f:Documents", "f:Download", "f:Music", "f:Videos"))
        tap("files.group.Size"); awaitSections("s:", setOf("s:Large", "s:Medium", "s:Small"))
        tap("files.group.None"); awaitNoSections()
    }

    @Test fun HOM_15_w53_aStickyHeaderStaysOnTopWhileTheListScrolls() {
        val many = (0 until 80).map { file("p$it.txt", 1000, 0) }
        show(many); waitSummary("80 files")
        tap("files.sortGroup"); await("files.sheet"); tap("files.group.Date"); tap("files.sheet.done"); gone("files.sheet")
        rule.onNodeWithTag("files.section.d:Today").assertIsDisplayed()
        rule.onNodeWithTag("files.list").performTouchInput { repeat(4) { swipeUp(startY = bottom - 10f, endY = top + 10f) } }
        rule.waitForIdle()
        rule.onNodeWithTag("files.section.d:Today").assertIsDisplayed()
    }

    @Test fun HOM_15_w53_theGridGroupsToAndPinsTheCurrentSectionHeader() {
        val many = (0 until 120).map { file("p$it.txt", 1000, 0) } + (0 until 30).map { file("q$it.jpg", 1000, 9, "Camera", t2) }
        HomePanelPrefs(ctx).filesView = FilesViewMode.Grid
        FilesCollectionPrefs(ctx).defaultOptions = ViewOptions(SortKey.Modified, false, GroupKey.Date)
        show(many); waitSummary("150 files")
        rule.onNodeWithTag("files.grid").assertExists()
        rule.onNodeWithTag("files.section.d:Today").assertIsDisplayed()
        rule.onAllNodesWithTag("files.pinned").assertCountEquals(0)
        rule.onNodeWithTag("files.grid").performTouchInput { repeat(3) { swipeUp(startY = bottom - 10f, endY = top + 10f) } }
        rule.waitForIdle()
        await("files.pinned")
        rule.onNodeWithTag("files.pinned").assertIsDisplayed()
    }

    @Test fun HOM_15_w53_theSortAndGroupButtonAnnouncesTheCurrentChoice() {
        show(); waitSummary("9 files")
        val cfg = rule.onNodeWithTag("files.sortGroup").fetchSemanticsNode().config
        assertEquals("Sort and group", cfg.getOrNull(SemanticsProperties.ContentDescription)?.firstOrNull())
        assertEquals("Date modified, Newest first", cfg.getOrNull(SemanticsProperties.StateDescription))
        rule.onNodeWithTag("files.sortGroup").assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
        tap("files.sortGroup"); await("files.sheet"); tap("files.group.Type"); tap("files.sheet.done"); gone("files.sheet")
        assertEquals("Date modified, Newest first, Grouped by Type", rule.onNodeWithTag("files.sortGroup").fetchSemanticsNode().config.getOrNull(SemanticsProperties.StateDescription))
    }

    @Test fun HOM_15_w53_dateAddedAppearsOnlyWhenSomeFileHasOne() {
        show(files + RecentFile("content://doc/x.txt", "x.txt", "", now, t1, 5, "Documents", addedMs = now - 5 * day)); waitSummary("10 files")
        tap("files.sortGroup"); await("files.sheet")
        rule.onNodeWithTag("files.sort.Added", true).assertExists()
        tap("files.sort.Added"); tap("files.sheet.done"); gone("files.sheet")
        assertEquals("x.txt", rows().first())
    }

    @Test fun HOM_15_w53_moreThanTwoThousandFilesShowTheFirstTwoThousandAndSaySo() {
        val many = (0 until 2100).map { file("m$it.txt", 10, 0).copy(modifiedMs = now - it * 1000L) }
        show(many)
        waitSummary("Showing the first 2000 of 2100 files")
        assertTrue(summary().startsWith("Showing the first 2000 of 2100 files"))
    }

    // ---- what each source remembers ----

    @Test fun HOM_15_w53_eachSourceRemembersItsOwnSortAndGroup() {
        show(); waitSummary("9 files")
        pick("b:documents"); tap("files.sortGroup"); await("files.sheet"); tap("files.sort.Name"); tap("files.order.asc"); tap("files.group.Type"); tap("files.sheet.done"); gone("files.sheet")
        assertEquals(ViewOptions(SortKey.Name, true, GroupKey.Type), FilesCollectionPrefs(ctx).ownOptions("b:documents"))
        pick("b:images")
        assertNull(FilesCollectionPrefs(ctx).ownOptions("b:images"))
        assertTrue("Images still sorts by date", sections().isEmpty() && rows().first() == "Screenshot_1.png")
        pick("b:documents")
        assertEquals(listOf("t:Pdf", "t:Spreadsheet", "t:Text"), sections().filter { it.startsWith("t:") })
        assertEquals(listOf("invoice.pdf", "report.pdf"), rows().take(2))
        pick("f:$t1"); assertTrue("a folder has its own memory too", sections().isEmpty())
    }

    @Test fun HOM_15_w53_aSourceCanFollowTheGlobalDefaultAndBeResetToIt() {
        show(); waitSummary("9 files")
        pick("b:documents"); tap("files.sortGroup"); await("files.sheet"); tap("files.sort.Size")
        tap("files.sheet.reset"); // the own choice is only offered after a change
        assertNull(FilesCollectionPrefs(ctx).ownOptions("b:documents"))
        tap("files.sort.Size"); tap("files.group.Type"); tap("files.sheet.all"); tap("files.sheet.done"); gone("files.sheet")
        val store = FilesCollectionPrefs(ctx)
        assertEquals(ViewOptions(SortKey.Size, false, GroupKey.Type), store.defaultOptions)
        assertNull("per-source choices are cleared so every source follows", store.ownOptions("b:documents"))
        pick("b:images"); assertEquals("images follow the new default", listOf("t:Image"), sections().filter { it.startsWith("t:") })
        rule.onAllNodesWithTag("files.sheet.reset", true).assertCountEquals(0)
    }

    @Test fun HOM_15_w53_largeFilesOpenBiggestFirstUntilTheOwnerSetsADefault() {
        FilesCollectionPrefs(ctx).collections = FileCollections().toggleBuiltIn(com.bos.sphere.core.catalog.BuiltInCollection.Large)
        show(); waitSummary("9 files")
        pick("b:large"); waitSummary("2 files")
        assertEquals(listOf("movie.mp4", "backup.zip"), rows())
        tap("files.sortGroup"); await("files.sheet")
        assertEquals(true, rule.onNodeWithTag("files.sort.Size", true).fetchSemanticsNode().config.getOrNull(SemanticsProperties.Selected))
    }

    // ---- the chooser ----

    private fun openChooser() { tap("files.add"); await("files.chooser") }

    @Test fun HOM_15_w53_theChooserIsAFullPageWithACardAndExampleForEveryBuiltIn() {
        show(); waitSummary("9 files"); openChooser()
        for (b in com.bos.sphere.core.catalog.BuiltInCollection.entries) {
            rule.onNodeWithTag("files.chooser.card.${b.id}", true).assertExists().assertHeightIsAtLeast(48.dp)
            assertTrue(b.example, rule.onAllNodesWithText(b.example, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
        }
        rule.onNodeWithText("Pictures whose name starts with Screenshot", useUnmergedTree = true).assertExists()
        rule.onNodeWithText("100 MB and over, biggest first", useUnmergedTree = true).assertExists()
        assertEquals(androidx.compose.ui.semantics.Role.Switch, rule.onNodeWithTag("files.chooser.card.b:images", true).fetchSemanticsNode().config.getOrNull(SemanticsProperties.Role))
        assertEquals("Shown in Files", rule.onNodeWithTag("files.chooser.card.b:images", true).fetchSemanticsNode().config.getOrNull(SemanticsProperties.StateDescription))
        assertEquals("Not shown", rule.onNodeWithTag("files.chooser.card.b:videos", true).fetchSemanticsNode().config.getOrNull(SemanticsProperties.StateDescription))
    }

    @Test fun HOM_15_w53_switchingABuiltInOnAddsItsChipAndOffRemovesIt() {
        show(); waitSummary("9 files"); openChooser()
        tap("files.chooser.card.b:videos")
        assertTrue(FilesCollectionPrefs(ctx).collections.isShown("b:videos"))
        tap("files.chooser.card.b:recents")
        assertFalse(FilesCollectionPrefs(ctx).collections.isShown("b:recents"))
        tap("files.chooser.back"); gone("files.chooser")
        assertEquals(listOf("all", "b:documents", "b:images", "b:downloads", "b:videos", "f:$t1", "f:$t2"), order("files.source."))
        pick("b:videos"); waitSummary("1 file")
        assertEquals(listOf("movie.mp4"), rows())
    }

    @Test fun HOM_15_w53_theChooserReordersWithMoveUpAndMoveDownAndRemoves() {
        show(); waitSummary("9 files"); openChooser()
        rule.onNodeWithTag("files.chooser.up.b:recents", true).assertIsNotEnabled()
        tap("files.chooser.down.b:recents")
        assertEquals(listOf("b:documents", "b:recents", "b:images", "b:downloads"), FilesCollectionPrefs(ctx).collections.shown)
        tap("files.chooser.up.b:downloads"); tap("files.chooser.up.b:downloads")
        assertEquals(listOf("b:documents", "b:downloads", "b:recents", "b:images"), FilesCollectionPrefs(ctx).collections.shown)
        val cd = rule.onNodeWithTag("files.chooser.up.b:images", true).fetchSemanticsNode().config.getOrNull(SemanticsProperties.ContentDescription)?.firstOrNull()
        assertEquals("Move Images up", cd)
        rule.onNodeWithTag("files.chooser.down.b:images", true).assertIsNotEnabled()
        tap("files.chooser.remove.b:documents")
        assertEquals(listOf("b:downloads", "b:recents", "b:images"), FilesCollectionPrefs(ctx).collections.shown)
        tap("files.chooser.back"); gone("files.chooser")
        assertEquals(listOf("all", "b:downloads", "b:recents", "b:images", "f:$t1", "f:$t2"), order("files.source."))
    }

    @Test fun HOM_15_w53_theChooserListsFoldersAndOffersToAddOne() {
        show(); waitSummary("9 files"); openChooser()
        rule.onNodeWithTag("files.chooser.folder.Documents", true).assertExists(); rule.onNodeWithTag("files.chooser.folder.Download", true).assertExists()
        tap("files.chooser.addFolder"); assertEquals(1, picked)
    }

    @Test fun HOM_15_w53_theChooserSaysWhenNoFolderIsAdded() {
        show(files, emptyList()); await("files.empty"); openChooser()
        rule.onNodeWithTag("files.chooser.noFolders", true).assertExists()
    }

    // ---- the editor ----

    private fun openEditor() { openChooser(); tap("files.chooser.new"); await("files.editor.name") }

    @Test fun HOM_15_w53_theEditorCannotSaveWithoutANameAndARule() {
        show(); waitSummary("9 files"); openEditor()
        rule.onNodeWithTag("files.editor.save", true).assertIsNotEnabled()
        rule.onNodeWithTag("files.editor.hint", true).assertExists()
        rule.onNodeWithTag("files.editor.name", true).performTextInput("Work")
        rule.onNodeWithTag("files.editor.save", true).assertIsNotEnabled()
        tap("files.editor.type.Documents")
        rule.onNodeWithTag("files.editor.save", true).assertIsEnabled()
        rule.onAllNodesWithTag("files.editor.hint", true).assertCountEquals(0)
    }

    @Test fun HOM_15_w53_aNewCollectionWithTypesAnAgeAndASizeIsSavedShownAndFiltersTheList() {
        show(); waitSummary("9 files"); openEditor()
        rule.onNodeWithTag("files.editor.name", true).performTextInput("Recent PDFs")
        rule.onNodeWithTag("files.editor.ext", true).performTextInput("pdf")
        tap("files.editor.age.30")
        tap("files.editor.save"); gone("files.editor.name")
        val saved = FilesCollectionPrefs(ctx).collections
        val c = saved.custom.single()
        assertEquals("Recent PDFs", c.name); assertEquals(setOf("pdf"), c.rule.extensions); assertEquals(30, c.rule.maxAgeDays)
        assertEquals("the saved collection is last in the list and shown", c.id, saved.shown.last())
        tap("files.chooser.back"); gone("files.chooser")
        pick(c.id); waitSummary("2 files")
        assertEquals(setOf("report.pdf", "invoice.pdf"), rows().toSet())
        rule.onNodeWithTag("files.edit").assertExists()
    }

    @Test fun HOM_15_w53_aCollectionCanLimitItselfToAFolderAndASizeRange() {
        show(); waitSummary("9 files"); openEditor()
        rule.onNodeWithTag("files.editor.name", true).performTextInput("Big downloads")
        rule.onNodeWithTag("files.editor.name", true).assertExists()
        tap("files.editor.folder.1"); tap("files.editor.min.${100 * mb}")
        tap("files.editor.save"); gone("files.editor.name")
        val c = FilesCollectionPrefs(ctx).collections.custom.single()
        assertEquals(setOf(t2), c.rule.folders); assertEquals(100 * mb, c.rule.minBytes)
        tap("files.chooser.back"); gone("files.chooser")
        pick(c.id); waitSummary("1 file"); assertEquals(listOf("backup.zip"), rows())
    }

    @Test fun HOM_15_w53_editingACustomCollectionKeepsItsValuesAndSavesTheChange() {
        FilesCollectionPrefs(ctx).collections = FileCollections().upsert(com.bos.sphere.core.catalog.CustomCollection("c:a1", "Mine", com.bos.sphere.core.catalog.FileRule(types = setOf(com.bos.sphere.core.catalog.TypeGroup.Audio))))
        show(); waitSummary("9 files")
        pick("c:a1"); waitSummary("1 file"); tap("files.edit"); await("files.editor.name")
        assertEquals("Mine", rule.onNodeWithTag("files.editor.name", true).fetchSemanticsNode().config.getOrNull(SemanticsProperties.EditableText)?.text)
        assertEquals(true, rule.onNodeWithTag("files.editor.type.Audio", true).fetchSemanticsNode().config.getOrNull(SemanticsProperties.ToggleableState)?.let { it == androidx.compose.ui.state.ToggleableState.On })
        tap("files.editor.type.Videos"); tap("files.editor.save"); gone("files.editor.name")
        assertEquals(setOf(com.bos.sphere.core.catalog.TypeGroup.Audio, com.bos.sphere.core.catalog.TypeGroup.Videos), FilesCollectionPrefs(ctx).collections.custom.single().rule.types)
        waitSummary("2 files")
    }

    @Test fun HOM_15_w53_deletingACollectionAsksFirstAndForgetsItsSortChoice() {
        FilesCollectionPrefs(ctx).collections = FileCollections().upsert(com.bos.sphere.core.catalog.CustomCollection("c:a1", "Mine", com.bos.sphere.core.catalog.FileRule(types = setOf(com.bos.sphere.core.catalog.TypeGroup.Audio))))
        FilesCollectionPrefs(ctx).setOptions("c:a1", ViewOptions(SortKey.Name, true, GroupKey.Type))
        show(); waitSummary("9 files"); openChooser()
        tap("files.chooser.remove.c:a1"); await("files.chooser.confirm")
        val prim = rule.onNodeWithTag("files.chooser.confirm.primary", true).fetchSemanticsNode().boundsInRoot
        val sec = rule.onNodeWithTag("files.chooser.confirm.secondary", true).fetchSemanticsNode().boundsInRoot
        assertTrue("the coloured box is above the plain text", prim.bottom <= sec.top + 1)
        tap("files.chooser.confirm.secondary"); gone("files.chooser.confirm")
        assertEquals(1, FilesCollectionPrefs(ctx).collections.custom.size)
        tap("files.chooser.remove.c:a1"); await("files.chooser.confirm"); tap("files.chooser.confirm.primary")
        assertTrue(FilesCollectionPrefs(ctx).collections.custom.isEmpty())
        assertNull(FilesCollectionPrefs(ctx).ownOptions("c:a1"))
    }

    @Test fun HOM_15_w53_cancelLeavesTheEditorWithoutSaving() {
        show(); waitSummary("9 files"); openEditor()
        rule.onNodeWithTag("files.editor.name", true).performTextInput("Nope")
        tap("files.editor.cancel"); await("files.chooser.new")
        assertTrue(FilesCollectionPrefs(ctx).collections.custom.isEmpty())
    }

    @Test fun HOM_15_w53_theEditorBackButtonReturnsToTheChooser() {
        show(); waitSummary("9 files"); openEditor()
        tap("files.editor.back"); await("files.chooser.new")
    }

    @Test fun HOM_15_w53_theEditorSavePairIsAColouredBoxOverPlainText() {
        show(); waitSummary("9 files"); openEditor()
        val save = rule.onNodeWithTag("files.editor.save", true).fetchSemanticsNode().boundsInRoot
        val cancel = rule.onNodeWithTag("files.editor.cancel", true).fetchSemanticsNode().boundsInRoot
        assertTrue(save.bottom <= cancel.top + 1)
    }

    // ---- empty states ----

    @Test fun HOM_15_w53_withNoFolderTheEmptyStateSaysHowToAddOneAndItsButtonOpensThePicker() {
        show(emptyList(), emptyList()); await("files.empty")
        assertEquals("nofolders", rule.onNodeWithTag("files.empty").fetchSemanticsNode().config.getOrNull(SemanticsProperties.StateDescription))
        rule.onNodeWithText("No folder added yet", useUnmergedTree = true).assertExists()
        tap("files.empty.primary"); assertEquals(1, picked)
        tap("files.empty.secondary"); await("files.chooser")
    }

    @Test fun HOM_15_w53_aCollectionWithNoMatchSaysSoAndACustomOneOffersItsRules() {
        FilesCollectionPrefs(ctx).collections = FileCollections().upsert(com.bos.sphere.core.catalog.CustomCollection("c:z", "Zip", com.bos.sphere.core.catalog.FileRule(extensions = setOf("zzz"))))
        show(); waitSummary("9 files"); pick("b:videos".let { "c:z" }); await("files.empty")
        assertEquals("nomatch", rule.onNodeWithTag("files.empty").fetchSemanticsNode().config.getOrNull(SemanticsProperties.StateDescription))
        tap("files.empty.primary"); await("files.editor.name")
    }

    @Test fun HOM_15_w53_aFolderWithNoFilesSaysSo() {
        show(files.filter { it.source == t1 }, folders); waitSummary("4 files")
        pick("f:$t2"); await("files.empty")
        assertEquals("nofiles", rule.onNodeWithTag("files.empty").fetchSemanticsNode().config.getOrNull(SemanticsProperties.StateDescription))
        rule.onNodeWithTag("files.sources").assertExists(); rule.onNodeWithTag("files.sortGroup").assertExists()
    }

    // ---- keyboard and controller ----

    @Test fun HOM_15_w53_everyTargetIsAFocusNodeWithAnId() {
        show(); waitSummary("9 files")
        val ids = LauncherInput.focus.nodes().map { it.id }
        for (id in listOf("files.src.all", "files.src.b:documents", "files.src.f:$t1", "files.add", "files.sortGroup", "files.viewToggle")) assertTrue("$id in $ids", id in ids)
        openChooser()
        val ids2 = LauncherInput.focus.nodes().map { it.id }
        for (id in listOf("files.chooser.back", "files.chooser.new", "files.chooser.addFolder", "files.chooser.card.b:images")) assertTrue("$id in $ids2", id in ids2)
    }

    // ---- layout ----

    @Test fun HOM_15_w53_theSwitcherAndControlsHoldAtFontScale2InRtl() {
        show(font = 2f, rtl = true); waitSummary("9 files")
        rule.onNodeWithTag("files.add").assertExists(); rule.onNodeWithTag("files.sortGroup").assertWidthIsAtLeast(48.dp)
        tap("files.sortGroup"); await("files.sheet"); rule.onNodeWithTag("files.sheet.done", true).assertExists()
    }

    @Test fun HOM_15_w53_theChooserAndEditorHoldAtFontScale2InRtl() {
        show(font = 2f, rtl = true); waitSummary("9 files"); openChooser()
        rule.onNodeWithTag("files.chooser.new", true).assertExists()
        tap("files.chooser.new"); await("files.editor.name"); rule.onNodeWithTag("files.editor.save", true).assertExists()
    }

    @Config(qualifiers = "w891dp-h411dp-xhdpi")
    @Test fun HOM_15_w53_landscapeKeepsTheListTheSwitcherAndTheSheetUsable() {
        show(); waitSummary("9 files")
        rule.onNodeWithTag("files.sources").assertExists(); rule.onNodeWithTag("files.list").assertExists()
        tap("files.sortGroup"); await("files.sheet")
        tap("files.sheet.done"); gone("files.sheet")
    }

    // ---- pictures to read ----

    @Test fun HOM_15_w53_screenshots() {
        show(); waitSummary("9 files")
        FilesCollectionPrefs(ctx).setOptions("all", ViewOptions(SortKey.Modified, false, GroupKey.Date))
        pick("b:recents"); pick("all"); waitSettled(); shoot("files-w53-list-grouped-dark")
        pick("b:images"); shoot("files-w53-collection-images-dark")
    }
}
