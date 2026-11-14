package com.bos.sphere.feature.grid

import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.bos.sphere.core.data.AppEntry
import com.bos.sphere.core.design.HyleColors
import com.bos.sphere.core.design.L10n
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/* HOM-02 / HOM-03 / HOM-05: the dialogs behind "Home items": add things, pick an icon pack, override one icon, show hidden apps. */

@Composable
private fun Panel(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        val shape = RoundedCornerShape(24.dp)
        Column(Modifier.clip(shape).background(HyleColors.Surface.copy(alpha = 0.97f)).border(1.dp, HyleColors.Hairline, shape).padding(16.dp)) { content() }
    }
}

@Composable
private fun Row1(text: String, selected: Boolean = false, hint: String? = null, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(text, color = HyleColors.InkBright, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (hint != null) Text(hint, color = HyleColors.InkDim, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (selected) Text("✓", color = HyleColors.InkBright, fontSize = 16.sp)
    }
}

/** The "Home items" sheet: every way to put a non-app item on home, and the icon and hidden-app tools. */
@Composable
fun HomeItemsSheet(
    onWidget: () -> Unit,
    onFolder: () -> Unit,
    onLink: () -> Unit,
    onFile: () -> Unit,
    onShortcut: () -> Unit,
    onIconPack: () -> Unit,
    onHiddenApps: () -> Unit,
    onDismiss: () -> Unit,
) {
    Panel(onDismiss) {
        Text(L10n.tr("Home items"), color = HyleColors.InkBright, fontSize = 20.sp, modifier = Modifier.padding(bottom = 8.dp))
        Row1(L10n.tr("Widget"), hint = L10n.tr("Pick from a searchable list")) { onWidget() }
        Row1(L10n.tr("New folder")) { onFolder() }
        Row1(L10n.tr("App shortcut"), hint = L10n.tr("A shortcut an app offers")) { onShortcut() }
        Row1(L10n.tr("Web link")) { onLink() }
        Row1(L10n.tr("File"), hint = L10n.tr("Opens with the app you choose")) { onFile() }
        Row1(L10n.tr("Icon pack and themed icons")) { onIconPack() }
        Row1(L10n.tr("Hidden apps")) { onHiddenApps() }
    }
}

@Composable
fun LinkDialog(onAdd: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("https://") }
    Panel(onDismiss) {
        Text(L10n.tr("Web link"), color = HyleColors.InkBright, fontSize = 18.sp, modifier = Modifier.padding(bottom = 8.dp))
        BasicTextField(
            value = text, onValueChange = { text = it }, singleLine = true,
            textStyle = TextStyle(color = HyleColors.InkBright, fontSize = 16.sp), cursorBrush = SolidColor(HyleColors.InkBright),
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
        )
        Row1(L10n.tr("Add to home")) { text.trim().takeIf { it.length > 8 && ' ' !in it }?.let { onAdd(it); onDismiss() } }
    }
}

/** A shortcut an app offers. */
data class PinnableShortcut(val id: String, val label: String)

@Composable
fun ShortcutPickerDialog(
    apps: List<AppEntry>,
    shortcutsOf: (AppEntry) -> List<PinnableShortcut>,
    onPick: (AppEntry, PinnableShortcut) -> Unit,
    onDismiss: () -> Unit,
) {
    var app by remember { mutableStateOf<AppEntry?>(null) }
    var rows by remember { mutableStateOf<List<PinnableShortcut>>(emptyList()) }
    val a = app
    LaunchedEffect(a) { rows = if (a == null) emptyList() else withContext(Dispatchers.Default) { runCatching { shortcutsOf(a) }.getOrDefault(emptyList()) } }
    Panel(onDismiss) {
        Text(if (a == null) L10n.tr("Choose an app") else a.label, color = HyleColors.InkBright, fontSize = 18.sp, modifier = Modifier.padding(bottom = 8.dp))
        LazyColumn(Modifier.heightIn(max = 420.dp)) {
            if (a == null) items(apps, key = { it.key }) { ap -> Row1(ap.label) { app = ap } }
            else {
                if (rows.isEmpty()) item { Text(L10n.tr("This app offers no shortcuts."), color = HyleColors.InkDim, fontSize = 13.sp, modifier = Modifier.padding(8.dp)) }
                items(rows, key = { it.id }) { r -> Row1(r.label) { onPick(a, r); onDismiss() } }
            }
        }
    }
}

/** HOM-03: pick the icon pack and turn themed (monochrome) icons on or off. */
@Composable
fun IconPackDialog(
    packs: List<Pair<String, String>>,
    selected: String?,
    themed: Boolean,
    onSelect: (String?) -> Unit,
    onThemed: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    Panel(onDismiss) {
        Text(L10n.tr("Icons"), color = HyleColors.InkBright, fontSize = 18.sp, modifier = Modifier.padding(bottom = 8.dp))
        Row1(L10n.tr("Themed icons"), selected = themed, hint = L10n.tr("Uses the app's monochrome icon where it has one (Android 13 and later)")) { onThemed(!themed) }
        Row1(L10n.tr("No icon pack"), selected = selected == null) { onSelect(null) }
        LazyColumn(Modifier.heightIn(max = 360.dp)) {
            items(packs, key = { it.first }) { (pkg, label) -> Row1(label, selected = pkg == selected, hint = pkg) { onSelect(pkg) } }
            if (packs.isEmpty()) item { Text(L10n.tr("No icon packs are installed."), color = HyleColors.InkDim, fontSize = 13.sp, modifier = Modifier.padding(8.dp)) }
        }
    }
}

/** HOM-03: choose one drawable of an icon pack for a single item, or go back to the default. */
@Composable
fun IconOverrideDialog(
    title: String,
    packs: List<Pair<String, String>>,
    drawablesOf: (String) -> List<String>,
    drawable: (String, String) -> Drawable?,
    hasOverride: Boolean,
    onPick: (pack: String, drawable: String) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    var pack by remember { mutableStateOf<String?>(packs.singleOrNull()?.first) }
    var query by remember { mutableStateOf("") }
    val p = pack
    Panel(onDismiss) {
        Text(title, color = HyleColors.InkBright, fontSize = 18.sp, modifier = Modifier.padding(bottom = 8.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (hasOverride) Row1(L10n.tr("Use the default icon")) { onReset(); onDismiss() }
        if (p == null) {
            LazyColumn(Modifier.heightIn(max = 360.dp)) {
                items(packs, key = { it.first }) { (pkg, label) -> Row1(label, hint = pkg) { pack = pkg } }
                if (packs.isEmpty()) item { Text(L10n.tr("No icon packs are installed."), color = HyleColors.InkDim, fontSize = 13.sp, modifier = Modifier.padding(8.dp)) }
            }
        } else {
            val all = remember(p) { drawablesOf(p) }
            val shown = remember(all, query) { all.filter { query.isBlank() || it.contains(query.trim(), ignoreCase = true) }.take(240) }
            BasicTextField(
                value = query, onValueChange = { query = it }, singleLine = true,
                textStyle = TextStyle(color = HyleColors.InkBright, fontSize = 15.sp), cursorBrush = SolidColor(HyleColors.InkBright),
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            )
            LazyVerticalGrid(GridCells.Adaptive(56.dp), Modifier.heightIn(max = 360.dp), verticalArrangement = Arrangement.spacedBy(6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                gridItems(shown, key = { it }) { name ->
                    var bmp by remember(name) { mutableStateOf<ImageBitmap?>(null) }
                    LaunchedEffect(name) {
                        bmp = withContext(Dispatchers.Default) { runCatching { drawable(p, name)?.let { IconSources.rasterizeFull(it, 96).asImageBitmap() } }.getOrNull() }
                    }
                    Box(Modifier.size(56.dp).clip(RoundedCornerShape(12.dp)).clickable { onPick(p, name); onDismiss() }, contentAlignment = Alignment.Center) {
                        bmp?.let { Image(it, name, Modifier.size(52.dp)) }
                    }
                }
            }
        }
    }
}

/** HOM-05: the hidden apps, with a way to bring each back. */
@Composable
fun HiddenAppsDialog(apps: List<AppEntry>, onUnhide: (AppEntry) -> Unit, onLaunch: (AppEntry) -> Unit, onDismiss: () -> Unit) {
    Panel(onDismiss) {
        Text(L10n.tr("Hidden apps"), color = HyleColors.InkBright, fontSize = 18.sp, modifier = Modifier.padding(bottom = 8.dp))
        if (apps.isEmpty()) Text(L10n.tr("No apps are hidden."), color = HyleColors.InkDim, fontSize = 13.sp, modifier = Modifier.padding(8.dp))
        LazyColumn(Modifier.heightIn(max = 420.dp)) {
            items(apps, key = { it.key }) { app ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    AppIcon(app, 40.dp)
                    Text(app.label, color = HyleColors.InkBright, fontSize = 15.sp, modifier = Modifier.weight(1f).padding(start = 12.dp).clickable { onLaunch(app) }, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(L10n.tr("Unhide"), color = HyleColors.InkBright, fontSize = 13.sp, modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(HyleColors.VioletSoft.copy(alpha = 0.8f)).clickable { onUnhide(app) }.padding(horizontal = 12.dp, vertical = 6.dp))
                }
            }
        }
    }
}

/** A small menu for one home item: its actions as a list. */
@Composable
fun HomeItemMenu(title: String, entries: List<Pair<String, () -> Unit>>, onDismiss: () -> Unit) {
    Panel(onDismiss) {
        Text(title, color = HyleColors.InkBright, fontSize = 18.sp, modifier = Modifier.padding(bottom = 8.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
        entries.forEach { (label, run) -> Row1(label) { run(); onDismiss() } }
    }
}

/** HOM-04: create a folder from the menu: tick the apps, name it. */
@Composable
fun NewFolderDialog(apps: List<AppEntry>, onCreate: (keys: List<String>, name: String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(L10n.tr("Folder")) }
    var picked by remember { mutableStateOf(listOf<String>()) }
    Panel(onDismiss) {
        BasicTextField(
            value = name, onValueChange = { name = it }, singleLine = true,
            textStyle = TextStyle(color = HyleColors.InkBright, fontSize = 18.sp), cursorBrush = SolidColor(HyleColors.InkBright),
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        )
        LazyColumn(Modifier.heightIn(max = 380.dp)) {
            items(apps, key = { it.key }) { a ->
                Row1(a.label, selected = a.key in picked) { picked = if (a.key in picked) picked - a.key else picked + a.key }
            }
        }
        Row1(L10n.tr("Create folder")) { if (picked.isNotEmpty()) { onCreate(picked, name.trim().ifEmpty { L10n.tr("Folder") }); onDismiss() } }
    }
}
