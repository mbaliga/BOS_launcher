package com.bos.sphere.feature.grid

import com.bos.sphere.core.input.MenuAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppMenuModelTest {
    private val contexts = listOf(
        AppMenuModel.context(isHidden = false, isOnHome = null, hasIconColour = false, hasHomeSettings = false),
        AppMenuModel.context(isHidden = true, isOnHome = true, hasIconColour = true, hasHomeSettings = true),
        AppMenuModel.context(isHidden = false, isOnHome = false, hasIconColour = true, hasHomeSettings = false, canUninstall = false),
    )

    @Test fun INP_03_the_app_menu_dialog_rows_are_the_shared_model_for_every_trigger() {
        for (ctx in contexts) {
            val lp = AppMenuModel.rows(ctx, MenuTrigger.LongPress)
            assertEquals(lp, AppMenuModel.rows(ctx, MenuTrigger.SecondaryClick))
            assertEquals(lp, AppMenuModel.rows(ctx, MenuTrigger.MenuKey))
            assertEquals(InputMenuModel.entries(MenuTrigger.LongPress, ctx).map { it.action }, lp.map { it.action })
        }
    }

    @Test fun INP_03_rows_follow_the_state_of_the_app() {
        val plain = AppMenuModel.rows(contexts[0]).map { it.action }
        assertEquals(listOf(MenuAction.Open, MenuAction.AppInfo, MenuAction.Tags, MenuAction.Hide, MenuAction.Uninstall), plain)
        val full = AppMenuModel.rows(contexts[1]).map { it.action }
        assertTrue(MenuAction.Remove in full && MenuAction.Unhide in full && MenuAction.IconColour in full && MenuAction.HomeSettings in full)
        assertTrue(MenuAction.Hide !in full && MenuAction.AddToHome !in full)
        val off = AppMenuModel.rows(contexts[2]).map { it.action }
        assertTrue(MenuAction.AddToHome in off && MenuAction.Uninstall !in off)
    }

    @Test fun INP_03_only_uninstall_is_destructive() {
        assertEquals(listOf(MenuAction.Uninstall), AppMenuModel.rows(contexts[1]).filter { it.destructive }.map { it.action })
    }

    @Test fun INP_03_every_row_has_a_touch_path() {
        for (ctx in contexts) for (r in AppMenuModel.rows(ctx)) assertTrue(TouchPaths.forMenu(r.action).isNotBlank())
    }
}
