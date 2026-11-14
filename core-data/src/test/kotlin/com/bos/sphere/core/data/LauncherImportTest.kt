package com.bos.sphere.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import com.bos.sphere.core.data.LauncherImport as LI

class LauncherImportTest {
    private fun intent(c: String) = "#Intent;action=android.intent.action.MAIN;category=android.intent.category.LAUNCHER;launchFlags=0x10200000;component=$c;end"
    private fun app(id: Long, screen: Int, x: Int, y: Int, c: String, container: Long = LI.CONTAINER_DESKTOP) =
        LI.Row(id, container, screen, x, y, LI.TYPE_APP, intent(c))

    @Test fun CFG_12_componentBecomesTheKeyTheHomeUsesForTheOwnerProfile() {
        assertEquals("com.a/.Main#0", LI.appKey(intent("com.a/com.a.Main")))
        assertEquals("com.a/.Main#0", LI.appKey(intent("com.a/.Main")))
        assertEquals("com.a/org.other.Main#0", LI.appKey(intent("com.a/org.other.Main")))
        assertNull(LI.appKey("#Intent;action=x;end"))
        assertNull(LI.appKey(null))
    }

    @Test fun CFG_12_pagesKeepTheirOrderRowsThenColumnsAndHotseatLeadsPageOne() {
        val rows = listOf(
            app(1, 1, 1, 0, "b/.B"), app(2, 1, 0, 0, "a/.A"),
            app(3, 0, 0, 1, "d/.D"), app(4, 0, 0, 0, "c/.C"),
            app(5, 0, 0, 0, "h/.H", LI.CONTAINER_HOTSEAT),
            app(6, 0, 1, 0, "c/.C", LI.CONTAINER_HOTSEAT), // duplicate of a desktop app: placed once
        )
        val r = LI.toLayout(rows, emptySet())
        assertEquals(2, r.pages)
        assertEquals(listOf("h/.H#0", "c/.C#0", "d/.D#0"), r.layout.pages[0].keys.let { it })
        assertEquals(listOf("a/.A#0", "b/.B#0"), r.layout.pages[1].keys)
        assertTrue(r.layout.seeded)
        assertEquals(5, r.apps)
    }

    @Test fun CFG_12_foldersAreFlattenedAndWidgetsAndShortcutsAreCountedNotCarried() {
        val rows = listOf(
            LI.Row(10, LI.CONTAINER_DESKTOP, 0, 0, 0, LI.TYPE_FOLDER, null),
            app(11, 0, 1, 0, "f2/.F2", container = 10), app(12, 0, 0, 0, "f1/.F1", container = 10),
            LI.Row(20, LI.CONTAINER_DESKTOP, 0, 1, 0, LI.TYPE_WIDGET, null),
            LI.Row(21, LI.CONTAINER_DESKTOP, 0, 2, 0, LI.TYPE_SHORTCUT, "#Intent;action=x;end"),
            app(22, 0, 3, 0, "z/.Z"),
        )
        val r = LI.toLayout(rows, emptySet())
        assertEquals(listOf("f1/.F1#0", "f2/.F2#0", "z/.Z#0"), r.layout.pages.single().keys)
        assertEquals(1, r.foldersFlattened); assertEquals(1, r.widgetsSkipped); assertEquals(1, r.shortcutsSkipped)
    }

    @Test fun CFG_12_missingAppsStayAsPlaceholdersAndRenamedActivitiesResolveByPackage() {
        val rows = listOf(app(1, 0, 0, 0, "have/.Old"), app(2, 0, 1, 0, "gone/.G"), app(3, 0, 2, 0, "ok/.Ok"))
        val r = LI.toLayout(rows, setOf("have/.New#0", "ok/.Ok#0"))
        assertEquals(listOf("have/.New#0", "gone/.G#0", "ok/.Ok#0"), r.layout.pages.single().keys)
        assertEquals(setOf("gone/.G#0"), r.placeholders)
    }

    @Test fun CFG_12_detectsTheDatabaseAndAZipThatHoldsOneAndRefusesAnythingElse() {
        val db = "SQLite format 3\u0000".toByteArray(Charsets.ISO_8859_1) + ByteArray(100)
        assertEquals(LI.Kind.SqliteDatabase, LI.detect(db))
        val zip = ByteArrayOutputStream().also { o ->
            ZipOutputStream(o).use { z ->
                z.putNextEntry(ZipEntry("readme.txt")); z.write("x".toByteArray()); z.closeEntry()
                z.putNextEntry(ZipEntry("backup/Launcher.DB")); z.write(db); z.closeEntry()
            }
        }.toByteArray()
        assertEquals(LI.Kind.Zip, LI.detect(zip))
        assertEquals(db.size, LI.databaseBytes(zip).size)
        assertEquals(db.size, LI.databaseBytes(db).size)
        for (bad in listOf("{\"a\":1}".toByteArray(), ByteArrayOutputStream().also { o -> ZipOutputStream(o).use { z -> z.putNextEntry(ZipEntry("a.txt")); z.write(1); z.closeEntry() } }.toByteArray())) {
            try { LI.databaseBytes(bad); fail("accepted") } catch (_: StateFormatException) { }
        }
    }
}
