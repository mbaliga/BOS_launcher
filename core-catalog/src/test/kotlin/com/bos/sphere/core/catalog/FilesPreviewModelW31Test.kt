package com.bos.sphere.core.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Wave 31 (HOM-15, filesViews): file kinds, name splitting, size and duration text, the weighted LRU cache and the view mode. */
class FilesPreviewModelW31Test {
    @Test fun HOM_15_w31_kindComesFromTheExtensionThenTheMime() {
        assertEquals(FileKind.Pdf, FileKind.classify("Zyda-2005-computer.pdf"))
        assertEquals(FileKind.Video, FileKind.classify("xvideos.com_f07.MP4"))
        assertEquals(FileKind.Audio, FileKind.classify("Stardust- Rule the World.mp3"))
        assertEquals(FileKind.Apk, FileKind.classify("WhatsApp.apk"))
        assertEquals(FileKind.Image, FileKind.classify("IMG_1.HEIC"))
        assertEquals(FileKind.Spreadsheet, FileKind.classify("a.xlsx"))
        assertEquals(FileKind.Slides, FileKind.classify("a.pptx"))
        assertEquals(FileKind.Document, FileKind.classify("a.docx"))
        assertEquals(FileKind.Archive, FileKind.classify("a.zip"))
        assertEquals("a name with no useful extension uses the mime", FileKind.Image, FileKind.classify("photo", "image/jpeg"))
        assertEquals(FileKind.Other, FileKind.classify("blob", "application/octet-stream"))
        assertEquals(FileKind.Other, FileKind.classify("noext"))
    }

    @Test fun HOM_15_w31_aTempDownloadIsNotAnApk() {
        assertEquals("only the last extension counts", FileKind.Other, FileKind.classify("whatsapp-messenger-2-18-271.apk.temp"))
    }

    @Test fun HOM_15_w31_onlyMediaPdfAndApkAreLoadedAsPreviews() {
        assertEquals(setOf(FileKind.Image, FileKind.Video, FileKind.Audio, FileKind.Pdf, FileKind.Apk), FileKind.entries.filter { it.previewable }.toSet())
    }

    @Test fun HOM_15_w31_theExtensionStaysWholeWhenTheStemIsShortened() {
        assertEquals("report.final" to ".pdf", FileNames.split("report.final.pdf"))
        assertEquals("README" to "", FileNames.split("README"))
        assertEquals(".hidden" to "", FileNames.split(".hidden"))
        assertEquals("", FileNames.extension("trailing."))
        assertEquals("PDF", FileNames.tileLabel("a.pdf"))
        assertEquals("FILE", FileNames.tileLabel("noext"))
        assertEquals("TORR", FileNames.tileLabel("x.torrent"))
    }

    @Test fun HOM_15_w31_sizeTextUsesOneDecimalUnderTen() {
        assertEquals("", FileFormat.size(-1))
        assertEquals("0 B", FileFormat.size(0))
        assertEquals("1023 B", FileFormat.size(1023))
        assertEquals("1.0 KB", FileFormat.size(1024))
        assertEquals("4.2 KB", FileFormat.size(4300))
        assertEquals("62 MB", FileFormat.size(62L * 1024 * 1024))
        assertEquals("1.3 GB", FileFormat.size((1.3 * 1024 * 1024 * 1024).toLong()))
    }

    @Test fun HOM_15_w31_durationAndMetaLine() {
        assertEquals("", FileFormat.duration(-1))
        assertEquals("1:05", FileFormat.duration(65_000))
        assertEquals("1:02:03", FileFormat.duration(3_723_000))
        assertEquals("62 days ago · 4.2 KB · PDF document", FileFormat.meta("62 days ago", 4300, "PDF document"))
        assertEquals("an unknown size and an empty kind drop out", "62 days ago", FileFormat.meta("62 days ago", -1, ""))
    }

    @Test fun HOM_15_w31_cacheEvictsTheLeastRecentlyUsedWithinItsWeight() {
        val c = WeightedLruCache<String, Int>(10) { it.toLong() }
        c.put("a", 4); c.put("b", 4)
        assertEquals(4, c["a"]) // a is now the most recent
        c.put("c", 4)
        assertNull("b was the least recently used", c["b"])
        assertEquals(4, c["a"]); assertEquals(4, c["c"])
        assertTrue(c.weight <= 10)
    }

    @Test fun HOM_15_w31_cacheNeverExceedsItsBudgetAndSkipsAnOversizedEntry() {
        val c = WeightedLruCache<Int, Int>(100) { it.toLong() }
        for (i in 0 until 1000) c.put(i, 7 + i % 13)
        assertTrue(c.weight <= 100)
        c.put(-1, 101)
        assertNull(c[-1])
        c.put(5, 10); c.put(5, 20)
        assertEquals("replacing a key replaces its weight", 20, c[5])
        c.clear(); assertEquals(0L, c.weight); assertEquals(0, c.size)
    }

    @Test fun HOM_15_w31_viewModeDefaultsToListAndParsesGarbageAsList() {
        assertEquals(FilesViewMode.List, FilesViewMode.parse(null))
        assertEquals(FilesViewMode.List, FilesViewMode.parse("Tiles"))
        assertEquals(FilesViewMode.Grid, FilesViewMode.parse("Grid"))
        assertEquals(FilesViewMode.Grid, FilesViewMode.List.other()); assertEquals(FilesViewMode.List, FilesViewMode.Grid.other())
    }
}
