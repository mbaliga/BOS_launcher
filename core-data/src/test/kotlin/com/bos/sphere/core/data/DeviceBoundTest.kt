package com.bos.sphere.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** NFR-18: widget ids and the private background picture path live in the excluded device file, not in the backed-up files. */
class DeviceBoundTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test
    fun NFR_18_widgetIdsAndThePicturePathAreWrittenToTheDeviceFileAndNeverToTheBackedUpPrefs() {
        val main = MapSharedPreferences(); val corners = MapSharedPreferences(); val device = MapSharedPreferences()
        val prefs = LauncherPrefsStore(main, device)
        val sc = ShortcutStore(corners, device)
        prefs.update { it.copy(leftPage = LeftPage.Widget, leftWidgetId = 77, backgroundKind = BackgroundKind.Picture, backgroundPicturePath = "/nb/background_1.img") }
        sc.setCorner(Corner.TopLeft, CornerSlot.Widget(41, "Clock"))
        sc.setLandscapeCorner(Corner.TopRight, CornerSlot.Widget(42, "Weather"))
        sc.setCorner(Corner.BottomLeft, CornerSlot.Single(CornerItem.App("a/.A#0")))

        assertFalse(main.contains("leftWidgetId")); assertFalse(main.contains("bgPicture"))
        assertEquals(77, device.getInt("leftWidgetId", -1)); assertEquals("/nb/background_1.img", device.getString("bgPicture", null))
        assertFalse("no widget id in the backed-up shortcuts file", corners.all.values.any { it.toString().contains("\"widget\"") })
        assertTrue(device.getString("corner.TopLeft", "")!!.contains("\"widget\""))
        assertFalse("other slots stay in the main file", device.contains("corner.BottomLeft"))

        // A second process over the same files reads the same values.
        val prefs2 = LauncherPrefsStore(main, device); val sc2 = ShortcutStore(corners, device)
        assertEquals(77, prefs2.prefs.value.leftWidgetId)
        assertEquals("/nb/background_1.img", prefs2.prefs.value.backgroundPicturePath)
        assertEquals(CornerSlot.Widget(41, "Clock"), sc2.corners.value[Corner.TopLeft])
        assertEquals(CornerSlot.Widget(42, "Weather"), sc2.explicitLandscapeCorners()[Corner.TopRight])
    }

    @Test
    fun NFR_18_aRestoreThatBringsBackOnlyTheMainFilesShowsEmptyCornersAndNoWidgetOrPicture() {
        val main = MapSharedPreferences(); val corners = MapSharedPreferences(); val device = MapSharedPreferences()
        ShortcutStore(corners, device).setCorner(Corner.TopLeft, CornerSlot.Widget(41, "Clock"))
        LauncherPrefsStore(main, device).update { it.copy(leftWidgetId = 5, backgroundPicturePath = "/x/background_2.img") }
        val freshDevice = MapSharedPreferences() // the excluded file is not restored
        assertEquals(CornerSlot.Empty, ShortcutStore(corners, freshDevice).corners.value[Corner.TopLeft])
        val p = LauncherPrefsStore(main, freshDevice).prefs.value
        assertEquals(LauncherPrefs().leftWidgetId, p.leftWidgetId)
        assertEquals("", p.backgroundPicturePath)
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
        assertEquals(9, p.leftWidgetId); assertEquals("/files/background_3.img", p.backgroundPicturePath)
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
    fun NFR_18_aPictureFromAnOlderBuildIsMovedToTheNoBackupDirectoryAndThePathFollows() {
        val files = tmp.newFolder("files"); val noBackup = File(tmp.root, "nobackup")
        val old = File(files, BackgroundPictures.fileName(111L)).also { it.writeText("img") }
        val other = File(files, "keep.txt").also { it.writeText("k") }
        val prefs = LauncherPrefsStore(MapSharedPreferences(), MapSharedPreferences())
        prefs.update { it.copy(backgroundKind = BackgroundKind.Picture, backgroundPicturePath = old.absolutePath) }
        assertEquals(1, BackgroundPictures.migrate(files, noBackup, prefs))
        val dest = File(noBackup, old.name)
        assertTrue(dest.readText() == "img"); assertFalse(old.exists()); assertTrue(other.exists())
        assertEquals(dest.absolutePath, prefs.prefs.value.backgroundPicturePath)
        assertEquals("a second run has nothing to move", 0, BackgroundPictures.migrate(files, noBackup, prefs))
        assertTrue(BackgroundPictures.isPictureName(BackgroundPictures.fileName(5L)))
    }

    @Test
    fun NFR_18_theDeviceFileIsClassifiedAsExcludedAndIsNeverExported() {
        assertTrue(DeviceBound.FILE in StateSchema.BACKUP_EXCLUDED_PREF_FILES)
        assertFalse(DeviceBound.FILE in PrefFilesSource.ALLOWED)
    }
}
