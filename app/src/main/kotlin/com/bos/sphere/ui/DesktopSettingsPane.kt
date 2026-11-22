package com.bos.sphere.ui

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.bos.sphere.core.design.L10n
import com.bos.sphere.feature.desktop.DesktopController
import com.bos.sphere.feature.desktop.DisplayProfileEditor
import com.bos.sphere.feature.desktop.OfferSetting
import com.bos.sphere.feature.dock.AppGroupsContent
import com.bos.sphere.feature.dock.DockPagesScope
import com.bos.sphere.feature.dock.DockSettingsContent
import com.bos.sphere.feature.dock.PanelApp
import com.bos.sphere.feature.dock.PinnableActions
import com.bos.sphere.feature.dock.WorkspaceRepository
import com.bos.sphere.feature.dock.WorkspaceSummary
import com.bos.sphere.feature.dock.WorkspacesContent
import com.bos.sphere.feature.grid.Glyphs
import com.bos.sphere.feature.grid.OneUiCaption
import com.bos.sphere.feature.grid.OneUiGroup
import com.bos.sphere.feature.grid.OneUiGroupTitle
import com.bos.sphere.feature.grid.OneUiInfoCard
import com.bos.sphere.feature.grid.OneUiRadioPage
import com.bos.sphere.feature.grid.OneUiRowDivider
import com.bos.sphere.feature.grid.OneUiValueRow
import com.bos.sphere.feature.grid.SettingCategory
import com.bos.sphere.feature.grid.SettingItem
import kotlinx.coroutines.launch

/**
 * Ground Control's page for the dock and the external display desktop (EXT-02, EXT-06, EXT-09, DCK-04). Wave 41: the dock, the workspaces
 * and the app groups are each their own One UI page here (they were one floating card, "Workspaces, groups and sessions").
 */
fun desktopCategory(): SettingCategory = SettingCategory(
    L10n.tr("Desktop & dock"),
    Glyphs.Apps,
    listOf(
        SettingItem.Pane(L10n.tr("Dock"), Glyphs.Apps, L10n.tr("Show, mode, position, apps"), bare = true) { DockSettingsPane(DockPaneKind.Dock) },
        SettingItem.Pane(L10n.tr("App groups"), Glyphs.Apps, L10n.tr("Open several apps together, sessions, schedules"), bare = true) { DockSettingsPane(DockPaneKind.Groups) },
        SettingItem.Pane(L10n.tr("Dock and external display"), Glyphs.Apps, L10n.tr("Offer, disconnect, per-display profiles"), bare = true) { DesktopPane() },
    ),
)

/**
 * Wave 52 (settingsFont): Workspaces is a top-level row of Settings (near the top), not a page buried under "Desktop & dock". The category is the one pane,
 * so the row opens the Workspaces page itself, with the active workspace under its title.
 */
fun workspacesCategory(): SettingCategory = SettingCategory(
    L10n.tr("Workspaces"),
    Glyphs.Home,
    listOf(
        SettingItem.Pane(
            L10n.tr("Workspaces"), Glyphs.Home, L10n.tr("Separate home setups to switch between"), bare = true,
            pageSubtitle = { WorkspaceSummary.activeLine(WorkspaceRepository.get(LocalContext.current).state.collectAsState().value) },
        ) { DockSettingsPane(DockPaneKind.Workspaces) },
    ),
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

/** The external display page: what happens on connect and unplug (radio dialogs) and the displays Liftoff remembers. */
@Composable
fun ColumnScope.DesktopPane() {
    val context = LocalContext.current
    val controller = remember { DesktopController.get(context) }
    var offer by remember { mutableStateOf(controller.offerSetting) }
    var migrate by remember { mutableStateOf(controller.migrateOnDisconnect) }
    var bump by remember { mutableIntStateOf(0) }
    var offerOpen by remember { mutableStateOf(false) }
    var unplugOpen by remember { mutableStateOf(false) }

    OneUiGroup(2) {
        OneUiValueRow(L10n.tr("When a display that can host apps connects"), L10n.tr(DesktopSettingsText.offer(offer)), { offerOpen = true }, modifier = Modifier.testTag("desk.offer"))
        OneUiRowDivider()
        OneUiValueRow(L10n.tr("When that display is unplugged"), L10n.tr(DesktopSettingsText.disconnect(migrate)), { unplugOpen = true }, modifier = Modifier.testTag("desk.unplug"))
    }
    OneUiCaption(L10n.tr("The desktop's arrangement for each display is kept and comes back when it is plugged in again."))

    OneUiGroupTitle(L10n.tr("Displays Liftoff remembers"))
    val identities = remember(bump) { controller.profiles.identities() }
    if (identities.isEmpty()) OneUiInfoCard(L10n.tr("None yet. A display appears here after you open the desktop on it."))
    else OneUiGroup(identities.size) {
        identities.forEachIndexed { i, id ->
            if (i > 0) OneUiRowDivider()
            var profile by remember(id, bump) { mutableStateOf(controller.profiles.load(id)) }
            DisplayProfileEditor(com.bos.sphere.feature.desktop.DisplayIdentity.label(id), profile, { p -> profile = p; controller.profiles.save(id, p) }, Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
        }
    }
    if (offerOpen) OneUiRadioPage(L10n.tr("When a display that can host apps connects"), OfferSetting.entries, offer, { L10n.tr(DesktopSettingsText.offer(it)) }, { offer = it; controller.offerSetting = it }, { offerOpen = false })
    if (unplugOpen) OneUiRadioPage(L10n.tr("When that display is unplugged"), listOf(false, true), migrate, { L10n.tr(DesktopSettingsText.disconnect(it)) }, { migrate = it; controller.migrateOnDisconnect = it }, { unplugOpen = false })
}

enum class DockPaneKind { Dock, Workspaces, Groups }

/**
 * One of the dock's pages (Dock, Workspaces, App groups) as a Ground Control page, with the dock on or off (wave 24, docs/decisions/dockOptional.md).
 * The dialog hook is installed here, so a controller's B closes the dialogs these pages open (INP-20). A group run or session restore started from
 * here must outlive Ground Control closing, so the scope is unbound, like the palette providers' scope; each run ends on its own.
 */
@Composable
internal fun ColumnScope.DockSettingsPane(kind: DockPaneKind) {
    val context = LocalContext.current
    val view = androidx.compose.ui.platform.LocalView.current
    val scope = remember { kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Main.immediate) }
    DockInputWiring.Install(withBridge = false)
    val manager = remember { WorkspaceRepository.get(context) }
    val repo = remember { com.bos.sphere.core.data.LauncherAppsAppRepository(context) }
    val apps by repo.apps.collectAsState(initial = emptyList())
    val launcher = remember { com.bos.sphere.feature.dock.AppLauncher(context, rootView = { view }) }
    val tier = remember { com.bos.sphere.core.capabilities.CapabilityEvaluator.tier(launcher.inputs()) }
    val panelApps = remember(apps) { apps.map { PanelApp(it.key, it.label, it.packageName, launcher.targetOf(it)) } }
    val wsState by manager.state.collectAsState()
    DockPagesScope {
        when (kind) {
            DockPaneKind.Dock -> DockSettingsContent(manager, tier, panelApps, extra = { DesktopPinRow(manager) })
            DockPaneKind.Workspaces -> WorkspacesContent(manager, panelApps, PinnableActions.choices())
            DockPaneKind.Groups -> AppGroupsContent(
                manager, panelApps, launcher.displays(), onRunGroup = { g -> scope.launch { launcher.runGroup(g) } }, onRestore = { s -> scope.launch { launcher.restore(s) } },
                onSchedule = { t, label, at -> scheduleFromPanel(context, manager, t, label, at) },
                groupShelfOn = wsState.dock.groupShelf, onGroupShelf = { v -> manager.setDock { it.copy(groupShelf = v) } },
            )
        }
    }
}
