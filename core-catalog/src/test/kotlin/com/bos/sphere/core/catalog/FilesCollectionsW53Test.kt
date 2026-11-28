package com.bos.sphere.core.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/**
 * W53 (filesCollections, HOM-15): the collection rule matcher, the saved collections, sorting and grouping (date buckets with an injected clock and
 * zone, locale and Unicode name order), the 2000 file cap, and which files a source shows. All pure: no Android, no file system.
 */
class FilesCollectionsW53Test {
    private val day = 86_400_000L
    private val utc = TimeZone.getTimeZone("UTC")
    /** Wednesday 2026-11-18 12:00 UTC. */
    private val now = Calendar.getInstance(utc).apply { clear(); set(2026, Calendar.NOVEMBER, 18, 12, 0, 0) }.timeInMillis

    private fun at(y: Int, m: Int, d: Int, h: Int = 12, zone: TimeZone = utc) = Calendar.getInstance(zone).apply { clear(); set(y, m, d, h, 0, 0) }.timeInMillis
    private fun f(name: String, modified: Long = now, size: Long = 1000, mime: String = "", source: String = "T", folder: String = "", added: Long = -1, uri: String = "u/$name") =
        RecentFile(uri, name, mime, modified, source, size, folder, added)

    // ---- rule matcher ----

    private class Case(val label: String, val rule: FileRule, val file: RecentFile, val expect: Boolean)

    @Test fun HOM_15_w53_theRuleMatcherAgreesWithEveryRow() {
        val mb = 1024L * 1024
        val rows = listOf(
            Case("empty rule matches anything", FileRule(), f("x.bin"), true),
            Case("type group by extension", FileRule(types = setOf(TypeGroup.Documents)), f("a.PDF"), true),
            Case("docx is a document", FileRule(types = setOf(TypeGroup.Documents)), f("a.docx"), true),
            Case("epub is a document", FileRule(types = setOf(TypeGroup.Documents)), f("book.epub"), true),
            Case("odt is a document", FileRule(types = setOf(TypeGroup.Documents)), f("a.odt"), true),
            Case("txt is a document", FileRule(types = setOf(TypeGroup.Documents)), f("a.txt"), true),
            Case("an image is not a document", FileRule(types = setOf(TypeGroup.Documents)), f("a.jpg"), false),
            Case("type by mime when the name has no extension", FileRule(types = setOf(TypeGroup.Images)), f("IMG", mime = "image/jpeg"), true),
            Case("two groups are alternatives", FileRule(types = setOf(TypeGroup.Images, TypeGroup.Videos)), f("c.mp4"), true),
            Case("archive group", FileRule(types = setOf(TypeGroup.Archives)), f("a.7z"), true),
            Case("rar is an archive", FileRule(types = setOf(TypeGroup.Archives)), f("a.rar"), true),
            Case("apk group", FileRule(types = setOf(TypeGroup.Apps)), f("a.apk"), true),
            Case("extension list", FileRule(extensions = setOf("log")), f("a.LOG"), true),
            Case("extension list miss", FileRule(extensions = setOf("log")), f("a.txt"), false),
            Case("type or extension count as one rule (extension side)", FileRule(types = setOf(TypeGroup.Images), extensions = setOf("log")), f("a.log"), true),
            Case("type or extension count as one rule (type side)", FileRule(types = setOf(TypeGroup.Images), extensions = setOf("log")), f("a.png"), true),
            Case("type or extension count as one rule (neither)", FileRule(types = setOf(TypeGroup.Images), extensions = setOf("log")), f("a.zip"), false),
            Case("name contains is case-insensitive", FileRule(nameContains = "REPORT"), f("Annual report.pdf"), true),
            Case("name contains folds accents", FileRule(nameContains = "cafe"), f("Café menu.pdf"), true),
            Case("name contains folds accents the other way", FileRule(nameContains = "café"), f("cafe.txt"), true),
            Case("name contains miss", FileRule(nameContains = "invoice"), f("report.pdf"), false),
            Case("name contains ignores blank", FileRule(nameContains = "  "), f("report.pdf"), true),
            Case("folder grant match", FileRule(folders = setOf("T1")), f("a.txt", source = "T1"), true),
            Case("folder grant miss", FileRule(folders = setOf("T1")), f("a.txt", source = "T2"), false),
            Case("folder name is case-insensitive", FileRule(folderNames = setOf("download")), f("a.txt", folder = "Download"), true),
            Case("folder name miss", FileRule(folderNames = setOf("download")), f("a.txt", folder = "Pictures"), false),
            Case("age inside the window", FileRule(maxAgeDays = 7), f("a", modified = now - 6 * day), true),
            Case("age exactly at the edge", FileRule(maxAgeDays = 7), f("a", modified = now - 7 * day), true),
            Case("age outside", FileRule(maxAgeDays = 7), f("a", modified = now - 8 * day), false),
            Case("a file dated in the future is recent", FileRule(maxAgeDays = 7), f("a", modified = now + 3 * day), true),
            Case("min size inclusive", FileRule(minBytes = 100 * mb), f("a", size = 100 * mb), true),
            Case("min size miss", FileRule(minBytes = 100 * mb), f("a", size = 100 * mb - 1), false),
            Case("max size inclusive", FileRule(maxBytes = mb), f("a", size = mb), true),
            Case("max size miss", FileRule(maxBytes = mb), f("a", size = mb + 1), false),
            Case("unknown size fails a bound", FileRule(minBytes = 0), f("a", size = -1), false),
            Case("unknown size passes without bounds", FileRule(types = setOf(TypeGroup.Documents)), f("a.pdf", size = -1), true),
            Case("every criterion must hold: all", FileRule(types = setOf(TypeGroup.Documents), maxAgeDays = 7, minBytes = 10), f("a.pdf", modified = now - day, size = 50), true),
            Case("every criterion must hold: one fails", FileRule(types = setOf(TypeGroup.Documents), maxAgeDays = 7, minBytes = 10), f("a.pdf", modified = now - day, size = 5), false),
        )
        val bad = rows.filter { it.rule.matches(it.file, now) != it.expect }.map { it.label }
        assertTrue("rows that disagree: $bad", bad.isEmpty())
    }

    @Test fun HOM_15_w53_theBuiltInCollectionsPickWhatTheirExamplesSay() {
        fun picks(b: BuiltInCollection, file: RecentFile) = b.rule.matches(file, now)
        assertTrue(picks(BuiltInCollection.Recents, f("a", modified = now - 29 * day)))
        assertFalse(picks(BuiltInCollection.Recents, f("a", modified = now - 31 * day)))
        assertTrue(picks(BuiltInCollection.Documents, f("slides.pptx")))
        assertTrue(picks(BuiltInCollection.Documents, f("sheet.xlsx")))
        assertFalse(picks(BuiltInCollection.Documents, f("song.mp3")))
        assertTrue(picks(BuiltInCollection.Images, f("a.heic")))
        assertTrue(picks(BuiltInCollection.Videos, f("a.mkv")))
        assertTrue(picks(BuiltInCollection.Audio, f("a.flac")))
        assertTrue(picks(BuiltInCollection.Downloads, f("a.bin", folder = "Download")))
        assertTrue(picks(BuiltInCollection.Downloads, f("a.bin", folder = "downloads")))
        assertFalse(picks(BuiltInCollection.Downloads, f("a.bin", folder = "DCIM")))
        assertTrue(picks(BuiltInCollection.Archives, f("a.zip")))
        assertTrue(picks(BuiltInCollection.Screenshots, f("Screenshot_2026-11-01.png")))
        assertFalse(picks(BuiltInCollection.Screenshots, f("holiday.png")))
        assertFalse("a screenshot is a picture, not any file", picks(BuiltInCollection.Screenshots, f("Screenshot notes.txt")))
        assertTrue(picks(BuiltInCollection.Large, f("a.iso", size = LARGE_FILE_BYTES)))
        assertFalse(picks(BuiltInCollection.Large, f("a.iso", size = LARGE_FILE_BYTES - 1)))
        assertEquals("Large files open by size, biggest first", SortKey.Size, BuiltInCollection.Large.defaultSort)
        assertEquals(9, BuiltInCollection.entries.size)
        assertEquals(BuiltInCollection.entries.map { it.id }.size, BuiltInCollection.entries.map { it.id }.toSet().size)
    }

    @Test fun HOM_15_w53_extensionsAreParsedLoosely() {
        assertEquals(setOf("pdf", "docx", "txt"), FileText.parseExtensions("pdf, .DOCX; txt"))
        assertEquals(emptySet<String>(), FileText.parseExtensions("  , ;"))
        assertEquals(setOf("tar"), FileText.parseExtensions("a/b tar"))
        assertEquals(12, FileText.parseExtensions((1..30).joinToString(",") { "e$it" }).size)
    }

    // ---- saved collections ----

    @Test fun HOM_15_w53_aFreshInstallShowsTheDefaultBuiltIns() {
        assertEquals(listOf("b:recents", "b:documents", "b:images", "b:downloads"), FileCollections().shown)
        assertEquals(listOf("Recents", "Documents", "Images", "Downloads"), FileCollections().resolved().map { it.name })
    }

    @Test fun HOM_15_w53_collectionsToggleMoveAndRemove() {
        var c = FileCollections()
        c = c.toggleBuiltIn(BuiltInCollection.Videos)
        assertEquals("b:videos", c.shown.last())
        c = c.toggleBuiltIn(BuiltInCollection.Recents)
        assertFalse(c.isShown("b:recents"))
        assertEquals(listOf("b:documents", "b:images", "b:downloads", "b:videos"), c.shown)
        c = c.move("b:videos", -2)
        assertEquals(listOf("b:documents", "b:videos", "b:images", "b:downloads"), c.shown)
        assertEquals("moving past the end stops", listOf("b:videos", "b:documents", "b:images", "b:downloads"), c.move("b:videos", -5).shown)
        assertEquals(c, c.move("b:nothing", 1))
        c = c.remove("b:images")
        assertFalse(c.isShown("b:images"))
    }

    @Test fun HOM_15_w53_aCustomCollectionIsAddedEditedAndRemoved() {
        val a = CustomCollection("c:1", "Work PDFs", FileRule(types = setOf(TypeGroup.Documents), nameContains = "work"))
        var c = FileCollections().upsert(a)
        assertEquals("c:1", c.shown.last())
        assertEquals("Work PDFs", c.resolved().last().name)
        assertNull(c.resolved().last().builtIn)
        c = c.upsert(a.copy(name = "Work"))
        assertEquals(1, c.custom.size)
        assertEquals("Work", c.resolved().last().name)
        c = c.remove("c:1")
        assertTrue(c.custom.isEmpty()); assertFalse(c.isShown("c:1"))
    }

    @Test fun HOM_15_w53_collectionsSurviveSavingAndReadingBackWithAwkwardText() {
        val rule = FileRule(setOf(TypeGroup.Images, TypeGroup.Apps), setOf("log", "tmp"), "tab\there, comma\nline \\ back", setOf("content://x/tree/a%3Ab", "content://x/tree/c"), setOf("download"), 30, 1024, 4096)
        val c = FileCollections(listOf("b:audio", "c:ab12", "b:recents"), listOf(CustomCollection("c:ab12", "Ünïcode, \"quoted\" \ttab", rule)))
        val back = FileCollections.decode(c.encode())
        assertEquals(c, back)
        assertEquals(rule, back.custom.single().rule)
    }

    @Test fun HOM_15_w53_aDamagedValueGivesTheDefaultsAndABadLineIsSkipped() {
        assertEquals(FileCollections(), FileCollections.decode(null))
        assertEquals(FileCollections(), FileCollections.decode(""))
        assertEquals(FileCollections(), FileCollections.decode("   "))
        val c = FileCollections.decode("S\tb:audio,b:nonsense,c:gone\nC\tnot-an-id\tx\nC\tc:1\tName")
        assertEquals("unknown and removed ids are dropped", listOf("b:audio"), c.shown)
        assertTrue(c.custom.isEmpty())
        assertEquals("an explicit empty list means nothing is shown", emptyList<String>(), FileCollections.decode("S\t").shown)
    }

    @Test fun HOM_15_w53_newIdsDoNotCollide() {
        val taken = setOf("c:1", "c:2")
        var n = 0
        val id = FileCollections.newId(taken) { if (n++ < 2) 1 else 255 }
        assertEquals("c:ff", id)
    }

    // ---- date buckets ----

    @Test fun HOM_15_w53_dateBucketsFollowTheInjectedClockAndZone() {
        val sunday = Calendar.SUNDAY; val monday = Calendar.MONDAY
        class Row(val label: String, val ms: Long, val first: Int, val expect: DateBucket)
        // now = Wed 18 Nov 2026 12:00 UTC. Week starting Monday began Mon 16 Nov; starting Sunday began Sun 15 Nov.
        val rows = listOf(
            Row("this morning", at(2026, Calendar.NOVEMBER, 18, 0), monday, DateBucket.Today),
            Row("just before midnight yesterday", at(2026, Calendar.NOVEMBER, 17, 23), monday, DateBucket.Yesterday),
            Row("Tuesday 00:00 is yesterday", at(2026, Calendar.NOVEMBER, 17, 0), monday, DateBucket.Yesterday),
            Row("Monday of this week", at(2026, Calendar.NOVEMBER, 16, 9), monday, DateBucket.ThisWeek),
            Row("Sunday is last week for a Monday start", at(2026, Calendar.NOVEMBER, 15, 9), monday, DateBucket.ThisMonth),
            Row("Sunday is this week for a Sunday start", at(2026, Calendar.NOVEMBER, 15, 9), sunday, DateBucket.ThisWeek),
            Row("earlier this month", at(2026, Calendar.NOVEMBER, 2, 9), monday, DateBucket.ThisMonth),
            Row("first second of the month", at(2026, Calendar.NOVEMBER, 1, 0), monday, DateBucket.ThisMonth),
            Row("last month", at(2026, Calendar.OCTOBER, 31, 23), monday, DateBucket.Older),
            Row("last year", at(2025, Calendar.NOVEMBER, 18, 12), monday, DateBucket.Older),
            Row("a future date is today", now + 5 * day, monday, DateBucket.Today),
            Row("the epoch", 0, monday, DateBucket.Older),
        )
        val bad = rows.filter { DateBucket.of(it.ms, now, utc, it.first) != it.expect }.map { it.label }
        assertTrue("rows that disagree: $bad", bad.isEmpty())
    }

    @Test fun HOM_15_w53_theBucketUsesTheZoneOfTheClock() {
        val auckland = TimeZone.getTimeZone("Pacific/Auckland") // UTC+13 in November
        val lateUtc = at(2026, Calendar.NOVEMBER, 18, 23) // 18 Nov 23:00 UTC = 19 Nov 12:00 in Auckland
        assertEquals(DateBucket.Today, DateBucket.of(lateUtc, lateUtc + 60_000, auckland))
        assertEquals("the same instant a day earlier in UTC terms is still yesterday there", DateBucket.Yesterday, DateBucket.of(lateUtc - 18 * 3600_000L, lateUtc + 60_000, auckland))
        val la = TimeZone.getTimeZone("America/Los_Angeles")
        // 2026-11-18 03:00 UTC is 17 Nov 19:00 in Los Angeles.
        assertEquals(DateBucket.Today, DateBucket.of(at(2026, Calendar.NOVEMBER, 18, 3), at(2026, Calendar.NOVEMBER, 18, 5), la))
    }

    @Test fun HOM_15_w53_aDaylightSavingChangeDoesNotMoveTheMidnight() {
        val berlin = TimeZone.getTimeZone("Europe/Berlin") // clocks went back on 25 Oct 2026
        val nowBerlin = at(2026, Calendar.OCTOBER, 26, 10, berlin)
        assertEquals(DateBucket.Yesterday, DateBucket.of(at(2026, Calendar.OCTOBER, 25, 23, berlin), nowBerlin, berlin))
        assertEquals(DateBucket.Today, DateBucket.of(at(2026, Calendar.OCTOBER, 26, 0, berlin), nowBerlin, berlin))
    }

    @Test fun HOM_15_w53_sizeBandsHaveExactEdges() {
        val mb = 1024L * 1024
        val rows = listOf(-1L to SizeBand.Unknown, 0L to SizeBand.Small, mb - 1 to SizeBand.Small, mb to SizeBand.Medium, 100 * mb - 1 to SizeBand.Medium, 100 * mb to SizeBand.Large, 1024 * mb - 1 to SizeBand.Large, 1024 * mb to SizeBand.Huge)
        rows.forEach { (b, e) -> assertEquals("$b", e, SizeBand.of(b)) }
    }

    // ---- sort ----

    private fun names(l: List<RecentFile>) = l.map { it.name }
    private fun sorted(files: List<RecentFile>, o: ViewOptions, locale: Locale = Locale.ENGLISH) = FilesArrange.arrange(files, o, now, utc, locale).sections.flatMap { it.files }

    @Test fun HOM_15_w53_nameSortIsNaturalAndIgnoresCase() {
        val l = listOf(f("file10.txt"), f("File2.txt"), f("file1.txt"), f("Zebra.txt"), f("apple.txt"), f("file02.txt"))
        assertEquals(listOf("apple.txt", "file1.txt", "File2.txt", "file02.txt", "file10.txt", "Zebra.txt"), names(sorted(l, ViewOptions(SortKey.Name, true))))
        assertEquals("descending is the exact reverse", names(sorted(l, ViewOptions(SortKey.Name, true))).reversed(), names(sorted(l, ViewOptions(SortKey.Name, false))))
    }

    @Test fun HOM_15_w53_nameSortFollowsTheLocale() {
        val l = listOf(f("Zoo"), f("Äpfel"), f("Apfel"), f("Åland"), f("Ölz"), f("Oz"))
        assertEquals("German reads Å, Ä and Ö as variants of A and O", listOf("Åland", "Apfel", "Äpfel", "Ölz", "Oz", "Zoo"), names(sorted(l, ViewOptions(SortKey.Name, true), Locale.GERMAN)))
        val sv = names(sorted(l, ViewOptions(SortKey.Name, true), Locale("sv")))
        assertTrue("Swedish puts Å Ä Ö after Z: $sv", sv.indexOf("Zoo") < sv.indexOf("Åland") && sv.indexOf("Åland") < sv.indexOf("Äpfel") && sv.indexOf("Äpfel") < sv.indexOf("Ölz"))
    }

    @Test fun HOM_15_w53_nameSortHandlesUnicodeAndEmptyNames() {
        val l = listOf(f("日本語.txt"), f("éclair.txt"), f("Eagle.txt"), f("😀.txt"), f(""), f("123.txt"), f("ñandú.txt"))
        val r = names(sorted(l, ViewOptions(SortKey.Name, true), Locale.ENGLISH))
        assertEquals("", r.first())
        assertTrue("digits before letters: $r", r.indexOf("123.txt") < r.indexOf("Eagle.txt"))
        assertTrue("accents sort with their letter: $r", r.indexOf("Eagle.txt") < r.indexOf("éclair.txt") && r.indexOf("éclair.txt") < r.indexOf("ñandú.txt"))
        assertEquals(l.size, r.size)
        val composed = f("é.txt", uri = "a"); val precomposed = f("é.txt", uri = "b")
        assertEquals("a decomposed and a precomposed name are neighbours, not apart", 2, sorted(listOf(composed, f("zzz"), precomposed), ViewOptions(SortKey.Name, true)).let { s -> if (kotlin.math.abs(s.indexOf(composed) - s.indexOf(precomposed)) == 1) 2 else 0 })
    }

    @Test fun HOM_15_w53_dateSizeAndTypeSortsWithTiesByName() {
        val l = listOf(
            f("b.pdf", modified = now - 3 * day, size = 500), f("a.pdf", modified = now - 3 * day, size = 500), f("c.mp3", modified = now - day, size = -1),
            f("d.jpg", modified = now - 9 * day, size = 90_000), f("e.zip", modified = now, size = 9_000_000),
        )
        assertEquals(listOf("e.zip", "c.mp3", "a.pdf", "b.pdf", "d.jpg"), names(sorted(l, ViewOptions(SortKey.Modified, false))))
        assertEquals(listOf("d.jpg", "a.pdf", "b.pdf", "c.mp3", "e.zip"), names(sorted(l, ViewOptions(SortKey.Modified, true))))
        assertEquals("an unknown size is the smallest", listOf("e.zip", "d.jpg", "a.pdf", "b.pdf", "c.mp3"), names(sorted(l, ViewOptions(SortKey.Size, false))))
        assertEquals(listOf("c.mp3", "a.pdf", "b.pdf", "d.jpg", "e.zip"), names(sorted(l, ViewOptions(SortKey.Size, true))))
        // kinds in the order of FileKind: Image, Video, Audio, PDF, ... Archive
        assertEquals(listOf("d.jpg", "c.mp3", "a.pdf", "b.pdf", "e.zip"), names(sorted(l, ViewOptions(SortKey.Type, true))))
        assertEquals(listOf("e.zip", "a.pdf", "b.pdf", "c.mp3", "d.jpg"), names(sorted(l, ViewOptions(SortKey.Type, false))))
    }

    @Test fun HOM_15_w53_dateAddedPutsFilesWithoutOneLastInBothDirections() {
        val l = listOf(f("a", added = 5), f("b"), f("c", added = 9), f("d", added = 1))
        assertEquals(listOf("c", "a", "d", "b"), names(sorted(l, ViewOptions(SortKey.Added, false))))
        assertEquals(listOf("d", "a", "c", "b"), names(sorted(l, ViewOptions(SortKey.Added, true))))
    }

    @Test fun HOM_15_w53_sortingTwiceGivesTheSameOrder() {
        val l = (1..50).map { f("n${it % 7}", modified = now - (it % 3) * day, uri = "u$it") }
        val a = FilesArrange.arrange(l, ViewOptions(SortKey.Name), now, utc, Locale.ENGLISH).sections.single().files.map { it.uri }
        val b = FilesArrange.arrange(l.shuffled(java.util.Random(4)), ViewOptions(SortKey.Name), now, utc, Locale.ENGLISH).sections.single().files.map { it.uri }
        assertEquals(a, b)
    }

    // ---- grouping ----

    @Test fun HOM_15_w53_groupByDateMakesTheFiveBucketsWithCounts() {
        val l = listOf(
            f("t1", modified = now), f("t2", modified = now - 3600_000), f("y", modified = at(2026, Calendar.NOVEMBER, 17, 8)), f("w", modified = at(2026, Calendar.NOVEMBER, 16, 8)),
            f("m", modified = at(2026, Calendar.NOVEMBER, 3, 8)), f("o1", modified = at(2026, Calendar.JUNE, 3, 8)), f("o2", modified = at(2024, Calendar.JUNE, 3, 8)),
        )
        val r = FilesArrange.arrange(l, ViewOptions(SortKey.Modified, false, GroupKey.Date), now, utc, Locale.US)
        assertEquals(listOf("Today", "Yesterday", "This week", "This month", "Older"), r.sections.map { it.title })
        assertEquals(listOf(2, 1, 1, 1, 2), r.sections.map { it.count })
        assertEquals(listOf("t1", "t2"), names(r.sections[0].files))
        assertEquals(7, r.shown); assertEquals(7, r.total); assertFalse(r.capped)
        val asc = FilesArrange.arrange(l, ViewOptions(SortKey.Modified, true, GroupKey.Date), now, utc, Locale.US)
        assertEquals("ascending date sort lists the oldest bucket first", listOf("Older", "This month", "This week", "Yesterday", "Today"), asc.sections.map { it.title })
        val byName = FilesArrange.arrange(l, ViewOptions(SortKey.Name, true, GroupKey.Date), now, utc, Locale.US)
        assertEquals("a name sort keeps the newest bucket first", "Today", byName.sections.first().title)
    }

    @Test fun HOM_15_w53_emptyBucketsAreLeftOut() {
        val r = FilesArrange.arrange(listOf(f("a", modified = now), f("b", modified = at(2020, 0, 1))), ViewOptions(group = GroupKey.Date), now, utc, Locale.US)
        assertEquals(listOf("Today", "Older"), r.sections.map { it.title })
        assertTrue(FilesArrange.arrange(emptyList(), ViewOptions(group = GroupKey.Date), now, utc, Locale.US).sections.isEmpty())
    }

    @Test fun HOM_15_w53_groupByTypeUsesTheKindOrderAndLabels() {
        val l = listOf(f("a.pdf"), f("b.jpg"), f("c.jpg"), f("d.zip"), f("e.xyz"), f("f.mp4"))
        val r = FilesArrange.arrange(l, ViewOptions(group = GroupKey.Type), now, utc, Locale.US)
        assertEquals(listOf("Image", "Video", "PDF document", "Archive", "File"), r.sections.map { it.title })
        assertEquals(listOf(2, 1, 1, 1, 1), r.sections.map { it.count })
        assertTrue(r.sections.none { it.literal })
    }

    @Test fun HOM_15_w53_groupByFolderUsesTheFolderNamesAsTheyAre() {
        val l = listOf(f("a", folder = "Zeta"), f("b", folder = "alpha"), f("c", folder = ""), f("d", folder = "alpha"), f("e", folder = "Émile"))
        val r = FilesArrange.arrange(l, ViewOptions(SortKey.Name, true, GroupKey.Folder), now, utc, Locale.US)
        assertEquals(listOf("alpha", "Émile", "Zeta", "No folder"), r.sections.map { it.title })
        assertEquals(listOf(true, true, true, false), r.sections.map { it.literal })
        assertEquals(listOf("b", "d"), names(r.sections[0].files))
    }

    @Test fun HOM_15_w53_groupBySizeBandsBiggestFirstAndUnknownLast() {
        val mb = 1024L * 1024
        val l = listOf(f("s", size = 10), f("m", size = 5 * mb), f("l", size = 200 * mb), f("h", size = 3000 * mb), f("u", size = -1))
        val r = FilesArrange.arrange(l, ViewOptions(SortKey.Name, true, GroupKey.Size), now, utc, Locale.US)
        assertEquals(listOf("Over 1 GB", "100 MB to 1 GB", "1 to 100 MB", "Under 1 MB", "Unknown size"), r.sections.map { it.title })
        val asc = FilesArrange.arrange(l, ViewOptions(SortKey.Size, true, GroupKey.Size), now, utc, Locale.US)
        assertEquals(listOf("Under 1 MB", "1 to 100 MB", "100 MB to 1 GB", "Over 1 GB", "Unknown size"), asc.sections.map { it.title })
    }

    @Test fun HOM_15_w53_sectionIdsAreUniqueSoTheListKeysAreStable() {
        val l = (1..40).map { f("f$it.${listOf("pdf", "jpg", "mp3")[it % 3]}", modified = now - it * 5 * day, size = it * 700_000L, folder = "d${it % 4}") }
        GroupKey.entries.forEach { g ->
            val ids = FilesArrange.arrange(l, ViewOptions(group = g), now, utc, Locale.US).sections.map { it.id }
            assertEquals("$g", ids.size, ids.toSet().size)
        }
    }

    // ---- cap ----

    @Test fun HOM_15_w53_resultsAreCappedAtTwoThousandAfterSortingAndSaySo() {
        val l = (1..2500).map { f("f$it", modified = now - it * 1000L, uri = "u$it") }
        val r = FilesArrange.arrange(l, ViewOptions(SortKey.Modified, false, GroupKey.None), now, utc, Locale.US)
        assertEquals(2000, r.shown); assertEquals(2500, r.total); assertTrue(r.capped)
        assertEquals("the cap keeps the newest", "f1", r.sections.single().files.first().name)
        assertEquals("f2000", r.sections.single().files.last().name)
        val g = FilesArrange.arrange(l, ViewOptions(group = GroupKey.Date), now, utc, Locale.US)
        assertEquals("groups hold only what is shown", 2000, g.sections.sumOf { it.count })
        assertEquals(FilesArrange.CAP, 2000)
        assertFalse(FilesArrange.arrange(l.take(2000), ViewOptions(), now, utc, Locale.US).capped)
    }

    @Test fun HOM_15_w53_twentyThousandFilesAreArrangedQuickly() {
        val l = (1..20_000).map { f("name $it.${listOf("pdf", "jpg", "mp3", "zip")[it % 4]}", modified = now - it * 60_000L, size = it * 1000L, folder = "d${it % 50}", uri = "u$it") }
        val t0 = System.nanoTime()
        GroupKey.entries.forEach { g -> FilesArrange.arrange(l, ViewOptions(SortKey.Name, true, g), now, utc, Locale.US) }
        val ms = (System.nanoTime() - t0) / 1_000_000
        assertTrue("five arrangements of 20000 files took $ms ms", ms < 8_000)
    }

    // ---- options ----

    @Test fun HOM_15_w53_viewOptionsRoundTripAndReadBadValuesAsNull() {
        val o = ViewOptions(SortKey.Size, true, GroupKey.Folder)
        assertEquals(o, ViewOptions.decode(o.encode()))
        assertNull(ViewOptions.decode(null)); assertNull(ViewOptions.decode("")); assertNull(ViewOptions.decode("Name\t1")); assertNull(ViewOptions.decode("Nope\t1\tNone"))
        assertEquals(ViewOptions(SortKey.Modified, false, GroupKey.None), ViewOptions.Default)
    }

    @Test fun HOM_15_w53_sortAndGroupWordsNameEveryChoice() {
        SortKey.entries.forEach { assertTrue(FilesSortText.sortLabel(it).isNotEmpty()); assertNotEquals(FilesSortText.orderLabel(it, true), FilesSortText.orderLabel(it, false)) }
        GroupKey.entries.forEach { assertTrue(FilesSortText.groupLabel(it).isNotEmpty()) }
        assertEquals("Newest first", FilesSortText.orderLabel(SortKey.Modified, false))
        assertEquals("A to Z", FilesSortText.orderLabel(SortKey.Name, true))
    }

    // ---- which files a source shows ----

    private val files = listOf(
        f("a.pdf", source = "T1", folder = "Docs"), f("b.jpg", source = "T2", folder = "Camera"), f("c.pdf", source = "media"),
        f("d.pdf", source = "T3-revoked"),
    )

    @Test fun HOM_15_w53_allAndFoldersShowGrantedFoldersOnlyButCollectionsAlsoReadTheMediaListing() {
        val cols = FileCollections()
        val granted = setOf("T1", "T2")
        assertEquals(listOf("a.pdf", "b.jpg"), names(FilesSources.select(files, FileSourceIds.ALL, cols, now, granted)))
        assertEquals(listOf("b.jpg"), names(FilesSources.select(files, FileSourceIds.folder("T2"), cols, now, granted)))
        assertTrue("a folder whose grant is gone shows nothing", FilesSources.select(files, FileSourceIds.folder("T3-revoked"), cols, now, granted).isEmpty())
        assertEquals(listOf("a.pdf", "c.pdf"), names(FilesSources.select(files, "b:documents", cols.toggleBuiltIn(BuiltInCollection.Documents).toggleBuiltIn(BuiltInCollection.Documents), now, granted)))
        assertTrue("a collection that is not shown has no files", FilesSources.select(files, "b:videos", cols, now, granted).isEmpty())
    }

    @Test fun HOM_15_w53_aCustomCollectionLimitedToAFolderSeesOnlyThatFolder() {
        val c = FileCollections().upsert(CustomCollection("c:1", "Docs only", FileRule(types = setOf(TypeGroup.Documents), folders = setOf("T1"))))
        assertEquals(listOf("a.pdf"), names(FilesSources.select(files, "c:1", c, now, setOf("T1", "T2"))))
        assertTrue("on a device where the folder is not granted it matches nothing", FilesSources.select(files, "c:1", c, now, setOf("T2")).isEmpty())
    }

    @Test fun HOM_15_w53_indexRowsBecomeFilesWithTheirFolderAndAMillisecondDate() {
        val top = FilesSources.fromIndex("u1", "a.txt", "text/plain", 12, 1_700_000_000L, "tree:content://x/tree/T", "") { "Download" }
        assertEquals("content://x/tree/T", top.source); assertEquals("Download", top.folder); assertEquals(1_700_000_000_000L, top.modifiedMs)
        val sub = FilesSources.fromIndex("u2", "b.txt", "", 5, 1_700_000_000_123L, "tree:content://x/tree/T", "Reports") { "Download" }
        assertEquals("Reports", sub.folder); assertEquals("a millisecond date is kept", 1_700_000_000_123L, sub.modifiedMs)
        val media = FilesSources.fromIndex("u3", "c.jpg", "image/jpeg", -5, 0, "media", "") { "x" }
        assertEquals("media", media.source); assertEquals("", media.folder); assertEquals(-1L, media.sizeBytes)
        assertEquals(0L, FilesArrange.normalizeMs(0))
    }

    @Test fun HOM_15_w53_largeFilesOpenBiggestFirstWhileTheOthersFollowTheDefault() {
        assertEquals(SortKey.Size, BuiltInCollection.Large.defaultSort)
        BuiltInCollection.entries.filter { it != BuiltInCollection.Large }.forEach { assertEquals(it.name, SortKey.Modified, it.defaultSort) }
    }
}
