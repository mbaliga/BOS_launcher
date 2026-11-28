package com.bos.sphere.feature.grid

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
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
    // Wave 48: a full One UI page (large title, search, the apps as lazy rows), not a popup. The reset row is a flat text button under the list.
    OneUiFullPage(onDismiss) {
        Column(Modifier.fillMaxSize().imePadding()) {
            OneUiPageFrame(title, onDismiss, modifier = Modifier.weight(1f), tag = OneUiDialogTags.EDITOR_PAGE) {
                item(key = "search") {
                    BasicTextField(
                        value = query, onValueChange = { query = it }, singleLine = true,
                        textStyle = TextStyle(color = HyleColors.InkBright, fontSize = 16.sp),
                        cursorBrush = SolidColor(HyleColors.Violet),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp).background(HyleColors.Surface.copy(alpha = 0.7f), RoundedCornerShape(10.dp)).padding(horizontal = 12.dp, vertical = 10.dp),
                        decorationBox = { inner -> Box { if (query.isEmpty()) Text(L10n.tr("Search apps"), color = HyleColors.InkDim, fontSize = 16.sp); inner() } },
                    )
                }
                if (list.first.isNotEmpty()) item(key = "h:s") { Box(Modifier.padding(horizontal = 18.dp)) { Header(L10n.tr("Suggested")) } }
                items(list.first, key = { "s:" + it.key }) { Box(Modifier.padding(horizontal = 18.dp)) { PickerRow(it, onPick) } }
                if (list.second.isNotEmpty()) item(key = "h:a") { Box(Modifier.padding(horizontal = 18.dp)) { Header(L10n.tr("All apps")) } }
                items(list.second, key = { "a:" + it.key }) { Box(Modifier.padding(horizontal = 18.dp)) { PickerRow(it, onPick) } }
            }
            if (resetLabel != null && onReset != null) {
                Box(Modifier.fillMaxWidth().height(1.dp).background(HyleColors.Hairline))
                Text(
                    resetLabel, color = HyleColors.Violet, fontSize = 16.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onReset).padding(horizontal = 28.dp, vertical = 16.dp),
                )
            }
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
