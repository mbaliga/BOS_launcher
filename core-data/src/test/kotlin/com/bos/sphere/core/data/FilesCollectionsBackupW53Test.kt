package com.bos.sphere.core.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * W53 (HOM-15, filesCollections): the Files collections, the per-source sort and group choices and the last source live in `home_state` under
 * `panel.files.*`, a file that is in the export allow-list and not in the backup exclusions, so they travel with a backup. The granted folders
 * themselves are held by the system and are not part of any export; the file index that the collections read (`palette-files.idx`) is excluded.
 */
class FilesCollectionsBackupW53Test {
    private val keys = listOf("panel.files.collections", "panel.files.source", "panel.files.sort.default", "panel.files.sort.by")

    @Test fun HOM_15_w53_homeStateIsExportedAndNotExcludedFromBackup() {
        assertTrue(PrefFilesSource.ALLOWED.contains("home_state"))
        assertFalse(StateSchema.BACKUP_EXCLUDED_PREF_FILES.contains("home_state"))
    }

    @Test fun HOM_15_w53_theFilesChoicesRoundTripThroughAnExportAndImport() {
        val collections = "S\tb:recents,c:ab12\nC\tc:ab12\tUnicode \\c name\tImages\tpdf\\ctxt\tword\tcontent://x/tree/a%3Ab\u0001content://x/tree/c\t\t30\t1024\t-1"
        val perSource = "b:documents\tName\t1\tType\nf:content://x/tree/a%3Ab\tSize\t0\tNone"
        val src = mapOf("home_state" to MapSharedPreferences().also { p ->
            p.edit().putString(keys[0], collections).putString(keys[1], "b:documents").putString(keys[2], "Modified\t0\tDate").putString(keys[3], perSource).putBoolean("other", true).apply()
        })
        val exported = PrefFilesSource({ src.getValue(it) }, listOf("home_state")).readObjects().getValue(StateSchema.K_PREF_FILES)
        val text = exported.toString()
        val target = mapOf("home_state" to MapSharedPreferences())
        PrefFilesSource({ target.getValue(it) }, listOf("home_state")).write(emptyMap(), mapOf(StateSchema.K_PREF_FILES to JSONObject(text)), setOf(StateSchema.K_PREF_FILES))
        val back = target.getValue("home_state")
        assertEquals(collections, back.getString(keys[0], null))
        assertEquals("b:documents", back.getString(keys[1], null))
        assertEquals("Modified\t0\tDate", back.getString(keys[2], null))
        assertEquals(perSource, back.getString(keys[3], null))
    }

    @Test fun HOM_15_w53_aCollectionNamedLikeAWidgetIdIsStillExported() {
        // The export removes widget ids from the arrangement files; the text of a collection is not an arrangement and must come through as typed.
        val named = "S\tc:1\nC\tc:1\tMy widget list\t\t\t\t\t\t0\t-1\t-1"
        val src = mapOf("home_state" to MapSharedPreferences().also { it.edit().putString(keys[0], named).apply() })
        val out = PrefFilesSource({ src.getValue(it) }, listOf("home_state")).readObjects().getValue(StateSchema.K_PREF_FILES).toString()
        assertTrue(out.contains("My widget list"))
    }
}
