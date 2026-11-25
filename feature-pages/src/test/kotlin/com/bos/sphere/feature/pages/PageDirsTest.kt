package com.bos.sphere.feature.pages

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files

private const val P = "p00000000000000aa"
private const val Q = "p00000000000000bb"

private val MANIFEST = """{"id":"t.w","name":"T","version":1,"sizes":["fill"],"network":false}"""

/** Imports on disk: staging, the swap, errors and the start-up sweep. */
class PageDirsTest {

    private val root: File = Files.createTempDirectory("pagedirs").toFile()
    private val dirs = PageDirs(root)

    @After
    fun cleanUp() {
        root.deleteRecursively()
    }

    private val layout = LayoutCheck.Ok(mapOf("widget.json" to "widget.json", "widget.html" to "widget.html"), ignored = 0)

    private fun files(html: String) = mapOf("widget.json" to MANIFEST, "widget.html" to html)

    private fun write(id: String, html: String): ImportResult =
        dirs.writePackage(id, layout, "w.zip", 1L) { source -> files(html)[source]?.let { ByteArrayInputStream(it.toByteArray()) } }

    private fun html(id: String) = File(dirs.contentDir(id), "widget.html").readText()

    private fun page(name: String) = ImportRecord(name, false, ManifestRules.forPage(name), false, emptyList(), 3, "$name.html", 1L, emptyList())

    @Test
    fun anImportIsWrittenWithItsRecord() {
        val r = write(P, "<p>one") as ImportResult.Ok
        assertEquals("T", r.record.name)
        assertEquals("<p>one", html(P))
        assertEquals("T", PageDirs.decodeRecord(dirs.recordFile(P).readText())?.name)
        assertEquals(listOf(P), root.list()!!.toList()) // no staging or swap left behind
    }

    @Test
    fun aReimportSwapsTheFilesAndKeepsNoOldCopy() {
        write(P, "<p>one")
        assertTrue(write(P, "<p>two") is ImportResult.Ok)
        assertEquals("<p>two", html(P))
        assertEquals(listOf(P), root.list()!!.toList())
    }

    @Test
    fun aStreamThatThrowsIsAFailedImportAndTheOldFilesStay() {
        write(P, "<p>one")
        val broken = object : InputStream() {
            override fun read(): Int = throw IOException("corrupt entry")
            override fun read(b: ByteArray, off: Int, len: Int): Int = throw java.util.zip.ZipException("invalid stored block lengths")
        }
        val r = dirs.writePackage(P, layout, "w.zip", 2L) { source -> if (source == "widget.html") broken else ByteArrayInputStream(MANIFEST.toByteArray()) }
        assertEquals(ImportResult.Failed(ImportFailure.Unreadable), r)
        assertEquals("<p>one", html(P))
        assertEquals(listOf(P), root.list()!!.toList())
    }

    @Test
    fun aProviderThatCantOpenAFileIsAFailedImport() {
        val r = dirs.writePackage(Q, layout, null, 1L) { throw SecurityException("not available offline") }
        assertEquals(ImportResult.Failed(ImportFailure.Unreadable), r)
        assertFalse(dirs.dir(Q).exists())
    }

    @Test
    fun aSinglePageIsWritten() {
        val r = dirs.writePage(P, "<p>x".toByteArray(), page("Orrery")) as ImportResult.Ok
        assertEquals("Orrery", r.record.name)
        assertEquals("<p>x", File(dirs.contentDir(P), ServePolicy.PAGE_FILE).readText())
    }

    @Test
    fun aSwapCutShortIsPutBackAtStart() {
        write(P, "<p>one")
        // The process died after moving the old files aside and before the new ones went in.
        assertTrue(dirs.dir(P).renameTo(File(root, P + PageDirs.OLD)))
        File(root, P + PageDirs.STAGING).mkdirs()
        dirs.sweep(setOf(P))
        assertEquals("<p>one", html(P))
        assertEquals(listOf(P), root.list()!!.toList())
    }

    @Test
    fun aFinishedSwapsOldCopyIsRemoved() {
        write(P, "<p>two")
        File(root, P + PageDirs.OLD).mkdirs()
        dirs.sweep(setOf(P))
        assertEquals(listOf(P), root.list()!!.toList())
    }

    @Test
    fun theSweepRemovesUnlistedPagesOnlyWhenTheListWasReadFully() {
        write(P, "<p>one")
        write(Q, "<p>two")
        dirs.sweep(null) // list unreadable or from a newer version: nothing removed
        assertEquals(setOf(P, Q), root.list()!!.toSet())
        dirs.sweep(setOf(P))
        assertEquals(setOf(P), root.list()!!.toSet())
    }

    @Test
    fun deleteRemovesEverythingOfAPage() {
        write(P, "<p>one")
        File(root, P + PageDirs.OLD).mkdirs()
        dirs.delete(P)
        assertTrue(root.list()!!.isEmpty())
        assertNull(PageDirs.decodeRecord("not json"))
    }
}

/** What survives the start-up sweep, and which widget slots are taken. */
class KeepSetTest {

    private val html = Page("p000000000000000a", PageKind.Html)
    private val web = Page("p000000000000000b", PageKind.Web, url = "https://a.org/")
    private val unknown = listOf<Map<String, Any?>>(
        mapOf("id" to "p000000000000000c", "kind" to "html", "package" to "yes?"), // damaged entry
        mapOf("id" to "p000000000000000d", "kind" to "canvas", "slot" to 7L), // a newer kind
    )
    private val list = PageList(listOf(Page.Home, html, web), unknown)

    @Test
    fun unreadEntriesKeepTheirFiles() {
        assertEquals(setOf("p000000000000000a", "p000000000000000c", "p000000000000000d"), PageEdits.filesToKeep(list, listReadFully = true))
    }

    @Test
    fun anUnreadableOrNewerListSweepsNothing() {
        assertNull(PageEdits.filesToKeep(PageList.HomeOnly, listReadFully = false))
    }

    @Test
    fun slotsNamedByUnreadEntriesAreTaken() {
        assertEquals(setOf(7), PageEdits.unknownSlots(list))
        assertEquals(8, PageEdits.nextWidgetSlot(list, placedSlots = setOf(2)))
        // The placement in slot 7 belongs to the unread entry: no second page is made for it.
        val synced = PageEdits.syncWidgetPages(list, placedSlots = setOf(7, 9)) { "p00000000000000ff" }
        assertEquals(listOf(9), synced.pages.filter { it.kind == PageKind.AppWidget }.map { it.widgetSlot })
    }
}

class PageBackupsTest {

    @Test
    fun aSecondDocumentNeverOverwritesTheFirst() {
        val stored = mutableMapOf<String, String>()
        assertEquals("list_unreadable", PageBackups.keyFor("list_unreadable", "one") { stored[it] })
        stored["list_unreadable"] = "one"
        assertNull(PageBackups.keyFor("list_unreadable", "one") { stored[it] }) // already kept
        assertEquals("list_unreadable_1", PageBackups.keyFor("list_unreadable", "two") { stored[it] })
        stored["list_unreadable_1"] = "two"
        assertEquals("list_unreadable_2", PageBackups.keyFor("list_unreadable", "three") { stored[it] })
        assertTrue(PageBackups.isBackupKey("list_unreadable_2"))
        assertTrue(PageBackups.isBackupKey("list_schema_3_1"))
        assertFalse(PageBackups.isBackupKey("list"))
        assertFalse(PageBackups.isBackupKey("nav_mode"))
    }
}
