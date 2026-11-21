package com.bos.sphere.ui

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bos.sphere.core.data.DeliveryMode
import com.bos.sphere.core.data.GroupPriority
import com.bos.sphere.core.data.GroupRule
import com.bos.sphere.core.data.GroupsEdit
import com.bos.sphere.core.data.GroupsSettings
import com.bos.sphere.core.data.LaunchpadGroup
import com.bos.sphere.core.data.ContactsDirectoryLoader
import com.bos.sphere.core.data.NotificationProvider
import com.bos.sphere.core.design.GrantHooks
import com.bos.sphere.core.design.GrantKind
import com.bos.sphere.core.design.L10n
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

/** The colours a group can take, with names for screen readers (the colour alone never carries meaning). */
internal val GroupSwatches: List<Pair<String, Long>> = listOf(
    "Violet" to 0xFF8B6CF0, "Teal" to 0xFF22B8A6, "Amber" to 0xFFF2A93B, "Rose" to 0xFFE5547A, "Green" to 0xFF4CAF6E, "Blue" to 0xFF3D8BEF,
)

private fun swatchName(n: String) = when (n) {
    "Violet" -> L10n.tr("Violet"); "Teal" -> L10n.tr("Teal"); "Amber" -> L10n.tr("Amber")
    "Rose" -> L10n.tr("Rose"); "Green" -> L10n.tr("Green"); else -> L10n.tr("Blue")
}

private fun deliveryLabel(d: DeliveryMode?) = when (d) {
    null -> L10n.tr("Default")
    DeliveryMode.Immediate -> L10n.tr("Show at once")
    DeliveryMode.Digest -> L10n.tr("Digest")
    DeliveryMode.SilentInLaunchpad -> L10n.tr("Silent in Comms")
}

/**
 * STR-13: the Groups manager (Launchpad rules pane, Ground Control). Create, rename, reorder (Move up and Move down, so the
 * keyboard and screen readers need no dragging), delete (a CFG-04 snapshot is taken first) and set each group's priority;
 * then the ordered rules, the linked people and "my last name". Nothing here touches system notifications.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LaunchpadGroupsPane(modifier: Modifier = Modifier) = LaunchpadGroupsPane(modifier, feed = null, colors = null)

/** The Groups manager with an explicit feed and colours (wave 24: opened from inside Launchpad). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun LaunchpadGroupsPane(
    modifier: Modifier = Modifier,
    /** Wave 24: the feed to pick people from and preview rules on (Launchpad passes what it shows); null reads the live feed. */
    feed: List<com.bos.sphere.core.data.HubNotification>?,
    /** Wave 24: Launchpad's own colours when opened from inside Launchpad; null is the Ground Control look. */
    colors: GColors?,
) {
    val ctx = LocalContext.current
    remember(ctx) { GroupsSettings.init(ctx); true }
    val cfg by GroupsSettings.config.collectAsStateWithLifecycle()
    val liveFeed by NotificationProvider.notifications.collectAsStateWithLifecycle(remember { NotificationProvider.snapshot })
    val feed = feed ?: liveFeed
    val contacts = rememberContactDirectory()
    val granted = rememberGranted(GrantKind.Contacts)
    val c = colors ?: hyleGColors()
    val apps by produceState(emptyList<Pair<String, String>>()) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val pm = ctx.packageManager
                pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
                    .map { it.activityInfo.packageName to it.loadLabel(pm).toString() }.distinctBy { it.first }.sortedBy { it.second.lowercase() }
            }.getOrDefault(emptyList())
        }
    }
    var newName by remember { mutableStateOf("") }
    var newPriority by remember { mutableStateOf(GroupPriority.Normal) }
    var editing by remember { mutableStateOf<GroupRule?>(null) }

    Column(modifier.fillMaxWidth().testTag("grp.pane"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(L10n.tr("Groups"), color = c.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Text(
            L10n.tr("Groups become the sections of Comms. Priority groups show at once in Liftoff and can light the edge indicator. System notifications and Do Not Disturb are not changed."),
            color = c.dim, fontSize = 12.sp,
        )
        if (cfg.groups.isEmpty()) Text(L10n.tr("No groups yet"), color = c.dim, fontSize = 13.sp)
        val ordered = cfg.groups.sortedBy { it.order }
        ordered.forEachIndexed { i, g -> GroupCard(c, g, i, ordered.size, cfg, feed, contacts) }
        // Wave 24: Priority groups break through system Do Not Disturb in Liftoff's own indicator (read only, never changed).
        GChip(
            c, L10n.tr("Priority groups break through Do Not Disturb"), cfg.priorityBreaksDnd, tag = "grp.dnd",
            description = L10n.tr("Priority groups break through Do Not Disturb") + ": " + (if (cfg.priorityBreaksDnd) L10n.tr("On") else L10n.tr("Off")),
        ) { GroupsSettings.setPriorityBreaksDnd(!cfg.priorityBreaksDnd) }
        Text(L10n.tr("While Do Not Disturb is on, only Priority groups light Liftoff's edge indicator. Comms still lists everything. Do Not Disturb itself is not changed."), color = c.dim, fontSize = 12.sp)

        Column(Modifier.fillMaxWidth().background(c.surface, RoundedCornerShape(14.dp)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            GField(c, newName, { newName = it }, L10n.tr("New group name"), tag = "grp.pane.newName")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                GroupPriority.entries.forEach { p -> GChip(c, GroupTexts.priorityLabel(p), newPriority == p) { newPriority = p } }
                GChip(c, L10n.tr("Add group"), tag = "grp.pane.add") {
                    if (newName.isNotBlank()) { GroupsSettings.addGroup(newName, newPriority); newName = "" }
                }
            }
        }

        Text(L10n.tr("Group rules"), color = c.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
        Text(L10n.tr("A person or chat you move by hand always beats a rule. Otherwise the first rule that matches wins."), color = c.dim, fontSize = 12.sp)
        if (cfg.rules.isEmpty()) Text(L10n.tr("No group rules yet"), color = c.dim, fontSize = 13.sp)
        cfg.rules.forEachIndexed { i, r ->
            Column(
                Modifier.fillMaxWidth().testTag("grp.rule.$i").background(c.surface, RoundedCornerShape(12.dp)).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(GroupTexts.summary(r, cfg) + if (r.enabled) "" else "  (" + L10n.tr("off") + ")", color = if (r.enabled) c.ink2 else c.dim, fontSize = 13.sp)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    GChip(c, L10n.tr("Edit"), tag = "grp.rule.$i.edit") { editing = r }
                    GChip(c, if (r.enabled) L10n.tr("Rule is on") else L10n.tr("Rule is off"), r.enabled, tag = "grp.rule.$i.toggle") {
                        GroupsSettings.updateRule(r.copy(enabled = !r.enabled))
                    }
                    if (i > 0) GChip(c, L10n.tr("Move up"), description = L10n.tr("Move rule up"), tag = "grp.rule.$i.up") { GroupsSettings.moveRule(r.id, -1) }
                    if (i < cfg.rules.size - 1) GChip(c, L10n.tr("Move down"), description = L10n.tr("Move rule down"), tag = "grp.rule.$i.down") { GroupsSettings.moveRule(r.id, 1) }
                    GChip(c, L10n.tr("Delete"), description = L10n.tr("Delete rule"), tag = "grp.rule.$i.delete") { GroupsSettings.removeRule(r.id) }
                }
            }
        }
        val ed = editing
        if (ed != null) {
            Column(Modifier.fillMaxWidth().background(c.surface, RoundedCornerShape(14.dp)).padding(14.dp)) {
                GroupRuleEditor(
                    c, cfg, ed, feed, contacts, apps,
                    onSave = { r -> if (cfg.rules.any { it.id == r.id }) GroupsSettings.updateRule(r) else GroupsSettings.addRule(r); editing = null },
                    onCancel = { editing = null },
                )
            }
        } else if (cfg.groups.isNotEmpty()) {
            GChip(c, L10n.tr("Add group rule…"), tag = "grp.pane.addRule") {
                editing = GroupRule(UUID.randomUUID().toString(), emptyList(), cfg.ordered.first().id)
            }
        }

        Text(L10n.tr("People"), color = c.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
        Text(L10n.tr("One person across apps. Link senders from a notification with \"This is the same person as...\"."), color = c.dim, fontSize = 12.sp)
        if (cfg.people.isEmpty()) Text(L10n.tr("No linked people yet"), color = c.dim, fontSize = 13.sp)
        cfg.people.forEach { p ->
            Column(Modifier.fillMaxWidth().background(c.surface, RoundedCornerShape(12.dp)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(p.name, color = c.ink, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                if (p.aliases.isNotEmpty()) Text(p.aliases.joinToString(", "), color = c.dim, fontSize = 12.sp)
                GChip(c, L10n.tr("Remove link"), description = L10n.tr("Remove link for %s", p.name)) { GroupsSettings.removePerson(p.id) }
            }
        }

        Text(L10n.tr("My last name"), color = c.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
        Text(L10n.tr("Used by the rule \"sender last name is my last name\". It stays on this device and is never sent anywhere."), color = c.dim, fontSize = 12.sp)
        GField(c, cfg.myLastName, { GroupsSettings.setMyLastName(it) }, L10n.tr("e.g. Sharma"), label = L10n.tr("My last name"), tag = "grp.myLast")
        val suggestion by produceState<String?>(null, granted) { value = if (granted) withContext(Dispatchers.IO) { ContactsDirectoryLoader.ownerLastName(ctx) } else null }
        suggestion?.takeIf { cfg.myLastName.isBlank() }?.let { s ->
            GChip(c, L10n.tr("Use %s from my profile", s), tag = "grp.myLast.suggest") { GroupsSettings.setMyLastName(s) }
        }
        if (!granted) {
            Text(L10n.tr("Contacts are optional. Allow them to match senders to contacts and to fill in your last name."), color = c.dim, fontSize = 12.sp)
            GChip(c, L10n.tr("Allow contacts (optional)"), tag = "grp.contacts") {
                GrantHooks.ask(GrantKind.Contacts) { GrantSupport.request(ctx, GrantKind.Contacts) }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GroupCard(
    c: GColors, g: LaunchpadGroup, index: Int, count: Int,
    cfg: com.bos.sphere.core.data.GroupsConfig, feed: List<com.bos.sphere.core.data.HubNotification>, contacts: com.bos.sphere.core.data.ContactDirectory,
) {
    var renaming by remember(g.id) { mutableStateOf(false) }
    var name by remember(g.id, g.name) { mutableStateOf(g.name) }
    var confirm by remember(g.id) { mutableStateOf(false) }
    Column(
        Modifier.fillMaxWidth().testTag("grp.card.${g.id}").background(c.surface, RoundedCornerShape(12.dp)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            g.colorArgb?.let { Box(Modifier.size(12.dp).background(Color(it), CircleShape)) }
            if (renaming) GField(c, name, { name = it }, L10n.tr("Group name"), tag = "grp.card.${g.id}.name", modifier = Modifier.weight(1f))
            else Text(g.name, color = c.ink, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            Text(GroupTexts.priorityLabel(g.priority), color = c.dim, fontSize = 12.sp)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            GroupPriority.entries.forEach { p ->
                GChip(c, GroupTexts.priorityLabel(p), g.priority == p, description = L10n.tr("%s priority: %s", g.name, GroupTexts.priorityLabel(p)), tag = "grp.card.${g.id}.${p.name}") {
                    GroupsSettings.setPriority(g.id, p)
                    if (p == GroupPriority.Priority && g.delivery == DeliveryMode.Digest) GroupsSettings.setDelivery(g.id, null)
                }
            }
        }
        Text(L10n.tr("Delivery"), color = c.dim, fontSize = 12.sp)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val modes = if (g.priority == GroupPriority.Priority) listOf(null, DeliveryMode.Immediate, DeliveryMode.SilentInLaunchpad)
            else listOf(null) + DeliveryMode.entries
            modes.forEach { m -> GChip(c, deliveryLabel(m), g.delivery == m, tag = "grp.card.${g.id}.delivery.${m?.name ?: "default"}") { GroupsSettings.setDelivery(g.id, m) } }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            GChip(c, L10n.tr("No colour"), g.colorArgb == null) { GroupsSettings.edit { GroupsEdit.setAppearance(it, g.id, g.icon, null) } }
            GroupSwatches.forEach { (n, argb) ->
                Box(
                    Modifier.size(48.dp).semantics { contentDescription = swatchName(n) }
                        .androidClick { GroupsSettings.edit { GroupsEdit.setAppearance(it, g.id, g.icon, argb) } },
                    androidx.compose.ui.Alignment.Center,
                ) { Box(Modifier.size(if (g.colorArgb == argb) 28.dp else 20.dp).background(Color(argb), CircleShape)) }
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            GChip(c, if (renaming) L10n.tr("Save name") else L10n.tr("Rename"), description = L10n.tr("Rename %s", g.name), tag = "grp.card.${g.id}.rename") {
                if (renaming) { GroupsSettings.renameGroup(g.id, name); renaming = false } else renaming = true
            }
            if (index > 0) GChip(c, L10n.tr("Move up"), description = L10n.tr("Move %s up", g.name), tag = "grp.card.${g.id}.up") { GroupsSettings.moveGroup(g.id, -1) }
            if (index < count - 1) GChip(c, L10n.tr("Move down"), description = L10n.tr("Move %s down", g.name), tag = "grp.card.${g.id}.down") { GroupsSettings.moveGroup(g.id, 1) }
            GChip(c, L10n.tr("Delete"), description = L10n.tr("Delete %s", g.name), tag = "grp.card.${g.id}.delete") { confirm = true }
        }
        GroupPeoplePicker(c, g, cfg, feed, contacts) // wave 24: pick a person, members
        if (confirm) {
            Text(L10n.tr("Delete %s? Its assignments and rules go with it. A snapshot is saved first.", g.name), color = c.ink2, fontSize = 13.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GChip(c, L10n.tr("Delete"), selected = true, tag = "grp.card.${g.id}.confirm") { GroupsSettings.deleteGroup(g.id) }
                GChip(c, L10n.tr("Cancel")) { confirm = false }
            }
        }
    }
}

private fun Modifier.androidClick(onClick: () -> Unit): Modifier =
    this.semantics(mergeDescendants = true) { role = androidx.compose.ui.semantics.Role.Button }.clickable(onClick = onClick)
