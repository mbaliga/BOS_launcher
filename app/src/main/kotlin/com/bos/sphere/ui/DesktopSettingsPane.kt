package com.bos.sphere.ui

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bos.sphere.core.design.HyleColors
import com.bos.sphere.core.design.L10n
import com.bos.sphere.feature.desktop.DesktopController
import com.bos.sphere.feature.desktop.DisplayProfileEditor
import com.bos.sphere.feature.desktop.OfferSetting
import com.bos.sphere.feature.dock.WorkspaceRepository
import com.bos.sphere.feature.grid.ActionRow
import com.bos.sphere.feature.grid.Glyphs
import com.bos.sphere.feature.grid.SettingCategory
import com.bos.sphere.feature.grid.SettingItem
import kotlinx.coroutines.launch

/** Ground Control's page for the dock and the external display desktop (EXT-02, EXT-06, EXT-09, DCK-04). */
fun desktopCategory(): SettingCategory = SettingCategory(
    L10n.tr("Desktop & dock"),
    Glyphs.Apps,
    listOf(SettingItem.Pane(L10n.tr("Dock and external display"), Glyphs.Apps, L10n.tr("Offer, disconnect, per-display profiles")) { DesktopPane() }),
)

/** What the offer and disconnect rows say, kept apart so a test can read them without a screen. */
object DesktopSettingsText {
    fun offer(s: OfferSetting) = when (s) {
        OfferSetting.Ask -> "Ask me when a display connects"
        OfferSetting.Always -> "Open the desktop on its own"
        OfferSetting.Never -> "Never open the desktop"
    }
    fun disconnect(migrate: Boolean) = if (migrate) "Move the desktop to the phone" else "Close the desktop"
}

@Composable
fun ColumnScope.DesktopPane() {
    val context = LocalContext.current
    val controller = remember { DesktopController.get(context) }
    val manager = remember { WorkspaceRepository.get(context) }
    var offer by remember { mutableStateOf(controller.offerSetting) }
    var migrate by remember { mutableStateOf(controller.migrateOnDisconnect) }
    var dockOn by remember { mutableStateOf(manager.current.dock.enabled) }
    var bump by remember { mutableIntStateOf(0) }

    Text(L10n.tr("When a display that can host apps connects"), color = HyleColors.InkDim, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
    OfferSetting.entries.forEach { s ->
        ActionRow(label = L10n.tr(DesktopSettingsText.offer(s)), selected = offer == s) { offer = s; controller.offerSetting = s }
    }
    Text(L10n.tr("When that display is unplugged"), color = HyleColors.InkDim, fontSize = 12.sp, modifier = Modifier.padding(top = 12.dp))
    listOf(false, true).forEach { m ->
        ActionRow(label = L10n.tr(DesktopSettingsText.disconnect(m)), selected = migrate == m) { migrate = m; controller.migrateOnDisconnect = m }
    }
    Text(L10n.tr("The desktop's arrangement for each display is kept and comes back when it is plugged in again."), color = HyleColors.InkDim, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))

    Text(L10n.tr("Dock on the phone"), color = HyleColors.InkDim, fontSize = 12.sp, modifier = Modifier.padding(top = 12.dp))
    listOf<Boolean?>(null, true, false).forEach { v ->
        ActionRow(label = L10n.tr(when (v) { null -> "Default for this device"; true -> "Always show the dock"; false -> "Never show the dock" }), selected = dockOn == v) {
            dockOn = v; manager.setDock { it.copy(enabled = v) }
        }
    }

    DockPanelEntry()

    Text(L10n.tr("Displays Liftoff remembers"), color = HyleColors.InkDim, fontSize = 12.sp, modifier = Modifier.padding(top = 12.dp))
    val identities = remember(bump) { controller.profiles.identities() }
    if (identities.isEmpty()) Text(L10n.tr("None yet. A display appears here after you open the desktop on it."), color = HyleColors.InkDim, fontSize = 12.sp)
    identities.forEach { id ->
        var profile by remember(id, bump) { mutableStateOf(controller.profiles.load(id)) }
        DisplayProfileEditor(com.bos.sphere.feature.desktop.DisplayIdentity.label(id), profile, { p -> profile = p; controller.profiles.save(id, p) }, Modifier.padding(vertical = 8.dp))
    }
}

/**
 * The dock stays optional, so its panel (workspaces, app groups, sessions, scheduled launches, Radar actions per workspace) must not be
 * reachable only from the dock's own button: this row opens the same panel from Ground Control, with the dock on or off (wave 24,
 * docs/decisions/dockOptional.md). The dialog hook is installed here too, so a controller's B closes the panel's windows (INP-20).
 */
@Composable
internal fun DockPanelEntry() {
    val context = LocalContext.current
    val view = androidx.compose.ui.platform.LocalView.current
    var open by remember { mutableStateOf(false) }
    // Kept outside the open branch: a group run or session restore started from the panel must outlive the panel closing (it closes itself first).
    val scope = rememberCoroutineScope()
    Text(L10n.tr("Dock panel"), color = HyleColors.InkDim, fontSize = 12.sp, modifier = Modifier.padding(top = 12.dp))
    ActionRow(label = L10n.tr("Workspaces, groups and dock settings"), selected = false) { open = true }
    Text(L10n.tr("Opens the same panel as the dock's own button. It works with the dock off."), color = HyleColors.InkDim, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
    if (!open) return
    DockInputWiring.Install(withBridge = false)
    val repo = remember { com.bos.sphere.core.data.LauncherAppsAppRepository(context) }
    val apps by repo.apps.collectAsState(initial = emptyList())
    val launcher = remember { com.bos.sphere.feature.dock.AppLauncher(context, rootView = { view }) }
    DockPanelHost(
        apps, launcher, onDismiss = { open = false },
        onRunGroup = { g -> scope.launch { launcher.runGroup(g) } },
        onRestore = { s -> scope.launch { launcher.restore(s) } },
    )
}
