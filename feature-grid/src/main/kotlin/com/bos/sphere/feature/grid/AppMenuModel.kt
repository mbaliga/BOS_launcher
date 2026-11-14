package com.bos.sphere.feature.grid

import com.bos.sphere.core.input.ContextMenus
import com.bos.sphere.core.input.MenuAction
import com.bos.sphere.core.input.MenuContext

/** One row of the app menu dialog: the model's action, the English label (translated when drawn) and whether it is a destructive one. */
data class AppMenuRow(val action: MenuAction, val label: String, val destructive: Boolean)

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

    fun rows(ctx: MenuContext, trigger: MenuTrigger = MenuTrigger.LongPress): List<AppMenuRow> =
        InputMenuModel.entries(trigger, ctx).map { AppMenuRow(it.action, it.action.label, it.action == MenuAction.Uninstall) }
}
