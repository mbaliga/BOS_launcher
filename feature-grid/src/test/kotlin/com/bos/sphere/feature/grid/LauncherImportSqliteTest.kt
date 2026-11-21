package com.bos.sphere.feature.grid

import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import com.bos.sphere.core.data.LauncherImport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** CFG-12: the SQLite read of a Launcher3 `favorites` table, through Robolectric's real SQLite (core-data has no Robolectric). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LauncherImportSqliteTest {
    private val dir: File get() = ApplicationProvider.getApplicationContext<android.content.Context>().cacheDir

    private fun intent(c: String) = "#Intent;action=android.intent.action.MAIN;category=android.intent.category.LAUNCHER;component=$c;end"

    private fun makeDb(createTable: Boolean = true): ByteArray {
        val f = File.createTempFile("src", ".db", dir)
        val d = SQLiteDatabase.openOrCreateDatabase(f, null)
        if (createTable) {
            d.execSQL("CREATE TABLE favorites (_id INTEGER PRIMARY KEY, title TEXT, intent TEXT, container INTEGER, screen INTEGER, cellX INTEGER, cellY INTEGER, itemType INTEGER)")
            fun ins(id: Int, c: Int, s: Int, x: Int, y: Int, t: Int, i: String?) =
                d.execSQL("INSERT INTO favorites (_id, intent, container, screen, cellX, cellY, itemType) VALUES (?,?,?,?,?,?,?)", arrayOf<Any?>(id, i, c, s, x, y, t))
            ins(1, -100, 1, 1, 0, 0, intent("com.b/.B"))
            ins(2, -100, 0, 0, 0, 0, intent("com.a/.A"))
            ins(3, -101, 0, 0, 0, 0, intent("com.h/.H"))
            ins(4, -100, 1, 0, 0, 2, null)
            ins(5, 4, 0, 0, 0, 0, intent("com.f/.F"))
            ins(6, -100, 1, 2, 0, 4, null)
        }
        d.close()
        return f.readBytes().also { f.delete() }
    }

    @Test fun CFG_12_readsRowsFromARealSqliteFile() {
        val rows = LauncherImport.readRows(makeDb(), dir)
        assertEquals(6, rows.size)
        assertEquals(LauncherImport.TYPE_FOLDER, rows.first { it.id == 4L }.itemType)
        assertEquals(-101L, rows.first { it.id == 3L }.container)
    }

    @Test fun CFG_12_wholePipelineFromBareDatabaseAndFromZip() {
        val db = makeDb()
        val zip = ByteArrayOutputStream().also { o -> ZipOutputStream(o).use { z -> z.putNextEntry(ZipEntry("backup/launcher.db")); z.write(db); z.closeEntry() } }.toByteArray()
        for (bytes in listOf(db, zip)) {
            val r = LauncherImport.parse(bytes, emptySet(), dir)
            assertEquals(listOf("com.h/.H#0", "com.a/.A#0"), r.layout.pages[0].keys)
            assertEquals(listOf("com.f/.F#0", "com.b/.B#0"), r.layout.pages[1].keys)
            assertEquals(1, r.foldersFlattened)
            assertEquals(1, r.widgetsSkipped)
        }
    }

    @Test fun CFG_12_aDatabaseWithoutFavoritesIsRefusedWithAMessage() {
        try { LauncherImport.readRows(makeDb(createTable = false), dir); fail() } catch (e: com.bos.sphere.core.data.StateFormatException) { assertTrue(e.message!!.contains("favorites")) }
    }
}
