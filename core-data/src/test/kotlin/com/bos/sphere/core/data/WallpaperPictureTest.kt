package com.bos.sphere.core.data

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/**
 * Wave 34 (wallpaper), pure JVM: the playback clock per GIF mode, the policy that decides what really plays, the size limits, the GIF reader, the
 * power-warning rule and the stored settings.
 */
class WallpaperPictureTest {
    private fun walk(mode: GifMode, delays: IntArray, steps: Int, stepMs: Long = 100): List<Int> = (0 until steps).map { GifTimeline(delays, mode).at(it * stepMs).frame }
    private val four = intArrayOf(100, 100, 100, 100)

    @Test fun HOM_16_gifOncePlaysToTheLastFrameAndStopsThere() {
        val tl = GifTimeline(four, GifMode.Once)
        assertEquals(listOf(0, 1, 2, 3, 3, 3), walk(GifMode.Once, four, 6))
        assertFalse(tl.at(399).finished); assertTrue(tl.at(400).finished); assertTrue(tl.at(999_999).finished)
        assertEquals(Long.MAX_VALUE, tl.at(5000).untilNextMs)
    }

    @Test fun HOM_16_gifContinuousWraps() {
        assertEquals(listOf(0, 1, 2, 3, 0, 1, 2, 3, 0), walk(GifMode.LoopContinuous, four, 9))
        assertFalse(GifTimeline(four, GifMode.LoopContinuous).at(123_456).finished)
    }

    @Test fun HOM_16_gifBouncePlaysForwardThenRewindsWithoutRepeatingTheEndFrames() {
        // 0 1 2 3 2 1 | 0 1 2 3 2 1 ...: frames 0 and 3 are each shown once per cycle
        assertEquals(listOf(0, 1, 2, 3, 2, 1, 0, 1, 2, 3, 2, 1, 0), walk(GifMode.LoopBounce, four, 13))
        assertEquals(600L, GifTimeline(four, GifMode.LoopBounce).cycleMs)
        assertEquals(listOf(0, 1, 0, 1), walk(GifMode.LoopBounce, intArrayOf(100, 100), 4))
        assertEquals(listOf(0, 1, 2, 1, 0), walk(GifMode.LoopBounce, intArrayOf(100, 100, 100), 5))
    }

    @Test fun HOM_16_theClockUsesTheRealPerFrameDelays() {
        val d = intArrayOf(50, 200, 400)
        val tl = GifTimeline(d, GifMode.LoopContinuous)
        assertEquals(0, tl.at(49).frame); assertEquals(1, tl.at(50).frame); assertEquals(1, tl.at(249).frame); assertEquals(2, tl.at(250).frame)
        assertEquals(2, tl.at(649).frame); assertEquals(0, tl.at(650).frame)
        assertEquals(1L, tl.at(49).untilNextMs); assertEquals(200L, tl.at(50).untilNextMs)
        // the rewind uses the delay of the frame being shown, so a long frame is long both ways
        val b = GifTimeline(d, GifMode.LoopBounce) // 0 1 2 1 -> 50 200 400 200
        assertEquals(850L, b.cycleMs); assertEquals(1, b.at(650).frame); assertEquals(0, b.at(850).frame)
    }

    @Test fun HOM_16_aZeroOrTinyDelayIsAHundredMillisecondsAsBrowsersDo() {
        assertEquals(100, GifTimeline.normal(0)); assertEquals(100, GifTimeline.normal(10)); assertEquals(20, GifTimeline.normal(20))
        assertEquals(1, GifTimeline(intArrayOf(0, 0), GifMode.LoopContinuous).at(100).frame)
    }

    @Test fun HOM_16_stillAndSingleFrameNeverAdvance() {
        assertTrue(GifTimeline(four, GifMode.Still).at(10_000).let { it.frame == 0 && it.finished })
        assertTrue(GifTimeline(intArrayOf(100), GifMode.LoopBounce).at(10_000).let { it.frame == 0 && it.finished })
        assertEquals(0, GifTimeline(IntArray(0), GifMode.Once).at(5).frame)
    }

    @Test fun NFR_14_policyFallsBackToTheStillWhenMotionIsOffAndNamesWhy() {
        assertEquals(GifPlan(GifMode.Still, GifNote.MotionOff), GifPolicy.plan(GifMode.LoopBounce, true, motionAllowed = false))
        assertEquals(GifPlan(GifMode.LoopBounce, null), GifPolicy.plan(GifMode.LoopBounce, true, motionAllowed = true))
        assertEquals(GifPlan(GifMode.Still, null), GifPolicy.plan(GifMode.LoopBounce, false, motionAllowed = true)) // a still image has nothing to play
        assertEquals(GifPlan(GifMode.Still, null), GifPolicy.plan(GifMode.Still, true, motionAllowed = false)) // chosen still: no note
    }

    @Test fun HOM_16_bounceFallsBackToContinuousWhenTheFileCannotBeRewound() {
        assertEquals(GifPlan(GifMode.LoopContinuous, GifNote.TooLargeToBounce), GifPolicy.plan(GifMode.LoopBounce, true, true, BounceSupport.TooLarge))
        assertEquals(GifPlan(GifMode.LoopContinuous, GifNote.BounceNeedsGif), GifPolicy.plan(GifMode.LoopBounce, true, true, BounceSupport.NeedsGif))
        assertEquals(GifPlan(GifMode.Once, null), GifPolicy.plan(GifMode.Once, true, true, BounceSupport.TooLarge))
        assertEquals("Too large to bounce, looping instead", GifNote.TooLargeToBounce.text)
    }

    @Test fun HOM_16_aVideoHasNoBounce() {
        assertEquals(GifMode.LoopContinuous, PictureSpec(file = "a.mp4", animated = true, video = true, gif = GifMode.LoopBounce).normalised().gif)
        assertEquals(GifMode.LoopBounce, PictureSpec(file = "a.gif", animated = true, gif = GifMode.LoopBounce).normalised().gif)
    }

    @Test fun HOM_16_stillsAreDecodedToTheScreenTimesOnePointFiveAndNeverUpscaled() {
        assertEquals(1620 to 3240, PictureLimits.stillSize(8000, 16000, 1080, 2160))
        assertEquals(500 to 400, PictureLimits.stillSize(500, 400, 1080, 2160))
        assertEquals(1620 to 1215, PictureLimits.stillSize(4000, 3000, 1080, 2160))
    }

    @Test fun HOM_16_theBounceCacheIsBoundedAt48Megabytes() {
        assertEquals(48L * 1024 * 1024, PictureLimits.FRAME_CACHE_BYTES)
        assertEquals(1f, PictureLimits.cacheScale(60, 480, 270, 1080, 2160)!!, 0.001f) // 60 x 0.5 MB
        assertNull(PictureLimits.cacheScale(2000, 1080, 1920, 1080, 2160)) // far too many frames at any scale
        val s = PictureLimits.cacheScale(20, 1080, 1080, 1080, 2160)!! // 20 x 4.6 MB: only fits scaled down
        assertTrue(s < 1f && 20L * (1080 * s).toInt() * (1080 * s).toInt() * 4 <= PictureLimits.FRAME_CACHE_BYTES)
    }

    @Test fun HOM_16_sizeLimitsAreTheOwnersNumbers() {
        assertEquals(30L * 1024 * 1024, PictureLimits.MAX_ANIMATED_BYTES); assertEquals(200L * 1024 * 1024, PictureLimits.MAX_VIDEO_BYTES)
        assertEquals(listOf(0, 25, 50), PictureLimits.DIMS); assertEquals(25, PictureLimits.nearestDim(30)); assertEquals(50, PictureLimits.nearestDim(99))
    }

    @Test fun HOM_16_aStoredFileNameCanNeverEscapeTheWallpaperFolder() {
        for (bad in listOf("../x.jpg", "a/b.jpg", "/etc/passwd", "a b.jpg", "", "x".repeat(200))) assertEquals(bad, "", PictureLimits.safeName(bad))
        assertEquals("glance-k3j2.gif", PictureLimits.safeName("glance-k3j2.gif"))
    }

    private fun gif(delaysCs: List<Int>, w: Int = 4, h: Int = 3): ByteArray {
        val o = ByteArrayOutputStream()
        o.write("GIF89a".toByteArray()); o.write(byteArrayOf(w.toByte(), 0, h.toByte(), 0, 0x80.toByte(), 0, 0)); o.write(ByteArray(6)) // 2-colour global table
        for (d in delaysCs) {
            o.write(byteArrayOf(0x21, 0xF9.toByte(), 4, 0, (d and 0xFF).toByte(), (d shr 8).toByte(), 0, 0))
            o.write(byteArrayOf(0x2C, 0, 0, 0, 0, w.toByte(), 0, h.toByte(), 0, 0)); o.write(byteArrayOf(2, 2, 0x44, 1, 0)) // LZW size, one sub-block, end
        }
        o.write(0x3B)
        return o.toByteArray()
    }

    @Test fun HOM_16_theGifReaderFindsFramesAndTheirDelays() {
        val info = AnimationSniffer.gif(ByteArrayInputStream(gif(listOf(5, 10, 250))))
        assertNotNull(info); assertEquals(3, info!!.frames); assertTrue(info.animated); assertEquals(4, info.width); assertEquals(3, info.height)
        assertArrayEquals(intArrayOf(50, 100, 2500), info.delaysMs)
        assertFalse(AnimationSniffer.gif(ByteArrayInputStream(gif(listOf(5))))!!.animated) // one frame is a still
        assertNull(AnimationSniffer.gif(ByteArrayInputStream("not a gif at all".toByteArray())))
        assertNull(AnimationSniffer.gif(ByteArrayInputStream(ByteArray(0))))
        val cut = gif(listOf(5, 5, 5)).let { it.copyOf(it.size - 12) } // truncated file: the frames read so far are kept
        assertEquals(2, AnimationSniffer.gif(ByteArrayInputStream(cut))!!.frames)
    }

    @Test fun HOM_16_theSnifferTellsGifWebpVideoAndStills() {
        assertEquals(AnimationSniffer.Format.Gif, AnimationSniffer.format("GIF89a....".toByteArray()))
        val webp = ByteArray(32).also { "RIFF".toByteArray().copyInto(it); "WEBP".toByteArray().copyInto(it, 8); "VP8X".toByteArray().copyInto(it, 12); it[20] = 0x12 }
        assertEquals(AnimationSniffer.Format.WebP, AnimationSniffer.format(webp)); assertTrue(AnimationSniffer.webpAnimated(webp))
        webp[20] = 0x10; assertFalse(AnimationSniffer.webpAnimated(webp))
        val mp4 = ByteArray(16).also { "ftypisom".toByteArray().copyInto(it, 4) }
        assertEquals(AnimationSniffer.Format.Video, AnimationSniffer.format(mp4))
        assertEquals(AnimationSniffer.Format.Video, AnimationSniffer.format(byteArrayOf(0x1A, 0x45, 0xDF.toByte(), 0xA3.toByte(), 0, 0)))
        assertEquals(AnimationSniffer.Format.Other, AnimationSniffer.format(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 0, 0, 0, 0, 0, 0, 0, 0)))
    }

    @Test fun HOM_16_theWarningIsAskedWhenAnAnimatedFileIsSwitchedOnAndOnlyUntilConfirmed() {
        val still = PictureSpec(file = "a.gif", animated = true)
        assertFalse(PowerWarning.mustAsk(still)) // a still of an animated file plays nothing
        assertTrue(PowerWarning.mustAsk(still.copy(gif = GifMode.Once)))
        assertTrue(PowerWarning.mustAsk(still.copy(gif = GifMode.LoopBounce)))
        assertFalse(PowerWarning.mustAsk(still.copy(gif = GifMode.LoopContinuous, powerOk = true)))
        assertTrue(PowerWarning.mustAsk(PictureSpec(file = "v.mp4", animated = true, video = true, gif = GifMode.LoopContinuous)))
        assertFalse(PowerWarning.mustAsk(PictureSpec(file = "p.jpg", gif = GifMode.Once))) // a plain picture never animates
        assertTrue(PowerWarning.standing(Wallpaper(WallpaperKind.Picture, picture = still.copy(gif = GifMode.Once))))
        assertFalse(PowerWarning.standing(Wallpaper(WallpaperKind.Solid, picture = still.copy(gif = GifMode.Once))))
    }

    @Test fun HOM_16_thePictureSettingsSurviveTheStoreAndTheOtherKindsKeepTheirOwn() {
        val pic = PictureSpec("glance-a1.gif", PictureFit.Fit, 25, GifMode.LoopBounce, animated = true, powerOk = true, bounce = BounceSupport.TooLarge)
        val p = LauncherPrefs().withWallpaper(WallpaperSurface.Glance, Wallpaper(WallpaperKind.Picture, solid = 0xFF123456, picture = pic, systemDim = 50))
        val sp = MapSharedPreferences()
        LauncherPrefsStore(sp).update { p }
        val back = LauncherPrefsStore(sp).prefs.value
        assertEquals(pic, back.glancePicture); assertEquals(50, back.glanceSystemDim); assertEquals(WallpaperKind.Picture, back.glanceWallpaperKind)
        assertEquals(0xFF123456L, back.wallpaper(WallpaperSurface.Glance).solid) // the fallback colour is kept
        assertEquals(PictureSpec(), back.pagesPicture)
    }

    @Test fun CFG_03_garbageStoredValuesReadAsSafeDefaults() {
        val p = LauncherPrefsStore(MapSharedPreferences(mapOf("wpGlancePic" to "../../x", "wpGlancePicDim" to 99, "wpGlancePicGif" to "zzz", "wpGlancePicFit" to 7.toString(), "wpGlanceKind" to "picture"))).prefs.value
        assertEquals("", p.glancePicture.file); assertEquals(50, p.glancePicture.dim); assertEquals(GifMode.Still, p.glancePicture.gif); assertEquals(PictureFit.Fill, p.glancePicture.fit)
    }

    @Test fun NFR_18_theFileAndWhatDescribesItStayOnTheDeviceButFitDimAndModeTravel() {
        val p = LauncherPrefs().withWallpaper(WallpaperSurface.Glance, Wallpaper(WallpaperKind.Picture, picture = PictureSpec("glance-a1.gif", PictureFit.Fit, 25, GifMode.Once, animated = true, powerOk = true)))
        val json = SettingsCodec.encode(p).getJSONObject("prefs")
        for (k in StateSchema.WALLPAPER_PICTURE_KEYS) assertFalse("$k exported", json.has(k))
        assertTrue(StateSchema.WALLPAPER_PICTURE_KEYS.containsAll(listOf("wpGlancePic", "wpPagesPic", "wpGlancePicAnim", "wpGlancePicOk", "wpPagesPicVid")))
        assertEquals("fit", json.getString("wpGlancePicFit")); assertEquals(25, json.getInt("wpGlancePicDim")); assertEquals("once", json.getString("wpGlancePicGif")); assertEquals("picture", json.getString("wpGlanceKind"))
        val back = SettingsCodec.decode(SettingsCodec.encode(p)).prefs
        assertEquals(WallpaperKind.Picture, back.glanceWallpaperKind); assertEquals("", back.glancePicture.file); assertFalse(back.glancePicture.powerOk)
        assertTrue("wallpapers" in StateSchema.BACKUP_EXCLUDED_FILES)
    }
}
