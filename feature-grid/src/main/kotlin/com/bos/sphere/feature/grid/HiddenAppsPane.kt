package com.bos.sphere.feature.grid

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bos.sphere.core.data.HiddenAppRow
import com.bos.sphere.core.data.HiddenAppsModel
import com.bos.sphere.core.data.Rule
import com.bos.sphere.core.design.HyleColors
import com.bos.sphere.core.design.L10n

/**
 * Wave 38 (popupsHidden): the Hidden apps page of Ground Control. Every hidden app (from the app menu's Hide or a Comms "Hide this app" rule) with its
 * icon, name and where it is hidden, a search field, an Unhide button per app and Unhide all; each asks once, in one line, before it clears every
 * mechanism for the app. Below it the "Hidden by Comms rules" list (every Hide rule, also the narrower ones) with a Remove button each.
 */
@Composable
fun ColumnScope.HiddenAppsPane(
    rows: List<HiddenAppRow>,
    commsRules: List<Rule>,
    appLabel: (String) -> String?,
    onUnhide: (HiddenAppRow) -> Unit,
    onUnhideAll: () -> Unit,
    onRemoveRule: (Rule) -> Unit,
    icon: @Composable (HiddenAppRow) -> Unit,
    initialQuery: String = "",
) {
    var query by remember { mutableStateOf(initialQuery) }
    var confirm by remember { mutableStateOf<String?>(null) } // a row key, or ALL
    val shown = HiddenAppsModel.filter(rows, query)
    Text(L10n.tr("Hidden apps come back to home, the drawer, Radar and Comms when you unhide them."), color = HyleColors.InkDim, fontSize = 12.sp, modifier = Modifier.padding(vertical = 6.dp))
    BasicTextField(
        value = query, onValueChange = { query = it; confirm = null }, singleLine = true,
        textStyle = TextStyle(color = HyleColors.InkBright, fontSize = 16.sp), cursorBrush = SolidColor(HyleColors.InkBright),
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).testTag("hid.search").semantics { contentDescription = L10n.tr("Search hidden apps") },
        decorationBox = { inner ->
            Box(Modifier.fillMaxWidth().heightIn(min = 48.dp).border(1.dp, HyleColors.Hairline, RoundedCornerShape(12.dp)).padding(horizontal = 14.dp), Alignment.CenterStart) {
                if (query.isEmpty()) Text(L10n.tr("Search hidden apps"), color = HyleColors.InkDim, fontSize = 16.sp)
                inner()
            }
        },
    )
    if (rows.isEmpty()) Text(L10n.tr("No apps are hidden."), color = HyleColors.InkDim, fontSize = 14.sp, modifier = Modifier.padding(vertical = 12.dp).testTag("hid.empty"))
    else if (shown.isEmpty()) Text(L10n.tr("No hidden app matches."), color = HyleColors.InkDim, fontSize = 14.sp, modifier = Modifier.padding(vertical = 12.dp).testTag("hid.nomatch"))
    shown.forEach { row ->
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(vertical = 4.dp).testTag("hid.row.${row.key}"), verticalAlignment = Alignment.CenterVertically) {
            icon(row)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(row.label, color = HyleColors.InkBright, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(row.sources.joinToString(" · ") { L10n.tr(it.label) }, color = HyleColors.InkDim, fontSize = 11.5.sp, maxLines = 2, modifier = Modifier.testTag("hid.source.${row.key}"))
            }
            Pill(L10n.tr("Unhide"), "hid.unhide.${row.key}", L10n.tr("Unhide %s", row.label)) { confirm = row.key }
        }
        if (confirm == row.key) ConfirmLine(L10n.tr("Unhide %s everywhere? It comes back to home, the drawer, Radar and Comms.", row.label), "hid.confirm", { confirm = null }) { onUnhide(row); confirm = null }
    }
    if (rows.size > 1) {
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
            Pill(L10n.tr("Unhide all"), "hid.unhideAll", L10n.tr("Unhide all")) { confirm = ALL }
        }
        if (confirm == ALL) ConfirmLine(L10n.tr("Unhide all %d apps? They come back to home, the drawer, Radar and Comms.", rows.size), "hid.confirmAll", { confirm = null }) { onUnhideAll(); confirm = null }
    }
    if (commsRules.isNotEmpty()) {
        Text(L10n.tr("Hidden by Comms rules"), color = HyleColors.InkBright, fontSize = 15.sp, modifier = Modifier.padding(top = 20.dp, bottom = 2.dp).semantics { heading() })
        Text(L10n.tr("Comms hides these notifications. Remove a rule to show them again."), color = HyleColors.InkDim, fontSize = 12.sp, modifier = Modifier.padding(bottom = 6.dp))
        commsRules.forEach { r ->
            Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(vertical = 2.dp).testTag("hid.rule.${r.id}"), verticalAlignment = Alignment.CenterVertically) {
                Text(r.describe(r.packageName?.let(appLabel)), color = HyleColors.InkBright, fontSize = 14.sp, maxLines = 3, modifier = Modifier.weight(1f).padding(end = 12.dp))
                Pill(L10n.tr("Remove"), "hid.remove.${r.id}", L10n.tr("Remove rule: %s", r.describe(r.packageName?.let(appLabel)))) { onRemoveRule(r) }
            }
        }
    }
}

private const val ALL = "*"

@Composable
private fun Pill(text: String, tag: String, description: String, onClick: () -> Unit) {
    Box(
        Modifier.heightIn(min = 48.dp).testTag(tag).semantics { role = Role.Button; contentDescription = description }, Alignment.Center,
    ) {
        Text(
            text, color = HyleColors.InkBright, fontSize = 13.sp,
            modifier = Modifier.clip10().background(HyleColors.VioletSoft.copy(alpha = 0.8f)).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp),
        )
    }
}

private fun Modifier.clip10() = this.then(Modifier.clip(RoundedCornerShape(10.dp)))

@Composable
private fun ConfirmLine(text: String, tag: String, onCancel: () -> Unit, onConfirm: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(bottom = 6.dp).background(HyleColors.Surface.copy(alpha = 0.7f), RoundedCornerShape(12.dp)).padding(12.dp).testTag(tag)) {
        Text(text, color = HyleColors.InkBright, fontSize = 13.sp)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Pill(L10n.tr("Cancel"), "$tag.cancel", L10n.tr("Cancel"), onCancel)
            Pill(L10n.tr("Unhide"), "$tag.ok", L10n.tr("Unhide"), onConfirm)
        }
    }
}
