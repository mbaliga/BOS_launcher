package com.bos.sphere.core.input

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** HOM-21: the long-press quick sheet is the same context-menu model as the item menus, with a toggle entry kind. */
class HomeSheetMenuTest {
    private val paged = HomeSheetContext(page = 1, pageCount = 3, alignTop = true, transitions = listOf("Slide" to true, "Fade" to false))

    @Test
    fun HOM_21_touchLongPressRightClickAndMenuKeyAskForTheSameRows() {
        assertEquals(ContextMenus.onLongPressHome(paged), ContextMenus.onSecondaryClickHome(paged))
        assertEquals(ContextMenus.onLongPressHome(paged), ContextMenus.onMenuKeyHome(paged))
        assertEquals(ContextMenus.forHomeSheet(paged), ContextMenus.onLongPressHome(paged))
    }

    @Test
    fun HOM_21_theThreeSwitchesComeFirstAndCarryTheirState() {
        val rows = ContextMenus.forHomeSheet(HomeSheetContext(timeOn = true, weatherOn = false, batteryOn = true))
        assertEquals(
            listOf(
                AppMenuEntry.Toggle(SheetToggle.Time, true), AppMenuEntry.Toggle(SheetToggle.Weather, false),
                AppMenuEntry.Toggle(SheetToggle.Battery, true),
            ),
            rows.take(3),
        )
        assertEquals("Battery %", rows[2].label)
    }

    @Test
    fun HOM_21_aHomeWithoutPagesHasNoPageRows() {
        val rows = ContextMenus.forHomeSheet(HomeSheetContext(page = null))
        assertTrue(rows.none { it is AppMenuEntry.Toggle && it.kind in setOf(SheetToggle.AlignTop, SheetToggle.AlignBottom, SheetToggle.MakeHome) })
        assertTrue(rows.none { it is AppMenuEntry.Choice })
        assertTrue(rows.none { it is AppMenuEntry.Sheet && it.action in setOf(HomeSheetAction.AddPage, HomeSheetAction.DeletePage) })
    }

    @Test
    fun HOM_21_aPagedHomeOffersPageRowsAndDeleteOnlyWithMoreThanOnePage() {
        val rows = ContextMenus.forHomeSheet(paged)
        assertTrue(AppMenuEntry.Toggle(SheetToggle.AlignTop, true) in rows)
        assertTrue(AppMenuEntry.Toggle(SheetToggle.AlignBottom, false) in rows)
        assertTrue(AppMenuEntry.Choice(ContextMenus.TRANSITION_GROUP, "Slide", true) in rows)
        assertTrue(AppMenuEntry.Sheet(HomeSheetAction.DeletePage, danger = true) in rows)
        val single = ContextMenus.forHomeSheet(paged.copy(pageCount = 1))
        assertFalse(single.any { it is AppMenuEntry.Sheet && it.action == HomeSheetAction.DeletePage })
        assertTrue(single.any { it is AppMenuEntry.Sheet && it.action == HomeSheetAction.AddPage })
    }

    @Test
    fun HOM_21_theClosingActionsFollowTheCapabilitiesOfTheHost() {
        val all = ContextMenus.forHomeSheet(HomeSheetContext()).filterIsInstance<AppMenuEntry.Sheet>().map { it.action }
        assertEquals(listOf(HomeSheetAction.EditHome, HomeSheetAction.Panels, HomeSheetAction.Wallpaper, HomeSheetAction.GroundControl, HomeSheetAction.HomeItems), all)
        val bare = ContextMenus.forHomeSheet(HomeSheetContext(canEdit = false, hasPanels = false, hasHomeItems = false)).filterIsInstance<AppMenuEntry.Sheet>().map { it.action }
        assertEquals(listOf(HomeSheetAction.Wallpaper, HomeSheetAction.GroundControl), bare)
    }

    @Test
    fun HOM_21_theToggleKindIsPartOfTheSameEntryModelAsTheItemMenu() {
        // The item menu's rows and the sheet's rows are one sealed type, so one list renderer can draw either.
        val mixed: List<AppMenuEntry> = ContextMenus.forApp(MenuContext()) + ContextMenus.forHomeSheet(HomeSheetContext())
        assertTrue(mixed.any { it is AppMenuEntry.Shared } && mixed.any { it is AppMenuEntry.Toggle })
        assertTrue(mixed.all { it.label.isNotBlank() })
    }
}
