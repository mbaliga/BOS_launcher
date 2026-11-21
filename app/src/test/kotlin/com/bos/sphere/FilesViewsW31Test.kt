package com.bos.sphere

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AColor
import android.graphics.Paint
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.test.swipeUp
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.bos.sphere.core.catalog.FileKind
import com.bos.sphere.core.catalog.FilesViewMode
import com.bos.sphere.core.catalog.HomePanelPrefs
import com.bos.sphere.core.catalog.PinnedFolder
import com.bos.sphere.core.catalog.RecentFile
import com.bos.sphere.core.design.HyleColors
import com.bos.sphere.ui.HubView
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
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

/**
 * Wave 31 (HOM-15, filesViews): the Files tab as a list or a grid of previews, the toggle, the right tile for each kind, the failure fallback,
 * the placeholder-to-loaded step and the long-press preview. Previews come from a fake [FileThumbs] (no real media is read), so these tests prove
 * the screen's behaviour; they do not prove the platform's thumbnail providers (that is a device check).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi")
class FilesViewsW31Test {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val ctx get() = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val outDir = File(System.getProperty("user.dir"), "build/screenshots").apply { mkdirs() }
    private val now = 1_000_000_000_000L
    private val day = 86_400_000L

    private fun file(name: String, mime: String = "", size: Long = 4300, ageDays: Int = 62) =
        RecentFile("content://x/doc/$name", name, mime, now - ageDays * day, "content://x/tree", size)

    private val files = listOf(
        file("Zyda-2005-computer.pdf", "application/pdf", 2_400_000),
        file("movie_clip.mp4", "video/mp4", 52_000_000),
        file("holiday.jpg", "image/jpeg", 3_100_000),
        file("Stardust- Rule the World.mp3", "audio/mpeg", 6_000_000),
        file("WhatsApp.apk", "application/vnd.android.package-archive", 48_000_000),
        file("budget.xlsx", "", 90_000),
        file("notes.txt", "text/plain", 800),
        file("whatsapp-messenger-2-18-271.apk.temp", "", 1200),
    )
    private val folders = listOf(PinnedFolder("content://x/tree", "downloads"))

    @Before fun setUp() {
        FileThumbCache.cache.clear()
        ctx.getSharedPreferences("home_state", 0).edit().clear().commit()
    }

    private fun bitmap(color: Int): Bitmap = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888).also { b ->
        val c = Canvas(b); c.drawColor(color)
        val p = Paint().apply { this.color = AColor.argb(120, 255, 255, 255) }
        c.drawCircle(30f, 30f, 22f, p); c.drawRect(40f, 55f, 90f, 90f, p)
    }

    private class Fake(val onLoad: suspend (RecentFile, FileKind) -> ThumbData) : FileThumbs {
        val asked = java.util.Collections.synchronizedList(mutableListOf<String>())
        override suspend fun load(file: RecentFile, kind: FileKind, widthPx: Int, heightPx: Int): ThumbData { asked += file.name; return onLoad(file, kind) }
    }

    private fun goodFake() = Fake { _, kind ->
        when (kind) {
            FileKind.Image -> ThumbData(bitmap(0xFF3F8CFF.toInt()))
            FileKind.Video -> ThumbData(bitmap(0xFF8E5CF0.toInt()), 65_000)
            FileKind.Audio -> ThumbData(null) // no embedded art: the tinted tile
            FileKind.Pdf -> ThumbData(bitmap(0xFFFFFFFF.toInt()))
            FileKind.Apk -> ThumbData(bitmap(0xFF3DB06A.toInt()))
            else -> ThumbData(null)
        }
    }

    private var opened: RecentFile? = null
    private class Cfg(val mode: FilesViewMode, val fake: FileThumbs, val list: List<RecentFile>, val font: Float, val rtl: Boolean, val light: Boolean)
    private var cfg: androidx.compose.runtime.MutableState<Cfg>? = null

    /** Composes the panel once per test; later calls swap the configuration in place (Compose allows one setContent per test). */
    private fun show(mode: FilesViewMode, fake: FileThumbs = goodFake(), list: List<RecentFile> = files, font: Float = 1f, rtl: Boolean = false, light: Boolean = false) {
        val c = Cfg(mode, fake, list, font, rtl, light)
        cfg?.let { s -> rule.runOnIdle { s.value = c }; return }
        val state = androidx.compose.runtime.mutableStateOf(c); cfg = state
        rule.setContent {
            val k = state.value
            val d = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(d.density, k.font), LocalLayoutDirection provides if (k.rtl) LayoutDirection.Rtl else LayoutDirection.Ltr, LocalFileThumbs provides k.fake) {
                androidx.compose.runtime.key(k) {
                    Box(Modifier.fillMaxSize().background(if (k.light) Color(0xFFF4F5F8) else HyleColors.Surface).padding(horizontal = 18.dp, vertical = 12.dp)) {
                        FilesPanelContent(
                            folders, k.list, null, k.mode, now,
                            bright = if (k.light) Color(0xFF14161A) else HyleColors.InkBright, ink = if (k.light) Color(0xFF30343B) else HyleColors.Ink, dim = if (k.light) Color(0xFF6A707B) else HyleColors.InkDim, accent = HyleColors.Violet,
                            onToggle = {}, onChoose = {}, onFolderOpen = {}, onFolderLong = {}, onFileOpen = { opened = it },
                        )
                    }
                }
            }
        }
    }

    private fun tag(name: String, suffix: String) = "files.thumb.$name.$suffix"
    private fun shoot(name: String) {
        rule.mainClock.autoAdvance = false; rule.mainClock.advanceTimeBy(800)
        val view = rule.activity.findViewById<android.view.View>(android.R.id.content)
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread { view.draw(Canvas(bmp)) }
        File(outDir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        rule.mainClock.autoAdvance = true
    }

    // ---- the toggle ----

    @Test fun HOM_15_w31_theToggleSwitchesBetweenListAndGridAndPersists() {
        rule.setContent { HomePanelsBody(HubView.Files, emptyList(), emptyMap()) }
        rule.waitForIdle()
        rule.onNodeWithTag("files.list").assertExists()
        rule.onAllNodesWithTag("files.grid").assertCountEquals(0)
        rule.onNodeWithTag("files.viewToggle").performClick(); rule.waitForIdle()
        rule.onNodeWithTag("files.grid").assertExists()
        rule.onAllNodesWithTag("files.list").assertCountEquals(0)
        assertEquals(FilesViewMode.Grid, HomePanelPrefs(ctx).filesView)
        rule.onNodeWithTag("files.viewToggle").performClick(); rule.waitForIdle()
        rule.onNodeWithTag("files.list").assertExists()
        assertEquals(FilesViewMode.List, HomePanelPrefs(ctx).filesView)
    }

    @Test fun HOM_15_w31_theChoiceIsRestoredOnTheNextOpen() {
        HomePanelPrefs(ctx).filesView = FilesViewMode.Grid
        rule.setContent { HomePanelsBody(HubView.Files, emptyList(), emptyMap()) }
        rule.waitForIdle()
        rule.onNodeWithTag("files.grid").assertExists()
    }

    @Test fun HOM_15_w31_theDefaultViewIsTheList() {
        assertEquals(FilesViewMode.List, HomePanelPrefs(ctx).filesView)
    }

    @Test fun HOM_15_w31_theToggleIsA48dpButtonThatNamesItsActionAndAnnouncesTheCurrentView() {
        rule.setContent { HomePanelsBody(HubView.Files, emptyList(), emptyMap()) }
        rule.waitForIdle()
        val t = rule.onNodeWithTag("files.viewToggle")
        t.assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
        var n = t.fetchSemanticsNode().config
        assertEquals("Switch to grid view", n.getOrNull(SemanticsProperties.ContentDescription)?.firstOrNull())
        assertEquals("List view", n.getOrNull(SemanticsProperties.StateDescription))
        t.performClick(); rule.waitForIdle()
        n = rule.onNodeWithTag("files.viewToggle").fetchSemanticsNode().config
        assertEquals("Switch to list view", n.getOrNull(SemanticsProperties.ContentDescription)?.firstOrNull())
        assertEquals("Grid view", n.getOrNull(SemanticsProperties.StateDescription))
    }

    @Test fun HOM_15_w31_theHeaderKeepsFoldersAndChooseAFolder() {
        rule.setContent { HomePanelsBody(HubView.Files, emptyList(), emptyMap()) }
        rule.waitForIdle()
        rule.onNodeWithText("Folders").assertExists(); rule.onNodeWithText("Choose a folder").assertExists(); rule.onNodeWithText("Recent files").assertExists()
    }

    // ---- list rows and grid cells ----

    private fun assertTiles(mode: FilesViewMode, loadedTags: List<String>, tileTags: List<String>) {
        show(mode); rule.waitForIdle()
        for (n in loadedTags) rule.onNodeWithTag(tag(n, "loaded"), true).assertExists()
        for (n in tileTags) rule.onNodeWithTag(tag(n, "tile"), true).assertExists()
    }

    @Test fun HOM_15_w31_listRowsShowARealPreviewOrTheRightTileForEachKind() = assertTiles(
        FilesViewMode.List,
        loadedTags = listOf("Zyda-2005-computer.pdf", "movie_clip.mp4", "holiday.jpg", "WhatsApp.apk"),
        tileTags = listOf("Stardust- Rule the World.mp3", "budget.xlsx", "notes.txt", "whatsapp-messenger-2-18-271.apk.temp"),
    )

    @Test fun HOM_15_w31_gridCellsShowARealPreviewOrTheRightTileForEachKind() = assertTiles(
        FilesViewMode.Grid,
        loadedTags = listOf("Zyda-2005-computer.pdf", "movie_clip.mp4", "holiday.jpg", "WhatsApp.apk"),
        tileTags = listOf("Stardust- Rule the World.mp3", "budget.xlsx", "notes.txt", "whatsapp-messenger-2-18-271.apk.temp"),
    )

    @Test fun HOM_15_w31_documentsAndOtherFilesAreNeverSentToTheLoader() {
        val fake = goodFake(); show(FilesViewMode.List, fake); rule.waitForIdle()
        assertTrue("only previewable kinds are loaded: ${fake.asked}", fake.asked.none { it in setOf("budget.xlsx", "notes.txt", "whatsapp-messenger-2-18-271.apk.temp") })
        assertTrue(fake.asked.containsAll(listOf("holiday.jpg", "movie_clip.mp4", "Zyda-2005-computer.pdf", "WhatsApp.apk", "Stardust- Rule the World.mp3")))
    }

    @Test fun HOM_15_w31_listRowsCarryNameAgeSizeAndKind() {
        show(FilesViewMode.List); rule.waitForIdle()
        rule.onNodeWithText("62 days ago · 2.3 MB · PDF document").assertExists()
        rule.onNodeWithText("62 days ago · 800 B · Text").assertExists()
        rule.onNodeWithTag("files.row.holiday.jpg").assertHeightIsAtLeast(48.dp)
    }

    @Test fun HOM_15_w31_gridCellsCarryNameAndDateAndSize() {
        show(FilesViewMode.Grid); rule.waitForIdle()
        rule.onNodeWithText("Zyda-2005-computer.pdf").assertExists()
        rule.onNodeWithText("62 days ago · 2.3 MB").assertExists()
    }

    @Test fun HOM_15_w31_aVideoShowsItsDuration() {
        show(FilesViewMode.Grid); rule.waitForIdle()
        rule.onNodeWithTag(tag("movie_clip.mp4", "duration"), true).assertExists()
        rule.onNodeWithText("1:05", useUnmergedTree = true).assertExists()
    }

    @Test fun HOM_15_w31_aFolderShowsAFolderTileAndKeepsItsPinnedLabel() {
        show(FilesViewMode.List); rule.waitForIdle()
        rule.onNodeWithTag("files.folder.downloads").assertExists()
        rule.onNodeWithText("Pinned folder").assertExists()
    }

    @Test fun HOM_15_w31_gridFoldersAreCellsToo() {
        show(FilesViewMode.Grid); rule.waitForIdle()
        rule.onNodeWithTag("files.folder.downloads").assertExists()
    }

    @Test fun HOM_15_w31_tappingAFileOpensIt() {
        show(FilesViewMode.List); rule.waitForIdle()
        rule.onNodeWithTag("files.row.holiday.jpg").performClick(); rule.waitForIdle()
        assertEquals("holiday.jpg", opened?.name)
    }

    // ---- failure and loading ----

    @Test fun HOM_15_w31_aFailedOrThrowingLoadFallsBackToTheTypeTileWithoutCrashing() {
        val boom = Fake { f, _ -> if (f.name == "holiday.jpg") throw IllegalStateException("corrupt") else ThumbData(null) }
        show(FilesViewMode.List, boom); rule.waitForIdle()
        rule.onNodeWithTag(tag("holiday.jpg", "tile"), true).assertExists()
        rule.onAllNodesWithTag(tag("holiday.jpg", "loaded"), true).assertCountEquals(0)
        rule.onNodeWithTag(tag("movie_clip.mp4", "tile"), true).assertExists()
    }

    @Test fun HOM_15_w31_thePlaceholderShowsWhileLoadingThenThePreviewFadesIn() {
        val gate = CompletableDeferred<Unit>()
        val slow = Fake { _, _ -> gate.await(); ThumbData(bitmap(0xFF3F8CFF.toInt())) }
        show(FilesViewMode.Grid, slow, listOf(file("holiday.jpg", "image/jpeg"))); rule.waitForIdle()
        rule.onNodeWithTag(tag("holiday.jpg", "placeholder"), true).assertExists()
        rule.onAllNodesWithTag(tag("holiday.jpg", "loaded"), true).assertCountEquals(0)
        rule.onAllNodesWithTag(tag("holiday.jpg", "tile"), true).assertCountEquals(0)
        gate.complete(Unit); rule.waitForIdle()
        rule.onNodeWithTag(tag("holiday.jpg", "loaded"), true).assertExists()
        rule.onAllNodesWithTag(tag("holiday.jpg", "placeholder"), true).assertCountEquals(0)
    }

    @Test fun HOM_15_w31_aLoadedPreviewIsCachedSoAnotherOpenDoesNotAskTheLoaderAgain() {
        val fake = goodFake()
        show(FilesViewMode.List, fake, listOf(file("holiday.jpg", "image/jpeg"))); rule.waitForIdle()
        val first = fake.asked.size
        assertTrue(first >= 1)
        assertNotNull(FileThumbCache.cache.size.takeIf { it > 0 })
        val again = goodFake()
        show(FilesViewMode.List, again, listOf(file("holiday.jpg", "image/jpeg")), light = true)
        rule.waitForIdle()
        assertEquals("the cached 48 dp size is reused", 0, again.asked.size)
    }

    @Test fun HOM_15_w31_aThumbnailThatLeavesTheListIsCancelled() {
        val started = CompletableDeferred<Unit>(); var cancelled = false
        val hang = Fake { _, _ -> try { started.complete(Unit); kotlinx.coroutines.awaitCancellation() } catch (e: kotlinx.coroutines.CancellationException) { cancelled = true; throw e } }
        val many = (0 until 60).map { file("p$it.jpg", "image/jpeg") }
        show(FilesViewMode.List, hang, many); rule.waitForIdle()
        rule.onNodeWithTag("files.list").performTouchInput { repeat(6) { swipeUp(startY = bottom - 10f, endY = top + 10f) } }
        rule.waitForIdle()
        assertTrue("loads of rows that scrolled away were cancelled", cancelled)
    }

    // ---- quick look ----

    @Test fun HOM_15_w31_aLongPressOpensALargePreviewWithDetailsAndAnOpenButton() {
        show(FilesViewMode.Grid); rule.waitForIdle()
        rule.onNodeWithTag("files.cell.Zyda-2005-computer.pdf").performTouchInput { longClick() }; rule.waitForIdle()
        rule.onNodeWithTag("files.preview").assertExists()
        rule.onNodeWithTag("files.preview.name", true).assertExists()
        val det = rule.onNodeWithTag("files.preview.details", true).fetchSemanticsNode().config.getOrNull(SemanticsProperties.Text)?.joinToString { it.text }.orEmpty()
        assertTrue("kind and size in $det", det.startsWith("PDF document · 2.3 MB"))
        rule.onNodeWithTag("files.preview.open", true).assertHeightIsAtLeast(48.dp).performClick(); rule.waitForIdle()
        assertEquals("Zyda-2005-computer.pdf", opened?.name)
        rule.onAllNodesWithTag("files.preview").assertCountEquals(0)
    }

    @Test fun HOM_15_w31_thePreviewClosesWithCloseAndInTheListToo() {
        show(FilesViewMode.List); rule.waitForIdle()
        rule.onNodeWithTag("files.row.holiday.jpg").performTouchInput { longClick() }; rule.waitForIdle()
        rule.onNodeWithTag("files.preview").assertExists()
        rule.onNodeWithTag("files.preview.close", true).assertHeightIsAtLeast(48.dp).performClick(); rule.waitForIdle()
        rule.onAllNodesWithTag("files.preview").assertCountEquals(0)
        assertEquals("closing does not open the file", null, opened)
    }

    @Test fun HOM_15_w31_rowsAndCellsOfferPreviewToAccessibilityServices() {
        show(FilesViewMode.List); rule.waitForIdle()
        val cfg = rule.onNodeWithTag("files.row.holiday.jpg").fetchSemanticsNode().config
        assertEquals("Preview", cfg.getOrNull(androidx.compose.ui.semantics.SemanticsActions.OnLongClick)?.label)
    }

    // ---- layout robustness ----

    @Test fun HOM_15_w31_bothViewsHoldAtFontScale2AndInRtl() {
        for (mode in FilesViewMode.entries) for (rtl in listOf(false, true)) {
            show(mode, font = 2f, rtl = rtl); rule.waitForIdle()
            rule.onNodeWithText("Choose a folder").assertExists()
            rule.onNodeWithTag("files.viewToggle").assertWidthIsAtLeast(48.dp)
        }
    }

    @Test fun HOM_15_w31_aThousandFilesStayLazy() {
        val fake = goodFake()
        val many = (0 until 2000).map { file("img$it.jpg", "image/jpeg", ageDays = it % 90) }
        show(FilesViewMode.Grid, fake, many); rule.waitForIdle()
        assertTrue("only the visible cells were asked for a preview (${fake.asked.size})", fake.asked.size < 120)
        show(FilesViewMode.List, fake, many); rule.waitForIdle()
        assertTrue(fake.asked.size < 240)
    }

    // ---- pictures to read ----

    @Test fun HOM_15_w31_screenshots() {
        show(FilesViewMode.List); rule.waitForIdle(); shoot("files-w31-list-dark")
        show(FilesViewMode.Grid); rule.waitForIdle(); shoot("files-w31-grid-dark")
        show(FilesViewMode.List, light = true); rule.waitForIdle(); shoot("files-w31-list-light")
        show(FilesViewMode.Grid, light = true); rule.waitForIdle(); shoot("files-w31-grid-light")
        for (f in listOf("list-dark", "grid-dark", "list-light", "grid-light")) assertTrue(File(outDir, "files-w31-$f.png").length() > 1000)
    }
}
