package com.bos.sphere.core.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** HOM-15: the Files tile's thumbnails are bounded, cached, and fall back to the file's name. */
class FileThumbsTest {
    private fun file(i: Int, modified: Long = 10L) = RecentFile("content://t/doc/$i", "file$i.png", "image/png", modified, "content://t")

    @Test fun HOM_15_onlyTheFirstFourFilesAreAskedFor() {
        val asked = mutableListOf<String>()
        val t = FileThumbs<String>({ asked += it.name; "img-${it.name}" })
        val cells = t.load((1..9).map { file(it) })
        assertEquals(4, cells.size)
        assertEquals(listOf("file1.png", "file2.png", "file3.png", "file4.png"), asked)
        assertEquals("img-file1.png", cells[0].image)
    }

    @Test fun HOM_15_aFileWithoutAThumbnailOrWhoseLoaderThrowsFallsBackToItsName() {
        val t = FileThumbs<String>({ if (it.name == "file2.png") throw IllegalStateException("provider died") else if (it.name == "file3.png") null else "ok" })
        val cells = t.load((1..3).map { file(it) })
        assertEquals(listOf("file1.png", "file2.png", "file3.png"), cells.map { it.name })
        assertEquals(listOf("ok", null, null), cells.map { it.image })
    }

    @Test fun HOM_15_aSecondLoadOfTheSameFilesAsksTheProviderAgainForNothingEvenForFailures() {
        var calls = 0
        val t = FileThumbs<String>({ calls++; if (it.name == "file1.png") null else "x" })
        val files = (1..3).map { file(it) }
        t.load(files); t.load(files)
        assertEquals(3, calls)
    }

    @Test fun HOM_15_aChangedFileIsLoadedAgain() {
        var calls = 0
        val t = FileThumbs<String>({ calls++; "x" })
        t.load(listOf(file(1, 10L))); t.load(listOf(file(1, 99L)))
        assertEquals(2, calls)
    }

    @Test fun HOM_15_theCacheIsBounded() {
        val c = BoundedCache<String, FileThumbs.Result<String>>(3)
        val t = FileThumbs<String>({ "x" }, c)
        (1..10).forEach { t.load(listOf(file(it))) }
        assertEquals(3, c.size)
        val cache = BoundedCache<String, Int>(3)
        (1..10).forEach { cache[it.toString()] = it }
        assertEquals(3, cache.size)
        assertNull(cache["1"]); assertEquals(10, cache["10"])
        assertTrue(FileThumbs.CACHE_MAX >= FileThumbs.MAX_TILE)
    }

    @Test fun HOM_15_noFilesGivesNoCellsAndAZeroLimitAsksForNothing() {
        val t = FileThumbs<String>({ error("must not be called") })
        assertTrue(t.load(emptyList()).isEmpty())
        assertTrue(t.load(listOf(file(1)), max = 0).isEmpty())
    }
}
