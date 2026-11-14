package com.bos.sphere.feature.grid

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bos.sphere.core.data.AppEntry
import com.bos.sphere.core.data.SearchCategory
import com.bos.sphere.core.data.SearchHit
import com.bos.sphere.core.design.HyleColors
import com.bos.sphere.core.design.L10n
import dev.aarso.search.palette.QueryCache
import dev.aarso.search.palette.Ranker
import dev.aarso.search.palette.StableOrder

/** Last hub results per query, so reopening Radar and typing the same query shows them at once (PAL-12). Process-local, never persisted. */
internal object RadarMemory {
    val hits = QueryCache<Map<SearchCategory, List<SearchHit>>>(capacity = 8)
}

/** PAL-09 and PAL-12 for streamed hub hits: ranked by match blended with local history, and stable under the selection. */
internal object HubOrdering {
    fun usageKey(h: SearchHit): String = h.extras["usage"] ?: h.id

    fun order(
        shown: Map<SearchCategory, List<SearchHit>>,
        fresh: Map<SearchCategory, List<SearchHit>>,
        selectedHitId: String?,
        ranker: Ranker?,
    ): Map<SearchCategory, List<SearchHit>> = fresh.mapValues { (cat, list) ->
        val ranked = if (ranker == null) list else list.sortedByDescending { ranker.blend(it.score.toDouble(), usageKey(it)) }
        StableOrder.merge(shown[cat].orEmpty(), ranked, selectedHitId) { it.id }
    }
}

internal fun appUninstallIntent(app: AppEntry): Intent =
    Intent(Intent.ACTION_DELETE).setData(Uri.fromParts("package", app.packageName, null)).putExtra(Intent.EXTRA_USER, app.user).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

/** The empty state lists the scope prefixes (PAL-13); tapping one starts the query with it. They are edited in Ground Control. */
@Composable
internal fun PrefixHints(mapping: ScopePrefixes.Mapping, onPick: (String) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(L10n.tr("Start with a letter and a space to search one place"), color = Color(0xB3FFFFFF), fontSize = 12.sp)
        Row(Modifier.padding(top = 6.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SearchCategory.entries.forEach { c ->
                val p = mapping.letterOf(c).toString()
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clip14().background(Color(0x22FFFFFF)).clickable { onPick("$p ") }.padding(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Text(p, color = Color(0xFFB9A8FF), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Text(" " + L10n.tr(c.label), color = Color.White, fontSize = 13.sp)
                }
            }
        }
    }
}

private fun Modifier.clip14(): Modifier = this.clip(RoundedCornerShape(14.dp))

/** PAL-02: the user's own name for an app, searched like its label. Empty clears it. */
@Composable
internal fun AliasDialog(app: AppEntry, current: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(current) }
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().clip14().background(Color(0xF2101018)).border(1.dp, Color(0x2EFFFFFF), RoundedCornerShape(14.dp)).padding(22.dp),
        ) {
            Text(L10n.tr("Search alias for %s", app.label), color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Text(L10n.tr("Type this in Radar to find the app"), color = Color(0xB3FFFFFF), fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
            BasicTextField(
                value = text, onValueChange = { text = it.replace('\n', ' ') }, singleLine = true,
                textStyle = TextStyle(color = Color.White, fontSize = 18.sp), cursorBrush = SolidColor(Color.White),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { onSave(text.trim()) }),
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp).background(Color(0x22FFFFFF), RoundedCornerShape(10.dp)).padding(12.dp),
            )
            Row(Modifier.padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(L10n.tr("CANCEL"), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clip14().background(Color(0xFF241F37)).clickable(onClick = onDismiss).padding(horizontal = 20.dp, vertical = 12.dp))
                Text(L10n.tr("SAVE"), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clip14().background(HyleColors.Violet.copy(alpha = 0.55f)).clickable { onSave(text.trim()) }.padding(horizontal = 28.dp, vertical = 12.dp))
            }
        }
    }
}

internal fun copiedToast(context: Context) =
    android.widget.Toast.makeText(context, L10n.tr("Copied"), android.widget.Toast.LENGTH_SHORT).show()
