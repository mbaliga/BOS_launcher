package com.bos.sphere.feature.grid

import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
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
    val resizing: String? = null,
    val onResize: (key: String, dCols: Int, dRows: Int) -> Unit = { _, _, _ -> },
    val onResizeDone: () -> Unit = {},
    val onTap: (String) -> Unit = {},
    val onLongPress: (String) -> Unit = {},
    val onDropOnto: (drag: String, target: String) -> Unit = { _, _ -> },
) {
    private val owned: Set<String> = state.nonAppKeys().toSet()
    fun owns(key: String) = key in owned
    fun spanOf(key: String): WidgetSpan = state.spanOf(key)
    fun isWidget(key: String) = ItemKeys.kind(key) == ItemKind.Widget
}

/** One non-app item drawn as a grid tile: folder, shortcut, link or file. Widgets are [HomeWidgetCell]. */
@Composable
fun ExtraItemFace(key: String, extras: HomeExtras, size: Dp, modifier: Modifier = Modifier) {
    when (ItemKeys.kind(key)) {
        ItemKind.Folder -> {
            val folder = ItemKeys.folderId(key)?.let { extras.state.folders[it] }
            if (folder != null) FolderTile(folder, extras.resolveApp, size, onOpen = {}, modifier = modifier, interactive = false)
        }
        else -> extras.state.items[key]?.let { ItemGlyphTile(it, extras.shortcutIcon, size, modifier) }
    }
}

/** The label under an extra item. */
fun extraLabel(key: String, extras: HomeExtras): String = when (ItemKeys.kind(key)) {
    ItemKind.Folder -> ItemKeys.folderId(key)?.let { extras.state.folders[it]?.name }.orEmpty()
    else -> extras.state.items[key]?.label.orEmpty()
}

@Composable
fun ItemGlyphTile(def: ItemDef, iconOf: (ItemDef) -> Drawable?, size: Dp, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(22)
    var bmp by remember(def.key) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(def.key) {
        bmp = withContext(Dispatchers.Default) {
            runCatching { iconOf(def)?.let { d -> IconSources.rasterizeFull(d, 192).asImageBitmap() } }.getOrNull()
        }
    }
    Box(modifier.size(size).clip(shape).background(Color(0x26FFFFFF), shape), contentAlignment = Alignment.Center) {
        val b = bmp
        if (b != null) Image(b, null, contentScale = ContentScale.Fit, modifier = Modifier.size(size))
        else Text(
            when (def.kind) { ItemKind.Link -> if (def.isFile) "▤" else "↗"; else -> def.label.take(1).uppercase().ifEmpty { "•" } },
            color = HyleColors.InkBright, fontSize = (size.value * 0.42f).sp,
        )
    }
}

/**
 * A hosted widget filling its span box. While [HomeExtras.resizing] is this key it gains the resize
 * frame (handles and Shift+arrows) and a Done button; an unavailable widget shows a placeholder.
 */
@Composable
fun HomeWidgetCell(key: String, extras: HomeExtras, cellWpx: Float, cellHpx: Float, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(16.dp)
    val editing = extras.resizing == key
    Box(modifier.clip(shape)) {
        if (editing) {
            val fr = remember { FocusRequester() }
            LaunchedEffect(key) { runCatching { fr.requestFocus() } }
            WidgetResizeFrame(cellWpx, cellHpx, onResize = { dc, dr -> extras.onResize(key, dc, dr) }, modifier = Modifier.focusRequester(fr)) {
                extras.widget(key, Modifier.fillMaxSize())
            }
            Text(
                L10n.tr("Done"), color = HyleColors.InkBright, fontSize = 12.sp,
                modifier = Modifier.align(Alignment.TopStart).padding(6.dp).clip(RoundedCornerShape(10.dp))
                    .background(HyleColors.VioletSoft.copy(alpha = 0.9f)).clickable { extras.onResizeDone() }.padding(horizontal = 10.dp, vertical = 4.dp),
            )
        } else extras.widget(key, Modifier.fillMaxSize())
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
