package com.bos.sphere.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * NFR-18: widget ids live in the excluded device file, not in the backed-up files. Wave 15: the private background picture
 * went with the removed background gallery, so its path is no longer stored; the leftover key and files are removed.
 */
class DeviceBoundTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test
    fun NFR_18_widgetIdsAreWrittenToTheDeviceFileAndNeverToTheBackedUpPrefs() {
        val main = MapSharedPreferences(); val corners = MapSharedPreferences(); val device = MapSharedPreferences()
        val prefs = LauncherPrefsStore(main, device)
        val sc = ShortcutStore(corners, device)
        prefs.update { it.copy(leftPage = LeftPage.Widget, leftWidgetId = 77) }
        sc.setCorner(Corner.TopLeft, CornerSlot.Widget(41, "Clock"))
        sc.setLandscapeCorner(Corner.TopRight, CornerSlot.Widget(42, "Weather"))
        sc.setCorner(Corner.BottomLeft, CornerSlot.Single(CornerItem.App("a/.A#0")))

        assertFalse(main.contains("leftWidgetId")); assertFalse(main.contains("bgPicture"))
        assertEquals(77, device.getInt("leftWidgetId", -1)); assertFalse(device.contains("bgPicture"))
        assertFalse("no widget id in the backed-up shortcuts file", corners.all.values.any { it.toString().contains("\"widget\"") })
        assertTrue(device.getString("corner.TopLeft", "")!!.contains("\"widget\""))
        assertFalse("other slots stay in the main file", device.contains("corner.BottomLeft"))

        // A second process over the same files reads the same values.
        val prefs2 = LauncherPrefsStore(main, device); val sc2 = ShortcutStore(corners, device)
        assertEquals(77, prefs2.prefs.value.leftWidgetId)
        assertEquals(CornerSlot.Widget(41, "Clock"), sc2.corners.value[Corner.TopLeft])
        assertEquals(CornerSlot.Widget(42, "Weather"), sc2.explicitLandscapeCorners()[Corner.TopRight])
    }

    @Test
    fun NFR_18_aRestoreThatBringsBackOnlyTheMainFilesShowsEmptyCornersAndNoWidget() {
        val main = MapSharedPreferences(); val corners = MapSharedPreferences(); val device = MapSharedPreferences()
        ShortcutStore(corners, device).setCorner(Corner.TopLeft, CornerSlot.Widget(41, "Clock"))
        LauncherPrefsStore(main, device).update { it.copy(leftWidgetId = 5) }
        val freshDevice = MapSharedPreferences() // the excluded file is not restored
        assertEquals(CornerSlot.Empty, ShortcutStore(corners, freshDevice).corners.value[Corner.TopLeft])
        val p = LauncherPrefsStore(main, freshDevice).prefs.value
        assertEquals(LauncherPrefs().leftWidgetId, p.leftWidgetId)
    }

    @Test
    fun NFR_18_valuesAnOlderBuildWroteToTheMainFilesAreMovedOnFirstRead() {
        val main = MapSharedPreferences(mapOf("leftWidgetId" to 9, "bgPicture" to "/files/background_3.img", "style" to "Dsi"))
        val corners = MapSharedPreferences(mapOf(
            "corner.TopLeft" to ShortcutCodec.encodeSlot(CornerSlot.Widget(31, "Notes")),
            "corner.TopRight" to ShortcutCodec.encodeSlot(CornerSlot.Single(CornerItem.App("a/.A#0"))),
        ))
        val device = MapSharedPreferences()
        val p = LauncherPrefsStore(main, device).prefs.value
        val sc = ShortcutStore(corners, device)
        assertEquals(9, p.leftWidgetId)
        assertEquals(HomeStyle.Dsi, p.homeStyle)
        assertFalse(main.contains("leftWidgetId")); assertFalse(main.contains("bgPicture"))
        assertEquals(CornerSlot.Widget(31, "Notes"), sc.corners.value[Corner.TopLeft])
        assertFalse((corners.getString("corner.TopLeft", "") ?: "").contains("\"widget\""))
        assertTrue(CornerSlot.Single(CornerItem.App("a/.A#0")) == sc.corners.value[Corner.TopRight])
        assertNull(device.getString("corner.TopRight", null))
    }

    @Test
    fun NFR_18_replacingAWidgetWithAnAppRemovesTheWidgetFromTheDeviceFile() {
        val corners = MapSharedPreferences(); val device = MapSharedPreferences()
        val sc = ShortcutStore(corners, device)
        sc.setCorner(Corner.TopLeft, CornerSlot.Widget(41, "Clock"))
        sc.setCorner(Corner.TopLeft, CornerSlot.Single(CornerItem.App("a/.A#0")))
        assertFalse(device.contains("corner.TopLeft"))
        assertEquals(CornerSlot.Single(CornerItem.App("a/.A#0")), ShortcutStore(corners, device).corners.value[Corner.TopLeft])
    }

    @Test
    fun HOM_16_leftoverPicturesOfTheRemovedBackgroundGalleryArePurgedAndOtherFilesKept() {
        val files = tmp.newFolder("files"); val noBackup = tmp.newFolder("nobackup")
        File(files, "background_111.img").writeText("img"); File(noBackup, "background_222.img").writeText("img")
        val other = File(files, "keep.txt").also { it.writeText("k") }
        assertEquals(2, BackgroundPictures.purge(files, noBackup))
        assertTrue(other.exists()); assertFalse(File(files, "background_111.img").exists()); assertFalse(File(noBackup, "background_222.img").exists())
        assertEquals("a second run has nothing to remove", 0, BackgroundPictures.purge(files, noBackup))
    }

    @Test
    fun NFR_18_theDeviceFileIsClassifiedAsExcludedAndIsNeverExported() {
        assertTrue(DeviceBound.FILE in StateSchema.BACKUP_EXCLUDED_PREF_FILES)
        assertFalse(DeviceBound.FILE in PrefFilesSource.ALLOWED)
    }
}
