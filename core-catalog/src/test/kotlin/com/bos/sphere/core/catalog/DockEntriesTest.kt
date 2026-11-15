package com.bos.sphere.core.catalog

import com.bos.sphere.core.data.HomePage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** HOM-02: the home edits keep `pin:` dock entries in step with the items they point at. */
class DockEntriesTest {
    private val cap: (com.bos.sphere.core.data.PageLayout) -> Int = { 12 }
    private fun state(dock: List<String>, vararg keys: String) = HomeState(pages = listOf(HomePage(keys.toList(), id = 1L)), dock = dock)

    @Test
    fun HOM_02_packageRemovalKeepsLivePinsAndNonAppPinsAndDropsOnlyGoneAppPins() {
        val s = state(listOf("pin:app:a", "pin:app:gone", "pin:folder:f1", "pin:group:g", "pin:widget:widget:5"), "a")
        val after = HomeEdit.pruneMissing(s, setOf("a"), cap)
        assertEquals(listOf("pin:app:a", "pin:folder:f1", "pin:group:g", "pin:widget:widget:5"), after.dock)
    }

    @Test
    fun HOM_02_anAppPinnedOnlyToTheRailSurvivesAnotherAppsRemoval() {
        val s = state(listOf("pin:app:railOnly"), "x")
        val diff = CatalogDiff(added = emptyList(), removed = listOf(AppRecord("x", "x", "X")), changed = emptyList())
        assertEquals(listOf("pin:app:railOnly"), PackageReconcile.applyToState(s, diff, cap).dock)
    }

    @Test
    fun HOM_02_removingAnItemFromHomeRemovesItsPin() {
        val w = ItemKeys.widget(5)
        var s = HomeEdit.add(state(listOf("pin:widget:$w", "pin:app:a"), "a"), ItemDef(w, ItemKind.Widget, "W"), 0, cap, WidgetSpan(2, 1))
        s = HomeEdit.remove(s, w, cap)
        assertEquals(listOf("pin:app:a"), s.dock)
        assertTrue(DockEntries.homeKey("pin:group:g") == null)
    }
}
