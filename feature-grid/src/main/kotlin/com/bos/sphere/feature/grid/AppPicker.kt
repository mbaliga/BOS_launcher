package com.bos.sphere.feature.grid

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bos.sphere.core.data.AppEntry
import com.bos.sphere.core.design.HyleColors
import com.bos.sphere.core.design.L10n

/**
 * Pick any installed app for an action ("Music" → Apple Music, VLC, anything). Apps that look like
 * a fit ([suggested] package names) lead; the whole inventory follows, searchable.
 */
@Composable
fun AppPickerDialog(
    title: String,
    apps: List<AppEntry>,
    suggested: Set<String>,
    onPick: (AppEntry) -> Unit,
    onDismiss: () -> Unit,
    /** Optional row at the bottom, e.g. "Ask me each time". */
    resetLabel: String? = null,
    onReset: (() -> Unit)? = null,
) {
    var query by remember { mutableStateOf("") }
    val q = query.trim()
    val list = remember(apps, suggested, q) {
        val matches = if (q.isEmpty()) apps else apps.filter { it.label.contains(q, ignoreCase = true) || it.packageName.contains(q, ignoreCase = true) }
        val (fit, rest) = matches.sortedBy { it.label.lowercase() }.partition { it.packageName in suggested }
        fit to rest
    }
    GlassDialog(title = title, onDismiss = onDismiss) {
        BasicTextField(
            value = query, onValueChange = { query = it }, singleLine = true,
            textStyle = TextStyle(color = HyleColors.InkBright, fontSize = 16.sp),
            cursorBrush = SolidColor(HyleColors.Violet),
            modifier = Modifier.fillMaxWidth().background(HyleColors.Surface.copy(alpha = 0.7f), RoundedCornerShape(10.dp)).padding(horizontal = 12.dp, vertical = 10.dp),
            decorationBox = { inner -> Box { if (query.isEmpty()) Text(L10n.tr("Search apps"), color = HyleColors.InkDim, fontSize = 16.sp); inner() } },
        )
        Spacer(Modifier.height(8.dp))
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
            if (list.first.isNotEmpty()) item { Header(L10n.tr("Suggested")) }
            items(list.first, key = { "s:" + it.key }) { PickerRow(it, onPick) }
            if (list.second.isNotEmpty()) item { Header(L10n.tr("All apps")) }
            items(list.second, key = { "a:" + it.key }) { PickerRow(it, onPick) }
        }
        if (resetLabel != null && onReset != null) {
            Text(
                resetLabel, color = HyleColors.InkDim, fontSize = 14.sp,
                modifier = Modifier.fillMaxWidth().clickable(onClick = onReset).padding(vertical = 14.dp),
            )
        }
    }
}

@Composable
private fun Header(text: String) {
    Text(text.uppercase(), color = HyleColors.InkDim, fontSize = 11.sp, letterSpacing = 1.2.sp, modifier = Modifier.padding(top = 10.dp, bottom = 4.dp))
}

@Composable
private fun PickerRow(app: AppEntry, onPick: (AppEntry) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onPick(app) }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        AppIcon(app, 40.dp)
        Spacer(Modifier.width(14.dp))
        Text(app.label, color = HyleColors.InkBright, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
