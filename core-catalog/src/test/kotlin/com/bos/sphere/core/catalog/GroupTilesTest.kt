package com.bos.sphere.core.catalog

import com.bos.sphere.core.data.HomePage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** WSP-05: a group tile follows the group it points at. */
class GroupTilesTest {
    private val cap: (com.bos.sphere.core.data.PageLayout) -> Int = { 12 }
    private val key = ItemKeys.group("g1")

    private fun withTile(name: String = "Work"): HomeState =
        HomeEdit.add(HomeState(pages = listOf(HomePage(listOf("a"), id = 1L))), ItemDef(key, ItemKind.Group, name, "g1"), 0, cap)

    @Test
    fun WSP_05_deletingTheGroupRemovesItsTileFromThePageAndTheDefinition() {
        val s = GroupTiles.reconcile(withTile(), emptyList(), cap)
        assertFalse(key in s.pages[0].keys)
        assertFalse(key in s.items)
        assertEquals(listOf("a"), s.pages[0].keys)
    }

    @Test
    fun WSP_05_deletingTheGroupAlsoRemovesATileFiledInAFolder() {
        var s = withTile()
        s = HomeEdit.createFolder(s, listOf("a", key), "f1", "Stuff", cap)
        assertTrue(key in s.folders["f1"]!!.keys)
        val after = GroupTiles.reconcile(s, emptyList(), cap)
        assertTrue(after.folders.values.none { key in it.keys })
        assertFalse(key in after.items)
    }

    @Test
    fun WSP_05_renamingTheGroupRenamesItsTile() {
        val s = GroupTiles.reconcile(withTile("Work"), listOf("g1" to "Focus"), cap)
        assertEquals("Focus", s.items[key]!!.label)
        assertTrue(key in s.pages[0].keys)
    }

    @Test
    fun WSP_05_aGroupThatStillExistsWithTheSameNameChangesNothing() {
        val s = withTile()
        assertSame(s, GroupTiles.reconcile(s, listOf("g1" to "Work", "g2" to "Other"), cap))
    }

    @Test
    fun WSP_05_otherTilesAreUntouched() {
        var s = withTile()
        s = HomeEdit.add(s, ItemDef(ItemKeys.group("g2"), ItemKind.Group, "Play", "g2"), 0, cap)
        val after = GroupTiles.reconcile(s, listOf("g2" to "Play"), cap)
        assertTrue(ItemKeys.group("g2") in after.pages[0].keys)
        assertFalse(key in after.pages[0].keys)
    }
}
