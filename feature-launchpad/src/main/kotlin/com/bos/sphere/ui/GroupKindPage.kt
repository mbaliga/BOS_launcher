package com.bos.sphere.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.bos.sphere.core.data.ConditionKind
import com.bos.sphere.core.design.L10n

/** Test tags of the full page the rule editor's condition choice opens (wave 48). */
internal object GroupKindPageTags {
    const val PAGE = "grp.kindPage"
    fun option(k: ConditionKind) = "grp.kindPage.option.${k.name}"
}

/**
 * Wave 48 (popupPages): choosing a condition kind is a choice from many, so it is a full page (back arrow, the title in the bar, one radio row
 * per kind) and no longer a dropdown. [selected] is the kind the row already has (null when adding a new condition). Picking a row applies it
 * and returns; the back arrow leaves without changing anything. Launchpad does not depend on the grid module, so the page is drawn here in
 * the Launchpad's own colours.
 */
@Composable
internal fun GroupKindPage(c: GColors, title: String, selected: ConditionKind?, onPick: (ConditionKind) -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize().background(LocalLp.current.bg).semantics { paneTitle = title }.testTag(GroupKindPageTags.PAGE)) {
            Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.padding(start = 4.dp).size(48.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onDismiss)
                            .semantics { contentDescription = L10n.tr("Back") },
                        contentAlignment = Alignment.Center,
                    ) { Text("‹", color = c.ink, fontSize = 30.sp) }
                    Text(
                        title, color = c.ink, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.weight(1f).semantics { heading() },
                    )
                    Box(Modifier.size(52.dp))
                }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 8.dp).selectableGroup(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    ConditionKind.entries.forEach { k ->
                        val on = k == selected
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag(GroupKindPageTags.option(k))
                                .selectable(selected = on, role = Role.RadioButton, onClick = { onPick(k); onDismiss() }).padding(horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(GroupTexts.kindLabel(k), color = c.ink, fontSize = 17.sp, modifier = Modifier.weight(1f))
                            Box(
                                Modifier.size(22.dp).clip(CircleShape).background(if (on) c.accent else c.line),
                                contentAlignment = Alignment.Center,
                            ) { Box(Modifier.size(if (on) 8.dp else 18.dp).clip(CircleShape).background(if (on) c.surface else LocalLp.current.bg)) }
                        }
                    }
                }
            }
        }
    }
}
