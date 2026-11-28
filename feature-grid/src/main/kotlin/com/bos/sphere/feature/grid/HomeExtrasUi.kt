package com.bos.sphere.feature.grid

import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bos.sphere.core.catalog.HomeState
import com.bos.sphere.core.catalog.ItemDef
import com.bos.sphere.core.catalog.ItemKeys
import com.bos.sphere.core.catalog.ItemKind
import com.bos.sphere.core.catalog.WidgetSpan
import com.bos.sphere.core.data.AppEntry
import com.bos.sphere.core.design.HyleColors
import com.bos.sphere.core.design.L10n
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * HOM-01 / HOM-02 / HOM-07: everything on home that is not a plain app, handed to the live
 * surfaces. [state] is the one shared arrangement extras (folders, shortcut / link definitions,
 * widget spans); the lambdas are the host's: app lookup, shortcut icons, the hosted widget view,
 * taps, menus, drag-onto and resize.
 */
class HomeExtras(
    val state: HomeState,
    val resolveApp: (String) -> AppEntry?,
    val shortcutIcon: (ItemDef) -> Drawable?,
    val widget: @Composable (key: String, modifier: Modifier) -> Unit,
    val onTap: (String) -> Unit = {},
    val onLongPress: (String) -> Unit = {},
    val onDropOnto: (drag: String, target: String) -> Unit = { _, _ -> },
    /** INP-11: links and files dropped on the folder tile [folderKey] are filed into it. */
    val onDropItems: (folderKey: String, defs: List<ItemDef>) -> Unit = { _, _ -> },
    /** A keyboard edit (INP-09): the same [ItemAction] a drag produces, applied through the host's one edit path. */
    val onAction: (ItemAction) -> Unit = {},
    /** HOM-17: the usage split the Usage tile draws as a bar inside itself (empty: no bar). */
    val usageSlices: List<com.bos.sphere.core.catalog.UsageSlice> = emptyList(),
    /** HOM-15: the first few recent files for the Files tile (thumbnail or just the name); empty draws the plain glyph. */
    val fileCells: List<FileTileCell> = emptyList(),
    /** Wave 45 (editHandles): the one selection of home edit mode (a stable object the host owns, not rebuilt with these extras). */
    val selection: EditSelectionState = EditSelectionState(),
    /** The provider's resize rules for a widget (null: free within the grid, e.g. a stack or no provider info). */
    val limitsOf: (String) -> com.bos.sphere.core.catalog.ProviderLimits? = { null },
    /** A resize result from the handles, the keyboard or an accessibility action: the new size, and whether the origin moved too. */
    val onResizeTo: (key: String, rect: com.bos.sphere.core.catalog.CellRect, moveOrigin: Boolean) -> Unit = { _, _, _ -> },
    /** The grid size (columns, rows) for surfaces that do not report their own. */
    val gridDims: () -> Pair<Int, Int> = { 4 to 6 },
) {
    private val owned: Set<String> = state.nonAppKeys().toSet()
    fun owns(key: String) = key in owned
    fun spanOf(key: String): WidgetSpan = state.spanOf(key)
    fun isWidget(key: String) = ItemKeys.kind(key) == ItemKind.Widget
    /** The item with the outline (and the handles, for a widget): the selection of edit mode. */
    fun isSelected(key: String) = selection.isSelected(key)
}

/** One non-app item drawn as a grid tile: folder, shortcut, link or file. Widgets are [HomeWidgetCell]. */
@Composable
fun ExtraItemFace(key: String, extras: HomeExtras, size: Dp, modifier: Modifier = Modifier) {
    when (ItemKeys.kind(key)) {
        ItemKind.Folder -> {
            val folder = ItemKeys.folderId(key)?.let { extras.state.folders[it] }
            val context = androidx.compose.ui.platform.LocalContext.current
            // INP-11: an app, link or file dropped on the tile is filed into the folder.
            if (folder != null) FolderTile(folder, extras.resolveApp, size, onOpen = {}, modifier = modifier.launcherDropTarget { r -> FolderDrop.file(extras, key, r).also { n -> if (n > 0) android.widget.Toast.makeText(context, L10n.tr("Added %d to folder", n), android.widget.Toast.LENGTH_SHORT).show() } > 0 }, interactive = false)
        }
        else -> extras.state.items[key]?.let { ItemGlyphTile(it, extras.shortcutIcon, size, modifier, extras.usageSlices, extras.fileCells) }
    }
}

/** The label under an extra item. */
fun extraLabel(key: String, extras: HomeExtras): String = when (ItemKeys.kind(key)) {
    ItemKind.Folder -> ItemKeys.folderId(key)?.let { extras.state.folders[it]?.name }.orEmpty()
    else -> extras.state.items[key]?.label.orEmpty()
}

@Composable
fun ItemGlyphTile(def: ItemDef, iconOf: (ItemDef) -> Drawable?, size: Dp, modifier: Modifier = Modifier, usageSlices: List<com.bos.sphere.core.catalog.UsageSlice> = emptyList(), fileCells: List<FileTileCell> = emptyList()) {
    val shape = RoundedCornerShape(22)
    var bmp by remember(def.key) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(def.key) {
        bmp = withContext(Dispatchers.Default) {
            runCatching { iconOf(def)?.let { d -> IconSources.rasterizeFull(d, 192).asImageBitmap() } }.getOrNull()
        }
    }
    Box(modifier.size(size).clip(shape).background(Color(0x26FFFFFF), shape), contentAlignment = Alignment.Center) {
        val b = bmp
        val filesOn = def.kind == ItemKind.Link && fileCells.isNotEmpty() && com.bos.sphere.core.catalog.HomePanelLinks.tabOf(def.target) == com.bos.sphere.core.catalog.PanelTab.Files
        if (b != null) Image(b, null, contentScale = ContentScale.Fit, modifier = Modifier.size(size))
        else if (!filesOn) Text(
            when (def.kind) { ItemKind.Link -> com.bos.sphere.core.catalog.HomePanelLinks.tabOf(def.target)?.let { panelGlyph(it) } ?: if (def.isFile) "▤" else "↗"; ItemKind.Group -> "▦"; else -> def.label.take(1).uppercase().ifEmpty { "•" } },
            color = HyleColors.InkBright, fontSize = (size.value * 0.42f).sp,
        )
        // HOM-15: the Files tile shows its first few files: a thumbnail where the platform gave one, the file's name where it did not.
        if (filesOn) FileTileCells(fileCells, size)
        // HOM-17: the Usage tile carries the split bar itself, along its lower edge.
        if (def.kind == ItemKind.Link && com.bos.sphere.core.catalog.HomePanelLinks.tabOf(def.target) == com.bos.sphere.core.catalog.PanelTab.Usage) {
            val seg = remember(usageSlices) { com.bos.sphere.core.catalog.UsageTileBar.segments(usageSlices) }
            if (seg.isNotEmpty()) UsageTileBarView(seg, Modifier.align(Alignment.BottomCenter).padding(bottom = size / 10).size(size * 0.7f, (size / 12).coerceAtLeast(3.dp)))
        }
    }
}

/** HOM-17: the colours follow the Usage panel's slice order so the bar and the panel agree. */
@Composable
fun UsageTileBarView(segments: List<com.bos.sphere.core.catalog.UsageTileBar.Segment>, modifier: Modifier = Modifier) {
    val colors = listOf(Color(0xFF7C5CFF), Color(0xFF3FB6A8), Color(0xFFE5A04D), Color(0xFFE5484D), Color(0xFF5B9BF0), Color(0xFF8A90A8))
    androidx.compose.foundation.layout.Row(modifier.clip(RoundedCornerShape(50)).background(Color(0x22FFFFFF))) {
        segments.forEach { s -> Box(Modifier.weight(s.share).fillMaxHeight().background(colors[s.sliceIndex % colors.size])) }
    }
}

/**
 * A hosted widget filling its span box. While it is the selected item it gains the selection frame (outline, handles, live resize,
 * ghost, size chip, see [SelectedWidgetFrame]); there is no Done pill any more (the top bar's Done is the only one).
 * [ctx] tells the frame where the widget sits on the grid; surfaces whose items flow pass none and get the east / south handles.
 */
@Composable
fun HomeWidgetCell(key: String, extras: HomeExtras, cellWpx: Float, cellHpx: Float, modifier: Modifier = Modifier, ctx: ResizeContext? = null, editing: Boolean = false) {
    val shape = RoundedCornerShape(16.dp)
    if (extras.isSelected(key)) {
        SelectedWidgetFrame(key, extras, ctx ?: ResizeContext.flowing(extras.spanOf(key), extras.gridDims(), cellWpx, cellHpx), modifier) {
            extras.widget(key, Modifier.fillMaxSize())
        }
    } else Box(modifier.clip(shape)) {
        extras.widget(key, Modifier.fillMaxSize())
        if (editing) Box(Modifier.fillMaxSize().editTouchShield())
    }
}

/** The placeholder a widget slot shows while its provider is missing or the id is unbound. */
@Composable
fun WidgetPlaceholder(label: String, onRestore: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.background(Color(0x33FFFFFF)).clickable(onClick = onRestore).padding(12.dp),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(label.ifEmpty { L10n.tr("Widget") }, color = HyleColors.InkBright, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        Text(L10n.tr("Not available. Tap to restore."), color = HyleColors.InkDim, fontSize = 11.sp, textAlign = TextAlign.Center)
    }
}

/** HOM-15, HOM-17, HOM-18: the glyph of a panel tile on home. */
fun panelGlyph(tab: com.bos.sphere.core.catalog.PanelTab): String = when (tab) {
    com.bos.sphere.core.catalog.PanelTab.Files -> "\u25A4"
    com.bos.sphere.core.catalog.PanelTab.Usage -> "\u25D4"
    com.bos.sphere.core.catalog.PanelTab.Timeline -> "\u29D6"
}

/** INP-11: what a drop on a folder tile does, apart from the UI: apps and items are filed into the folder through the host's edit path. */
internal object FolderDrop {
    /** Files [r] into folder [folderKey]; returns how many items went in. */
    fun file(extras: HomeExtras, folderKey: String, r: DropResult): Int {
        r.appKeys.forEach { extras.onDropOnto(it, folderKey) }
        if (r.items.isNotEmpty()) extras.onDropItems(folderKey, r.items)
        return r.appKeys.size + r.items.size
    }
}

/** HOM-15: one cell of the Files tile. */
class FileTileCell(val name: String, val image: ImageBitmap?)

/** HOM-15: up to four files as a 2 x 2 block inside the tile. */
@Composable
fun FileTileCells(cells: List<FileTileCell>, size: Dp, modifier: Modifier = Modifier) {
    val gap = size / 28
    val cellSize = (size * 0.8f - gap) / 2
    androidx.compose.foundation.layout.Column(modifier.size(size), verticalArrangement = Arrangement.spacedBy(gap, Alignment.CenterVertically), horizontalAlignment = Alignment.CenterHorizontally) {
        cells.take(4).chunked(2).forEach { row ->
            androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                row.forEach { c ->
                    Box(Modifier.size(cellSize).clip(RoundedCornerShape(12)).background(Color(0x33FFFFFF)).semantics { contentDescription = c.name }, contentAlignment = Alignment.Center) {
                        val img = c.image
                        if (img != null) Image(img, null, contentScale = ContentScale.Crop, modifier = Modifier.size(cellSize))
                        else Text(c.name, color = HyleColors.Ink, fontSize = (size.value * 0.1f).coerceAtLeast(7f).sp, maxLines = 3, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.padding(2.dp))
                    }
                }
            }
        }
    }
}
