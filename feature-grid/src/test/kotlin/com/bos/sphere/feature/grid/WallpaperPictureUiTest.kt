package com.bos.sphere.feature.grid

import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.printToLog
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.bos.sphere.core.data.BounceSupport
import com.bos.sphere.core.data.GifMode
import com.bos.sphere.core.data.GifNote
import com.bos.sphere.core.data.LauncherPrefs
import com.bos.sphere.core.data.PictureFit
import com.bos.sphere.core.data.PictureSpec
import com.bos.sphere.core.data.Wallpaper
import com.bos.sphere.core.data.WallpaperKind
import com.bos.sphere.core.data.WallpaperSurface
import com.bos.sphere.core.data.wallpaper
import com.bos.sphere.core.data.withWallpaper
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
 * Wave 34 (wallpaper): the one Wallpaper picker with its five kinds, the Picture row (Photo Picker seam and importer seam with fakes), the animated choices that only an
 * animated file gets (Still / Animated, Play once / Loop, Continuous / Bounce; a video has no Bounce), the power-warning dialog, the standing warning, the fallbacks and the
 * System live wallpaper row. Screenshots: build/screenshots/wallpaper34-*.png (read and judged by hand).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h1600dp-port-mdpi")
class WallpaperPictureUiTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private var prefs by mutableStateOf(LauncherPrefs())
    private val outDir = File(System.getProperty("user.dir"), "build/screenshots").apply { mkdirs() }
    private val ctx get() = ApplicationProvider.getApplicationContext<android.content.Context>()

    private var importResult: ImportResult = ImportResult.Ok(PictureSpec("glance-new.jpg"))
    private val imported = ArrayList<Uri>()
    private var pickerOpened = 0
    private var chooserOpened = 0
    private var chooserWorks = true
    private var systemLabel: String? = "Aurora Waves"
    private var saver: Boolean? = false

    private val picker = object : MediaPickerFactory {
        @Composable override fun create(onResult: (Uri?) -> Unit): () -> Unit = { pickerOpened++; onResult(Uri.parse("content://fake/media/1")) }
    }
    private val importer = PictureImporter { uri, _, _, _ -> imported += uri; importResult }
    private val system = object : SystemWallpaperAccess {
        override fun currentLabel() = systemLabel
        override fun openChooser(): Boolean { chooserOpened++; return chooserWorks }
    }

    @Before fun setUp() {
        com.bos.sphere.core.design.L10n.setLanguage("en")
        com.bos.sphere.core.design.ReducedMotionTesting.force(null)
        PictureFiles.dir(ctx).deleteRecursively()
    }
    @After fun tearDown() { com.bos.sphere.core.design.ReducedMotionTesting.force(null); PictureFiles.dir(ctx).deleteRecursively() }

    private fun show(surface: WallpaperSurface = WallpaperSurface.Glance) {
        rule.setContent {
            CompositionLocalProvider(LocalMediaPicker provides picker, LocalPictureImporter provides importer, LocalSystemWallpaper provides system, LocalBatterySaverOverride provides saver) {
                Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) { BackgroundPane(prefs, surface) { f -> prefs = f(prefs) } }
            }
        }
    }
    private fun click(text: String) { rule.onNodeWithText(text).performScrollTo().performClick(); rule.waitForIdle() }
    private fun tap(text: String) { rule.onNodeWithText(text).performClick(); rule.waitForIdle() }
    private fun exists(text: String) = rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
    private fun androidx.compose.ui.test.junit4.ComposeTestRule.onAllNodesWithText(t: String) = onAllNodes(androidx.compose.ui.test.hasText(t))
    private val glance get() = prefs.wallpaper(WallpaperSurface.Glance)
    private fun with(spec: PictureSpec, kind: WallpaperKind = WallpaperKind.Picture) { prefs = prefs.withWallpaper(WallpaperSurface.Glance, Wallpaper(kind, picture = spec)) }
    private fun shot(name: String) {
        rule.waitForIdle()
        val view = rule.activity.findViewById<View>(android.R.id.content)
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread { view.draw(Canvas(bmp)) }
        File(outDir, "wallpaper34-$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
    private fun file(name: String) = File(PictureFiles.dir(ctx).also { it.mkdirs() }, name).also { it.writeBytes(byteArrayOf(1)) }

    @Test fun HOM_16_thereIsOneWallpaperPickerWithTheFiveKindsAndNoSeparateBackgroundRow() {
        show()
        for (k in listOf("Solid colour", "Gradient", "Signal wallpaper", "System live wallpaper", "Picture")) assertTrue("$k listed", exists(k))
        assertEquals(listOf("solid", "gradient", "live", "system", "picture"), WallpaperKind.entries.map { it.id })
        assertFalse(exists("Background")); assertFalse(exists("Live wallpaper"))
        // the existing signal wallpapers are untouched: still reachable under their kind
        assertTrue(exists("Slot 1"))
        shot("kinds")
    }

    @Test fun HOM_16_theOldSignalWallpaperIsStoredAsLiveSoNothingIsMigrated() {
        assertEquals("live", WallpaperKind.Live.id); assertEquals("Signal wallpaper", WallpaperKind.Live.label)
    }

    @Test fun HOM_16_thePictureRowOpensThePickerThroughTheSeamAndStoresTheCopyThatCameBack() {
        show()
        click("Picture")
        assertEquals(WallpaperKind.Picture, glance.kind)
        click("Choose from gallery")
        assertEquals(1, pickerOpened)
        rule.waitUntil(3000) { prefs.glancePicture.file == "glance-new.jpg" }
        assertEquals(listOf(Uri.parse("content://fake/media/1")), imported)
        assertEquals(GifMode.Still, prefs.glancePicture.gif)
        assertFalse("a plain picture has no animation choices", exists("Animation"))
        assertTrue(exists("Picture fit")); assertTrue(exists("Dim"))
        assertTrue("a button to change it now", exists("Change picture or video"))
        shot("picture-still")
    }

    @Test fun HOM_16_fitAndDimAreChosenAndStored() {
        file("glance-a.jpg"); with(PictureSpec("glance-a.jpg"))
        show()
        assertEquals(PictureFit.Fill, glance.picture.fit)
        click("Fit"); assertEquals(PictureFit.Fit, prefs.glancePicture.fit)
        click("25%"); assertEquals(25, prefs.glancePicture.dim)
        click("50%"); assertEquals(50, prefs.glancePicture.dim)
        click("0%"); assertEquals(0, prefs.glancePicture.dim)
    }

    @Test fun HOM_16_anAnimatedFileGetsStillOrAnimatedThenOnceOrLoopThenContinuousOrBounce() {
        file("glance-a.gif"); with(PictureSpec("glance-a.gif", animated = true))
        show()
        assertTrue(exists("Animation")); assertTrue(exists("Still (first frame)")); assertTrue(exists("Animated"))
        assertFalse("not before Animated is chosen", exists("Play once") || exists("Continuous"))
        click("Animated") // the power warning comes first
        assertTrue(exists("Animated wallpapers use more power"))
        assertEquals("nothing applied yet", GifMode.Still, prefs.glancePicture.gif)
        tap("Use anyway")
        assertEquals(GifMode.Once, prefs.glancePicture.gif); assertTrue(prefs.glancePicture.powerOk)
        assertTrue(exists("Play once")); assertTrue(exists("Loop"))
        assertFalse(exists("Bounce"))
        click("Loop"); assertEquals(GifMode.LoopContinuous, prefs.glancePicture.gif)
        assertTrue(exists("Continuous")); assertTrue(exists("Bounce"))
        click("Bounce"); assertEquals("no second dialog: it was confirmed for this file", GifMode.LoopBounce, prefs.glancePicture.gif)
        click("Continuous"); assertEquals(GifMode.LoopContinuous, prefs.glancePicture.gif)
        click("Play once"); assertEquals(GifMode.Once, prefs.glancePicture.gif)
        click("Still (first frame)"); assertEquals(GifMode.Still, prefs.glancePicture.gif)
        assertFalse("Still hides the animated choices again", exists("Play once"))
        shot("gif-choices")
    }

    @Test fun HOM_16_aStoredModeIsShownSelectedWhenThePaneOpens() {
        file("glance-a.gif"); with(PictureSpec("glance-a.gif", animated = true, gif = GifMode.LoopBounce, powerOk = true))
        show(); assertTrue(exists("Bounce"))
    }

    @Test fun HOM_16_chooseAStillInsteadLeavesTheWallpaperStill() {
        file("glance-a.gif"); with(PictureSpec("glance-a.gif", animated = true))
        show()
        click("Animated"); tap("Choose a still instead")
        assertEquals(GifMode.Still, prefs.glancePicture.gif); assertFalse(prefs.glancePicture.powerOk)
        assertFalse(exists("Animated wallpapers use more power"))
        click("Animated") // asked again: it was never confirmed
        assertTrue(exists("Animated wallpapers use more power"))
        tap("Use anyway")
        assertEquals(GifMode.Once, prefs.glancePicture.gif)
    }

    @Test fun HOM_16_aNewFileIsAskedAgainEvenIfTheOldOneWasConfirmed() {
        file("glance-a.gif"); with(PictureSpec("glance-a.gif", animated = true, gif = GifMode.LoopContinuous, powerOk = true))
        importResult = ImportResult.Ok(PictureSpec("glance-b.gif", animated = true))
        show()
        click("Change picture or video")
        rule.waitUntil(3000) { prefs.glancePicture.file == "glance-b.gif" }
        assertEquals("a new file starts as a still and unconfirmed", GifMode.Still, prefs.glancePicture.gif); assertFalse(prefs.glancePicture.powerOk)
        click("Animated"); assertTrue(exists("Animated wallpapers use more power"))
    }

    @Test fun HOM_16_aVideoOffersOnlyStillPlayOnceAndLoopAndItsCancelButtonKeepsItStill() {
        file("glance-v.mp4"); with(PictureSpec("glance-v.mp4", animated = true, video = true, durationMs = 8000))
        show()
        assertTrue(exists("Video")); assertTrue(exists("Still (first frame)"))
        click("Video")
        assertTrue(exists("Animated wallpapers use more power"))
        assertTrue("a video's dialog says Cancel, not 'a still'", exists("Cancel")); assertFalse(exists("Choose a still instead"))
        tap("Cancel"); assertEquals(GifMode.Still, prefs.glancePicture.gif)
        click("Video"); tap("Use anyway")
        assertEquals(GifMode.Once, prefs.glancePicture.gif)
        click("Loop"); assertEquals(GifMode.LoopContinuous, prefs.glancePicture.gif)
        assertFalse("no Bounce for a video", exists("Bounce")); assertFalse(exists("Loop style"))
        shot("video-choices")
    }

    @Test fun HOM_16_theStandingWarningShowsWhileAnAnimatedWallpaperPlaysAndTheSummaryCarriesIt() {
        file("glance-a.gif"); with(PictureSpec("glance-a.gif", animated = true, gif = GifMode.LoopContinuous, powerOk = true))
        show()
        val line = "Uses more power: this may drain the battery, heat the phone up or make it lag."
        assertTrue("under the kind chips", rule.onAllNodes(androidx.compose.ui.test.hasText(line)).fetchSemanticsNodes().size >= 1)
        assertTrue(wallpaperSummary(glance) { it }.contains("uses more power"))
        assertEquals("Picture: Animated, uses more power", wallpaperSummary(glance) { it })
        assertEquals("Picture: Video, uses more power", wallpaperSummary(Wallpaper(WallpaperKind.Picture, picture = PictureSpec("v.mp4", animated = true, video = true, gif = GifMode.Once, powerOk = true))) { it })
        assertEquals("Picture", wallpaperSummary(Wallpaper(WallpaperKind.Picture, picture = PictureSpec("a.gif", animated = true))) { it })
        shot("standing-warning")
        click("Still (first frame)")
        assertFalse(rule.onAllNodes(androidx.compose.ui.test.hasText(line)).fetchSemanticsNodes().isNotEmpty())
    }

    @Test fun HOM_16_theWarningIsAccessible() {
        file("glance-a.gif"); with(PictureSpec("glance-a.gif", animated = true, gif = GifMode.Once, powerOk = true))
        show()
        rule.onAllNodes(androidx.compose.ui.test.hasContentDescription("Warning: Uses more power: this may drain the battery, heat the phone up or make it lag.")).fetchSemanticsNodes().isNotEmpty().let(::assertTrue)
        // buttons are real buttons at least 48 dp tall
        val h = rule.onNodeWithText("Change picture or video").fetchSemanticsNode().boundsInRoot.height
        assertTrue("height $h px at mdpi", h >= 48f)
    }

    @Test fun HOM_16_aBrokenFileShowsAQuietMessageAndChangesNothing() {
        for (r in ImportFailure.entries) {
            importResult = ImportResult.Failed(r)
            prefs = LauncherPrefs(); imported.clear()
            val before = prefs
            if (r == ImportFailure.entries.first()) show() else rule.runOnUiThread { }
            click("Picture"); click("Choose from gallery")
            rule.waitUntil(3000) { exists(r.text) }
            assertEquals("kind switched but nothing stored for ${r.name}", before.glancePicture, prefs.glancePicture)
        }
        shot("import-error")
    }

    @Test fun HOM_16_replacingThePictureDeletesTheOldCopyAndRemovingDeletesToo() {
        val old = file("glance-old.jpg"); val newer = file("glance-new.jpg")
        with(PictureSpec("glance-old.jpg"))
        show()
        click("Change picture or video")
        rule.waitUntil(3000) { prefs.glancePicture.file == "glance-new.jpg" }
        rule.waitUntil(3000) { !old.exists() }
        assertTrue(newer.exists())
        click("Remove picture")
        rule.waitUntil(3000) { !newer.exists() }
        assertEquals("", prefs.glancePicture.file)
    }

    @Test fun HOM_16_aMissingFileSaysSoAndTheSolidColourIsTheFallback() {
        with(PictureSpec("glance-gone.jpg"))
        show()
        rule.waitUntil(3000) { exists("The picture file is missing, so the solid colour is shown. Choose it again.") }
    }

    @Test fun NFR_14_reducedMotionAndBatterySaverFallBackToTheFirstFrameWithAVisibleNote() {
        file("glance-a.gif"); with(PictureSpec("glance-a.gif", animated = true, gif = GifMode.LoopContinuous, powerOk = true))
        val note = GifNote.MotionOff.text
        saver = true
        show()
        assertTrue("battery saver: $note", exists(note))
    }

    @Test fun NFR_14_reducedMotionAlsoShowsTheNote() {
        file("glance-a.gif"); with(PictureSpec("glance-a.gif", animated = true, gif = GifMode.LoopContinuous, powerOk = true))
        com.bos.sphere.core.design.ReducedMotionTesting.force(com.bos.sphere.core.quality.MotionPrefs(0f, 0f, 0f))
        show()
        assertTrue(exists(GifNote.MotionOff.text))
    }

    @Test fun HOM_16_bounceOnAFileThatCannotRewindSaysSoAndLoopsInstead() {
        file("glance-a.gif"); with(PictureSpec("glance-a.gif", animated = true, gif = GifMode.LoopBounce, powerOk = true, bounce = BounceSupport.TooLarge))
        show()
        assertTrue(exists("Too large to bounce, looping instead"))
        file("glance-b.webp"); with(PictureSpec("glance-b.webp", animated = true, gif = GifMode.LoopBounce, powerOk = true, bounce = BounceSupport.NeedsGif))
        rule.waitForIdle()
        assertTrue(exists("Bounce needs a GIF, looping instead"))
        shot("bounce-fallback")
    }

    @Test fun HOM_16_theSystemLiveWallpaperRowShowsTheActiveOneOpensTheChooserAndKeepsTheDim() {
        show()
        click("System live wallpaper")
        assertEquals(WallpaperKind.System, glance.kind)
        assertTrue(exists("System wallpaper: Aurora Waves"))
        click("Choose live wallpaper"); assertEquals(1, chooserOpened)
        assertFalse(exists("The wallpaper chooser could not be opened."))
        chooserWorks = false; click("Choose live wallpaper")
        assertTrue(exists("The wallpaper chooser could not be opened."))
        click("25%"); assertEquals(25, prefs.glanceSystemDim)
        click("50%"); assertEquals(50, glance.systemDim)
        shot("system")
        systemLabel = null; chooserWorks = true
    }

    @Test fun HOM_16_theSystemRowSaysSoWhenNoLiveWallpaperNameIsAvailable() {
        systemLabel = null
        show(); click("System live wallpaper")
        assertTrue(exists("System wallpaper: not a live wallpaper, or hidden by the system"))
    }

    @Test fun HOM_16_thePagesHaveTheSameFiveKindsWhenTheyHaveTheirOwn() {
        prefs = LauncherPrefs(pagesSameAsGlance = false)
        show(WallpaperSurface.Pages)
        click("Picture"); click("Choose from gallery")
        rule.waitUntil(3000) { prefs.pagesPicture.file == "glance-new.jpg" }
        assertEquals(WallpaperKind.Picture, prefs.pagesWallpaperKind)
        assertEquals("the Glance has no picture", "", prefs.glancePicture.file)
    }
}
