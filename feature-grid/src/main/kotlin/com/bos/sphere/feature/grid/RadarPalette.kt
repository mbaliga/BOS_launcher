package com.bos.sphere.feature.grid

import com.bos.sphere.feature.search.palette.PaletteHost
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

/** PAL-09 reset: everything Radar learned from selections, including the people habits and the remembered results, is forgotten. */
internal object RadarLearning {
    private const val STAMP = "forgottenAt"
    private fun sp(c: Context) = c.getSharedPreferences("search_habits", Context.MODE_PRIVATE) // excluded from backup (backup_rules.xml)

    /**
     * [now] is stamped so the resting Radar ignores the home's launch log from before this moment: the log belongs to the home (recents,
     * suggestions), so it is not deleted, but without the stamp the rings would look exactly as before the reset.
     */
    fun forget(context: Context, now: Long = System.currentTimeMillis()) {
        PaletteHost.resetLearning(context)
        ContactHabits.clear(context)
        RadarMemory.hits.clear()
        runCatching { sp(context).edit().putLong(STAMP, now).apply() }
    }

    /** When the user last forgot what Radar learned; 0 when never. Never later than the clock says now (a clock set back). */
    fun forgottenAt(context: Context): Long = runCatching { sp(context).getLong(STAMP, 0L) }.getOrDefault(0L)
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

internal fun appUninstallIntent(app: AppEntry): Intent = com.bos.sphere.core.data.AppUninstall.intent(app)

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
