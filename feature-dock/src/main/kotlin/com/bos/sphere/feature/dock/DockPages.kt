package com.bos.sphere.feature.dock

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bos.sphere.core.capabilities.Tier
import com.bos.sphere.core.design.HyleColors
import com.bos.sphere.core.design.L10n
import com.bos.sphere.core.launch.AppGroup
import com.bos.sphere.core.launch.DisplayRef
import com.bos.sphere.core.launch.GroupLayout
import com.bos.sphere.core.launch.LaunchRequest
import com.bos.sphere.core.launch.Session
import com.bos.sphere.core.launch.SessionRestorer
import com.bos.sphere.feature.grid.Glyphs
import com.bos.sphere.feature.grid.OneUiBadge
import com.bos.sphere.feature.grid.OneUiCaption
import com.bos.sphere.feature.grid.OneUiCheckPage
import com.bos.sphere.feature.grid.OneUiConfirmDialog
import com.bos.sphere.feature.grid.OneUiDialog
import com.bos.sphere.feature.grid.OneUiGap
import com.bos.sphere.feature.grid.OneUiGroup
import com.bos.sphere.feature.grid.OneUiGroupTitle
import com.bos.sphere.feature.grid.OneUiInfoCard
import com.bos.sphere.feature.grid.OneUiInlineRadio
import com.bos.sphere.feature.grid.OneUiListRow
import com.bos.sphere.feature.grid.OneUiMenuEntry
import com.bos.sphere.feature.grid.OneUiMoreButton
import com.bos.sphere.feature.grid.OneUiPrimaryButton
import com.bos.sphere.feature.grid.OneUiRadioPage
import com.bos.sphere.feature.grid.OneUiRowDivider
import com.bos.sphere.feature.grid.OneUiSegmented
import com.bos.sphere.feature.grid.OneUiSegmentedRow
import com.bos.sphere.feature.grid.OneUiSwitchRow
import com.bos.sphere.feature.grid.OneUiTextEntryDialog
import com.bos.sphere.feature.grid.OneUiUnderlineField
import com.bos.sphere.feature.grid.OneUiValueRow

/** Test tags of the dock pages. */
object DockPageTags {
    const val EXPLAIN = "ws.explain"
    const val ADD = "ws.add"
    const val CALM = "ws.calm"
    const val RADAR_ACTIONS = "ws.radarActions"
    const val SHOW_DOCK = "dock.show"
    const val MODE = "dock.mode"
    const val ADD_APPS = "dock.addApps"
    const val EDIT = "dock.edit"
    const val PREVIEW = "dock.livePreview"
    const val GROUP_NEW = "groups.new"
    const val GROUP_SHELF = "groups.shelf"
    const val SCHEDULE = "groups.schedule"
    fun workspace(id: String) = "ws.row.$id"
    fun workspaceMore(id: String) = "ws.more.$id"
    fun workspaceBadge(id: String) = "ws.badge.$id"
    fun group(id: String) = "groups.row.$id"
    fun groupMore(id: String) = "groups.more.$id"
    fun session(id: String) = "sessions.row.$id"
    fun sessionMore(id: String) = "sessions.more.$id"
}

private val TileBlue = Color(0xFF4F7BE8)
private val TileGreen = Color(0xFF2FA37E)
private val TileOrange = Color(0xFFE08A3C)
private val TileViolet = Color(0xFF7B6BE0)

/** Wraps the dock pages so every One UI dialog they open is a dock dialog (INP-20); the panel and the Ground Control panes both use it. */
@Composable
fun DockPagesScope(content: @Composable () -> Unit) {
    androidx.compose.runtime.CompositionLocalProvider(com.bos.sphere.feature.grid.LocalOneUiDialogHook provides DockDialogHook, content = content)
}

/**
 * The Dock page (Settings > Desktop & dock > Dock). W55 (dockSheet): a live preview of the dock as it is now, then every dock option as the same cards and
 * switches the dock's sheet shows ([DockOptionsBody]), then the pins (Edit dock list, Add apps), [extra] (the Desktop item), the way on to Workspaces and
 * App groups, and the way back to the device default. Both surfaces write the one store, `manager.setDock`.
 */
@Composable
fun DockSettingsContent(
    manager: WorkspaceManager,
    tier: Tier,
    apps: List<PanelApp>,
    extra: (@Composable () -> Unit)? = null,
    onOpenWorkspaces: (() -> Unit)? = null,
    onOpenGroups: (() -> Unit)? = null,
) {
    val state by manager.state.collectAsState()
    var addingPins by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    val landscape = androidx.compose.ui.platform.LocalConfiguration.current.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

    DockLivePreview(manager, apps, landscape)
    OneUiGap()
    DockOptionsBody(manager, tier, DockOptionsScope(), landscape = landscape)
    OneUiGap()
    OneUiGroup(2) {
<<<<<<< HEAD
        OneUiValueRow(L10n.tr("Dock mode"), L10n.tr(state.dock.mode.label), { pickingMode = true }, modifier = Modifier.testTag(DockPageTags.MODE))
        OneUiRowDivider()
        com.bos.sphere.feature.grid.OneUiVisualChoiceRow(
            L10n.tr("Dock position"), DockEdge.entries, state.dock.edge, { e -> manager.setDock { it.copy(edge = e) } },
            { L10n.tr(it.label) }, { DockEdgeArt.edge.art(it) }, tag = "dock.edge",
        )
    }
    OneUiCaption(L10n.tr("In portrait the dock is always at the bottom. In landscape it can also sit on a side."))
    OneUiGap()
    OneUiGroup(1) {
=======
        OneUiListRow(L10n.tr("Edit dock"), summary = L10n.tr("Reorder or remove the pinned apps"), tile = Glyphs.Sliders, tileColor = TileViolet, chevron = true, onClick = { editing = true }, modifier = Modifier.testTag(DockPageTags.EDIT))
        OneUiRowDivider(72.dp)
>>>>>>> w55-dockSheet
        // W26: an empty dock is hidden, so this is also the way to the first pin from Ground Control.
        OneUiListRow(L10n.tr("Add apps to the dock"), tile = Glyphs.Apps, tileColor = TileBlue, chevron = true, onClick = { addingPins = true }, modifier = Modifier.testTag(DockPageTags.ADD_APPS))
    }
    if (extra != null) { OneUiGap(); OneUiGroup(1) { extra() } }
    if (onOpenWorkspaces != null || onOpenGroups != null) {
        OneUiGap()
        OneUiGroup(listOfNotNull(onOpenWorkspaces, onOpenGroups).size) {
            if (onOpenWorkspaces != null) OneUiListRow(L10n.tr("Workspaces"), summary = WorkspaceSummary.activeLine(state), tile = Glyphs.Home, tileColor = TileGreen, chevron = true, onClick = onOpenWorkspaces, modifier = Modifier.testTag("dock.openWorkspaces"))
            if (onOpenWorkspaces != null && onOpenGroups != null) OneUiRowDivider(72.dp)
            if (onOpenGroups != null) OneUiListRow(L10n.tr("App groups"), summary = if (state.groups.size == 1) L10n.tr("1 group") else L10n.tr("%d groups", state.groups.size), tile = Glyphs.Apps, tileColor = TileOrange, chevron = true, onClick = onOpenGroups, modifier = Modifier.testTag("dock.openGroups"))
        }
    }
    DockUseDefaultRow(manager)
    if (editing) {
        val byKey = remember(apps) { apps.associateBy { it.key } }
        val pinned = state.active.dockPins.mapNotNull { ref ->
            val label = when (ref.kind) { ItemKind.App -> byKey[ref.id]?.label; ItemKind.Group -> state.groups.firstOrNull { it.id == ref.id }?.name; else -> ref.id }
            label?.let { DockItem(ref, it) }
        }
        EditDockDialog(
            DockContent(pinned, emptyList(), showEnableRow = false, showRecentSection = false, collapsed = false),
            DockActions(onReorder = { f, t -> manager.setPins(DockPins.move(manager.current.active.dockPins, f, t)) }, onUnpin = { manager.setPins(DockPins.remove(manager.current.active.dockPins, it.ref)) }, onAddApps = { addingPins = true }),
        ) { editing = false }
    }
    if (addingPins) AppPicker(
        L10n.tr("Add apps to the dock"), apps, state.active.dockPins.filter { it.kind == ItemKind.App }.map { it.id }.toSet(), max = Int.MAX_VALUE, min = 0,
        onDone = { picked -> manager.setPins(DockPinPicking.apply(state.active.dockPins, picked, apps)); addingPins = false },
        onDismiss = { addingPins = false },
    )
}

/** The Workspaces page: what a workspace is, the list of them, adding one, and what the calm centre shows for the active one. */
@Composable
fun WorkspacesContent(manager: WorkspaceManager, apps: List<PanelApp>, paletteActionChoices: List<Pair<String, String>> = emptyList(), onOpenGroups: (() -> Unit)? = null) {
    val state by manager.state.collectAsState()
    val rows = WorkspaceSummary.rows(state, manager.liveHome())
    var menuFor by remember { mutableStateOf<String?>(null) }
    var renaming by remember { mutableStateOf<Workspace?>(null) }
    var deleting by remember { mutableStateOf<Workspace?>(null) }
    var adding by remember { mutableStateOf(false) }
    var calmOpen by remember { mutableStateOf(false) }
    var pickingApps by remember { mutableStateOf(false) }
    var pickingGroup by remember { mutableStateOf(false) }
    var actionsOpen by remember { mutableStateOf(false) }
    val active = state.active

    OneUiInfoCard(
        L10n.tr("Workspaces are separate home setups, like Work and Personal. Each has its own home pages, dock, widgets and calm-centre apps. Switch between them here, from Radar or from the dock's menu."),
        DockPageTags.EXPLAIN,
    )
    OneUiGroupTitle(L10n.tr("Your workspaces"))
    OneUiGroup(rows.size) {
        rows.forEachIndexed { i, r ->
            if (i > 0) OneUiRowDivider()
            val w = state.workspaces.first { it.id == r.id }
            val entries = buildList {
                if (!r.active) add(OneUiMenuEntry(L10n.tr("Make active")) { manager.switchTo(r.id) })
                add(OneUiMenuEntry(L10n.tr("Rename")) { renaming = w })
                add(OneUiMenuEntry(L10n.tr("Duplicate")) { manager.create(WorkspaceSummary.copyName(w.name, state.workspaces.map { it.name }), r.id) })
                if (state.workspaces.size > 1) add(OneUiMenuEntry(L10n.tr("Delete"), destructive = true) { deleting = w })
            }
            OneUiListRow(
                r.name, summary = r.summary,
                onClick = { if (!r.active) manager.switchTo(r.id) },
                modifier = Modifier.testTag(DockPageTags.workspace(r.id)).semantics(mergeDescendants = false) { if (r.active) stateDescription = L10n.tr("In use") },
                trailing = {
                    if (r.active) OneUiBadge(L10n.tr("In use"), Modifier.testTag(DockPageTags.workspaceBadge(r.id)))
                    OneUiMoreButton(L10n.tr("More options for %s", r.name), menuFor == r.id, { open -> menuFor = if (open) r.id else null }, entries, tag = DockPageTags.workspaceMore(r.id))
                },
            )
        }
    }
    OneUiGap()
    OneUiPrimaryButton(L10n.tr("Add workspace"), { adding = true }, Modifier.testTag(DockPageTags.ADD))

    OneUiGroupTitle(L10n.tr("Calm centre"))
    OneUiGroup(if (paletteActionChoices.isEmpty()) 1 else 2) {
        OneUiValueRow(L10n.tr("What the calm centre shows"), WorkspaceSummary.calmValue(active), { calmOpen = true }, modifier = Modifier.testTag(DockPageTags.CALM))
        if (paletteActionChoices.isNotEmpty()) {
            OneUiRowDivider()
            val n = active.paletteActions.count { id -> paletteActionChoices.any { it.first == id } }
            OneUiValueRow(L10n.tr("Radar actions"), if (n == 0) L10n.tr("None") else L10n.tr("%d pinned", n), { actionsOpen = true }, modifier = Modifier.testTag(DockPageTags.RADAR_ACTIONS))
        }
    }
    OneUiCaption(L10n.tr("The calm centre and the Radar actions are set for %s, the active workspace. This only changes what Liftoff shows. System notification settings are not touched.", active.name))
    val widgetCount = active.widgets.count { it != WorkspaceWidgets.MARKER }
    OneUiCaption(if (active.widgets.isEmpty()) L10n.tr("Widgets: saved with this workspace the first time you leave it.") else L10n.tr("Widgets: %d saved with this workspace.", widgetCount))
    if (onOpenGroups != null) {
        OneUiGap()
        OneUiGroup(1) {
            OneUiListRow(L10n.tr("App groups"), summary = if (state.groups.size == 1) L10n.tr("1 group") else L10n.tr("%d groups", state.groups.size), tile = Glyphs.Apps, tileColor = TileOrange, chevron = true, onClick = onOpenGroups, modifier = Modifier.testTag("dock.openGroups"))
        }
    }

    if (adding) {
        var copy by remember { mutableStateOf(false) }
        OneUiTextEntryDialog(
            L10n.tr("Add workspace"), "", L10n.tr("Workspace name"), L10n.tr("Add"),
            onConfirm = { name -> manager.create(name, if (copy) state.activeId else null); adding = false },
            onDismiss = { adding = false },
            error = { n -> if (n.isNotEmpty() && WorkspaceSummary.nameTaken(n, state.workspaces.map { it.name })) L10n.tr("A workspace with this name already exists") else null },
        ) {
            Text(L10n.tr("Start from"), color = HyleColors.InkDim, fontSize = 13.sp)
            OneUiInlineRadio(L10n.tr("An empty home"), !copy, "ws.copy.empty") { copy = false }
            OneUiInlineRadio(L10n.tr("A copy of %s", active.name), copy, "ws.copy.current") { copy = true }
        }
    }
    renaming?.let { w ->
        OneUiTextEntryDialog(
            L10n.tr("Rename workspace"), w.name, L10n.tr("Workspace name"), L10n.tr("Rename"),
            onConfirm = { name -> manager.rename(w.id, name); renaming = null }, onDismiss = { renaming = null },
            error = { n -> if (n.isNotEmpty() && WorkspaceSummary.nameTaken(n, state.workspaces.map { it.name }, except = w.name)) L10n.tr("A workspace with this name already exists") else null },
        )
    }
    deleting?.let { w ->
        OneUiConfirmDialog(
            L10n.tr("Delete %s?", w.name),
            L10n.tr("Its saved home pages, dock and widgets are removed. The apps themselves are not touched."),
            L10n.tr("Delete"), { manager.delete(w.id); deleting = null }, { deleting = null }, destructive = true,
        )
    }
    if (calmOpen) {
        val opts = buildList { add(CalmChoice.All); add(CalmChoice.Chosen); if (state.groups.isNotEmpty()) add(CalmChoice.Group) }
        OneUiRadioPage(
            L10n.tr("What the calm centre shows"), opts, if (active.notificationApps == null) CalmChoice.All else CalmChoice.Chosen, { L10n.tr(it.label) },
            onSelect = { c ->
                when (c) {
                    CalmChoice.All -> manager.setNotificationApps(active.id, null)
                    CalmChoice.Chosen -> pickingApps = true
                    CalmChoice.Group -> pickingGroup = true
                }
            },
            onDismiss = { calmOpen = false },
            detail = { c -> c.detail?.let { L10n.tr(it) } },
            body = L10n.tr("Notifications from these apps can appear in the calm centre while %s is active.", active.name),
        )
    }
    if (pickingApps) AppPicker(
        L10n.tr("Apps shown in the calm centre"), apps, active.notificationApps ?: emptySet(), max = Int.MAX_VALUE, min = 0,
        onDone = { picked -> manager.setNotificationApps(active.id, picked); pickingApps = false }, onDismiss = { pickingApps = false },
    )
    if (pickingGroup) OneUiRadioPage(
        L10n.tr("Apps of which group?"), state.groups, null, { "${it.name} · ${it.members.size}" },
        onSelect = { g -> manager.setNotificationApps(active.id, g.members.map { it.target.packageName }.toSet()) }, onDismiss = { pickingGroup = false },
        body = L10n.tr("The group's apps are copied now. Later changes to the group do not follow."),
    )
    if (actionsOpen) OneUiCheckPage(
        L10n.tr("Radar actions"), paletteActionChoices.map { it.first }, active.paletteActions.toSet(), { id -> L10n.tr(paletteActionChoices.first { it.first == id }.second) },
        onDone = { picked -> manager.setPaletteActions(active.id, paletteActionChoices.map { it.first }.filter { it in picked }); actionsOpen = false },
        onDismiss = { actionsOpen = false },
        body = L10n.tr("Pinned actions show in Radar before you type, only while this workspace is active."),
    )
}

/** What the calm centre can show. Only what the workspace model holds: every app, or a set of apps. */
private enum class CalmChoice(val label: String, val detail: String?) {
    All("All apps", "Every app's notifications can appear"),
    Chosen("Only apps I choose", null),
    Group("Apps of a group", "Copies the group's apps once"),
}

/** The App groups page: the groups, adding one, showing them on the home, scheduled launches and saved sessions. */
@Composable
fun AppGroupsContent(
    manager: WorkspaceManager,
    apps: List<PanelApp>,
    displays: List<DisplayRef>,
    onRunGroup: (AppGroup) -> Unit,
    onRestore: (Session) -> Unit,
    onSchedule: (RunTarget, String, Long) -> Unit,
    now: () -> Long = System::currentTimeMillis,
    groupShelfOn: Boolean = true,
    onGroupShelf: (Boolean) -> Unit = {},
) {
    val state by manager.state.collectAsState()
    val active = state.active
    var menuFor by remember { mutableStateOf<String?>(null) }
    var editingGroup by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<AppGroup?>(null) }
    var sessionFor by remember { mutableStateOf<AppGroup?>(null) }
    var scheduleFor by remember { mutableStateOf<Pair<RunTarget, String>?>(null) }
    var pickingScheduleApp by remember { mutableStateOf(false) }

    OneUiGroupTitle(L10n.tr("Your groups"))
    if (state.groups.isEmpty()) OneUiInfoCard(L10n.tr("A group opens several apps together, tiled or full size. Make one to start."), "groups.empty")
    else OneUiGroup(state.groups.size) {
        state.groups.forEachIndexed { i, g ->
            if (i > 0) OneUiRowDivider(72.dp)
            val pinned = DockItemRef.group(g.id) in active.dockPins
            val entries = listOf(
                OneUiMenuEntry(L10n.tr("Open")) { onRunGroup(g) },
                OneUiMenuEntry(if (pinned) L10n.tr("Unpin from dock") else L10n.tr("Pin to dock")) {
                    manager.setPins(if (pinned) DockPins.remove(active.dockPins, DockItemRef.group(g.id)) else DockPins.add(active.dockPins, DockItemRef.group(g.id)))
                },
                OneUiMenuEntry(L10n.tr("Save session")) { sessionFor = g },
                OneUiMenuEntry(L10n.tr("Remind me")) { scheduleFor = RunTarget(RunKind.Group, g.id) to g.name },
                OneUiMenuEntry(L10n.tr("Delete"), destructive = true) { deleting = g },
            )
            OneUiListRow(
                g.name, summary = (if (g.members.size == 1) L10n.tr("1 app") else L10n.tr("%d apps", g.members.size)) + " · " + L10n.tr(g.layout.label) + if (pinned) " · " + L10n.tr("In the dock") else "",
                tile = Glyphs.Apps, tileColor = TileOrange, onClick = { onRunGroup(g) }, modifier = Modifier.testTag(DockPageTags.group(g.id)),
                trailing = { OneUiMoreButton(L10n.tr("More options for %s", g.name), menuFor == g.id, { open -> menuFor = if (open) g.id else null }, entries, tag = DockPageTags.groupMore(g.id)) },
            )
        }
    }
    OneUiGap()
    OneUiPrimaryButton(L10n.tr("New group"), { editingGroup = true }, Modifier.testTag(DockPageTags.GROUP_NEW))
    OneUiGap()
    OneUiGroup(2) {
        OneUiSwitchRow(L10n.tr("Show groups on the home"), groupShelfOn, onGroupShelf, modifier = Modifier.testTag(DockPageTags.GROUP_SHELF))
        OneUiRowDivider()
        OneUiListRow(L10n.tr("Schedule an app"), tile = Glyphs.Clock, tileColor = TileViolet, chevron = true, onClick = { pickingScheduleApp = true }, modifier = Modifier.testTag(DockPageTags.SCHEDULE))
    }
    OneUiCaption(L10n.tr("A scheduled launch posts a notification with an Open action at the chosen time. Nothing opens on its own."))

    OneUiGroupTitle(L10n.tr("Sessions"))
    if (state.sessions.isEmpty()) OneUiCaption(L10n.tr("A session saves where a group's apps were placed."))
    else OneUiGroup(state.sessions.size) {
        state.sessions.forEachIndexed { i, s ->
            if (i > 0) OneUiRowDivider()
            val entries = listOf(
                OneUiMenuEntry(L10n.tr("Restore")) { onRestore(s) },
                OneUiMenuEntry(L10n.tr("Delete"), destructive = true) { manager.deleteSession(s.id) },
            )
            OneUiListRow(
                s.name, onClick = { onRestore(s) }, modifier = Modifier.testTag(DockPageTags.session(s.id)),
                trailing = { OneUiMoreButton(L10n.tr("More options for %s", s.name), menuFor == s.id, { open -> menuFor = if (open) s.id else null }, entries, tag = DockPageTags.sessionMore(s.id)) },
            )
        }
    }
    OneUiCaption(L10n.tr(SessionRestorer.APPS_ONLY_NOTICE))

    scheduleFor?.let { (t, label) ->
        ScheduleDialog(label, now(), onDismiss = { scheduleFor = null }, onPick = { at -> onSchedule(t, label, at); scheduleFor = null })
    }
    if (pickingScheduleApp) AppPicker(
        L10n.tr("Which app?"), apps, emptySet(), max = 1, min = 1,
        onDone = { picked -> pickingScheduleApp = false; apps.firstOrNull { it.key in picked }?.let { scheduleFor = RunTarget(RunKind.App, it.key) to it.label } },
        onDismiss = { pickingScheduleApp = false },
    )
    if (editingGroup) GroupEditor(apps, onSave = { manager.saveGroup(it); editingGroup = false }, onDismiss = { editingGroup = false })
    deleting?.let { g ->
        OneUiConfirmDialog(L10n.tr("Delete %s?", g.name), L10n.tr("The group is removed from the dock and the home. The apps themselves are not touched."), L10n.tr("Delete"),
            { manager.deleteGroup(g.id); deleting = null }, { deleting = null }, destructive = true)
    }
    sessionFor?.let { g ->
        SessionDialog(g, displays, onSave = { name, assign ->
            manager.saveSession(SessionRestorer.capture(manager.idFor("s"), name, g, { i -> assign[i] }, now())); sessionFor = null
        }, onDismiss = { sessionFor = null })
    }
}

@Composable
fun GroupEditor(apps: List<PanelApp>, onSave: (AppGroup) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var layout by remember { mutableStateOf(GroupLayout.Tile) }
    var chosen by remember { mutableStateOf(emptySet<String>()) }
    var picking by remember { mutableStateOf(false) }
    val valid = chosen.size in 2..4 && name.isNotBlank()
    DockPagesScope {
        com.bos.sphere.feature.grid.OneUiFullPage(onDismiss) {
            com.bos.sphere.feature.grid.OneUiPageFrame(L10n.tr("New app group"), onDismiss, tag = DockPanelTags.GROUP_PAGE) {
                item(key = "form") {
                    Column(Modifier.padding(horizontal = 32.dp)) {
                        OneUiUnderlineField(name, { name = it }, L10n.tr("Group name"))
                        Spacer(Modifier.size(16.dp))
                        OneUiSegmented(GroupLayout.entries.map { L10n.tr(it.label) }, GroupLayout.entries.indexOf(layout), { layout = GroupLayout.entries[it] }, description = L10n.tr("Layout"))
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(role = Role.Button) { picking = true }.padding(top = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(L10n.tr("Apps"), color = HyleColors.InkBright, fontSize = 17.sp)
                                Text(apps.filter { it.key in chosen }.joinToString { it.label }.ifEmpty { L10n.tr("Choose 2 to 4 apps") }, color = HyleColors.InkDim, fontSize = 13.sp)
                            }
                        }
                    }
                }
                item(key = "save") {
                    Column {
                        com.bos.sphere.feature.grid.OneUiGap()
                        com.bos.sphere.feature.grid.OneUiPrimaryButton(
                            L10n.tr("Save"),
                            {
                                val members = apps.filter { it.key in chosen }.map { LaunchRequest(it.target) }
                                onSave(AppGroup(name.trim(), members, layout, "g" + System.currentTimeMillis().toString(36)))
                            },
                            Modifier.testTag(com.bos.sphere.feature.grid.OneUiDialogTags.CONFIRM), glyph = com.bos.sphere.feature.grid.Glyphs.Check, enabled = valid,
                        )
                    }
                }
            }
        }
    }
    if (picking) AppPicker(L10n.tr("Pick 2 to 4 apps"), apps, chosen, max = 4, min = 2, onDone = { chosen = it; picking = false }, onDismiss = { picking = false })
}

/**
 * The wide app picker answers with package names (the same picker feeds the calm centre, which stores packages), while a dock pin stores the app's key.
 * W55: the pick is mapped back to keys here, so keeping an app that is already pinned really keeps it (a key never equals a package name) and a new one is
 * pinned by its key, the id the rail resolves.
 */
object DockPinPicking {
    fun apply(pins: List<DockItemRef>, picked: Set<String>, apps: List<PanelApp>): List<DockItemRef> {
        fun pkgOf(id: String) = apps.firstOrNull { it.key == id }?.packageName ?: id
        val keep = pins.filter { it.kind != ItemKind.App || pkgOf(it.id) in picked || it.id in picked }
        val have = keep.filter { it.kind == ItemKind.App }.mapTo(HashSet()) { pkgOf(it.id) }
        return picked.filter { it !in have }.fold(keep) { acc, p -> DockPins.add(acc, DockItemRef.app(apps.firstOrNull { it.packageName == p || it.key == p }?.key ?: p)) }
    }
}

@Composable
fun AppPicker(title: String, apps: List<PanelApp>, selected: Set<String>, max: Int, min: Int, onDone: (Set<String>) -> Unit, onDismiss: () -> Unit) {
    val wide = max == Int.MAX_VALUE
    val idOf = { a: PanelApp -> if (wide) a.packageName else a.key }
    val start = apps.filter { it.key in selected || it.packageName in selected }.toSet()
    DockPagesScope {
        OneUiCheckPage(
            title, apps, start, { it.label }, onDone = { picked -> onDone(picked.map(idOf).toSet()) }, onDismiss = onDismiss, min = min, max = max,
        )
    }
}

@Composable
fun SessionDialog(group: AppGroup, displays: List<DisplayRef>, onSave: (String, Map<Int, DisplayRef?>) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(group.name) }
    val assign = remember { mutableStateListOf<Int>().also { l -> repeat(group.members.size) { l.add(-1) } } }
    DockPagesScope {
        com.bos.sphere.feature.grid.OneUiFullPage(onDismiss) {
            com.bos.sphere.feature.grid.OneUiPageFrame(L10n.tr("Save session"), onDismiss, tag = DockPanelTags.SESSION_PAGE) {
                item(key = "form") {
                    Column(Modifier.padding(horizontal = 32.dp)) {
                        OneUiUnderlineField(name, { name = it }, L10n.tr("Session name"))
                        group.members.forEachIndexed { i, m ->
                            Row(
                                Modifier.fillMaxWidth().heightIn(min = 56.dp)
                                    .clickable(role = Role.Button) { assign[i] = if (assign[i] + 1 >= displays.size) -1 else assign[i] + 1 },
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(m.target.packageName, color = HyleColors.InkBright, fontSize = 15.sp, modifier = Modifier.weight(1f))
                                Text(assign[i].let { if (it < 0) L10n.tr("This display") else displays.getOrNull(it)?.name ?: "?" }, color = HyleColors.Violet, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                        Text(L10n.tr(SessionRestorer.APPS_ONLY_NOTICE), color = HyleColors.InkDim, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
                    }
                }
                item(key = "save") {
                    Column {
                        com.bos.sphere.feature.grid.OneUiGap()
                        com.bos.sphere.feature.grid.OneUiPrimaryButton(
                            L10n.tr("Save"),
                            { onSave(name.ifBlank { group.name }, assign.withIndex().associate { (i, d) -> i to displays.getOrNull(d) }) },
                            Modifier.testTag(com.bos.sphere.feature.grid.OneUiDialogTags.CONFIRM), glyph = com.bos.sphere.feature.grid.Glyphs.Check,
                        )
                    }
                }
            }
        }
    }
}
