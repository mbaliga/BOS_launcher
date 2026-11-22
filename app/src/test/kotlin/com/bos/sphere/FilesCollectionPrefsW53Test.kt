package com.bos.sphere

import androidx.test.core.app.ApplicationProvider
import com.bos.sphere.core.catalog.BuiltInCollection
import com.bos.sphere.core.catalog.CustomCollection
import com.bos.sphere.core.catalog.FileCollections
import com.bos.sphere.core.catalog.FileRule
import com.bos.sphere.core.catalog.FilesCollectionPrefs
import com.bos.sphere.core.catalog.GroupKey
import com.bos.sphere.core.catalog.SortKey
import com.bos.sphere.core.catalog.TypeGroup
import com.bos.sphere.core.catalog.ViewOptions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** W53 (HOM-15): the stored side of the Files section: collections, per-source options with a global default, and the last source. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FilesCollectionPrefsW53Test {
    private val ctx get() = ApplicationProvider.getApplicationContext<android.content.Context>()
    @Before fun clear() { ctx.getSharedPreferences("home_state", 0).edit().clear().commit() }

    @Test fun HOM_15_w53_aFreshStoreHasTheDefaultCollectionsAllAndDateModifiedNewestFirst() {
        val p = FilesCollectionPrefs(ctx)
        assertEquals(FileCollections(), p.collections); assertEquals("all", p.source); assertEquals(ViewOptions.Default, p.defaultOptions)
        assertEquals(ViewOptions(SortKey.Modified, false, GroupKey.None), p.optionsFor("b:images"))
    }

    @Test fun HOM_15_w53_collectionsAndTheSourceAreKeptAcrossInstances() {
        val c = FileCollections().toggleBuiltIn(BuiltInCollection.Large).upsert(CustomCollection("c:1", "Mine", FileRule(types = setOf(TypeGroup.Audio), minBytes = 5)))
        FilesCollectionPrefs(ctx).apply { collections = c; source = "c:1" }
        val again = FilesCollectionPrefs(ctx)
        assertEquals(c, again.collections); assertEquals("c:1", again.source)
        assertTrue("stored in home_state", ctx.getSharedPreferences("home_state", 0).contains("panel.files.collections"))
    }

    @Test fun HOM_15_w53_aSourceUsesItsOwnChoiceElseTheDefault() {
        val p = FilesCollectionPrefs(ctx)
        p.defaultOptions = ViewOptions(SortKey.Name, true, GroupKey.Type)
        p.setOptions("b:documents", ViewOptions(SortKey.Size, false, GroupKey.None))
        assertEquals(ViewOptions(SortKey.Size, false, GroupKey.None), p.optionsFor("b:documents"))
        assertEquals(ViewOptions(SortKey.Name, true, GroupKey.Type), p.optionsFor("b:images"))
        p.clearOptions("b:documents")
        assertEquals(ViewOptions(SortKey.Name, true, GroupKey.Type), p.optionsFor("b:documents"))
    }

    @Test fun HOM_15_w53_setForAllForgetsThePerSourceChoices() {
        val p = FilesCollectionPrefs(ctx)
        p.setOptions("a", ViewOptions(SortKey.Name)); p.setOptions("f:content://t/1", ViewOptions(SortKey.Type, true, GroupKey.Folder))
        p.setDefaultForAll(ViewOptions(SortKey.Size, true, GroupKey.Size))
        assertNull(p.ownOptions("a")); assertNull(p.ownOptions("f:content://t/1"))
        assertEquals(ViewOptions(SortKey.Size, true, GroupKey.Size), p.optionsFor("b:large"))
    }

    @Test fun HOM_15_w53_largeFilesStartBySizeUntilADefaultIsSet() {
        val p = FilesCollectionPrefs(ctx)
        assertEquals(SortKey.Size, p.optionsFor("b:large").sort)
        p.defaultOptions = ViewOptions(SortKey.Name, true)
        assertEquals(SortKey.Name, p.optionsFor("b:large").sort)
    }

    @Test fun HOM_15_w53_theMemoryIsBoundedAndKeepsTheNewest() {
        val p = FilesCollectionPrefs(ctx)
        repeat(260) { p.setOptions("f:content://t/$it", ViewOptions(SortKey.Name)) }
        assertNull(p.ownOptions("f:content://t/0")); assertEquals(ViewOptions(SortKey.Name), p.ownOptions("f:content://t/259"))
    }

    @Test fun HOM_15_w53_damagedStoredValuesFallBackToTheDefaults() {
        ctx.getSharedPreferences("home_state", 0).edit().putString("panel.files.collections", "\u0000garbage").putString("panel.files.sort.default", "nope").putString("panel.files.sort.by", "x\n\ty\tz").commit()
        val p = FilesCollectionPrefs(ctx)
        assertEquals(ViewOptions.Default, p.defaultOptions); assertFalse(p.collections.custom.isNotEmpty()); assertNull(p.ownOptions("x"))
    }
}
