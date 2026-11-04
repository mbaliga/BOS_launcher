package com.bos.sphere.feature.grid

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bos.sphere.core.data.ActionApps
import com.bos.sphere.core.data.AppEntry
import com.bos.sphere.core.data.ComplicationKind
import com.bos.sphere.core.data.Corner
import com.bos.sphere.core.data.CornerActionId
import com.bos.sphere.core.data.CornerItem
import com.bos.sphere.core.data.CornerSlot
import com.bos.sphere.core.data.GridSpec
import com.bos.sphere.core.data.HomeGesture
import com.bos.sphere.core.data.HomePage
import com.bos.sphere.core.data.LauncherPrefs
import com.bos.sphere.core.data.PageLayout
import com.bos.sphere.core.data.PageShape
import com.bos.sphere.core.design.HyleColors
import com.bos.sphere.core.design.L10n
import com.bos.sphere.feature.search.AppSearch

private enum class SlotKind(val label: String) {
    Action("Action"), App("App"), Palette("Palette"), Complication("Complication"), Cluster("Cluster"), Widget("Widget"), None("Nothing")
}

private fun kindOf(slot: CornerSlot): SlotKind = when (slot) {
    CornerSlot.Empty -> SlotKind.None
    is CornerSlot.Single -> if (slot.item is CornerItem.App) SlotKind.App else SlotKind.Action
    is CornerSlot.Palette -> SlotKind.Palette
    is CornerSlot.Complication -> SlotKind.Complication
    is CornerSlot.ComplicationCluster -> SlotKind.Cluster
    is CornerSlot.Widget -> SlotKind.Widget
}

/**
 * Edits what one (physical) corner holds: a single action or app, a radial palette (a folder
 * that fans out from the corner), a complication or cluster, a peek widget, or nothing.
 * [onPickWidget] hands off to the widget picker; the caller saves the resulting slot.
 */
@Composable
fun CornerEditorDialog(
    corner: Corner,
    current: CornerSlot,
    apps: List<AppEntry>,
    appsByKey: Map<String, AppEntry>,
    onSave: (CornerSlot) -> Unit,
    onPickWidget: () -> Unit,
    onDismiss: () -> Unit,
) {
    var kind by remember { mutableStateOf(kindOf(current)) }
    // Palette draft
    var paletteName by remember { mutableStateOf((current as? CornerSlot.Palette)?.name ?: "Palette") }
    val paletteItems = remember { mutableStateListOf<CornerItem>().apply { (current as? CornerSlot.Palette)?.items?.let(::addAll) } }
    var addingToPalette by remember { mutableStateOf(false) }
    // Cluster draft
    val clusterKinds = remember {
        mutableStateListOf<ComplicationKind>().apply { (current as? CornerSlot.ComplicationCluster)?.kinds?.let(::addAll) }
    }

    GlassDialog(
        title = L10n.tr("%s corner", L10n.tr(corner.label)),
        onDismiss = onDismiss,
        footer = {
            when (kind) {
                SlotKind.Palette -> TextButtonPill(L10n.tr("Save palette"), primary = true, enabled = paletteItems.isNotEmpty()) {
                    onSave(CornerSlot.Palette(paletteName.ifBlank { "Palette" }, paletteItems.toList()))
                }
                SlotKind.Cluster -> TextButtonPill(L10n.tr("Save cluster"), primary = true, enabled = clusterKinds.isNotEmpty()) {
                    onSave(CornerSlot.ComplicationCluster(clusterKinds.toList()))
                }
                SlotKind.None -> TextButtonPill(L10n.tr("Clear corner"), primary = true) { onSave(CornerSlot.Empty) }
                else -> Unit
            }
            TextButtonPill(L10n.tr("Cancel"), onClick = onDismiss)
        },
    ) {
        Text(
            "Corners stay with the device's physical corner as it rotates (changeable in settings).",
            color = HyleColors.InkDim,
            fontSize = 11.5.sp,
        )
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 8.dp)) {
            ChipRow(SlotKind.entries, kind, { L10n.tr(it.label) }) { kind = it }
        }
        when (kind) {
            SlotKind.Action -> ActionList(apps, selected = ((current as? CornerSlot.Single)?.item as? CornerItem.Action)?.id) {
                onSave(CornerSlot.Single(it))
            }
            SlotKind.App -> AppList(apps, selected = ((current as? CornerSlot.Single)?.item as? CornerItem.App)?.key) {
                onSave(CornerSlot.Single(CornerItem.App(it.key)))
            }
            SlotKind.Palette -> {
                Text(L10n.tr("A palette is a folder: its items fan out in arcs from the corner."), color = HyleColors.InkDim, fontSize = 11.5.sp)
                NameField(paletteName) { paletteName = it.take(24) }
                paletteItems.forEachIndexed { i, item ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                        Box(Modifier.size(30.dp), contentAlignment = Alignment.Center) { ItemGlyph(item, appsByKey, 24.dp) }
                        Text(itemLabel(item, appsByKey), color = HyleColors.InkBright, fontSize = 13.sp, modifier = Modifier.weight(1f).padding(start = 10.dp))
                        SmallGlyphButton(Glyphs.Up, "Move up", enabled = i > 0) { paletteItems.add(i - 1, paletteItems.removeAt(i)) }
                        SmallGlyphButton(Glyphs.Down, "Move down", enabled = i < paletteItems.lastIndex) { paletteItems.add(i + 1, paletteItems.removeAt(i)) }
                        SmallGlyphButton(Glyphs.Close, "Remove") { paletteItems.removeAt(i) }
                    }
                }
                if (paletteItems.size < CornerSlot.MAX_PALETTE_ITEMS) {
                    if (addingToPalette) {
                        ItemPicker(apps = apps, onPick = { paletteItems += it; addingToPalette = false })
                    } else {
                        Row(Modifier.padding(top = 8.dp)) {
                            TextButtonPill(L10n.tr("+ Add item")) { addingToPalette = true }
                        }
                    }
                }
                Text("${paletteItems.size} / ${CornerSlot.MAX_PALETTE_ITEMS}", color = HyleColors.InkDim, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp))
            }
            SlotKind.Complication -> ComplicationKind.entries.forEach { k ->
                ActionRow(
                    label = L10n.tr(k.label),
                    summary = k.action?.let { "Tap: ${it.label}" },
                    leading = { ComplicationView(k, 28.dp, onClick = null) },
                    selected = (current as? CornerSlot.Complication)?.kind == k,
                ) { onSave(CornerSlot.Complication(k)) }
            }
            SlotKind.Cluster -> {
                Text("Pick up to ${CornerSlot.MAX_CLUSTER}; they sit on an arc around the corner.", color = HyleColors.InkDim, fontSize = 11.5.sp)
                ComplicationKind.entries.forEach { k ->
                    val on = k in clusterKinds
                    ActionRow(
                        label = L10n.tr(k.label),
                        summary = if (on) "#${clusterKinds.indexOf(k) + 1}" else null,
                        leading = { ComplicationView(k, 28.dp, onClick = null) },
                        selected = on,
                    ) {
                        if (on) clusterKinds.remove(k) else if (clusterKinds.size < CornerSlot.MAX_CLUSTER) clusterKinds += k
                    }
                }
            }
            SlotKind.Widget -> {
                Text(
                    "A widget peeks out of the corner and pops out to full size when you touch it.",
                    color = HyleColors.InkDim,
                    fontSize = 11.5.sp,
                )
                (current as? CornerSlot.Widget)?.let {
                    Text("Current: ${it.label}", color = HyleColors.InkBright, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
                }
                Row(Modifier.padding(top = 10.dp)) { TextButtonPill(L10n.tr("Choose widget…"), primary = true, onClick = onPickWidget) }
            }
            SlotKind.None -> Text(L10n.tr("This corner will be empty (long-press it to assign something later)."), color = HyleColors.InkDim, fontSize = 12.sp)
        }
    }
}

/** Binds a gesture to an action, an app, or nothing. */
@Composable
fun GestureBindingDialog(
    gesture: HomeGesture,
    current: CornerItem?,
    apps: List<AppEntry>,
    onSave: (CornerItem?) -> Unit,
    onDismiss: () -> Unit,
) {
    GlassDialog(
        title = L10n.tr(gesture.label),
        onDismiss = onDismiss,
        footer = {
            TextButtonPill(L10n.tr("Do nothing")) { onSave(null) }
            TextButtonPill(L10n.tr("Cancel"), onClick = onDismiss)
        },
    ) {
        Text(L10n.tr(gesture.hint), color = HyleColors.InkDim, fontSize = 11.5.sp, modifier = Modifier.padding(bottom = 6.dp))
        ItemPicker(apps = apps, selected = current, onPick = onSave)
    }
}

/** Actions first, then a searchable app list. */
@Composable
fun ItemPicker(apps: List<AppEntry>, selected: CornerItem? = null, onPick: (CornerItem) -> Unit) {
    var tab by remember { mutableStateOf(if (selected is CornerItem.App) 1 else 0) }
    ChipRow(listOf(0, 1), tab, { if (it == 0) "Actions" else "Apps" }) { tab = it }
    if (tab == 0) {
        ActionList(apps, (selected as? CornerItem.Action)?.id, onPick)
    } else {
        AppList(apps, (selected as? CornerItem.App)?.key) { onPick(CornerItem.App(it.key)) }
    }
}

/**
 * Actions to pick from. Ones that open an app (Music, Camera, Phone …) take a second step — "open
 * with" — so you can choose the system default or a specific app, including players the system
 * chooser doesn't list (e.g. Apple Music).
 */
@Composable
private fun ActionList(apps: List<AppEntry>, selected: CornerActionId?, onPick: (CornerItem) -> Unit) {
    var pending by remember { mutableStateOf<CornerActionId?>(null) }
    val id = pending
    if (id != null) {
        OpenWith(id, apps, onBack = { pending = null }, onPick = onPick)
        return
    }
    CornerActionId.entries.forEach { a ->
        ActionRow(label = L10n.tr(a.label), glyph = Glyphs.forAction(a), selected = a == selected) {
            if (a in ActionApps.appCapable) pending = a else onPick(CornerItem.Action(a))
        }
    }
}

@Composable
private fun OpenWith(id: CornerActionId, apps: List<AppEntry>, onBack: () -> Unit, onPick: (CornerItem) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val handlerPackages = remember(id) {
        val intent = ActionApps.intentFor(id)
        if (intent == null) emptySet() else runCatching {
            context.packageManager.queryIntentActivities(intent, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)
                .map { it.activityInfo.packageName }.toSet()
        }.getOrDefault(emptySet())
    }
    val suggested = remember(apps, handlerPackages) { apps.filter { it.packageName in handlerPackages } }
    ActionRow(label = L10n.tr("‹  Back"), onClick = onBack)
    SectionHeader("Open ${id.label} with")
    ActionRow(label = L10n.tr("System default"), summary = "Uses your default; asks if there are several", glyph = Glyphs.forAction(id)) {
        onPick(CornerItem.Action(id))
    }
    if (suggested.isNotEmpty()) {
        SectionHeader(L10n.tr("Apps that handle it"))
        suggested.forEach { app ->
            ActionRow(label = app.label, leading = { AppIcon(app, 28.dp, Modifier.size(28.dp)) }) { onPick(CornerItem.App(app.key)) }
        }
    }
    SectionHeader(L10n.tr("Any app"))
    AppList(apps, selected = null) { onPick(CornerItem.App(it.key)) }
}

@Composable
private fun AppList(apps: List<AppEntry>, selected: String?, onPick: (AppEntry) -> Unit) {
    var query by remember { mutableStateOf("") }
    NameField(query, placeholder = L10n.tr("Search apps")) { query = it }
    val shown = remember(apps, query) { if (query.isBlank()) apps else AppSearch.rank(apps, query, limit = 40) }
    shown.forEach { app ->
        ActionRow(label = app.label, leading = { AppIcon(app, 28.dp, Modifier.size(28.dp)) }, selected = app.key == selected) { onPick(app) }
    }
}

@Composable
private fun NameField(value: String, placeholder: String = "Name", onChange: (String) -> Unit) {
    Box(
        Modifier
            .padding(vertical = 8.dp)
            .fillMaxWidth()
            .background(HyleColors.Surface, RoundedCornerShape(10.dp))
            .border(1.dp, HyleColors.Hairline, RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        if (value.isEmpty()) Text(placeholder, color = HyleColors.InkDim, fontSize = 13.sp)
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            textStyle = TextStyle(color = HyleColors.InkBright, fontSize = 13.sp),
            cursorBrush = SolidColor(HyleColors.Violet),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun SmallGlyphButton(glyph: androidx.compose.ui.graphics.vector.ImageVector, description: String, enabled: Boolean = true, onClick: () -> Unit) {
    Icon(
        glyph,
        contentDescription = description,
        tint = if (enabled) HyleColors.Ink else HyleColors.InkDim.copy(alpha = 0.3f),
        modifier = Modifier
            .size(30.dp)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(5.dp),
    )
}

/** Long-press on a page tile (the clock). */
@Composable
fun TileMenuDialog(title: String, onRemove: () -> Unit, onDismiss: () -> Unit) {
    GlassDialog(title = title, onDismiss = onDismiss, footer = { TextButtonPill(L10n.tr("Cancel"), onClick = onDismiss) }) {
        Text(L10n.tr("Long-press and drag to move it."), color = HyleColors.InkDim, fontSize = 12.sp)
        Row(horizontalArrangement = Arrangement.Start, modifier = Modifier.padding(top = 10.dp).semantics { contentDescription = "Remove tile" }) {
            TextButtonPill(L10n.tr("Remove from page"), primary = true, onClick = onRemove)
        }
    }
}
