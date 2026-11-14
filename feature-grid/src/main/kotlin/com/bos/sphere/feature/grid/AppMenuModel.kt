package com.bos.sphere.feature.grid

import com.bos.sphere.core.input.AppMenuEntry
import com.bos.sphere.core.input.ContextMenus
import com.bos.sphere.core.input.HomeMenuAction
import com.bos.sphere.core.input.MenuAction
import com.bos.sphere.core.input.MenuContext

/** One row of the app menu dialog: the model's entry, its English label (translated when drawn) and whether it is destructive. */
data class AppMenuRow(val entry: AppMenuEntry, val destructive: Boolean) {
    val label: String get() = entry.label
    val shared: MenuAction? get() = (entry as? AppMenuEntry.Shared)?.entry?.action
    val home: HomeMenuAction? get() = (entry as? AppMenuEntry.Home)?.action
}

/**
 * INP-03: the rows of the app menu dialog come from [ContextMenus] and nothing else, so a long-press, a right-click
 * and the Menu key open the same rows. The dock stream's launch entries (open on a display, open beside, bounds, dock
 * pinning) are not part of this list: they are drawn as a group after "Open", and the context passed here says the
 * dock group already covers display, beside and dock so the model does not list them twice.
 */
object AppMenuModel {
    fun context(
        isHidden: Boolean,
        isOnHome: Boolean?,
        hasIconColour: Boolean,
        hasHomeSettings: Boolean,
        canUninstall: Boolean = true,
    ) = MenuContext(
        isApp = true,
        canUninstall = canUninstall,
        otherDisplays = 0, adjacentSupported = false, // the dock stream's group supplies these
        onHome = false,
        inDock = true, // ...and dock pinning
        canMoveToFolder = false, // folders are not on the home yet
        canTag = true,
        homeMembership = isOnHome,
        canPickIconColour = hasIconColour,
        hidden = isHidden,
        canOpenHomeSettings = hasHomeSettings,
        workspacesSupported = false, // a workspace has no app list besides its dock pins, which the dock group edits
    )

    /** Every trigger asks [ContextMenus.forApp]; the parameter exists so a test can assert the three agree. */
    fun entries(trigger: MenuTrigger, ctx: MenuContext): List<AppMenuEntry> = when (trigger) {
        MenuTrigger.LongPress, MenuTrigger.SecondaryClick, MenuTrigger.MenuKey -> ContextMenus.forApp(ctx)
    }

    fun rows(ctx: MenuContext, trigger: MenuTrigger = MenuTrigger.LongPress): List<AppMenuRow> =
        entries(trigger, ctx).map { AppMenuRow(it, destructive = (it as? AppMenuEntry.Shared)?.entry?.action == MenuAction.Uninstall) }
}
