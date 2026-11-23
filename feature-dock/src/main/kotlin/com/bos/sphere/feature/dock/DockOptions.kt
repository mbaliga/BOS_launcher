package com.bos.sphere.feature.dock

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.bos.sphere.core.capabilities.Tier
import com.bos.sphere.core.data.DockStyle
import com.bos.sphere.core.design.HyleColors
import com.bos.sphere.core.design.L10n
import com.bos.sphere.feature.grid.OneUiCaption
import com.bos.sphere.feature.grid.OneUiGap
import com.bos.sphere.feature.grid.OneUiGroup
import com.bos.sphere.feature.grid.OneUiGroupTitle
import com.bos.sphere.feature.grid.OneUiRowDivider
import com.bos.sphere.feature.grid.OneUiSwitchRow
import com.bos.sphere.feature.grid.OneUiListRow

object DockOptionTags {
    const val GROUP_SHELF = "dock.groupShelf"
    const val USE_DEFAULT = "dock.useDefault"
    const val DESKTOP_ITEM = "dock.desktopItem"
    fun position(e: DockEdge) = "dock.position.${e.name}"
    fun mode(m: DockMode) = "dock.modeCard.${m.name}"
    fun style(s: DockStyle) = DockStyleUi.rowTag(s)
    const val MAGNIFY = "dock.magnify"
    fun strength(s: MagnifyStrength) = "dock.magnify.${s.id}"
}

/** What a surface offers of the dock options: the sheet and the settings page show the same body, each with what its host can do. */
class DockOptionsScope(
    /** The surface decides the dock's edge itself (the desktop surface): no Position cards. */
    val edgeFixed: Boolean = false,
    /** The dock can be switched off here (false when a surface forces it: the desktop, or Glance with Right side = Dock rail). */
    val canHide: Boolean = true,
    /** The Desktop item's pin, or null where the item is not offered. */
    val desktopPin: DockItemRef? = null,
)

/**
 * W55 (dockSheet): every dock option that exists, as drawn cards and switches, read from and written to the one store ([WorkspaceManager.setDock]).
 * Used by the dock's sheet (live, above the real dock) and by Settings > Desktop & dock > Dock, so the two cannot differ. Today's options are exactly:
 * Show the dock, Dock style, Dock position, Dock mode, the group chips on the home, and the Desktop item pin. There is no label, size, recents count,
 * auto-hide or haptics setting in the code (the dock draws icons only, see docs/decisions/dockIcons.md).
 */
@Composable
internal fun DockOptionsBody(manager: WorkspaceManager, tier: Tier, scope: DockOptionsScope, landscape: Boolean? = null) {
    val state by manager.state.collectAsState()
    val dock = state.dock
    val ink = HyleColors.InkDim
    val accent = HyleColors.Violet

    OneUiGroupTitle(L10n.tr("Dock style"))
    OneUiGroup(1) {
        DockCardRow {
            DockStyle.entries.forEach { s ->
                DockOptionCard(L10n.tr(s.label), dock.style == s, DockOptionTags.style(s), { manager.setDock { it.copy(style = s) } }, width = DockCardTokens.StyleWidth) { _ -> DockStylePreview(s) }
            }
        }
    }
    OneUiCaption(L10n.tr("Transparent draws no panel: only the icons, with a soft halo. 3D is a glass shelf with the icons standing on it."))

    OneUiGap()
    OneUiGroupTitle(L10n.tr("Magnify icons"))
    OneUiGroup(1) {
        OneUiSwitchRow(
            L10n.tr("Magnify icons"), dock.magnify, { v -> manager.setDock { it.copy(magnify = v) } },
            summary = L10n.tr("Icons grow around your finger or pointer, like the Mac dock."), modifier = Modifier.testTag(DockOptionTags.MAGNIFY),
        )
    }
    if (dock.magnify) {
        OneUiGap()
        OneUiGroup(1) {
            DockCardRow {
                MagnifyStrength.entries.forEach { m ->
                    DockOptionCard(L10n.tr(m.label) + " " + m.factor.toString() + "x", dock.magnifyStrength == m, DockOptionTags.strength(m), { manager.setDock { it.copy(magnifyStrength = m) } }, width = DockCardTokens.StyleWidth) { mod ->
                        androidx.compose.foundation.Canvas(mod) { strengthArt(m, ink, accent)(this, size) }
                    }
                }
            }
        }
        OneUiCaption(L10n.tr("Drag along the dock to see it. Edit dock turns it off while you rearrange."))
    }

    if (!scope.edgeFixed) {
        OneUiGap()
        OneUiGroupTitle(L10n.tr("Position"))
        OneUiGroup(1) {
            DockCardRow {
                DockEdge.entries.forEach { e ->
                    DockOptionCard(L10n.tr(e.label), dock.edge == e, DockOptionTags.position(e), { manager.setDock { it.copy(edge = e) } }) { m ->
                        androidx.compose.foundation.Canvas(m) { positionArt(e, ink, accent)(this, size) }
                    }
                }
            }
        }
        OneUiCaption(
            if (landscape == false) L10n.tr("In portrait the dock is always at the bottom. The side you choose applies in landscape.")
            else L10n.tr("In portrait the dock is always at the bottom. In landscape it can also sit on a side."),
        )
    }

    OneUiGap()
    OneUiGroupTitle(L10n.tr("Dock mode"))
    OneUiGroup(1) {
        DockCardRow {
            DockMode.entries.forEach { m ->
                DockOptionCard(L10n.tr(m.label), dock.mode == m, DockOptionTags.mode(m), { manager.setDock { it.copy(mode = m) } }, width = DockCardTokens.StyleWidth) { mod ->
                    androidx.compose.foundation.Canvas(mod) { modeArt(m, ink, accent)(this, size) }
                }
            }
        }
    }
    OneUiCaption(L10n.tr(if (dock.mode == DockMode.Collapsed) "A thin band that opens when you touch it" else "Your pinned apps, then the ones you used last"))

    OneUiGap()
    val enabled = dock.isEnabled(tier)
    OneUiGroup((if (scope.canHide) 1 else 0) + 1 + (if (scope.desktopPin != null) 1 else 0)) {
        if (scope.canHide) {
            OneUiSwitchRow(
                L10n.tr("Show the dock"), enabled, { v -> manager.setDock { it.copy(enabled = v) } },
                summary = if (dock.enabled == null) L10n.tr("Default for this device: %s", if (tier == Tier.T1) L10n.tr("off") else L10n.tr("on")) else null,
                modifier = Modifier.testTag(DockPageTags.SHOW_DOCK),
            )
            OneUiRowDivider()
        }
        OneUiSwitchRow(
            L10n.tr("Show app group chips on the home"), dock.groupShelf, { v -> manager.setDock { it.copy(groupShelf = v) } },
            summary = L10n.tr("Saved app groups appear as chips above the dock."), modifier = Modifier.testTag(DockOptionTags.GROUP_SHELF),
        )
        scope.desktopPin?.let { pin ->
            OneUiRowDivider()
            val pinned = pin in state.active.dockPins
            OneUiSwitchRow(
                L10n.tr("Desktop item in the dock"), pinned,
                { on -> manager.setPins(if (on) state.active.dockPins + pin else state.active.dockPins - pin) },
                summary = L10n.tr("The Desktop item opens the Liftoff desktop on a connected display."), modifier = Modifier.testTag(DockOptionTags.DESKTOP_ITEM),
            )
        }
    }
}

/** "Use the default for this device": only while the owner has chosen on or off; back to the tier's default. */
@Composable
internal fun DockUseDefaultRow(manager: WorkspaceManager) {
    val state by manager.state.collectAsState()
    if (state.dock.enabled == null) return
    OneUiGap()
    OneUiGroup(1) { OneUiListRow(L10n.tr("Use the default for this device"), onClick = { manager.setDock { it.copy(enabled = null) } }, modifier = Modifier.testTag(DockOptionTags.USE_DEFAULT)) }
}
