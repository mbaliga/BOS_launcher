package com.bos.sphere.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bos.sphere.core.data.AppPolicy
import com.bos.sphere.core.data.Condition
import com.bos.sphere.core.data.ConditionKind
import com.bos.sphere.core.data.ContactDirectory
import com.bos.sphere.core.data.GroupResolver
import com.bos.sphere.core.data.GroupRule
import com.bos.sphere.core.data.GroupsConfig
import com.bos.sphere.core.data.HubNotification
import com.bos.sphere.core.data.RulePreview
import com.bos.sphere.core.design.L10n
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.ZoneId

/** STR-13: whether a condition is complete enough to save. */
internal fun conditionComplete(c: Condition): Boolean = when (c.kind) {
    ConditionKind.AppIs, ConditionKind.PersonIs -> c.value.isNotBlank()
    in GroupTexts.textKinds -> c.value.isNotBlank()
    ConditionKind.CategoryIs -> c.value in GroupTexts.categories
    else -> true
}

/** A new condition of [kind] with a sensible starting value. */
internal fun newCondition(kind: ConditionKind) = when (kind) {
    ConditionKind.CategoryIs -> Condition(kind, "message")
    ConditionKind.TimeWindow -> Condition(kind, from = 22 * 60, to = 7 * 60)
    else -> Condition(kind)
}

/**
 * STR-13 rule editor: a simple form (add condition rows, choose the target group, enabled switch), a live "matches N current
 * notifications" preview with the first names, a test field for a sample sender and a plain-language summary. All conditions are
 * AND; the first matching rule wins (the list order in the manager decides).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun GroupRuleEditor(
    c: GColors,
    config: GroupsConfig,
    initial: GroupRule,
    notifications: List<HubNotification>,
    contacts: ContactDirectory,
    apps: List<Pair<String, String>>,
    onSave: (GroupRule) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    zone: ZoneId = ZoneId.systemDefault(),
) {
    var conds by remember(initial.id) { mutableStateOf(initial.conditions) }
    var groupId by remember(initial.id) { mutableStateOf(initial.groupId.takeIf { config.group(it) != null } ?: config.ordered.firstOrNull()?.id.orEmpty()) }
    var enabled by remember(initial.id) { mutableStateOf(initial.enabled) }
    var sample by remember { mutableStateOf("") }
    var newGroup by remember { mutableStateOf("") }
    val appChoices = remember(apps, notifications) {
        (apps + notifications.filter { it.packageName.isNotBlank() }.map { it.packageName to it.appLabel.ifBlank { it.packageName } })
            .distinctBy { it.first }.sortedBy { it.second.lowercase() }
    }
    fun appLabel(pkg: String) = appChoices.firstOrNull { it.first == pkg }?.second ?: pkg
    val draft = GroupRule(initial.id, conds, groupId, enabled, initial.name)
    val ready = conds.isNotEmpty() && conds.all(::conditionComplete) && config.group(groupId) != null

    // Matching runs off the main thread, on the current feed.
    val preview by produceState(RulePreview(0, emptyList(), emptyList()), conds, config, notifications, contacts) {
        val ok = conds.filter(::conditionComplete)
        value = withContext(Dispatchers.Default) { GroupResolver(config, contacts, zone).preview(ok, notifications) }
    }

    Column(modifier.fillMaxWidth().testTag("grp.editor"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(L10n.tr("Rule"), color = c.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Text(L10n.tr("All conditions must match. Rules are checked in order and the first match wins."), color = c.dim, fontSize = 12.sp)
        conds.forEachIndexed { i, cond ->
            ConditionRow(
                c, cond, config, appChoices, i,
                onChange = { n -> conds = conds.mapIndexed { j, x -> if (j == i) n else x } },
                onRemove = { conds = conds.filterIndexed { j, _ -> j != i } },
            )
        }
        Box1 {
            var open by remember { mutableStateOf(false) }
            GChip(c, L10n.tr("Add a condition"), tag = "grp.addCondition") { open = true }
            if (open) GroupKindPage(c, L10n.tr("Add a condition"), null, onPick = { k -> conds = conds + newCondition(k) }, onDismiss = { open = false })
        }

        Text(L10n.tr("Put it in"), color = c.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        if (config.groups.isEmpty()) {
            Text(L10n.tr("Create a group first."), color = c.dim, fontSize = 13.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                GField(c, newGroup, { newGroup = it }, L10n.tr("New group name"), tag = "grp.editor.newGroup", modifier = Modifier.weight(1f))
                GChip(c, L10n.tr("Create"), tag = "grp.editor.create") { if (newGroup.isNotBlank()) { GroupActions.createGroup(newGroup); newGroup = "" } }
            }
        }
        androidx.compose.runtime.LaunchedEffect(config.groups.size) {
            if (config.group(groupId) == null) groupId = config.ordered.firstOrNull()?.id.orEmpty()
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            config.ordered.forEach { g -> GChip(c, GroupTexts.targetLabel(g), selected = g.id == groupId, tag = "grp.target.${g.id}") { groupId = g.id } }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GChip(c, if (enabled) L10n.tr("Rule is on") else L10n.tr("Rule is off"), selected = enabled, tag = "grp.enabled") { enabled = !enabled }
        }

        // The summary and the preview are live regions, so a screen reader hears them change as conditions are edited.
        Text(
            GroupTexts.summary(draft, config, ::appLabel), color = c.ink, fontSize = 14.sp, fontWeight = FontWeight.Medium,
            modifier = Modifier.testTag("grp.summary").semantics { liveRegion = LiveRegionMode.Polite },
        )
        val previewText = if (conds.none(::conditionComplete)) L10n.tr("Add a condition to see what it matches.")
        else if (preview.count == 0) L10n.tr("Matches no current notifications")
        else if (preview.names.isEmpty()) L10n.tr("Matches %d current notifications", preview.count)
        else L10n.tr("Matches %d current notifications, for example %s", preview.count, preview.names.joinToString(", "))
        Text(previewText, color = c.dim, fontSize = 13.sp, modifier = Modifier.testTag("grp.preview").semantics { liveRegion = LiveRegionMode.Polite })

        Text(L10n.tr("Test with a sender"), color = c.dim, fontSize = 12.sp)
        GField(c, sample, { sample = it }, L10n.tr("e.g. Priya Sharma"), tag = "grp.sample")
        if (sample.isNotBlank()) {
            val hit = remember(conds, sample, config, contacts) {
                GroupResolver(config, contacts, zone).testSample(conds.filter(::conditionComplete), sample.trim())
            }
            Text(
                if (hit) L10n.tr("Would match: %s", sample.trim()) else L10n.tr("Would not match: %s", sample.trim()),
                color = c.ink2, fontSize = 13.sp, modifier = Modifier.testTag("grp.sampleResult").semantics { liveRegion = LiveRegionMode.Polite },
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GChip(c, L10n.tr("Save rule"), selected = ready, tag = "grp.save") { if (ready) onSave(draft) }
            GChip(c, L10n.tr("Cancel"), tag = "grp.cancel") { onCancel() }
        }
    }
}

@Composable
private fun Box1(content: @Composable () -> Unit) = androidx.compose.foundation.layout.Box { content() }

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ConditionRow(
    c: GColors, cond: Condition, config: GroupsConfig, apps: List<Pair<String, String>>, index: Int,
    onChange: (Condition) -> Unit, onRemove: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().testTag("grp.condition.$index")
            .semantics { contentDescription = GroupTexts.kindLabel(cond.kind) },
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            androidx.compose.foundation.layout.Box {
                var open by remember { mutableStateOf(false) }
                GChip(c, GroupTexts.kindLabel(cond.kind), description = L10n.tr("Condition: %s", GroupTexts.kindLabel(cond.kind)), tag = "grp.kind.$index") { open = true }
                if (open) GroupKindPage(c, L10n.tr("Condition"), cond.kind, onPick = { k -> if (k != cond.kind) onChange(newCondition(k).copy(negate = cond.negate)) }, onDismiss = { open = false })
            }
            GChip(c, L10n.tr("Not"), selected = cond.negate, tag = "grp.negate.$index") { onChange(cond.copy(negate = !cond.negate)) }
            GChip(c, L10n.tr("Remove"), description = L10n.tr("Remove condition: %s", GroupTexts.kindLabel(cond.kind)), tag = "grp.remove.$index") { onRemove() }
        }
        when {
            cond.kind in GroupTexts.textKinds -> GField(c, cond.value, { onChange(cond.copy(value = it)) }, L10n.tr("Type the text"), label = GroupTexts.kindLabel(cond.kind), tag = "grp.value.$index")
            cond.kind == ConditionKind.AppIs -> {
                var q by remember { mutableStateOf("") }
                if (cond.value.isNotBlank()) {
                    GChip(c, (apps.firstOrNull { it.first == cond.value }?.second ?: cond.value) + "  ×", selected = true) { onChange(cond.copy(value = "")) }
                } else {
                    GField(c, q, { q = it }, L10n.tr("Type an app name"), tag = "grp.value.$index")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        apps.filter { q.isBlank() || it.second.contains(q, ignoreCase = true) }.take(6).forEach { (p, l) -> GChip(c, l) { onChange(cond.copy(value = p)) } }
                    }
                }
            }
            cond.kind == ConditionKind.PersonIs -> {
                if (config.people.isEmpty()) Text(L10n.tr("No linked people yet. Use \"This is the same person as...\" on a notification."), color = c.dim, fontSize = 12.sp)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    config.people.forEach { p -> GChip(c, p.name, selected = cond.value == p.id) { onChange(cond.copy(value = p.id)) } }
                }
            }
            cond.kind == ConditionKind.CategoryIs -> FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GroupTexts.categories.forEach { k -> GChip(c, GroupTexts.categoryLabel(k), selected = cond.value == k) { onChange(cond.copy(value = k)) } }
            }
            cond.kind == ConditionKind.LastNameIsMine ->
                Text(if (config.myLastName.isBlank()) L10n.tr("Set your last name first (Groups settings). Until then this never matches.") else L10n.tr("Your last name: %s", config.myLastName), color = c.dim, fontSize = 12.sp)
            cond.needsContacts -> Text(L10n.tr("Needs the optional contacts permission. Without it this never matches."), color = c.dim, fontSize = 12.sp)
            cond.kind == ConditionKind.TimeWindow -> {
                TimeStepper(c, L10n.tr("From %s", AppPolicy.formatMinutes(cond.from)), "from$index", { onChange(cond.copy(from = (cond.from + it + 1440) % 1440)) })
                TimeStepper(c, L10n.tr("To %s", AppPolicy.formatMinutes(cond.to)), "to$index", { onChange(cond.copy(to = (cond.to + it + 1440) % 1440)) })
            }
            else -> Unit
        }
    }
}

@Composable
private fun TimeStepper(c: GColors, label: String, tag: String, step: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        GChip(c, "−", description = L10n.tr("%s, earlier", label), tag = "grp.$tag.minus") { step(-30) }
        Text(label, color = c.ink2, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 4.dp).testTag("grp.$tag.label"))
        GChip(c, "+", description = L10n.tr("%s, later", label), tag = "grp.$tag.plus") { step(30) }
    }
}
