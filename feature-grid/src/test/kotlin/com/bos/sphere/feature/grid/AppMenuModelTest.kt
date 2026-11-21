package com.bos.sphere.feature.grid

import com.bos.sphere.core.input.AppMenuEntry
import com.bos.sphere.core.input.ContextMenus
import com.bos.sphere.core.input.HomeMenuAction
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

    private fun names(rows: List<AppMenuRow>) = rows.map { it.shared?.name ?: it.home!!.name }

    @Test fun INP_03_the_app_menu_dialog_rows_are_the_shared_model_for_every_trigger() {
        for (ctx in contexts) {
            val lp = AppMenuModel.rows(ctx, MenuTrigger.LongPress)
            assertEquals(lp, AppMenuModel.rows(ctx, MenuTrigger.SecondaryClick))
            assertEquals(lp, AppMenuModel.rows(ctx, MenuTrigger.MenuKey))
            assertEquals(ContextMenus.forApp(ctx), lp.map { it.entry })
        }
    }

    @Test fun INP_03_the_home_menu_keeps_every_shared_entry_in_order() {
        for (ctx in contexts) {
            val shared = ContextMenus.forApp(ctx).filterIsInstance<AppMenuEntry.Shared>().map { it.entry.action }
            val palette = ContextMenus.forItem(ctx).map { it.action }
            assertEquals(palette.filter { it in shared }, shared.filter { it in palette })
        }
    }

    @Test fun INP_03_rows_follow_the_state_of_the_app() {
        assertEquals(listOf("Open", "AppInfo", "Tags", "Hide", "Uninstall"), names(AppMenuModel.rows(contexts[0])))
        val full = names(AppMenuModel.rows(contexts[1]))
        assertTrue("Remove" in full && "Unhide" in full && "IconColour" in full && "HomeSettings" in full)
        assertTrue("Hide" !in full && "AddToHome" !in full)
        val off = names(AppMenuModel.rows(contexts[2]))
        assertTrue("AddToHome" in off && "Uninstall" !in off)
    }

    @Test fun INP_03_only_uninstall_is_destructive() {
        assertEquals(listOf("Uninstall"), names(AppMenuModel.rows(contexts[1]).filter { it.destructive }))
    }

    @Test fun INP_03_every_row_has_a_touch_path() {
        for (ctx in contexts) for (r in AppMenuModel.rows(ctx)) {
            val path = r.shared?.let(TouchPaths::forMenu) ?: TouchPaths.forHomeMenu(r.home!!)
            assertTrue(path.isNotBlank() && !path.contains("hover", true) && !path.contains("right-click", true))
        }
        for (h in HomeMenuAction.entries) assertTrue(TouchPaths.forHomeMenu(h).isNotBlank())
    }
}
