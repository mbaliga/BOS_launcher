package com.bos.sphere.feature.dock

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.bos.sphere.core.design.HyleColors
import com.bos.sphere.core.design.L10n
import com.bos.sphere.core.launch.AppGroup
import com.bos.sphere.core.launch.DisplayRef
import com.bos.sphere.core.launch.LaunchTarget
import com.bos.sphere.core.launch.Session
import com.bos.sphere.feature.grid.OneUiPageFrame

/** An app the panel can put in a group or a notification filter. */
data class PanelApp(val key: String, val label: String, val packageName: String, val target: LaunchTarget)

/** Where the dock panel's own navigation is. */
enum class DockPanelPage { Workspaces, Groups }

object DockPanelTags {
    const val PAGE = "dockpanel.page"
    /** Wave 48: the Edit dock page, the group editor page and the session page. */
    const val EDIT_PAGE = "dockpanel.editPage"
    const val GROUP_PAGE = "dockpanel.groupEditor"
    const val SESSION_PAGE = "dockpanel.sessionPage"
}

/** The dock's window hook (INP-20) as the One UI dialogs' hook, so every dialog the pages open is a dock dialog. */
internal val DockDialogHook: @Composable () -> Unit = { DockDialogInput() }

/**
 * Wave 41 (settingsPopups): a full page in the One UI chrome (back arrow, large collapsing title, grouped rounded cards). W55 (dockSheet): it is the
 * dock sheet's "Profiles and groups" page: Profiles first (wave 57: Workspaces became Profiles), with a row on to App groups. The dock's own options (style, position, mode, ...) are in
 * the sheet and in Settings > Desktop & dock > Dock; nothing here is needed to use the dock.
 */
@Composable
fun DockPanel(
    manager: WorkspaceManager,
    apps: List<PanelApp>,
    displays: List<DisplayRef>,
    onRunGroup: (AppGroup) -> Unit,
    onRestore: (Session) -> Unit,
    onSchedule: (RunTarget, String, Long) -> Unit,
    onDismiss: () -> Unit,
    now: () -> Long = System::currentTimeMillis,
    /** Launcher actions the palette can pin per workspace (id, title); empty hides the row (WSP-07). */
    paletteActionChoices: List<Pair<String, String>> = emptyList(),
    /** Groups on the home as a shelf of chips (WSP-05). */
    groupShelfOn: Boolean = true,
    onGroupShelf: (Boolean) -> Unit = {},
    /** Wave 57: what the app group suggester knows about each app (category, work profile); null derives it from [apps] alone. */
    facts: List<com.bos.sphere.core.data.SuggestApp>? = null,
) {
    var page by remember { mutableStateOf(DockPanelPage.Workspaces) }
    val state by manager.state.collectAsState()
    val back = { if (page != DockPanelPage.Workspaces) page = DockPanelPage.Workspaces else onDismiss() }
    Dialog(onDismissRequest = back, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        DockDialogInput() // INP-20: controller window hook and the shared focus layer
        DockPagesScope {
            Box(Modifier.fillMaxSize().background(HyleColors.Background)) {
                when (page) {
                    DockPanelPage.Workspaces -> OneUiPageFrame(L10n.tr("Profiles"), back, subtitle = WorkspaceSummary.activeLine(state), tag = DockPanelTags.PAGE) {
                        item(key = "ws") { Column { ProfilesContent(manager, apps, facts ?: apps.map { com.bos.sphere.core.data.SuggestApp(it.key, it.packageName, it.label) }, paletteActionChoices, onOpenGroups = { page = DockPanelPage.Groups }) } }
                    }
                    DockPanelPage.Groups -> OneUiPageFrame(L10n.tr("App groups"), back, tag = DockPanelTags.PAGE) {
                        item(key = "groups") { Column { AppGroupsContent(manager, apps, displays, onRunGroup, onRestore, onSchedule, now, groupShelfOn, onGroupShelf) } }
                    }
                }
            }
        }
    }
}
