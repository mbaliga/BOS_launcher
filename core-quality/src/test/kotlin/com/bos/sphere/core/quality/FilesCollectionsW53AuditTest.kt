package com.bos.sphere.core.quality

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Wave 53 (HOM-15, filesCollections): the new Files code stays inside the rules: no new permission, work off the main thread, one classified preference file. */
class FilesCollectionsW53AuditTest {
    private fun text(p: String) = Repo.file(p).readText()
    private val ui = listOf("app/src/main/kotlin/com/bos/sphere/FilesSection.kt", "app/src/main/kotlin/com/bos/sphere/FilesCollectionsUi.kt", "app/src/main/kotlin/com/bos/sphere/FilesUi.kt")
    private val model = listOf("FileCollections.kt", "FilesSortGroup.kt", "FilesCollectionPrefs.kt").map { "core-catalog/src/main/kotlin/com/bos/sphere/core/catalog/$it" }

    @Test fun HOM_15_w53_theFilesCodeRequestsNoPermissionAndQueriesNoProvider() {
        for (f in ui + model) {
            val t = text(f)
            for (bad in listOf("requestPermissions", "READ_MEDIA", "READ_EXTERNAL_STORAGE", "MANAGE_EXTERNAL_STORAGE", "contentResolver.query", "MediaStore")) assertFalse("$f uses $bad", t.contains(bad))
        }
        assertFalse("no storage permission was added to the manifest by this wave", text("app/src/main/AndroidManifest.xml").contains("MANAGE_EXTERNAL_STORAGE"))
    }

    @Test fun HOM_15_w53_theFilesDataIsReadOnTheIoPoolAndArrangedOnTheDefaultPool() {
        val t = text("app/src/main/kotlin/com/bos/sphere/FilesSection.kt")
        assertTrue(t.contains("withContext(Dispatchers.IO) { data.load() }"))
        assertTrue(t.contains("withContext(Dispatchers.IO) { data.reindex() }"))
        assertTrue(t.contains("withContext(Dispatchers.Default)"))
        assertTrue("the result is capped", text("core-catalog/src/main/kotlin/com/bos/sphere/core/catalog/FilesSortGroup.kt").contains("const val CAP = 2000"))
    }

    @Test fun HOM_15_w53_theChoicesLiveInHomeStateThroughTolerantPrefs() {
        val t = text("core-catalog/src/main/kotlin/com/bos/sphere/core/catalog/FilesCollectionPrefs.kt")
        assertTrue(t.contains("TolerantPrefs.of(context.applicationContext, \"home_state\")"))
        assertFalse(t.contains("getSharedPreferences"))
        assertTrue(text("core-data/src/main/kotlin/com/bos/sphere/core/data/StateLiveSources.kt").contains("\"home_state\""))
    }

    @Test fun HOM_15_w53_theNewPagesAreFocusNodesAndTheOldFolderEntriesAndFilesPerFolderChipsAreGone() {
        val t = text("app/src/main/kotlin/com/bos/sphere/FilesUi.kt")
        assertTrue(t.contains("launcherItem(id, FocusKind.Item, focusable = false"))
        assertFalse("the old folder list rows were replaced by the switcher", t.contains("private fun FolderEntry"))
        assertFalse("the old per-folder file count chips were replaced by the 2000 file cap", text("app/src/main/kotlin/com/bos/sphere/HomePanels.kt").contains("prefs.filesPerFolder = it"))
    }
}
