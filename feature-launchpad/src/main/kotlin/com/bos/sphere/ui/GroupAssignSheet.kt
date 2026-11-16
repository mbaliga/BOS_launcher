package com.bos.sphere.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bos.sphere.core.data.AssignKind
import com.bos.sphere.core.data.AssignTargets
import com.bos.sphere.core.data.ContactDirectory
import com.bos.sphere.core.data.GroupPriority
import com.bos.sphere.core.data.GroupResolver
import com.bos.sphere.core.data.GroupsConfig
import com.bos.sphere.core.data.GroupsEdit
import com.bos.sphere.core.data.GroupsSettings
import com.bos.sphere.core.data.HubNotification
import com.bos.sphere.core.data.LaunchpadLogic
import com.bos.sphere.core.data.NoContacts
import com.bos.sphere.core.data.PersonNames
import com.bos.sphere.core.design.L10n
import java.util.UUID

/** STR-13: the edits a row's group actions make, applied to the persisted groups. Pure of any view state. */
internal object GroupActions {
    fun createGroup(name: String, priority: GroupPriority = GroupPriority.Normal): String? {
        val id = UUID.randomUUID().toString()
        GroupsSettings.edit { GroupsEdit.addGroup(it, name, priority, id = id) }
        return id.takeIf { GroupsSettings.snapshot().group(it) != null }
    }

    /** The stored person for [sender], created (and linked to the matching contact, if the grant exists) when new. */
    fun personFor(sender: String, contacts: ContactDirectory = NoContacts): String? {
        val key = PersonNames.key(sender)
        if (key.isEmpty()) return null
        fun find(c: GroupsConfig) = c.people.firstOrNull { p -> (p.aliases + p.name).any { PersonNames.key(it) == key } }
        find(GroupsSettings.snapshot())?.let { return it.id }
        val contact = contacts.find(sender, key)
        GroupsSettings.edit { GroupsEdit.linkSenders(it, sender, sender, contact?.key) }
        return find(GroupsSettings.snapshot())?.id
    }

    fun assignConversation(n: HubNotification, groupId: String?) {
        val title = n.conversation?.takeIf { it.isNotBlank() } ?: LaunchpadLogic.senderOf(n).ifBlank { n.title }
        GroupsSettings.assign(AssignKind.Conversation, AssignTargets.conversation(n.packageName, title), groupId)
    }

    fun assignPerson(n: HubNotification, groupId: String?, contacts: ContactDirectory = NoContacts) {
        personFor(LaunchpadLogic.senderOf(n), contacts)?.let { GroupsSettings.assign(AssignKind.Person, it, groupId) }
    }

    fun assignApp(n: HubNotification, groupId: String?) {
        if (n.packageName.isNotBlank()) GroupsSettings.assign(AssignKind.App, AssignTargets.app(n.packageName), groupId)
    }

    fun clear(n: HubNotification, scope: Scope, contacts: ContactDirectory = NoContacts) {
        val title = n.conversation?.takeIf { it.isNotBlank() } ?: LaunchpadLogic.senderOf(n).ifBlank { n.title }
        when (scope) {
            Scope.Chat -> GroupsSettings.unassign(AssignKind.Conversation, AssignTargets.conversation(n.packageName, title))
            Scope.Person -> personFor(LaunchpadLogic.senderOf(n), contacts)?.let { GroupsSettings.unassign(AssignKind.Person, it) }
            Scope.App -> GroupsSettings.unassign(AssignKind.App, AssignTargets.app(n.packageName))
        }
    }

    enum class Scope { Chat, Person, App }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GroupAssignSheet(
    n: HubNotification,
    config: GroupsConfig,
    contacts: ContactDirectory,
    feed: List<HubNotification>,
    onMakeRule: () -> Unit,
    onDone: (message: String?) -> Unit,
) {
    ModalBottomSheet(onDismissRequest = { onDone(null) }, containerColor = LocalLp.current.surface) {
        GroupAssignContent(lpGColors(), n, config, contacts, feed, onMakeRule, onDone, Modifier.navigationBarsPadding().padding(bottom = 12.dp))
    }
}

/** The body of the sheet (separate so tests can compose it without a bottom sheet). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun GroupAssignContent(
    c: GColors,
    n: HubNotification,
    config: GroupsConfig,
    contacts: ContactDirectory,
    feed: List<HubNotification>,
    onMakeRule: () -> Unit,
    onDone: (message: String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val sender = LaunchpadLogic.senderOf(n)
    val resolver = remember(config, contacts) { GroupResolver(config, contacts) }
    val facts = remember(resolver, n) { resolver.facts(n) }
    val canPerson = n.isMessaging && sender.isNotBlank() && !facts.isGroupChat
    val canApp = n.packageName.isNotBlank()
    val app = n.appLabel.ifBlank { n.packageName }
    var scope by remember { mutableStateOf(if (canPerson) GroupActions.Scope.Person else GroupActions.Scope.Chat) }
    var newName by remember { mutableStateOf("") }
    var linking by remember { mutableStateOf(false) }
    val current = resolver.resolve(n)

    fun label(g: String) = when (scope) {
        GroupActions.Scope.Chat -> g
        GroupActions.Scope.Person -> L10n.tr("Always put %s in %s", sender, g)
        GroupActions.Scope.App -> L10n.tr("Always put %s in %s", app, g)
    }
    fun apply(groupId: String?) {
        when (scope) {
            GroupActions.Scope.Chat -> GroupActions.assignConversation(n, groupId)
            GroupActions.Scope.Person -> GroupActions.assignPerson(n, groupId, contacts)
            GroupActions.Scope.App -> GroupActions.assignApp(n, groupId)
        }
        onDone(groupId?.let { id -> config.group(id)?.let { L10n.tr("Moved to %s", it.name) } } ?: L10n.tr("Kept out of groups"))
    }

    Column(modifier.fillMaxWidth().verticalScroll(rememberScrollState()).testTag("grp.assign"), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
            Text(L10n.tr("Move to group..."), color = c.ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(n.title, color = c.dim, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        FlowRow(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            GChip(c, L10n.tr("This chat only"), scope == GroupActions.Scope.Chat, tag = "grp.scope.chat") { scope = GroupActions.Scope.Chat }
            if (canPerson) GChip(c, sender, scope == GroupActions.Scope.Person, description = L10n.tr("Always this person: %s", sender), tag = "grp.scope.person") { scope = GroupActions.Scope.Person }
            if (canApp) GChip(c, app, scope == GroupActions.Scope.App, description = L10n.tr("Always this app: %s", app), tag = "grp.scope.app") { scope = GroupActions.Scope.App }
        }
        if (config.groups.isEmpty()) Text(L10n.tr("No groups yet. Create one below."), color = c.dim, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
        config.ordered.forEach { g ->
            val here = current.group?.id == g.id
            Line(c, (if (here) "✓  " else "") + label(g.name), tag = "grp.pick.${g.id}", description = label(g.name)) { apply(g.id) }
        }
        if (config.groups.isNotEmpty()) {
            Line(c, L10n.tr("Keep out of all groups"), tag = "grp.pick.none") { apply(null) }
            Line(c, L10n.tr("Back to automatic"), tag = "grp.pick.auto") {
                GroupActions.clear(n, scope, contacts); onDone(L10n.tr("Back to automatic"))
            }
        }
        Row(Modifier.padding(horizontal = 20.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GField(c, newName, { newName = it }, L10n.tr("New group name"), tag = "grp.newName", modifier = Modifier.weight(1f))
            GChip(c, L10n.tr("Create"), tag = "grp.create") {
                if (newName.isNotBlank()) { GroupActions.createGroup(newName); newName = "" }
            }
        }
        if (canPerson) {
            Line(c, L10n.tr("This is the same person as..."), tag = "grp.same") { linking = !linking }
            if (linking) {
                val mine = PersonNames.key(sender)
                val others = remember(feed, sender) {
                    feed.filter { it.isMessaging }.map { LaunchpadLogic.senderOf(it) }
                        .filter { it.isNotBlank() && PersonNames.key(it) != mine }.distinctBy { PersonNames.key(it) }.take(12)
                }
                if (others.isEmpty()) Text(L10n.tr("No other senders right now."), color = c.dim, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp))
                FlowRow(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    others.forEach { o ->
                        GChip(c, o, description = L10n.tr("%s is the same person as %s", sender, o)) {
                            GroupsSettings.linkSenders(sender, o, contacts.find(sender, mine)?.key)
                            onDone(L10n.tr("Linked %s and %s", sender, o))
                        }
                    }
                }
            }
        }
        Line(c, L10n.tr("Make a rule from this"), tag = "grp.makeRule", onClick = onMakeRule)
    }
}

@Composable
private fun Line(c: GColors, text: String, tag: String, description: String = text, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag(tag)
            .semantics { role = Role.Button; contentDescription = description }
            .clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) { Text(text, color = c.ink2, fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis) }
}

/** "Make a rule from this": the rule editor in a sheet, starting from a rule drafted from the notification. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GroupRuleSheet(
    n: HubNotification,
    config: GroupsConfig,
    contacts: ContactDirectory,
    feed: List<HubNotification>,
    onDone: (message: String?) -> Unit,
) {
    val initial = remember(n.key) { com.bos.sphere.core.data.RuleDrafts.fromNotification(n, config.ordered.firstOrNull()?.id.orEmpty()) }
    ModalBottomSheet(onDismissRequest = { onDone(null) }, containerColor = LocalLp.current.surface) {
        Column(Modifier.navigationBarsPadding().padding(horizontal = 20.dp, vertical = 8.dp).verticalScroll(rememberScrollState())) {
            GroupRuleEditor(
                lpGColors(), config, initial, feed, contacts, emptyList(),
                onSave = { r -> GroupsSettings.addRule(r); onDone(L10n.tr("Rule saved")) },
                onCancel = { onDone(null) },
            )
        }
    }
}
