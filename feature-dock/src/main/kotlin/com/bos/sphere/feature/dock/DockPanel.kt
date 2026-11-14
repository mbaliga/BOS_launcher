package com.bos.sphere.feature.dock

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.bos.sphere.core.capabilities.Tier
import com.bos.sphere.core.design.HyleColors
import com.bos.sphere.core.design.L10n
import com.bos.sphere.core.launch.AppGroup
import com.bos.sphere.core.launch.DisplayRef
import com.bos.sphere.core.launch.GroupLayout
import com.bos.sphere.core.launch.LaunchRequest
import com.bos.sphere.core.launch.LaunchTarget
import com.bos.sphere.core.launch.Session
import com.bos.sphere.core.launch.SessionRestorer

/** An app the panel can put in a group or a notification filter. */
data class PanelApp(val key: String, val label: String, val packageName: String, val target: LaunchTarget)

/**
 * Dock settings, workspaces, app groups, sessions (WSP-05, WSP-07 to WSP-11) in one dialog. Reached from the dock's
 * overflow button and from the workspace chip; nothing here is needed to use the dock.
 */
@Composable
fun DockPanel(
    manager: WorkspaceManager,
    tier: Tier,
    apps: List<PanelApp>,
    displays: List<DisplayRef>,
    onRunGroup: (AppGroup) -> Unit,
    onRestore: (Session) -> Unit,
    onSchedule: (RunTarget, String, Long) -> Unit,
    onDismiss: () -> Unit,
    now: () -> Long = System::currentTimeMillis,
) {
    val state by manager.state.collectAsState()
    var editingGroup by remember { mutableStateOf(false) }
    var pickingNotify by remember { mutableStateOf(false) }
    var sessionFor by remember { mutableStateOf<AppGroup?>(null) }
    var newWorkspace by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        val shape = RoundedCornerShape(24.dp)
        Column(
            Modifier.fillMaxWidth().heightIn(max = 640.dp).clip(shape).background(HyleColors.Surface).border(1.dp, HyleColors.Hairline, shape)
                .verticalScroll(rememberScrollState()).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Heading("Dock")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(L10n.tr("Show the dock"), color = HyleColors.Ink, fontSize = 15.sp, modifier = Modifier.weight(1f))
                Switch(state.dock.isEnabled(tier), { v -> manager.setDock { it.copy(enabled = v) } })
            }
            Hint(if (state.dock.enabled == null) L10n.tr("Default for this device: %s", if (tier == Tier.T1) L10n.tr("off") else L10n.tr("on")) else L10n.tr("Set by you"))
            Chips(DockMode.entries.map { it.label }, DockMode.entries.indexOf(state.dock.mode)) { i -> manager.setDock { it.copy(mode = DockMode.entries[i]) } }
            Chips(DockEdge.entries.map { it.label }, DockEdge.entries.indexOf(state.dock.edge)) { i -> manager.setDock { it.copy(edge = DockEdge.entries[i]) } }
            Hint(L10n.tr("In portrait the dock is always at the bottom. In landscape it can also sit on a side."))

            Heading("Workspaces")
            state.workspaces.forEach { w ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text((if (w.id == state.activeId) "● " else "") + w.name, color = HyleColors.InkBright, fontSize = 15.sp, modifier = Modifier.weight(1f))
                    if (w.id != state.activeId) { Btn("Switch") { manager.switchTo(w.id) }; Btn("Delete") { manager.delete(w.id) } }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Field(newWorkspace, { newWorkspace = it }, L10n.tr("New workspace name"), Modifier.weight(1f))
                Btn("Add") { if (newWorkspace.isNotBlank()) { manager.create(newWorkspace); newWorkspace = "" } }
            }
            val active = state.active
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (active.notificationApps == null) L10n.tr("Calm centre shows all apps") else L10n.tr("Calm centre shows %d chosen apps", active.notificationApps.size),
                    color = HyleColors.Ink, fontSize = 14.sp, modifier = Modifier.weight(1f),
                )
                Btn("Choose") { pickingNotify = true }
                if (active.notificationApps != null) Btn("All") { manager.setNotificationApps(active.id, null) }
            }
            Hint(L10n.tr("This only changes what Liftoff shows. System notification settings are not touched."))

            Heading("App groups")
            state.groups.forEach { g ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("${g.name} · ${g.members.size}", color = HyleColors.InkBright, fontSize = 15.sp, modifier = Modifier.weight(1f))
                    Btn("Open") { onRunGroup(g) }
                    Btn(if (DockItemRef.group(g.id) in active.dockPins) "Unpin" else "Pin") {
                        manager.setPins(if (DockItemRef.group(g.id) in active.dockPins) DockPins.remove(active.dockPins, DockItemRef.group(g.id)) else DockPins.add(active.dockPins, DockItemRef.group(g.id)))
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Btn("Save session") { sessionFor = g }
                    Btn("Remind in 1 h") { onSchedule(RunTarget(RunKind.Group, g.id), g.name, now() + 3_600_000L) }
                    Btn("Delete") { manager.deleteGroup(g.id) }
                }
            }
            Btn("New group") { editingGroup = true }

            Heading("Sessions")
            if (state.sessions.isEmpty()) Hint(L10n.tr("A session saves where a group's apps were placed."))
            state.sessions.forEach { s ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(s.name, color = HyleColors.InkBright, fontSize = 15.sp, modifier = Modifier.weight(1f))
                    Btn("Restore") { onRestore(s) }
                    Btn("Delete") { manager.deleteSession(s.id) }
                }
            }
            Hint(L10n.tr(SessionRestorer.APPS_ONLY_NOTICE))
            Box(Modifier.align(Alignment.End)) { Btn("Done", onDismiss) }
        }
    }

    if (editingGroup) GroupEditor(apps, onSave = { manager.saveGroup(it); editingGroup = false }, onDismiss = { editingGroup = false })
    if (pickingNotify) AppPicker(
        L10n.tr("Apps shown in the calm centre"), apps, state.active.notificationApps ?: emptySet(), max = Int.MAX_VALUE, min = 0,
        onDone = { picked -> manager.setNotificationApps(state.activeId, picked); pickingNotify = false }, onDismiss = { pickingNotify = false },
    )
    sessionFor?.let { g -> SessionDialog(g, displays, onSave = { name, assign ->
        manager.saveSession(SessionRestorer.capture(manager.idFor("s"), name, g, { i -> assign[i] }, now())); sessionFor = null
    }, onDismiss = { sessionFor = null }) }
}

@Composable
fun GroupEditor(apps: List<PanelApp>, onSave: (AppGroup) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var layout by remember { mutableStateOf(GroupLayout.Tile) }
    var chosen by remember { mutableStateOf(emptySet<String>()) }
    var picking by remember { mutableStateOf(false) }
    Dialog(onDismissRequest = onDismiss) {
        val shape = RoundedCornerShape(24.dp)
        Column(Modifier.fillMaxWidth().clip(shape).background(HyleColors.Surface).border(1.dp, HyleColors.Hairline, shape).padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Heading("New app group")
            Field(name, { name = it }, L10n.tr("Group name"), Modifier.fillMaxWidth())
            Chips(GroupLayout.entries.map { it.label }, GroupLayout.entries.indexOf(layout)) { layout = GroupLayout.entries[it] }
            Text(apps.filter { it.key in chosen }.joinToString { it.label }.ifEmpty { L10n.tr("Choose 2 to 4 apps") }, color = HyleColors.Ink, fontSize = 14.sp)
            Row { Btn("Choose apps") { picking = true }; Spacer(Modifier.width(8.dp)); Btn("Cancel", onDismiss)
                if (chosen.size in 2..4 && name.isNotBlank()) Btn("Save") {
                    val members = apps.filter { it.key in chosen }.map { LaunchRequest(it.target) }
                    onSave(AppGroup(name.trim(), members, layout, "g" + System.currentTimeMillis().toString(36)))
                }
            }
        }
    }
    if (picking) AppPicker(L10n.tr("Pick 2 to 4 apps"), apps, chosen, max = 4, min = 2, onDone = { chosen = it; picking = false }, onDismiss = { picking = false })
}

@Composable
fun AppPicker(title: String, apps: List<PanelApp>, selected: Set<String>, max: Int, min: Int, onDone: (Set<String>) -> Unit, onDismiss: () -> Unit) {
    val picked = remember { mutableStateListOf<String>().also { it.addAll(selected) } }
    Dialog(onDismissRequest = onDismiss) {
        val shape = RoundedCornerShape(24.dp)
        Column(Modifier.fillMaxWidth().heightIn(max = 560.dp).clip(shape).background(HyleColors.Surface).border(1.dp, HyleColors.Hairline, shape).padding(18.dp)) {
            Heading(title)
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                apps.forEach { a ->
                    val on = a.key in picked || a.packageName in picked
                    Row(Modifier.fillMaxWidth().clickable { if (on) { picked.remove(a.key); picked.remove(a.packageName) } else if (picked.size < max) picked.add(if (max == Int.MAX_VALUE) a.packageName else a.key) }.padding(vertical = 8.dp).semantics { role = Role.Checkbox }, verticalAlignment = Alignment.CenterVertically) {
                        Text(if (on) "✓" else "", color = HyleColors.Violet, modifier = Modifier.width(24.dp))
                        Text(a.label, color = HyleColors.InkBright, fontSize = 15.sp)
                    }
                }
            }
            Row { Btn("Cancel", onDismiss); if (picked.size >= min) Btn("Done") { onDone(picked.toSet()) } }
        }
    }
}

@Composable
private fun SessionDialog(group: AppGroup, displays: List<DisplayRef>, onSave: (String, Map<Int, DisplayRef?>) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(group.name) }
    val assign = remember { mutableStateListOf<Int>().also { l -> repeat(group.members.size) { l.add(-1) } } }
    Dialog(onDismissRequest = onDismiss) {
        val shape = RoundedCornerShape(24.dp)
        Column(Modifier.fillMaxWidth().clip(shape).background(HyleColors.Surface).border(1.dp, HyleColors.Hairline, shape).padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Heading("Save session")
            Field(name, { name = it }, L10n.tr("Session name"), Modifier.fillMaxWidth())
            group.members.forEachIndexed { i, m ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(m.target.packageName, color = HyleColors.Ink, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    Btn(assign[i].let { if (it < 0) L10n.tr("This display") else displays.getOrNull(it)?.name ?: "?" }) { assign[i] = if (assign[i] + 1 >= displays.size) -1 else assign[i] + 1 }
                }
            }
            Hint(L10n.tr(SessionRestorer.APPS_ONLY_NOTICE))
            Row { Btn("Cancel", onDismiss); Btn("Save") { onSave(name.ifBlank { group.name }, assign.withIndex().associate { (i, d) -> i to displays.getOrNull(d) }) } }
        }
    }
}

@Composable private fun Heading(text: String) = Text(L10n.tr(text), color = HyleColors.InkBright, fontSize = 17.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 8.dp))
@Composable private fun Hint(text: String) = Text(text, color = HyleColors.InkDim, fontSize = 12.sp)

@Composable
private fun Btn(label: String, onClick: () -> Unit) {
    Text(L10n.tr(label), color = HyleColors.Violet, fontSize = 14.sp, fontWeight = FontWeight.Medium,
        modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 8.dp).semantics { role = Role.Button })
}

@Composable
private fun Chips(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        labels.forEachIndexed { i, l ->
            Text(L10n.tr(l), color = if (i == selected) HyleColors.OnAccent else HyleColors.Ink, fontSize = 13.sp,
                modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(if (i == selected) HyleColors.Violet else HyleColors.Hairline)
                    .clickable { onSelect(i) }.padding(horizontal = 12.dp, vertical = 7.dp).semantics { role = Role.RadioButton })
        }
    }
}

@Composable
private fun Field(value: String, onChange: (String) -> Unit, hint: String, modifier: Modifier) {
    Box(modifier.clip(RoundedCornerShape(12.dp)).background(HyleColors.Hairline).padding(horizontal = 12.dp, vertical = 10.dp)) {
        if (value.isEmpty()) Text(hint, color = HyleColors.InkDim, fontSize = 14.sp)
        BasicTextField(value, onChange, singleLine = true, textStyle = TextStyle(color = HyleColors.InkBright, fontSize = 14.sp), cursorBrush = SolidColor(HyleColors.Violet), modifier = Modifier.fillMaxWidth())
    }
}
