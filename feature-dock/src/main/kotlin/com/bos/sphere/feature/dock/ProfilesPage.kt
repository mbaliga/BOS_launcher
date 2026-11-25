package com.bos.sphere.feature.dock

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bos.sphere.core.data.AppGroupSuggester
import com.bos.sphere.core.data.GroupSuggestion
import com.bos.sphere.core.data.HomeLayoutCodec
import com.bos.sphere.core.data.ProfileGroup
import com.bos.sphere.core.data.ProfileStart
import com.bos.sphere.core.data.ProfileStarters
import com.bos.sphere.core.data.SuggestApp
import com.bos.sphere.core.data.SuggestionKind
import com.bos.sphere.core.design.HyleColors
import com.bos.sphere.core.design.L10n
import com.bos.sphere.core.launch.AppGroup
import com.bos.sphere.core.launch.LaunchRequest
import com.bos.sphere.feature.grid.Glyphs
import com.bos.sphere.feature.grid.OneUiBadge
import com.bos.sphere.feature.grid.OneUiCaption
import com.bos.sphere.feature.grid.OneUiCheckMark
import com.bos.sphere.feature.grid.OneUiCheckPage
import com.bos.sphere.feature.grid.OneUiConfirmDialog
import com.bos.sphere.feature.grid.OneUiFullPage
import com.bos.sphere.feature.grid.OneUiGap
import com.bos.sphere.feature.grid.OneUiGroup
import com.bos.sphere.feature.grid.OneUiGroupTitle
import com.bos.sphere.feature.grid.OneUiListRow
import com.bos.sphere.feature.grid.OneUiMenuEntry
import com.bos.sphere.feature.grid.OneUiMoreButton
import com.bos.sphere.feature.grid.OneUiPageFrame
import com.bos.sphere.feature.grid.OneUiPrimaryButton
import com.bos.sphere.feature.grid.OneUiRadioPage
import com.bos.sphere.feature.grid.OneUiRowDivider
import com.bos.sphere.feature.grid.OneUiSwitchRow
import com.bos.sphere.feature.grid.OneUiTextEntryDialog
import com.bos.sphere.feature.grid.OneUiUnderlineField
import com.bos.sphere.feature.grid.OneUiValueRow
import com.bos.sphere.feature.grid.OneUiVisualCard
import com.bos.sphere.feature.grid.VisualCardGroup
import com.bos.sphere.feature.grid.VisualInk

/** Test tags of the Profiles pages (wave 57). */
object ProfileTags {
    const val ACTIVE_CARD = "profiles.active"
    const val UNDO = "profiles.undo"
    const val NEW = "profiles.new"
    const val NEW_PAGE = "profiles.newPage"
    const val NAME = "profiles.name"
    const val CREATE = "profiles.create"
    const val SUGGESTIONS = "profiles.suggestions"
    const val SUGGESTION_PAGE = "profiles.suggestionPage"
    const val SUGGESTION_SAVE = "profiles.suggestionSave"
    const val SUGGESTION_PIN = "profiles.suggestionPin"
    const val CARRIES_PAGE = "profiles.carriesPage"
    const val CALM = "profiles.calm"
    const val RADAR_ACTIONS = "profiles.radarActions"
    const val CHIPS = "profiles.chips"
    fun chip(id: String) = "profiles.chip.$id"
    fun row(id: String) = "profiles.row.$id"
    fun more(id: String) = "profiles.more.$id"
    fun badge(id: String) = "profiles.badge.$id"
    fun start(s: ProfileStart) = "profiles.start.${s.name}"
    fun carry(g: ProfileGroup) = "profiles.carry.${g.id}"
    fun suggestion(k: SuggestionKind) = "profiles.suggestion.${k.name}"
    fun suggestionApp(key: String) = "profiles.suggestionApp.$key"
    fun pick(k: SuggestionKind) = "profiles.pick.${k.name}"
    fun edit(k: SuggestionKind) = "profiles.edit.${k.name}"
}

/** What the Profiles page says about a profile, kept pure so a test reads it without a screen. */
object ProfileSummary {
    /** "Look, Radar" or "No settings ride along". */
    fun carriesLine(w: Workspace): String {
        val g = ProfileGroup.entries.filter { it.id in w.carries }
        return if (g.isEmpty()) L10n.tr("No settings ride along") else g.joinToString(", ") { L10n.tr(it.label) }
    }

    /** The names of the app groups linked to [w] that still exist. */
    fun groupNames(w: Workspace, groups: List<AppGroup>): List<String> = w.groupLinks.mapNotNull { id -> groups.firstOrNull { it.id == id }?.name }

    /** The number of apps on the first page of a saved arrangement, or null when unknown. */
    fun firstPageApps(arrangement: String?): Int? = arrangement?.let { a ->
        runCatching { HomeLayoutCodec.decode(org.json.JSONObject(a).getString("pages")).firstOrNull()?.keys?.size }.getOrNull()
    }

    /** "Settings: Look, Radar", or "No settings ride along". */
    fun settingsLine(w: Workspace): String = if (w.carries.isEmpty()) carriesLine(w) else L10n.tr("Settings: %s", carriesLine(w))

    /** One line under a profile's name: pages and dock, then what rides along. */
    fun rowLine(r: WorkspaceSummary.Row, w: Workspace): String = r.summary + " · " + settingsLine(w)

    /** A name not already taken by another profile (the start's own name first, then "Work 2" ...). */
    fun freeName(base: String, taken: Collection<String>): String {
        if (!WorkspaceSummary.nameTaken(base, taken)) return base
        var n = 2
        while (WorkspaceSummary.nameTaken("$base $n", taken)) n++
        return "$base $n"
    }

    /** The suggestions that are not already saved as a group of the same name. */
    fun openSuggestions(all: List<GroupSuggestion>, groups: List<AppGroup>): List<GroupSuggestion> =
        all.filter { s -> groups.none { it.name.equals(L10n.tr(s.kind.label), ignoreCase = true) } }
}

private val TileViolet = Color(0xFF7B6BE0)
private val TileOrange = Color(0xFFE08A3C)

/**
 * Wave 57 (profiles, CFG-45 to CFG-49): the Profiles page. A profile is a named home setup (pages, dock, widgets, what the calm centre
 * shows) plus the settings groups it carries and the app groups linked to it; it replaces Workspaces. The active profile is drawn as a
 * card at the top; each profile can be switched to, renamed, duplicated, given settings to carry, or deleted; New profile is a page.
 * [facts] tell the offline suggester about each app (category, work profile).
 */
@Composable
fun ProfilesContent(
    manager: WorkspaceManager,
    apps: List<PanelApp>,
    facts: List<SuggestApp> = apps.map { SuggestApp(it.key, it.packageName, it.label) },
    paletteActionChoices: List<Pair<String, String>> = emptyList(),
    onOpenGroups: (() -> Unit)? = null,
) {
    val state by manager.state.collectAsState()
    val undo by manager.undo.collectAsState()
    val liveArrangement = manager.liveHome()
    val rows = WorkspaceSummary.rows(state, liveArrangement)
    var menuFor by remember { mutableStateOf<String?>(null) }
    var renaming by remember { mutableStateOf<Workspace?>(null) }
    var deleting by remember { mutableStateOf<Workspace?>(null) }
    var carriesFor by remember { mutableStateOf<Workspace?>(null) }
    var adding by remember { mutableStateOf(false) }
    var suggestionOpen by remember { mutableStateOf<GroupSuggestion?>(null) }
    var calmOpen by remember { mutableStateOf(false) }
    var pickingApps by remember { mutableStateOf(false) }
    var pickingGroup by remember { mutableStateOf(false) }
    var actionsOpen by remember { mutableStateOf(false) }
    val active = state.active
    val activeRow = rows.first { it.active }

    ActiveProfileCard(active, activeRow, ProfileSummary.groupNames(active, state.groups), liveArrangement ?: active.homeArrangement)
    undo?.takeIf { it.toId == state.activeId && state.workspaces.any { w -> w.id == it.fromId } }?.let { u ->
        OneUiGap()
        OneUiGroup(1) {
            OneUiListRow(L10n.tr("Switch back to %s", u.fromName), summary = L10n.tr("Undo the last switch"), tile = Glyphs.Home, tileColor = TileViolet, onClick = { manager.switchBack() }, modifier = Modifier.testTag(ProfileTags.UNDO))
        }
    }

    OneUiGroupTitle(L10n.tr("Your profiles"))
    OneUiGroup(rows.size) {
        rows.forEachIndexed { i, r ->
            if (i > 0) OneUiRowDivider()
            val w = state.workspaces.first { it.id == r.id }
            val entries = buildList {
                if (!r.active) add(OneUiMenuEntry(L10n.tr("Switch"), tag = "profiles.switch.${r.id}") { manager.switchTo(r.id) })
                add(OneUiMenuEntry(L10n.tr("Rename")) { renaming = w })
                add(OneUiMenuEntry(L10n.tr("Duplicate")) { manager.create(WorkspaceSummary.copyName(w.name, state.workspaces.map { it.name }), r.id) })
                add(OneUiMenuEntry(L10n.tr("Settings that ride along"), tag = "profiles.carries.${r.id}") { carriesFor = w })
                if (state.workspaces.size > 1) add(OneUiMenuEntry(L10n.tr("Delete"), destructive = true) { deleting = w })
            }
            OneUiListRow(
                r.name, summary = ProfileSummary.rowLine(r, w),
                onClick = { if (!r.active) manager.switchTo(r.id) },
                modifier = Modifier.testTag(ProfileTags.row(r.id)).semantics(mergeDescendants = false) { if (r.active) stateDescription = L10n.tr("In use") },
                trailing = {
                    if (r.active) OneUiBadge(L10n.tr("In use"), Modifier.testTag(ProfileTags.badge(r.id)))
                    OneUiMoreButton(L10n.tr("More options for %s", r.name), menuFor == r.id, { open -> menuFor = if (open) r.id else null }, entries, tag = ProfileTags.more(r.id))
                },
            )
        }
    }
    OneUiGap()
    OneUiPrimaryButton(L10n.tr("New profile"), { adding = true }, Modifier.testTag(ProfileTags.NEW))

    // CFG-47: the suggested app groups not saved yet.
    val suggestions = remember(facts, state.groups) { ProfileSummary.openSuggestions(AppGroupSuggester.suggest(facts), state.groups) }
    if (suggestions.isNotEmpty()) {
        OneUiGroupTitle(L10n.tr("Suggested app groups"))
        OneUiGroup(suggestions.size, Modifier.testTag(ProfileTags.SUGGESTIONS)) {
            suggestions.forEachIndexed { i, s ->
                if (i > 0) OneUiRowDivider()
                OneUiListRow(L10n.tr(s.kind.label), summary = appsLine(s.apps), tile = Glyphs.Apps, tileColor = TileOrange, chevron = true, onClick = { suggestionOpen = s }, modifier = Modifier.testTag(ProfileTags.suggestion(s.kind)))
            }
        }
        OneUiCaption(L10n.tr("Made on this phone from the apps' names and categories. Nothing is sent anywhere."))
    }

    OneUiGroupTitle(L10n.tr("Calm centre"))
    OneUiGroup(if (paletteActionChoices.isEmpty()) 1 else 2) {
        OneUiValueRow(L10n.tr("What the calm centre shows"), WorkspaceSummary.calmValue(active), { calmOpen = true }, modifier = Modifier.testTag(ProfileTags.CALM))
        if (paletteActionChoices.isNotEmpty()) {
            OneUiRowDivider()
            val n = active.paletteActions.count { id -> paletteActionChoices.any { it.first == id } }
            OneUiValueRow(L10n.tr("Radar actions"), if (n == 0) L10n.tr("None") else L10n.tr("%d pinned", n), { actionsOpen = true }, modifier = Modifier.testTag(ProfileTags.RADAR_ACTIONS))
        }
    }
    OneUiCaption(L10n.tr("The calm centre and the Radar actions are set for %s, the active profile. This only changes what Liftoff shows. System notification settings are not touched.", active.name))
    if (onOpenGroups != null) {
        OneUiGap()
        OneUiGroup(1) {
            OneUiListRow(L10n.tr("App groups"), summary = if (state.groups.size == 1) L10n.tr("1 group") else L10n.tr("%d groups", state.groups.size), tile = Glyphs.Apps, tileColor = TileOrange, chevron = true, onClick = onOpenGroups, modifier = Modifier.testTag("dock.openGroups"))
        }
    }

    if (adding) NewProfilePage(manager, apps, facts, onDone = { adding = false })
    suggestionOpen?.let { s ->
        SuggestionPage(s, apps, pinTo = active.name, onSave = { members, pin ->
            val g = saveSuggestedGroup(manager, s.kind, members, apps)
            if (g != null) manager.linkGroup(state.activeId, g.id, pin)
            suggestionOpen = null
        }, onDismiss = { suggestionOpen = null })
    }
    renaming?.let { w ->
        OneUiTextEntryDialog(
            L10n.tr("Rename profile"), w.name, L10n.tr("Profile name"), L10n.tr("Rename"),
            onConfirm = { name -> manager.rename(w.id, name); renaming = null }, onDismiss = { renaming = null },
            error = { n -> if (n.isNotEmpty() && WorkspaceSummary.nameTaken(n, state.workspaces.map { it.name }, except = w.name)) L10n.tr("A profile with this name already exists") else null },
        )
    }
    deleting?.let { w ->
        OneUiConfirmDialog(
            L10n.tr("Delete %s?", w.name),
            L10n.tr("Its saved home pages, dock, widgets and settings are removed. The apps themselves are not touched."),
            L10n.tr("Delete"), { manager.delete(w.id); deleting = null }, { deleting = null }, destructive = true,
        )
    }
    carriesFor?.let { w ->
        DockPagesScope {
            OneUiCheckPage(
                L10n.tr("Settings that ride along"), ProfileGroup.entries, ProfileGroup.ofIds(w.carries), { L10n.tr(it.label) },
                onDone = { picked -> manager.setCarries(w.id, picked.mapTo(LinkedHashSet()) { it.id }); carriesFor = null },
                onDismiss = { carriesFor = null },
                body = L10n.tr("While %s is active, these settings are its own. Every other setting stays the same in all profiles.", w.name),
            )
        }
    }
    if (calmOpen) {
        val opts = buildList { add(CalmPick.All); add(CalmPick.Chosen); if (state.groups.isNotEmpty()) add(CalmPick.Group) }
        OneUiRadioPage(
            L10n.tr("What the calm centre shows"), opts, if (active.notificationApps == null) CalmPick.All else CalmPick.Chosen, { L10n.tr(it.label) },
            onSelect = { c ->
                when (c) {
                    CalmPick.All -> manager.setNotificationApps(active.id, null)
                    CalmPick.Chosen -> pickingApps = true
                    CalmPick.Group -> pickingGroup = true
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
        body = L10n.tr("Pinned actions show in Radar before you type, only while this profile is active."),
    )
}

/** What the calm centre can show (the profile model holds every app, or a set of apps). */
private enum class CalmPick(val label: String, val detail: String?) {
    All("All apps", "Every app's notifications can appear"),
    Chosen("Only apps I choose", null),
    Group("Apps of a group", "Copies the group's apps once"),
}

private fun appsLine(apps: List<SuggestApp>): String {
    val shown = apps.take(4).joinToString(", ") { it.label }
    return if (apps.size > 4) L10n.tr("%s and %d more", shown, apps.size - 4) else shown
}

/** Saves [members] (2 to 4 app keys) as the App group of a suggestion; null when fewer than 2 are left. */
internal fun saveSuggestedGroup(manager: WorkspaceManager, kind: SuggestionKind, members: List<String>, apps: List<PanelApp>): AppGroup? {
    val reqs = members.mapNotNull { k -> apps.firstOrNull { it.key == k }?.let { LaunchRequest(it.target) } }.take(AppGroupSuggester.GROUP_MAX)
    if (reqs.size < AppGroupSuggester.GROUP_MIN) return null
    val name = ProfileSummary.freeName(L10n.tr(kind.label), manager.current.groups.map { it.name })
    val g = AppGroup(name, reqs, id = manager.idFor("g"))
    manager.saveGroup(g)
    return g
}

/**
 * The active profile as a drawn card: a small phone with its first page, dock and calm-centre state, and its contents in words
 * (pages and dock, the calm centre, the settings that ride along, the linked app groups).
 */
@Composable
private fun ActiveProfileCard(w: Workspace, row: WorkspaceSummary.Row, groupNames: List<String>, arrangement: String?) {
    val shape = RoundedCornerShape(26.dp)
    val ink = VisualInk.current()
    val firstPage = ProfileSummary.firstPageApps(arrangement)
    val lines = listOfNotNull(
        row.summary,
        L10n.tr("Calm centre: %s", WorkspaceSummary.calmValue(w)),
        ProfileSummary.settingsLine(w),
        groupNames.takeIf { it.isNotEmpty() }?.let { L10n.tr("App groups: %s", it.joinToString(", ")) },
    )
    Row(
        Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(shape).background(HyleColors.Surface).border(1.dp, ink.faint, shape)
            .padding(18.dp).testTag(ProfileTags.ACTIVE_CARD)
            .semantics(mergeDescendants = true) { contentDescription = L10n.tr("Active profile: %s", w.name) + ". " + lines.joinToString(". ") },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(Modifier.width(64.dp).height(104.dp)) { drawProfilePhone(ink, firstPage ?: 6, row.dockItems, w.carries.isNotEmpty(), w.notificationApps != null) }
        Spacer(Modifier.width(18.dp))
        Column(Modifier.weight(1f)) {
            Text(L10n.tr("Active profile").uppercase(), color = HyleColors.InkDim, fontSize = 12.sp, letterSpacing = 1.2.sp)
            Text(w.name, color = HyleColors.InkBright, fontSize = 24.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(vertical = 2.dp).semantics { heading() })
            lines.forEach { Text(it, color = HyleColors.InkDim, fontSize = 13.sp, lineHeight = 18.sp) }
        }
    }
}

/** The card's phone: outline, a grid of [apps] dots (at most 12), a dock bar with [dock] dots, an accent ring when settings ride along, a quiet bell when the calm centre is narrowed. */
private fun DrawScope.drawProfilePhone(ink: VisualInk, apps: Int, dock: Int, settings: Boolean, narrowed: Boolean) {
    val d = density
    val w = size.width; val h = size.height
    drawRoundRect(if (settings) ink.accent else ink.strong, Offset(1.5f * d, 1.5f * d), Size(w - 3 * d, h - 3 * d), CornerRadius(10 * d), style = Stroke(if (settings) 2.5f * d else 1.5f * d))
    val cols = 3; val dot = 7f * d; val gapX = (w - 2 * 12 * d - cols * dot) / (cols - 1)
    for (i in 0 until apps.coerceIn(0, 12)) {
        val c = i % cols; val r = i / cols
        drawRoundRect(ink.soft, Offset(12 * d + c * (dot + gapX), 16 * d + r * (dot + 6 * d)), Size(dot, dot), CornerRadius(2 * d))
    }
    val barTop = h - 20 * d
    drawRoundRect(ink.faint, Offset(8 * d, barTop), Size(w - 16 * d, 10 * d), CornerRadius(5 * d))
    for (i in 0 until dock.coerceIn(0, 4)) drawCircle(ink.strong, 2.2f * d, Offset(16 * d + i * ((w - 32 * d) / 3f), barTop + 5 * d))
    if (narrowed) drawCircle(ink.accent, 3.5f * d, Offset(w - 12 * d, 10 * d))
}

/** The art of a start card: Current is the phone with apps, Empty an empty phone, the starters a phone with their mark. */
private fun DrawScope.drawStartArt(ink: VisualInk, s: ProfileStart) {
    val d = density
    val ph = size.height * 0.82f; val pw = ph * 0.56f
    val left = (size.width - pw) / 2; val top = (size.height - ph) / 2
    drawRoundRect(ink.strong, Offset(left, top), Size(pw, ph), CornerRadius(6 * d), style = Stroke(1.5f * d))
    when (s) {
        ProfileStart.Current -> for (i in 0 until 6) drawCircle(ink.soft, 2.4f * d, Offset(left + pw * (0.28f + 0.44f * (i % 2)), top + ph * (0.22f + 0.18f * (i / 2))))
        ProfileStart.Empty -> Unit
        ProfileStart.Work -> { drawRoundRect(ink.accent, Offset(left + pw * 0.22f, top + ph * 0.36f), Size(pw * 0.56f, ph * 0.3f), CornerRadius(2 * d), style = Stroke(1.5f * d)); drawLine(ink.accent, Offset(left + pw * 0.4f, top + ph * 0.36f), Offset(left + pw * 0.4f, top + ph * 0.3f), 1.5f * d); drawLine(ink.accent, Offset(left + pw * 0.6f, top + ph * 0.36f), Offset(left + pw * 0.6f, top + ph * 0.3f), 1.5f * d) }
        ProfileStart.Calm -> { drawCircle(ink.accent, pw * 0.18f, Offset(left + pw / 2, top + ph * 0.38f), style = Stroke(1.5f * d)); drawCircle(ink.soft, 2f * d, Offset(left + pw * 0.2f, top + ph * 0.88f)); drawCircle(ink.soft, 2f * d, Offset(left + pw * 0.8f, top + ph * 0.88f)) }
        ProfileStart.Desk -> { drawRoundRect(ink.accent, Offset(left + 3 * d, top + ph * 0.25f), Size(4 * d, ph * 0.5f), CornerRadius(2 * d)); drawRoundRect(ink.soft, Offset(left + pw * 0.25f, top + ph * 0.7f), Size(pw * 0.6f, ph * 0.14f), CornerRadius(2 * d)) }
    }
}

/** The art of a carried-settings card: the group's mark. */
private fun DrawScope.drawGroupArt(ink: VisualInk, g: ProfileGroup) {
    val d = density
    val c = Offset(size.width / 2, size.height / 2)
    val r = size.height * 0.28f
    when (g) {
        ProfileGroup.Look -> { drawCircle(ink.accent, r, c); drawCircle(ink.tile, r * 0.45f, Offset(c.x + r * 0.35f, c.y - r * 0.2f)) }
        ProfileGroup.Home -> { drawRoundRect(ink.strong, Offset(c.x - r, c.y - r * 1.2f), Size(2 * r, 2.4f * r), CornerRadius(4 * d), style = Stroke(1.5f * d)); for (q in listOf(-1, 1)) for (p in listOf(-1, 1)) drawCircle(ink.accent, 2.5f * d, Offset(c.x + q * r * 0.7f, c.y + p * r * 0.95f)) }
        ProfileGroup.Radar -> { drawCircle(ink.strong, r, c, style = Stroke(1.5f * d)); drawCircle(ink.soft, r * 0.55f, c, style = Stroke(1.2f * d)); drawCircle(ink.accent, 3f * d, c) }
        ProfileGroup.SidePanel -> { drawRoundRect(ink.strong, Offset(c.x - r, c.y - r * 1.2f), Size(2 * r, 2.4f * r), CornerRadius(4 * d), style = Stroke(1.5f * d)); drawRoundRect(ink.accent, Offset(c.x - r, c.y - r * 1.2f), Size(r * 0.7f, 2.4f * r), CornerRadius(4 * d)) }
        ProfileGroup.Sound -> { drawRoundRect(ink.strong, Offset(c.x - r * 0.9f, c.y - r * 0.4f), Size(r * 0.6f, r * 0.8f), CornerRadius(2 * d)); for (i in 1..3) drawArc(ink.accent, -45f, 90f, false, Offset(c.x - r * 0.3f * i, c.y - r * 0.3f * i - r * 0f), Size(r * 0.6f * i, r * 0.6f * i), style = Stroke(1.5f * d)) }
    }
}

/**
 * CFG-45: the New profile page (a page, not a popup): the name, what it starts from (Current setup, Empty, Work, Calm, Desk),
 * which settings ride along, and the suggested app groups to make and pin to its dock.
 */
@Composable
fun NewProfilePage(manager: WorkspaceManager, apps: List<PanelApp>, facts: List<SuggestApp>, onDone: () -> Unit) {
    DockPagesScope { OneUiFullPage(onDone) { NewProfileBody(manager, apps, facts, onDone) } }
}

/** The New profile page's content (without the window, so a screenshot can draw it). */
@Composable
fun NewProfileBody(manager: WorkspaceManager, apps: List<PanelApp>, facts: List<SuggestApp>, onDone: () -> Unit) {
    val state by manager.state.collectAsState()
    val taken = state.workspaces.map { it.name }
    var start by remember { mutableStateOf(ProfileStart.Current) }
    var name by remember { mutableStateOf("") }
    var nameEdited by remember { mutableStateOf(false) }
    var carries by remember { mutableStateOf(ProfileStarters.carries(ProfileStart.Current)) }
    val suggestions = remember(facts) { AppGroupSuggester.suggest(facts) }
    var picked by remember { mutableStateOf(emptySet<SuggestionKind>()) }
    var members by remember(suggestions) { mutableStateOf(suggestions.associate { it.kind to it.preselected.map { a -> a.key } }) }
    var editing by remember { mutableStateOf<GroupSuggestion?>(null) }
    fun pickStart(s: ProfileStart) {
        start = s
        carries = ProfileStarters.carries(s)
        picked = if (s == ProfileStart.Work) picked + SuggestionKind.Work else picked
        if (!nameEdited) name = if (s == ProfileStart.Work || s == ProfileStart.Calm || s == ProfileStart.Desk) ProfileSummary.freeName(L10n.tr(s.label), taken) else ""
    }
    val valid = name.isNotBlank() && !WorkspaceSummary.nameTaken(name, taken)
    run {
            OneUiPageFrame(L10n.tr("New profile"), onDone, tag = ProfileTags.NEW_PAGE) {
                item(key = "name") {
                    Column(Modifier.padding(horizontal = 32.dp)) {
                        OneUiUnderlineField(name, { name = it; nameEdited = true }, L10n.tr("Profile name"), tag = ProfileTags.NAME)
                        if (name.isNotBlank() && !valid) Text(L10n.tr("A profile with this name already exists"), color = Color(0xFFFF7A7A), fontSize = 13.sp)
                    }
                }
                item(key = "start") {
                    Column {
                        OneUiGroupTitle(L10n.tr("Start from"))
                        VisualCardGroup(Modifier.padding(horizontal = 24.dp)) {
                            ProfileStart.entries.forEach { s ->
                                OneUiVisualCard(L10n.tr(s.label), start == s, ProfileTags.start(s), { pickStart(s) }) { ink -> Canvas(Modifier.fillMaxSize()) { drawStartArt(ink, s) } }
                            }
                        }
                        OneUiCaption(L10n.tr(start.detail))
                    }
                }
                item(key = "carries") {
                    Column {
                        OneUiGroupTitle(L10n.tr("Settings that ride along"))
                        VisualCardGroup(Modifier.padding(horizontal = 24.dp)) {
                            ProfileGroup.entries.forEach { g ->
                                OneUiVisualCard(L10n.tr(g.label), g in carries, ProfileTags.carry(g), { carries = if (g in carries) carries - g else carries + g }, multi = true) { ink -> Canvas(Modifier.fillMaxSize()) { drawGroupArt(ink, g) } }
                            }
                        }
                        OneUiCaption(
                            if (carries.isEmpty()) L10n.tr("No settings ride along: every setting stays the same in all profiles.")
                            else L10n.tr("While this profile is active these are its own: %s. Every other setting stays the same in all profiles.", ProfileGroup.entries.filter { it in carries }.joinToString(", ") { L10n.tr(it.label) }),
                        )
                    }
                }
                if (suggestions.isNotEmpty()) item(key = "groups") {
                    Column {
                        OneUiGroupTitle(L10n.tr("Suggested app groups"))
                        OneUiGroup(suggestions.size) {
                            suggestions.forEachIndexed { i, s ->
                                if (i > 0) OneUiRowDivider()
                                val on = s.kind in picked
                                val chosen = members[s.kind].orEmpty()
                                OneUiListRow(
                                    L10n.tr(s.kind.label), summary = appsLine(s.apps.filter { it.key in chosen }),
                                    onClick = { picked = if (on) picked - s.kind else picked + s.kind }, role = Role.Checkbox,
                                    modifier = Modifier.testTag(ProfileTags.pick(s.kind)).semantics { stateDescription = if (on) L10n.tr("Chosen") else L10n.tr("Not chosen") },
                                    trailing = {
                                        Text(L10n.tr("Edit"), color = HyleColors.Violet, fontSize = 15.sp, modifier = Modifier.padding(end = 12.dp).clip(RoundedCornerShape(12.dp)).testTag(ProfileTags.edit(s.kind)).clickable(role = Role.Button) { editing = s }.padding(8.dp))
                                        OneUiCheckMark(on)
                                    },
                                )
                            }
                        }
                        OneUiCaption(L10n.tr("Each chosen group is saved with App groups and pinned to this profile's dock."))
                    }
                }
                item(key = "create") {
                    Column {
                        OneUiGap()
                        OneUiPrimaryButton(L10n.tr("Create profile"), {
                            createFromPage(manager, apps, facts, name.trim(), start, carries, suggestions.filter { it.kind in picked }, members)
                            onDone()
                        }, Modifier.testTag(ProfileTags.CREATE), glyph = Glyphs.Check, enabled = valid)
                        OneUiGap()
                    }
                }
            }
    }
    editing?.let { s ->
        SuggestionPage(s, apps, pinTo = null, initial = members[s.kind].orEmpty(), onSave = { m, _ -> members = members + (s.kind to m); picked = picked + s.kind; editing = null }, onDismiss = { editing = null })
    }
}

/**
 * CFG-47: one suggestion as a page: every app it found, each a check row (2 to 4 can be kept, the App groups rule), and with [pinTo]
 * a switch to pin the new group to that profile's dock. Nothing is saved until Save.
 */
@Composable
fun SuggestionPage(
    s: GroupSuggestion,
    apps: List<PanelApp>,
    pinTo: String?,
    initial: List<String> = s.preselected.map { it.key },
    onSave: (List<String>, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    DockPagesScope { OneUiFullPage(onDismiss) { SuggestionBody(s, pinTo, initial, onSave, onDismiss) } }
}

/** The suggestion page's content (without the window, so a screenshot can draw it). */
@Composable
fun SuggestionBody(s: GroupSuggestion, pinTo: String?, initial: List<String>, onSave: (List<String>, Boolean) -> Unit, onDismiss: () -> Unit) {
    var chosen by remember(s) { mutableStateOf(initial) }
    var pin by remember(s) { mutableStateOf(true) }
    val ok = chosen.size in AppGroupSuggester.GROUP_MIN..AppGroupSuggester.GROUP_MAX
    run {
            OneUiPageFrame(L10n.tr(s.kind.label), onDismiss, subtitle = L10n.tr("Keep 2 to 4 apps. They open together as an app group."), tag = ProfileTags.SUGGESTION_PAGE) {
                item(key = "apps") {
                    OneUiGroup(s.apps.size) {
                        s.apps.forEachIndexed { i, a ->
                            if (i > 0) OneUiRowDivider()
                            val on = a.key in chosen
                            val full = !on && chosen.size >= AppGroupSuggester.GROUP_MAX
                            OneUiListRow(
                                a.label, summary = if (full) L10n.tr("A group holds at most 4 apps") else null,
                                onClick = { chosen = if (on) chosen - a.key else if (full) chosen else chosen + a.key }, role = Role.Checkbox,
                                modifier = Modifier.testTag(ProfileTags.suggestionApp(a.key)).semantics { stateDescription = if (on) L10n.tr("Chosen") else L10n.tr("Not chosen") },
                                trailing = { OneUiCheckMark(on) },
                            )
                        }
                    }
                }
                if (pinTo != null) item(key = "pin") {
                    Column {
                        OneUiGap()
                        OneUiGroup(1) { OneUiSwitchRow(L10n.tr("Pin to the dock of %s", pinTo), pin, { pin = it }, modifier = Modifier.testTag(ProfileTags.SUGGESTION_PIN)) }
                    }
                }
                item(key = "save") {
                    Column {
                        OneUiGap()
                        OneUiPrimaryButton(L10n.tr("Save group"), { onSave(chosen, pin && pinTo != null) }, Modifier.testTag(ProfileTags.SUGGESTION_SAVE), glyph = Glyphs.Check, enabled = ok)
                        OneUiGap()
                    }
                }
            }
    }
}

/** What Create profile does: the groups first (so the profile can link them), then the profile with its start, settings and contents. */
internal fun createFromPage(
    manager: WorkspaceManager,
    apps: List<PanelApp>,
    facts: List<SuggestApp>,
    name: String,
    start: ProfileStart,
    carries: Set<ProfileGroup>,
    groups: List<GroupSuggestion>,
    members: Map<SuggestionKind, List<String>>,
): String {
    val made = groups.mapNotNull { s -> saveSuggestedGroup(manager, s.kind, members[s.kind] ?: s.preselected.map { it.key }, apps) }
    val settings = ProfileStarters.settingsFor(start, carries, manager.liveSettings())
    val copy = if (start == ProfileStart.Current) manager.current.activeId else null
    val id = manager.createProfile(
        name, copy, carries.mapTo(LinkedHashSet()) { it.id }, settings,
        homeArrangement = if (copy == null) ProfileStarters.arrangement(ProfileStarters.firstPage(start, facts)) else null,
        notificationApps = ProfileStarters.notificationApps(start, facts),
    )
    made.forEach { manager.linkGroup(id, it.id, pin = true) }
    return id
}

/**
 * CFG-48: one row of profile chips (the Home sheet's top row and the dock sheet): outlined pills, the active one filled, each a radio
 * button at least 48 dp tall, wrapping so a large font never pushes one off screen. Shown only with two profiles or more.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun ProfileChipRow(state: DockWorkspaceState, onPick: (String) -> Unit, modifier: Modifier = Modifier, dark: Boolean? = null) {
    if (state.workspaces.size < 2) return
    val ink = VisualInk.current(dark)
    androidx.compose.foundation.layout.FlowRow(
        modifier.fillMaxWidth().testTag(ProfileTags.CHIPS).semantics { contentDescription = L10n.tr("Profiles") },
        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        state.workspaces.forEach { w ->
            val on = w.id == state.activeId
            val shape = RoundedCornerShape(24.dp)
            Box(
                Modifier.height(48.dp).clip(shape).background(if (on) ink.strong else Color.Transparent).border(1.dp, if (on) ink.strong else ink.soft, shape)
                    .testTag(ProfileTags.chip(w.id))
                    .selectable(selected = on, role = Role.RadioButton) { if (!on) onPick(w.id) }
                    .padding(horizontal = 18.dp),
                contentAlignment = Alignment.Center,
            ) { Text(w.name, color = if (on) ink.tile else ink.strong, fontSize = 15.sp, maxLines = 1) }
        }
    }
}
