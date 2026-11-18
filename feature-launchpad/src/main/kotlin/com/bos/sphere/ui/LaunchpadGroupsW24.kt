package com.bos.sphere.ui

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.bos.sphere.core.data.AssignKind
import com.bos.sphere.core.data.ContactDirectory
import com.bos.sphere.core.data.DndState
import com.bos.sphere.core.data.GroupPeople
import com.bos.sphere.core.data.GroupResolver
import com.bos.sphere.core.data.GroupsConfig
import com.bos.sphere.core.data.GroupsSettings
import com.bos.sphere.core.data.HubNotification
import com.bos.sphere.core.data.LaunchpadGroup
import com.bos.sphere.core.data.PersonCandidate
import com.bos.sphere.core.design.L10n
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Wave 24 (STR-13): system Do Not Disturb as Liftoff sees it, read through the public interruption filter on every resume and
 * whenever the system says it changed. Read only: nothing in Liftoff turns Do Not Disturb on or off from here.
 */
@Composable
internal fun rememberDndOn(): Boolean {
    val ctx = LocalContext.current
    var on by remember { mutableStateOf(DndState.isOn(ctx)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { on = DndState.isOn(ctx) }
    DisposableEffect(ctx) {
        val r = object : BroadcastReceiver() { override fun onReceive(c: Context?, i: Intent?) { on = DndState.isOn(ctx) } }
        val ok = runCatching {
            ctx.registerReceiver(r, IntentFilter(NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED), Context.RECEIVER_NOT_EXPORTED)
        }.isSuccess
        onDispose { if (ok) runCatching { ctx.unregisterReceiver(r) } }
    }
    return on
}

/**
 * Wave 24 (STR-13, owner request): the Groups manager opened from inside Launchpad (header menu > "Groups and rules..."), so a
 * group, a person or a rule is two taps away from the notifications it changes. Back, Escape and Done close it; the column
 * behind regroups live as the groups change.
 */
@Composable
internal fun LaunchpadGroupsOverlay(open: Boolean, feed: List<HubNotification>, onClose: () -> Unit) {
    if (!open) return
    BackHandler(enabled = open, onBack = onClose)
    val req = remember { FocusRequester() }
    LaunchedEffect(Unit) { withFrameNanos { }; runCatching { req.requestFocus() } }
    val c = lpGColors()
    Column(
        Modifier.fillMaxSize().background(LocalLp.current.bg).testTag("lp.groups")
            .pointerInput(Unit) { detectTapGestures { } }
            .onPreviewKeyEvent { if (it.type == KeyEventType.KeyDown && it.key == Key.Escape) { onClose(); true } else false }
            .focusRequester(req).focusable()
            .statusBarsPadding().navigationBarsPadding(),
    ) {
        Row(Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(L10n.tr("Groups and rules"), color = c.ink, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.semantics { heading() })
            Spacer(Modifier.weight(1f))
            GChip(c, L10n.tr("Done"), selected = true, description = L10n.tr("Close groups and rules"), tag = "lp.groups.done") { onClose() }
        }
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp).testTag("lp.groups.scroll")) {
            LaunchpadGroupsPane(feed = feed, colors = c)
        }
    }
}

private fun kindLabel(k: AssignKind) = when (k) {
    AssignKind.Person -> L10n.tr("Person")
    AssignKind.App -> L10n.tr("App")
    AssignKind.Conversation -> L10n.tr("Chat")
}

/**
 * Wave 24 (STR-13): the people and members part of one group card. "Add people..." lists the people in the current feed, each
 * merged across every messaging app (the apps are named), and a pick puts all their 1:1 chats in this group at once; a name can
 * also be typed for someone with nothing waiting. The members list shows what was put here by hand, each removable, and a person
 * can get another name linked (the same human under a different name in another app).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun GroupPeoplePicker(c: GColors, g: LaunchpadGroup, cfg: GroupsConfig, feed: List<HubNotification>, contacts: ContactDirectory) {
    var picking by remember(g.id) { mutableStateOf(false) }
    var typed by remember(g.id) { mutableStateOf("") }
    var linkingFor by remember(g.id) { mutableStateOf<String?>(null) }
    val candidates by produceState(emptyList<PersonCandidate>(), feed, cfg, contacts) {
        value = withContext(Dispatchers.Default) { GroupPeople.candidates(feed, GroupResolver(cfg, contacts)) }
    }
    val members = remember(cfg, g.id, feed) { GroupPeople.members(cfg, g.id, feed) }

    if (members.isNotEmpty()) {
        Text(L10n.tr("Put here by hand"), color = c.dim, fontSize = 12.sp)
        members.forEachIndexed { i, m ->
            Column(Modifier.fillMaxWidth().testTag("grp.card.${g.id}.member.$i"), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(kindLabel(m.kind) + ": " + m.label, color = c.ink2, fontSize = 13.sp)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    GChip(c, L10n.tr("Remove"), description = L10n.tr("Remove %s from %s", m.label, g.name), tag = "grp.card.${g.id}.member.$i.remove") {
                        GroupsSettings.unassign(m.kind, m.target)
                    }
                    if (m.kind == AssignKind.Person) {
                        GChip(c, L10n.tr("Link another name"), linkingFor == m.target, description = L10n.tr("Link another name to %s", m.label), tag = "grp.card.${g.id}.member.$i.link") {
                            linkingFor = if (linkingFor == m.target) null else m.target
                        }
                    }
                }
                if (linkingFor == m.target) {
                    val others = candidates.filter { it.identity != m.target }
                    if (others.isEmpty()) Text(L10n.tr("No other senders right now."), color = c.dim, fontSize = 12.sp)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        others.forEachIndexed { j, o ->
                            GChip(c, o.senders.first(), description = L10n.tr("%s is the same person as %s", m.label, o.senders.first()), tag = "grp.card.${g.id}.member.$i.link.$j") {
                                o.senders.forEach { s -> GroupsSettings.linkSenders(m.label, s, o.contactKey) }
                                linkingFor = null
                            }
                        }
                    }
                }
            }
        }
    }
    GChip(c, L10n.tr("Add people…"), picking, description = L10n.tr("Add people to %s", g.name), tag = "grp.card.${g.id}.addPeople") { picking = !picking }
    if (picking) {
        Text(L10n.tr("One person's chats from every messaging app go here together."), color = c.dim, fontSize = 12.sp)
        if (candidates.isEmpty()) Text(L10n.tr("No one is waiting right now. Type a name instead."), color = c.dim, fontSize = 12.sp)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            candidates.forEachIndexed { i, p ->
                val here = p.currentGroupId == g.id
                val label = (if (here) "✓  " else "") + p.name + "  ·  " + p.apps.joinToString(", ")
                GChip(c, label, here, description = L10n.tr("Put %s in %s (%s)", p.name, g.name, p.apps.joinToString(", ")), tag = "grp.card.${g.id}.person.$i") {
                    GroupsSettings.edit { GroupPeople.addToGroup(it, p, g.id) }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            GField(c, typed, { typed = it }, L10n.tr("Type a name"), tag = "grp.card.${g.id}.typed", modifier = Modifier.weight(1f))
            GChip(c, L10n.tr("Add"), description = L10n.tr("Add %s to %s", typed, g.name), tag = "grp.card.${g.id}.addName") {
                if (typed.isNotBlank()) { GroupsSettings.edit { GroupPeople.addNameToGroup(it, typed, g.id) }; typed = "" }
            }
        }
    }
}
