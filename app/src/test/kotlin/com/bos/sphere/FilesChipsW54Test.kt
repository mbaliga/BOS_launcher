package com.bos.sphere

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.longClick
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.bos.sphere.core.catalog.BuiltInCollection
import com.bos.sphere.core.catalog.FileCollections
import com.bos.sphere.core.catalog.FileKind
import com.bos.sphere.core.catalog.FilesCollectionPrefs
import com.bos.sphere.core.catalog.HomePanelPrefs
import com.bos.sphere.core.catalog.PinnedFolder
import com.bos.sphere.core.catalog.RecentFile
import com.bos.sphere.core.design.HyleColors
import org.junit.Assert.assertEquals
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
 * Wave 54 (HOM-15, filesChips): the source switcher's chip row runs edge to edge, fades softly towards an edge that has more behind it, and its chips
 * can be reordered by a long press and drag (and by a screen reader's actions and Alt+arrow). Fake index; real touch feel and haptics are device checks.
 */
@OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi")
class FilesChipsW54Test {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val ctx get() = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val outDir = File(System.getProperty("user.dir"), "build/screenshots").apply { mkdirs() }
    private val now = 1_700_000_000_000L
    private val t1 = "content://t/1"; private val t2 = "content://t/2"
    private val folders = listOf(PinnedFolder(t1, "Documents"), PinnedFolder(t2, "Download"))
    private val files = listOf(RecentFile("content://doc/a.pdf", "a.pdf", "", now - 86_400_000L, t1, 1000, "Documents"))
    private class NoThumbs : FileThumbs { override suspend fun load(file: RecentFile, kind: FileKind, widthPx: Int, heightPx: Int) = ThumbData(null) }
    private val inset = 18.dp

    @Before fun setUp() { ctx.getSharedPreferences("home_state", 0).edit().clear().commit() }

    private fun show(granted: List<PinnedFolder> = folders, rtl: Boolean = false, shown: List<String>? = null) {
        if (shown != null) FilesCollectionPrefs(ctx).collections = FileCollections(shown = shown)
        val data = FakeFilesData(FilesSnapshot(granted, files))
        rule.setContent {
            CompositionLocalProvider(LocalFilesData provides data, LocalFileThumbs provides NoThumbs(), LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                Box(Modifier.fillMaxSize().background(HyleColors.Surface)) {
                    FilesSection(HomePanelPrefs(ctx), FilesCollectionPrefs(ctx), FilesActions({}, {}, {}), clock = { now }, inset = inset)
                }
            }
        }
        rule.waitUntil(10_000) { rule.onAllNodesWithTag("files.sources", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        rule.waitUntil(10_000) { chipOrder().size > 1 }
        rule.waitForIdle()
    }

    private fun sources() = rule.onNodeWithTag("files.sources", useUnmergedTree = true).fetchSemanticsNode()
    private fun fade() = sources().config.getOrNull(FilesChipFadeKey)
    private fun chipOrder(): List<String> =
        rule.onAllNodesWithTag("files.sources", useUnmergedTree = true).fetchSemanticsNodes().firstOrNull()?.let {
            rule.onAllNodes(androidx.compose.ui.test.SemanticsMatcher("chip") { n -> n.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("files.source.") == true }, useUnmergedTree = true)
                .fetchSemanticsNodes().sortedBy { n -> n.positionInRoot.x }.map { n -> n.config[SemanticsProperties.TestTag].removePrefix("files.source.") }
        }.orEmpty()
    private fun logicalOrder(rtl: Boolean) = chipOrder().let { if (rtl) it.reversed() else it }
    private fun chip(id: String) = rule.onNodeWithTag("files.source.$id", useUnmergedTree = true)
    private fun centreX(id: String) = chip(id).fetchSemanticsNode().fullBounds().center.x
    private fun scrollEnd() { rule.onNodeWithTag("files.sources", useUnmergedTree = true).performTouchInput { swipeLeft(); swipeLeft(); swipeLeft() }; rule.waitForIdle() }
    private fun scrollToStart() { rule.onNodeWithTag("files.sources", useUnmergedTree = true).performTouchInput { swipeRight(); swipeRight(); swipeRight() }; rule.waitForIdle() }

    private fun pill(id: String) = rule.onNodeWithTag("files.pill.$id", useUnmergedTree = true).fetchSemanticsNode()
    private fun reveal(id: String, rtl: Boolean = false) = rule.revealChip("files.source.$id", rtl)
    /** Carries [from] until its centre is [past] px beyond [to]'s centre (towards the end of the row). */
    private fun dragOnto(from: String, to: String, rtl: Boolean = false, past: Float = 40f) {
        rule.revealChip("files.source.$from", rtl, and = "files.source.$to")
        val sign = if (centreX(to) > centreX(from)) 1f else -1f
        dragBy(from, centreX(to) - centreX(from) + sign * past)
    }
    private fun dragBy(id: String, dx: Float, holdMs: Long = 0) {
        chip(id).performTouchInput {
            down(center); advanceEventTime(viewConfiguration.longPressTimeoutMillis + 200)
            moveBy(Offset(dx / 2, 0f)); moveBy(Offset(dx / 2, 0f))
            if (holdMs > 0) advanceEventTime(holdMs)
            up()
        }
        rule.waitForIdle()
    }

    private val shownNow get() = FilesCollectionPrefs(ctx).collections.shown

    // ---- edge to edge
    @Test fun HOM_15_w54_theRowRunsUnderTheWholePageWidthAndTheFirstChipKeepsTheInset() {
        show()
        val root = rule.onNodeWithTag("files.sources", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val page = rule.activity.findViewById<android.view.View>(android.R.id.content).width.toFloat()
        assertEquals(0f, root.left, 0.5f); assertEquals(page, root.right, 0.5f)
        val d = rule.activity.resources.displayMetrics.density
        // The visible pill starts 18 dp in (the chip's own box is the same as its pill horizontally).
        assertEquals(18f * d, chip("all").fetchSemanticsNode().boundsInRoot.left, 1f)
    }

    @Test fun HOM_15_w54_theAddTileStaysAtTheEndWhileTheRowScrollsAndKeepsTheInset() {
        show()
        val d = rule.activity.resources.displayMetrics.density
        val page = rule.activity.findViewById<android.view.View>(android.R.id.content).width.toFloat()
        val before = rule.onNodeWithTag("files.add", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertEquals(page - 18f * d, before.right, 1f)
        scrollEnd()
        val after = rule.onNodeWithTag("files.add", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertEquals(before, after)
    }

    @Test fun HOM_15_w54_theLastChipRestsBesideTheAddTileAndEarlierOnesScrollUnderTheEdge() {
        show()
        scrollEnd()
        val last = chip(chipOrder().last()).fetchSemanticsNode().boundsInRoot
        val add = rule.onNodeWithTag("files.add", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue("the last chip ${last} stops before the Add tile $add", last.right <= add.left)
        // After scrolling, the first chip has gone under the left edge (partly or wholly outside the page).
        assertTrue(chip("all").fetchSemanticsNode().fullBounds().left < 18f)
    }

    @Test fun HOM_15_w54_rtlMirrorsTheRowAndTheAddTile() {
        show(rtl = true)
        val page = rule.activity.findViewById<android.view.View>(android.R.id.content).width.toFloat()
        val d = rule.activity.resources.displayMetrics.density
        assertEquals(page - 18f * d, chip("all").fetchSemanticsNode().boundsInRoot.right, 1f)
        assertEquals(18f * d, rule.onNodeWithTag("files.add", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.left, 1f)
        assertEquals("end", fade())
    }

    // ---- fade
    @Test fun HOM_15_w54_noFadeAtRestWhenEveryChipFits() {
        show(granted = emptyList(), shown = listOf("b:documents"))
        assertEquals("none", fade())
    }

    @Test fun HOM_15_w54_onlyTheEndFadesAtRestWhenMoreIsHidden() { show(); assertEquals("end", fade()) }

    @Test fun HOM_15_w54_theStartFadesOnlyAfterScrollingAndTheEndStopsAtTheLastChip() {
        show()
        rule.onNodeWithTag("files.sources", useUnmergedTree = true).performTouchInput { down(Offset(width / 2f, centerY)); moveBy(Offset(-60f, 0f)); moveBy(Offset(-20f, 0f)); up() }
        rule.waitForIdle()
        assertEquals("both", fade())
        scrollEnd()
        assertEquals("start", fade())
        scrollToStart()
        assertEquals("end", fade())
    }

    // ---- reorder by long press and drag
    @Test fun HOM_15_w54_longPressAndDragMovesACollectionChipAndThePlaceIsKept() {
        show()
        val before = chipOrder(); assertEquals(listOf("all", "b:recents", "b:documents", "b:images", "b:downloads"), before.take(5))
        dragOnto("b:documents", "b:images")
        assertEquals(listOf("b:recents", "b:images", "b:documents", "b:downloads"), shownNow)
        assertEquals(listOf("all", "b:recents", "b:images", "b:documents", "b:downloads"), chipOrder().take(5))
        assertEquals("all", FilesCollectionPrefs(ctx).source)
    }

    @Test fun HOM_15_w54_dragLeftMovesAChipEarlier() {
        show()
        dragOnto("b:downloads", "b:documents", past = 90f)
        assertEquals(listOf("b:recents", "b:downloads", "b:documents", "b:images"), shownNow)
        assertEquals("all", chipOrder().first())
    }

    @Test fun HOM_15_w54_aFolderChipMovesAmongFoldersAndIsStored() {
        show()
        val a = "f:$t1"; val b = "f:$t2"
        dragOnto(b, a)
        assertEquals(listOf(t2, t1), FilesCollectionPrefs(ctx).folderOrder)
        assertEquals(listOf(b, a), chipOrder().filter { it.startsWith("f:") })
        // A folder never jumps in front of the collections.
        reveal(b); dragBy(b, -(centreX(b) - 20f))
        assertEquals("all", chipOrder().first()); assertTrue(chipOrder().indexOf(b) > chipOrder().indexOf("b:downloads"))
    }

    @Test fun HOM_15_w54_allIsFixedAndCannotBePickedUp() {
        show()
        dragBy("all", 200f)
        assertEquals("all", chipOrder().first())
        assertEquals(FileCollections().shown, shownNow)
    }

    @Test fun HOM_15_w54_aTapStillSelectsASourceAndADragDoesNot() {
        show()
        reveal("b:documents"); chip("b:documents").performClick(); rule.waitForIdle()
        assertEquals("b:documents", FilesCollectionPrefs(ctx).source)
        dragOnto("b:recents", "b:documents")
        assertEquals("b:documents", FilesCollectionPrefs(ctx).source)
        assertEquals(listOf("b:documents", "b:recents", "b:images", "b:downloads"), shownNow)
    }

    @Test fun HOM_15_w54_aShortSwipeStillScrollsTheRowInsteadOfDragging() {
        show()
        rule.onNodeWithTag("files.sources", useUnmergedTree = true).performTouchInput { down(Offset(width / 2f, centerY)); moveBy(Offset(-80f, 0f)); up() }
        rule.waitForIdle()
        assertEquals(FileCollections().shown, shownNow)
        assertTrue(fade() == "both")
    }

    @Test fun HOM_15_w54_holdingAChipAtTheEdgeScrollsTheRowUnderIt() {
        show()
        assertEquals("end", fade())
        val w = rule.onNodeWithTag("files.sources", useUnmergedTree = true).fetchSemanticsNode().size.width
        reveal("b:images")
        dragBy("b:images", w - centreX("b:images") - 4f, holdMs = 700)
        assertTrue("the row scrolled while the chip was held at the right edge: ${fade()}", fade() == "both" || fade() == "start")
    }

    @Test fun HOM_15_w54_rtlDragTowardsTheLeftMovesAChipLaterAndDragRightMovesItEarlier() {
        show(rtl = true)
        dragOnto("b:documents", "b:images", rtl = true)
        assertEquals(listOf("b:recents", "b:images", "b:documents", "b:downloads"), shownNow)
        dragOnto("b:downloads", "b:documents", rtl = true, past = 90f)
        assertEquals(listOf("b:recents", "b:images", "b:downloads", "b:documents"), shownNow)
    }

    // ---- screen reader and keyboard
    private fun actions(id: String) = chip(id).fetchSemanticsNode().config.getOrNull(SemanticsActions.CustomActions).orEmpty().map { it.label }

    @Test fun HOM_15_w54_eachMovableChipOffersMoveEarlierAndMoveLaterWhereThatIsPossible() {
        show()
        assertEquals(emptyList<String>(), actions("all"))
        assertEquals(listOf("Move later"), actions("b:recents"))
        assertEquals(listOf("Move earlier", "Move later"), actions("b:documents"))
        assertEquals(listOf("Move earlier"), actions("b:downloads"))
        scrollEnd()
        assertEquals(listOf("Move later"), actions("f:$t1")); assertEquals(listOf("Move earlier"), actions("f:$t2"))
        assertEquals(null, rule.onNodeWithTag("files.add", useUnmergedTree = true).fetchSemanticsNode().config.getOrNull(SemanticsActions.CustomActions))
    }

    @Test fun HOM_15_w54_theMoveActionsReorderAndPersist() {
        show()
        val later = chip("b:recents").fetchSemanticsNode().config[SemanticsActions.CustomActions].first { it.label == "Move later" }
        rule.runOnUiThread { assertTrue(later.action()) }; rule.waitForIdle()
        assertEquals(listOf("b:documents", "b:recents", "b:images", "b:downloads"), shownNow)
        val earlier = chip("b:images").fetchSemanticsNode().config[SemanticsActions.CustomActions].first { it.label == "Move earlier" }
        rule.runOnUiThread { assertTrue(earlier.action()) }; rule.waitForIdle()
        assertEquals(listOf("b:documents", "b:images", "b:recents", "b:downloads"), shownNow)
    }

    @Test fun HOM_15_w54_theSavedFolderOrderComesBackAndNewFoldersFollow() {
        FilesCollectionPrefs(ctx).folderOrder = listOf(t2, "content://gone")
        show(granted = folders + PinnedFolder("content://t/3", "Camera"))
        scrollEnd()
        assertEquals(listOf("f:$t2", "f:$t1", "f:content://t/3"), chipOrder().filter { it.startsWith("f:") })
    }

    @Test fun HOM_15_w54_theChooserListsFoldersInTheSameOrder() {
        FilesCollectionPrefs(ctx).folderOrder = listOf(t2)
        show()
        rule.onNodeWithTag("files.add", useUnmergedTree = true).performClick(); rule.waitForIdle()
        val ys = listOf("Download", "Documents").map { rule.onNodeWithTag("files.chooser.folder.$it", useUnmergedTree = true).fetchSemanticsNode().positionInRoot.y }
        assertTrue(ys[0] < ys[1])
    }

    /**
     * A picture of the top of the page (the chip row and a little below it). The JVM's software canvas does not keep the row's offscreen layer apart, so
     * the fade's mask punches through to a transparent pixel instead of to the page behind; the picture is therefore laid over the page colour (which
     * is the plain surface behind the row), which is what the real renderer shows.
     */
    private fun shoot(name: String) {
        rule.mainClock.autoAdvance = false; rule.mainClock.advanceTimeBy(400)
        val view = rule.activity.findViewById<android.view.View>(android.R.id.content)
        val raw = Bitmap.createBitmap(view.width, 260, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread { view.draw(Canvas(raw)) }
        val bmp = Bitmap.createBitmap(raw.width, raw.height, Bitmap.Config.ARGB_8888)
        Canvas(bmp).apply { drawColor(HyleColors.Surface.toArgb()); drawBitmap(raw, 0f, 0f, null) }
        File(outDir, "files-w54-$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        rule.mainClock.autoAdvance = true
    }

    @Test fun HOM_15_w54_screenshots() {
        show(); shoot("rest")
        rule.onNodeWithTag("files.sources", useUnmergedTree = true).performTouchInput { down(Offset(width / 2f, centerY)); moveBy(Offset(-100f, 0f)); up() }
        rule.waitForIdle(); shoot("scrolled")
    }

    @Test fun HOM_15_w54_theLiftedChipFollowsTheFingerAndItsNeighboursSlideAside() {
        show(); rule.revealChip("files.source.b:recents", and = "files.source.b:documents")
        val rec0 = pill("b:recents").fullBounds(); val doc0 = pill("b:documents").fullBounds()
        val dx = doc0.center.x - rec0.center.x + 30f
        chip("b:recents").performTouchInput { down(center); advanceEventTime(viewConfiguration.longPressTimeoutMillis + 200); moveBy(Offset(dx / 2, 0f)); moveBy(Offset(dx / 2, 0f)); advanceEventTime(300) }
        rule.waitForIdle()
        val rec1 = pill("b:recents").fullBounds(); val doc1 = pill("b:documents").fullBounds()
        assertEquals("the lifted chip moves with the finger", rec0.center.x + dx, rec1.center.x, 25f)
        val big = pill("b:recents").boundsInRoot.width
        assertTrue("and is drawn larger (scale 1.06): $big vs ${rec0.width}", big > rec0.width * 1.05f && big < rec0.width * 1.07f)
        assertEquals("the neighbour slides back by the lifted chip's width and the gap", doc0.left - (rec0.width + 8f * rule.activity.resources.displayMetrics.density), doc1.left, 2f)
        chip("b:recents").performTouchInput { up() }; rule.waitForIdle()
        assertEquals(listOf("b:documents", "b:recents", "b:images", "b:downloads"), shownNow)
    }

    @Test fun HOM_15_w54_screenshotRtl() { show(rtl = true); shoot("rtl") }
}

private typealias TouchInjectionScopeAlias = androidx.compose.ui.test.TouchInjectionScope
