package com.bos.sphere

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.bos.sphere.core.catalog.BuiltInCollection
import com.bos.sphere.core.catalog.CustomCollection
import com.bos.sphere.core.catalog.FileCollections
import com.bos.sphere.core.catalog.FileKind
import com.bos.sphere.core.catalog.FileRule
import com.bos.sphere.core.catalog.FilesCollectionPrefs
import com.bos.sphere.core.catalog.FilesViewMode
import com.bos.sphere.core.catalog.GroupKey
import com.bos.sphere.core.catalog.HomePanelPrefs
import com.bos.sphere.core.catalog.PinnedFolder
import com.bos.sphere.core.catalog.RecentFile
import com.bos.sphere.core.catalog.SortKey
import com.bos.sphere.core.catalog.TypeGroup
import com.bos.sphere.core.catalog.ViewOptions
import com.bos.sphere.core.design.HyleColors
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** W53 pictures of the Files section's pages and sheet. The pages are drawn on the activity (not in their dialog window) so a capture can include them. They are read by eye, not compared. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi")
class FilesCollectionsW53ShotsTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val ctx get() = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val outDir = File(System.getProperty("user.dir"), "build/screenshots").apply { mkdirs() }
    private val now = 1_700_000_000_000L
    private val day = 86_400_000L
    private val t1 = "content://t/1"; private val t2 = "content://t/2"
    private val folders = listOf(PinnedFolder(t1, "Documents"), PinnedFolder(t2, "Download"))
    private val mb = 1024L * 1024
    private fun file(name: String, size: Long, age: Int, folder: String, source: String = t1) = RecentFile("content://doc/$name", name, "", now - age * day, source, size, folder)
    private val files = listOf(
        file("report.pdf", 2 * mb, 0, "Documents"), file("Budget.xlsx", 90_000, 1, "Documents"), file("notes.txt", 800, 40, "Documents"),
        file("holiday.jpg", 3 * mb, 2, "Camera", t2), file("Screenshot_1.png", 400_000, 3, "Download", t2), file("movie.mp4", 700 * mb, 100, "Videos"),
        file("song.mp3", 6 * mb, 8, "Music", t2), file("backup.zip", 150 * mb, 5, "Download", t2), file("invoice.pdf", 300_000, 10, "Download", t2),
        file("slides.pptx", 12 * mb, 0, "Documents"), file("trip.mov", 80 * mb, 1, "Camera", t2), file("cover.png", 700_000, 6, "Camera", t2),
    )
    private class NoThumbs : FileThumbs { override suspend fun load(file: RecentFile, kind: FileKind, widthPx: Int, heightPx: Int) = ThumbData(null) }

    @Before fun setUp() { ctx.getSharedPreferences("home_state", 0).edit().clear().commit() }

    private fun shoot(name: String) {
        rule.mainClock.autoAdvance = false; rule.mainClock.advanceTimeBy(800)
        val view = rule.activity.findViewById<android.view.View>(android.R.id.content)
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread { view.draw(Canvas(bmp)) }
        File(outDir, "files-w53-$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        rule.mainClock.autoAdvance = true
        assertTrue(File(outDir, "files-w53-$name.png").length() > 1000)
    }

    private fun section(options: ViewOptions, grid: Boolean = false, rtl: Boolean = false, select: String = "all", name: String) {
        HomePanelPrefs(ctx).filesView = if (grid) FilesViewMode.Grid else FilesViewMode.List
        FilesCollectionPrefs(ctx).apply { defaultOptions = options; source = select }
        val data = FakeFilesData(FilesSnapshot(folders, files))
        rule.setContent {
            CompositionLocalProvider(LocalFilesData provides data, LocalFileThumbs provides NoThumbs(), LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                Box(Modifier.fillMaxSize().background(HyleColors.Surface).padding(horizontal = 18.dp, vertical = 12.dp)) {
                    FilesSection(HomePanelPrefs(ctx), FilesCollectionPrefs(ctx), FilesActions({}, {}, {}), clock = { now })
                }
            }
        }
        rule.waitUntil(10_000) { rule.onAllNodesWithTag(if (grid) "files.grid" else "files.list").fetchSemanticsNodes().isNotEmpty() }
        rule.waitForIdle(); Thread.sleep(200); rule.waitForIdle()
        shoot(name)
    }

    @Test fun HOM_15_w53_screenshotListGroupedByDate() = section(ViewOptions(SortKey.Modified, false, GroupKey.Date), name = "list-by-date")
    @Test fun HOM_15_w53_screenshotGridGroupedByType() = section(ViewOptions(SortKey.Name, true, GroupKey.Type), grid = true, name = "grid-by-type")
    @Test fun HOM_15_w53_screenshotCollectionDocuments() = section(ViewOptions(SortKey.Size, false, GroupKey.Size), select = "b:documents", name = "documents-by-size")
    @Test fun HOM_15_w53_screenshotRtlGridByFolder() = section(ViewOptions(SortKey.Modified, false, GroupKey.Folder), grid = true, rtl = true, name = "grid-by-folder-rtl")

    @Test fun HOM_15_w53_screenshotEmptyNoFolder() {
        val data = FakeFilesData(FilesSnapshot(emptyList(), emptyList()))
        rule.setContent {
            CompositionLocalProvider(LocalFilesData provides data, LocalFileThumbs provides NoThumbs()) {
                Box(Modifier.fillMaxSize().background(HyleColors.Surface).padding(horizontal = 18.dp, vertical = 12.dp)) { FilesSection(HomePanelPrefs(ctx), FilesCollectionPrefs(ctx), FilesActions({}, {}, {}), clock = { now }) }
            }
        }
        rule.waitUntil(10_000) { rule.onAllNodesWithTag("files.empty").fetchSemanticsNodes().isNotEmpty() }
        rule.waitForIdle(); shoot("empty")
    }

    @Test fun HOM_15_w53_screenshotSortGroupSheet() {
        rule.setContent { Box(Modifier.fillMaxSize().background(HyleColors.Surface)) { SortGroupSheetBody(ViewOptions(SortKey.Modified, false, GroupKey.Date), false, true, HyleColors.Violet, {}, {}, {}, {}) } }
        rule.waitForIdle(); shoot("sheet")
    }

    private fun pages(start: ChooserStart, collections: FileCollections, name: String, rtl: Boolean = false) {
        rule.setContent {
            var editing by remember { mutableStateOf(start) }
            CompositionLocalProvider(LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                FilesChooserPages(collections, folders, HyleColors.Violet, ChooserStart.List, editing, { editing = it }, {}, {}, {})
            }
        }
        rule.waitForIdle(); shoot(name)
    }

    private val custom = FileCollections().toggleBuiltIn(BuiltInCollection.Large).upsert(CustomCollection("c:ab", "Work PDFs", FileRule(types = setOf(TypeGroup.Documents), extensions = setOf("pdf"), nameContains = "invoice", maxAgeDays = 30, minBytes = 1024 * 1024)))
    @Test fun HOM_15_w53_screenshotChooser() = pages(ChooserStart.List, custom, "chooser")
    @Test fun HOM_15_w53_screenshotEditorNew() = pages(ChooserStart.New, FileCollections(), "editor-new")
    @Test fun HOM_15_w53_screenshotEditorEditing() = pages(ChooserStart.Edit("c:ab"), custom, "editor-edit")
    @Test fun HOM_15_w53_screenshotChooserRtl() = pages(ChooserStart.List, custom, "chooser-rtl", rtl = true)
}
