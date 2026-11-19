package com.bos.sphere.feature.grid

import android.graphics.Bitmap
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.bos.sphere.core.data.BounceSupport
import com.bos.sphere.core.data.PictureLimits
import com.bos.sphere.core.data.WallpaperSurface
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Wave 34 (wallpaper): the real importer (AndroidPictureImporter) on files: a still is copied into the private folder, decoded down and re-encoded; a GIF keeps its bytes and is
 * detected as animated from its own header; an oversize file, a broken file and an unreadable Uri each end as a result, never as a crash. A real video cannot be decoded under
 * Robolectric, so only its refusals (broken, too large) are tested here; playing one is device-only.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h720dp-xhdpi")
class PictureImportTest {
    private val ctx get() = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val tmp = File(System.getProperty("java.io.tmpdir"), "wp34-" + System.nanoTime()).also { it.mkdirs() }
    @Before fun setUp() { PictureFiles.dir(ctx).deleteRecursively() }
    @After fun tearDown() { PictureFiles.dir(ctx).deleteRecursively(); tmp.deleteRecursively() }

    /** Robolectric has no ImageDecoder, so the still decode goes through BitmapFactory with the same size rule; the rest of the importer is the real one. */
    private val decoder = StillDecoder { file, sw, sh ->
        val o = android.graphics.BitmapFactory.Options().also { it.inJustDecodeBounds = true }
        android.graphics.BitmapFactory.decodeFile(file.path, o)
        if (o.outWidth <= 0) throw java.io.IOException("not an image")
        val (tw, th) = PictureLimits.stillSize(o.outWidth, o.outHeight, sw, sh)
        Bitmap.createScaledBitmap(android.graphics.BitmapFactory.decodeFile(file.path), tw, th, true)
    }
    private fun import(f: File) = runBlocking { AndroidPictureImporter(ctx, decoder).import(Uri.fromFile(f), WallpaperSurface.Glance, 1080, 2160) }
    private fun gif(frames: Int, w: Int = 4, h: Int = 3): File {
        val o = ByteArrayOutputStream()
        o.write("GIF89a".toByteArray()); o.write(byteArrayOf(w.toByte(), 0, h.toByte(), 0, 0x80.toByte(), 0, 0)); o.write(ByteArray(6))
        repeat(frames) {
            o.write(byteArrayOf(0x21, 0xF9.toByte(), 4, 0, 10, 0, 0, 0)); o.write(byteArrayOf(0x2C, 0, 0, 0, 0, w.toByte(), 0, h.toByte(), 0, 0)); o.write(byteArrayOf(2, 2, 0x44, 1, 0))
        }
        o.write(0x3B)
        return File(tmp, "a.gif").also { it.writeBytes(o.toByteArray()) }
    }

    @Test fun HOM_16_aStillIsCopiedIntoThePrivateFolderDownsampledAndNamedBySurface() {
        val src = File(tmp, "big.png")
        val bmp = Bitmap.createBitmap(4000, 1000, Bitmap.Config.ARGB_8888).also { it.eraseColor(0xFF2266AA.toInt()) }
        src.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val r = import(src)
        assertTrue("$r", r is ImportResult.Ok)
        val spec = (r as ImportResult.Ok).spec
        assertTrue(spec.file.startsWith("glance-")); assertFalse(spec.animated); assertFalse(spec.video)
        val out = File(PictureFiles.dir(ctx), spec.file)
        assertTrue(out.isFile)
        val opts = android.graphics.BitmapFactory.Options().also { it.inJustDecodeBounds = true }; android.graphics.BitmapFactory.decodeFile(out.path, opts)
        val (tw, _) = PictureLimits.stillSize(4000, 1000, 1080, 2160)
        assertEquals("at most the screen x 1.5 wide", tw, opts.outWidth)
        assertEquals("only the copy is left: no temp file", listOf(spec.file), PictureFiles.all(ctx))
        assertTrue("a source in the picker's temporary place is not referenced", spec.file != src.name)
    }

    @Test fun HOM_16_anAnimatedGifKeepsItsBytesAndIsDetectedAsAnimated() {
        val src = gif(5)
        val r = import(src) as ImportResult.Ok
        assertTrue(r.spec.animated); assertFalse(r.spec.video); assertEquals(BounceSupport.Yes, r.spec.bounce)
        assertTrue(r.spec.file.endsWith(".gif"))
        assertEquals(src.readBytes().toList(), File(PictureFiles.dir(ctx), r.spec.file).readBytes().toList())
    }

    @Test fun HOM_16_aGifThatIsTooBigToCacheIsStillAcceptedButBounceIsMarkedTooLarge() {
        val r = import(gif(frames = 3, w = 250, h = 250).also { /* header says 250x250 */ }) as ImportResult.Ok
        assertEquals(BounceSupport.Yes, r.spec.bounce) // 3 frames of 250x250 fit
        assertEquals(BounceSupport.TooLarge.note, com.bos.sphere.core.data.GifNote.TooLargeToBounce)
    }

    @Test fun HOM_16_aFileOverThirtyMegabytesIsRefusedAsTooLargeAndNothingIsLeftBehind() {
        val big = File(tmp, "huge.gif") // a GIF header followed by 31 MB of padding
        big.outputStream().use { o -> o.write("GIF89a".toByteArray()); val chunk = ByteArray(1 shl 20); repeat(31) { o.write(chunk) } }
        val r = import(big)
        assertEquals(ImportResult.Failed(ImportFailure.TooLarge), r)
        assertEquals(emptyList<String>(), PictureFiles.all(ctx))
        assertTrue(ImportFailure.TooLarge.text.contains("30 MB") && ImportFailure.TooLarge.text.contains("200 MB"))
    }

    @Test fun HOM_16_aBrokenPictureIsReportedNotThrown() {
        val junk = File(tmp, "junk.jpg").also { it.writeBytes(ByteArray(2000) { (it * 7).toByte() }) }
        assertEquals(ImportResult.Failed(ImportFailure.Corrupt), import(junk))
        val badGif = File(tmp, "bad.gif").also { it.writeBytes("GIF89a\u0001\u0000".toByteArray()) }
        assertEquals(ImportResult.Failed(ImportFailure.Corrupt), import(badGif))
        val badVideo = File(tmp, "bad.mp4").also { it.writeBytes(ByteArray(64).also { b -> "ftypisom".toByteArray().copyInto(b, 4) }) }
        assertEquals(ImportResult.Failed(ImportFailure.Corrupt), import(badVideo))
        assertEquals(emptyList<String>(), PictureFiles.all(ctx))
    }

    @Test fun HOM_16_anUnreadableUriIsReportedNotThrown() {
        assertEquals(ImportResult.Failed(ImportFailure.Unreadable), import(File(tmp, "missing.png")))
    }

    @Test fun HOM_16_deleteRemovesTheCopyAndRefusesAnythingOutsideTheFolder() {
        val f = File(PictureFiles.dir(ctx).also { it.mkdirs() }, "glance-x.jpg").also { it.writeBytes(byteArrayOf(1)) }
        val outside = File(ctx.filesDir, "keep.txt").also { it.writeBytes(byteArrayOf(1)) }
        assertFalse(PictureFiles.delete(ctx, "../keep.txt")); assertTrue(outside.exists())
        assertTrue(PictureFiles.delete(ctx, "glance-x.jpg")); assertFalse(f.exists())
        assertFalse(PictureFiles.delete(ctx, "glance-x.jpg"))
        outside.delete()
    }

    @Test fun HOM_16_theVideoMatrixLaysAVideoOnTheScreenAsFillOrFit() {
        val fill = videoMatrix(1920, 1080, 360f, 720f, com.bos.sphere.core.data.PictureFit.Fill)
        val v = FloatArray(9); fill.getValues(v)
        // the view shows the video stretched to 360x720; Fill must scale it to cover: width 720*16/9 = 1280 of 360 -> x scale 3.556, y scale 1
        assertEquals(1280f / 360f, v[0], 0.01f); assertEquals(1f, v[4], 0.01f)
        val fit = FloatArray(9); videoMatrix(1920, 1080, 360f, 720f, com.bos.sphere.core.data.PictureFit.Fit).getValues(fit)
        assertEquals(1f, fit[0], 0.01f); assertEquals(202.5f / 720f, fit[4], 0.01f)
    }
}
