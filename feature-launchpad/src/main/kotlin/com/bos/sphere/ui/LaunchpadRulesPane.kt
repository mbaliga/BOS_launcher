package com.bos.sphere.ui

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bos.sphere.core.data.AppPolicy
import com.bos.sphere.core.data.DeliveryMode
import com.bos.sphere.core.data.LAUNCHPAD_SCOPE_NOTICE
import com.bos.sphere.core.data.LaunchpadAction
import com.bos.sphere.core.data.LaunchpadGesture
import com.bos.sphere.core.data.LaunchpadGestures
import com.bos.sphere.core.data.GroupMode
import com.bos.sphere.core.data.LaunchpadSettings
import com.bos.sphere.core.data.Rule
import com.bos.sphere.core.data.RuleAction
import com.bos.sphere.core.design.HyleColors
import com.bos.sphere.core.design.L10n
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private fun actionLabel(a: RuleAction) = when (a) {
    RuleAction.Hide -> L10n.tr("Hide")
    RuleAction.Silent -> L10n.tr("Mute (no badges)")
    RuleAction.Pin -> L10n.tr("Pin")
    RuleAction.Important -> L10n.tr("Important")
    RuleAction.AutoClear -> L10n.tr("Auto-clear")
    RuleAction.GroupApp -> L10n.tr("Group by app")
    RuleAction.GroupSender -> L10n.tr("Group by sender")
    RuleAction.GroupConversation -> L10n.tr("Group by conversation")
    RuleAction.Flat -> L10n.tr("Flat list")
}

private fun modeLabel(m: GroupMode) = when (m) {
    GroupMode.Time -> L10n.tr("Time")
    GroupMode.App -> L10n.tr("App")
    GroupMode.Sender -> L10n.tr("Sender")
    GroupMode.Conversation -> L10n.tr("Conversation")
    GroupMode.Flat -> L10n.tr("Flat")
    GroupMode.AppConversation -> L10n.tr("App and conversation")
}

/**
 * Settings pane for Launchpad: default grouping, the list of rules (readable, deletable) and an
 * "Add rule" form. Safe to drop inside any Column.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LaunchpadRulesPane(modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    remember(ctx) { LaunchpadSettings.init(ctx); true }
    val rules by LaunchpadSettings.rules.collectAsStateWithLifecycle()
    val prefs by LaunchpadSettings.prefs.collectAsStateWithLifecycle()

    val apps by produceState(emptyList<Pair<String, String>>()) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val pm = ctx.packageManager
                val q = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                pm.queryIntentActivities(q, 0)
                    .map { it.activityInfo.packageName to it.loadLabel(pm).toString() }
                    .distinctBy { it.first }
                    .sortedBy { it.second.lowercase() }
            }.getOrDefault(emptyList())
        }
    }
    fun labelOf(pkg: String?): String? =
        pkg?.let { p -> apps.firstOrNull { it.first == p }?.second ?: p }

    var adding by remember { mutableStateOf(false) }

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(L10n.tr("Default grouping"), color = HyleColors.InkBright, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            GroupMode.entries.forEach { m ->
                Chip(modeLabel(m), prefs.defaultMode == m) { LaunchpadSettings.setDefaultMode(m) }
            }
        }

        Text(L10n.tr(LAUNCHPAD_SCOPE_NOTICE), color = HyleColors.InkDim, fontSize = 12.sp)
        DeliveryPane(apps)

        Text(L10n.tr("Rules"), color = HyleColors.InkBright, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
        if (rules.isEmpty()) {
            Text(L10n.tr("No rules yet"), color = HyleColors.InkDim, fontSize = 13.sp)
        }
        rules.forEach { r ->
            Row(
                Modifier.fillMaxWidth().background(HyleColors.Surface, RoundedCornerShape(12.dp)).padding(start = 14.dp, top = 4.dp, bottom = 4.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(r.describe(labelOf(r.packageName)), color = HyleColors.Ink, fontSize = 13.sp, modifier = Modifier.weight(1f))
                Text(
                    L10n.tr("Delete"), color = HyleColors.InkDim, fontSize = 12.sp,
                    modifier = Modifier.clickable { LaunchpadSettings.removeRule(r.id) }.padding(12.dp),
                )
            }
        }

        if (!adding) {
            Chip(L10n.tr("Add rule…"), selected = false) { adding = true }
        } else {
            AddRuleForm(apps = apps, onDone = { adding = false })
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddRuleForm(apps: List<Pair<String, String>>, onDone: () -> Unit) {
    var pkg by remember { mutableStateOf<String?>(null) }
    var appQuery by remember { mutableStateOf("") }
    var sender by remember { mutableStateOf("") }
    var text by remember { mutableStateOf("") }
    var action by remember { mutableStateOf(RuleAction.Hide) }
    var minutes by remember { mutableStateOf(60) }

    Column(
        Modifier.fillMaxWidth().background(HyleColors.Surface, RoundedCornerShape(14.dp)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(L10n.tr("App"), color = HyleColors.InkDim, fontSize = 12.sp)
        if (pkg != null) {
            Chip((apps.firstOrNull { it.first == pkg }?.second ?: pkg).orEmpty() + "  ×", true) { pkg = null; appQuery = "" }
        } else {
            Field(appQuery, { appQuery = it }, L10n.tr("Any app (type to search)"))
            if (appQuery.isNotBlank()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    apps.filter { it.second.contains(appQuery, ignoreCase = true) }.take(6).forEach { (p, l) ->
                        Chip(l, false) { pkg = p }
                    }
                }
            }
        }
        Text(L10n.tr("Sender (optional)"), color = HyleColors.InkDim, fontSize = 12.sp)
        Field(sender, { sender = it }, L10n.tr("e.g. Mum"))
        Text(L10n.tr("Contains text (optional)"), color = HyleColors.InkDim, fontSize = 12.sp)
        Field(text, { text = it }, L10n.tr("e.g. promo"))

        Text(L10n.tr("Action"), color = HyleColors.InkDim, fontSize = 12.sp)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            RuleAction.entries.forEach { a -> Chip(actionLabel(a), action == a) { action = a } }
        }
        if (action == RuleAction.AutoClear) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(15 to "15 min", 60 to "1 h", 1440 to "1 day", 10080 to "1 week").forEach { (m, l) ->
                    Chip(l, minutes == m) { minutes = m }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip(L10n.tr("Add"), true) {
                LaunchpadSettings.addRule(
                    Rule.create(
                        action, packageName = pkg, sender = sender.trim(), text = text.trim(),
                        minutes = if (action == RuleAction.AutoClear) minutes else 0,
                    ),
                )
                onDone()
            }
            Chip(L10n.tr("Cancel"), false) { onDone() }
        }
    }
}

@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        Modifier
            .background(if (selected) HyleColors.Violet.copy(alpha = 0.22f) else Color.Transparent, shape)
            .border(1.dp, if (selected) HyleColors.Violet.copy(alpha = 0.6f) else HyleColors.Hairline, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        Text(label, color = if (selected) HyleColors.InkBright else HyleColors.Ink, fontSize = 13.sp)
    }
}

@Composable
private fun Field(value: String, onChange: (String) -> Unit, hint: String) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        Modifier.fillMaxWidth().border(1.dp, HyleColors.Hairline, shape).padding(horizontal = 12.dp, vertical = 9.dp),
    ) {
        if (value.isEmpty()) Text(hint, color = HyleColors.InkDim, fontSize = 14.sp)
        BasicTextField(
            value = value, onValueChange = onChange, singleLine = true,
            textStyle = TextStyle(color = HyleColors.InkBright, fontSize = 14.sp),
            cursorBrush = SolidColor(HyleColors.Violet),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** STR-04: per app, show immediately, batch into a digest at chosen times, or keep silent in Launchpad only. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DeliveryPane(apps: List<Pair<String, String>>) {
    val policies by LaunchpadSettings.policies.collectAsStateWithLifecycle()
    var pkg by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    Text(L10n.tr("Delivery per app"), color = HyleColors.InkBright, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
    policies.forEach { p ->
        Column(
            Modifier.fillMaxWidth().background(HyleColors.Surface, RoundedCornerShape(12.dp)).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(apps.firstOrNull { it.first == p.packageName }?.second ?: p.packageName, color = HyleColors.InkBright, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                Text(L10n.tr("Delete"), color = HyleColors.InkDim, fontSize = 12.sp, modifier = Modifier.clickable { LaunchpadSettings.setPolicy(AppPolicy(p.packageName)) }.padding(8.dp))
            }
            PolicyEditor(p)
        }
    }
    if (pkg == null) {
        Field(query, { query = it }, L10n.tr("Add an app (type to search)"))
        if (query.isNotBlank()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                apps.filter { it.second.contains(query, ignoreCase = true) && policies.none { p -> p.packageName == it.first } }.take(6).forEach { (p, l) ->
                    Chip(l, false) {
                        LaunchpadSettings.setPolicy(AppPolicy(p, DeliveryMode.Digest)); query = ""
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PolicyEditor(p: AppPolicy) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        DeliveryMode.entries.forEach { m -> Chip(L10n.tr(m.label), p.mode == m) { LaunchpadSettings.setPolicy(p.copy(mode = m)) } }
    }
    Text(L10n.tr(p.mode.hint), color = HyleColors.InkDim, fontSize = 12.sp)
    if (p.mode == DeliveryMode.Digest) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(7 * 60, 8 * 60, 12 * 60, 15 * 60, 18 * 60, 21 * 60).forEach { m ->
                val on = m in p.digestMinutes
                Chip(AppPolicy.formatMinutes(m), on) {
                    val next = if (on) p.digestMinutes - m else (p.digestMinutes + m).sorted()
                    if (next.isNotEmpty()) LaunchpadSettings.setPolicy(p.copy(digestMinutes = next))
                }
            }
        }
    }
    Text(L10n.tr("Slide left runs"), color = HyleColors.InkDim, fontSize = 12.sp)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Chip(L10n.tr("Default"), p.primaryAction == null) { LaunchpadSettings.setPolicy(p.copy(primaryAction = null)) }
        LaunchpadGestures.allowed(LaunchpadGesture.SwipeItemLeft)
            .filter { it in listOf(LaunchpadAction.Dismiss, LaunchpadAction.MarkRead, LaunchpadAction.MuteApp, LaunchpadAction.OpenApp, LaunchpadAction.None) }
            .forEach { a -> Chip(L10n.tr(a.label), p.primaryAction == a) { LaunchpadSettings.setPolicy(p.copy(primaryAction = a)) } }
    }
}
