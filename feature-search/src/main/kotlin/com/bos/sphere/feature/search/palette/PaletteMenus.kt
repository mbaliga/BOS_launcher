package com.bos.sphere.feature.search.palette

import com.bos.sphere.core.design.L10n
import com.bos.sphere.core.input.ContextMenus
import com.bos.sphere.core.input.MenuAction
import com.bos.sphere.core.input.MenuContext
import dev.aarso.search.palette.ResultAction
import dev.aarso.search.palette.ResultActionId

/**
 * PAL-08: the palette's action list for an app is core-input's one context-menu model, so long-press, right-click,
 * the Menu key and Tab/Right in the palette can never differ.
 */
object PaletteMenus {
    fun toId(a: MenuAction): ResultActionId = ResultActionId.valueOf(a.name)

    fun appActions(c: MenuContext): List<ResultAction> =
        ContextMenus.forItem(c).map { ResultAction(toId(it.action), it.enabled) }

    /** The text the UI shows for an action id (localised). */
    fun label(id: ResultActionId): String = when (id) {
        ResultActionId.Open -> L10n.tr("Open")
        ResultActionId.OpenOnDisplay -> L10n.tr("Open on another display")
        ResultActionId.OpenBeside -> L10n.tr("Open beside")
        ResultActionId.AppInfo -> L10n.tr("App info")
        ResultActionId.AddToDock -> L10n.tr("Add to dock")
        ResultActionId.AddToWorkspace -> L10n.tr("Add to workspace")
        ResultActionId.Uninstall -> L10n.tr("Uninstall")
        ResultActionId.Rename -> L10n.tr("Rename")
        ResultActionId.Remove -> L10n.tr("Remove from home")
        ResultActionId.MoveToFolder -> L10n.tr("Move to folder")
        ResultActionId.Copy -> L10n.tr("Copy")
        ResultActionId.Call -> L10n.tr("Call")
        ResultActionId.Message -> L10n.tr("Message")
        ResultActionId.Email -> L10n.tr("Email")
        ResultActionId.OpenContact -> L10n.tr("Open contact")
        ResultActionId.EnablePermission -> L10n.tr("Allow access")
        ResultActionId.SearchWeb -> L10n.tr("Search the web")
        ResultActionId.SearchStore -> L10n.tr("Search the app store")
        ResultActionId.Run -> L10n.tr("Run")
    }
}
