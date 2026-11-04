package com.bos.sphere.feature.widgets.ui

import android.content.Context
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.graphics.drawable.toBitmap
import com.bos.sphere.core.design.HyleColors
import com.bos.sphere.feature.sphere.TILE_SIZE_DP
import com.bos.sphere.feature.widgets.HubSize
import com.bos.sphere.feature.widgets.PickerItem
import com.bos.sphere.feature.widgets.PickerModel
import com.bos.sphere.feature.widgets.PlacementTarget
import com.bos.sphere.feature.widgets.R
import com.bos.sphere.feature.widgets.TouchGateLayout
import com.bos.sphere.feature.widgets.WidgetHosting
import com.bos.sphere.feature.widgets.WidgetSizing
import com.bos.sphere.feature.widgets.WidgetStatus
import com.bos.sphere.feature.widgets.WidgetSurface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min

/**
 * The picker and the widget menu, when open. [lens] is the equator's focus scale (for the sizes a
 * new equator widget is bound with); [focusedEquatorIndex] gives the index of the focused tile, used
 * when a widget goes onto the equator without an explicit position.
 */
@Composable
fun WidgetHostUi.Dialogs(lens: Float, focusedEquatorIndex: () -> Int) {
    val binding = binding ?: return
    val density = LocalDensity.current.density

    pickerTarget?.let { target ->
        WidgetPicker(
            hosting = binding.hosting,
            initialSurface = target.surface,
            onDismiss = { pickerTarget = null },
            onPick = { item, surface ->
                val index = if (surface == target.surface && target.equatorIndex >= 0) {
                    target.equatorIndex
                } else {
                    focusedEquatorIndex()
                }
                val sizes = when (surface) {
                    WidgetSurface.Equator -> WidgetSizing.equatorSizes(TILE_SIZE_DP, lens)
                    WidgetSurface.Hub -> WidgetSizing.hubSizes(
                        hubWidthDp,
                        WidgetSizing.defaultHubSize(item.info.minHeight / density),
                    )
                    WidgetSurface.Page -> WidgetSizing.pageSizes(pageWidthDp, pageHeightDp)
                }
                pickerTarget = null
                binding.addWidget(item, PlacementTarget(surface, index), sizes)
            },
        )
    }

    menuWidgetId?.let { id ->
        WidgetMenu(
            appWidgetId = id,
            focusedEquatorIndex = focusedEquatorIndex,
            onDismiss = { menuWidgetId = null },
        )
    }
}

// --- picker ---------------------------------------------------------------------------------------

@Composable
private fun WidgetPicker(
    hosting: WidgetHosting,
    initialSurface: WidgetSurface,
    onDismiss: () -> Unit,
    onPick: (PickerItem, WidgetSurface) -> Unit,
) {
    var surface by remember { mutableStateOf(initialSurface) }
    var query by remember { mutableStateOf("") }
    // Read from the system's widget service on a background thread; nothing leaves the device.
    val items by produceState<List<PickerItem>?>(initialValue = null, hosting) {
        value = withContext(Dispatchers.Default) {
            runCatching { hosting.loadPickerItems() }.getOrDefault(emptyList())
        }
    }
    val byKey = remember(items) { items.orEmpty().associateBy { it.entry.key } }
    val groups = remember(items, query) { PickerModel.group(items.orEmpty().map { it.entry }, query) }
    val shape = RoundedCornerShape(16.dp)

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.92f)
                .background(HyleColors.SurfaceTranslucent, shape)
                .border(1.dp, HyleColors.Hairline, shape)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.widgets_picker_title),
                    color = HyleColors.InkBright,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f),
                )
                Chip(
                    label = stringResource(R.string.widgets_picker_close),
                    contentDescription = stringResource(R.string.widgets_picker_close_cd),
                    onClick = onDismiss,
                )
            }
            // A widget for a page has its destination fixed: that page.
            if (initialSurface != WidgetSurface.Page) {
                SurfaceSelector(selected = surface, onSelect = { surface = it })
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                placeholder = { Text(stringResource(R.string.widgets_picker_filter), fontSize = 14.sp) },
                shape = RoundedCornerShape(13.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = HyleColors.InkBright,
                    unfocusedTextColor = HyleColors.InkBright,
                    focusedBorderColor = HyleColors.Violet,
                    unfocusedBorderColor = HyleColors.Hairline,
                    cursorColor = HyleColors.Violet,
                    focusedPlaceholderColor = HyleColors.InkDim,
                    unfocusedPlaceholderColor = HyleColors.InkDim,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            val message = when {
                items == null -> stringResource(R.string.widgets_picker_loading)
                groups.isEmpty() && query.isBlank() -> stringResource(R.string.widgets_picker_empty)
                groups.isEmpty() -> stringResource(R.string.widgets_picker_no_match, query.trim())
                else -> null
            }
            if (message != null) {
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Text(message, color = HyleColors.InkDim, fontSize = 14.sp)
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    groups.forEach { group ->
                        item(key = "group:${group.key}") {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(
                                    group.appLabel,
                                    color = HyleColors.InkBright,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false),
                                )
                                if (group.isWorkProfile) WorkProfileTag()
                            }
                        }
                        items(group.entries, key = { it.key }) { entry ->
                            byKey[entry.key]?.let { item ->
                                PickerRow(item = item, onClick = { onPick(item, surface) })
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SurfaceSelector(selected: WidgetSurface, onSelect: (WidgetSurface) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.widgets_picker_place_on), color = HyleColors.InkDim, fontSize = 12.sp)
        listOf(WidgetSurface.Equator, WidgetSurface.Hub).forEach { surface ->
            val label = stringResource(
                if (surface == WidgetSurface.Equator) R.string.widgets_surface_equator else R.string.widgets_surface_hub,
            )
            val isSelected = surface == selected
            Chip(
                // A check mark as well as the violet outline: the choice never rests on colour.
                label = if (isSelected) "✓ $label" else label,
                contentDescription = if (isSelected) {
                    stringResource(R.string.widgets_surface_selected_cd, label)
                } else {
                    stringResource(R.string.widgets_surface_choose_cd, label)
                },
                selected = isSelected,
                onClick = { onSelect(surface) },
            )
        }
    }
}

@Composable
private fun PickerRow(item: PickerItem, onClick: () -> Unit) {
    val entry = item.entry
    val sizeText = stringResource(R.string.widgets_picker_cells, entry.spanX, entry.spanY)
    val description = stringResource(R.string.widgets_picker_add_cd, entry.label, sizeText)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = description }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        WidgetPreview(item, Modifier.size(width = 104.dp, height = 78.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                entry.label,
                color = HyleColors.InkBright,
                fontSize = 14.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            entry.description?.takeIf { it.isNotBlank() }?.let {
                Text(it, color = HyleColors.InkDim, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Text(sizeText, color = HyleColors.Ink, fontSize = 12.sp)
        }
    }
}

/** Preview images and the (profile-badged) icon, loaded off the main thread. */
private class PreviewImages(val preview: ImageBitmap?, val icon: ImageBitmap?)

/**
 * The provider's preview image; else (API 31+) a rendering of its `previewLayout`; else its icon.
 * Not interactive: the rendered layout sits behind a closed touch gate.
 */
@Composable
private fun WidgetPreview(item: PickerItem, modifier: Modifier) {
    val context = LocalContext.current
    val images by produceState<PreviewImages?>(initialValue = null, item) {
        value = withContext(Dispatchers.IO) { loadPreviewImages(context, item) }
    }
    var layoutFailed by remember(item) { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(HyleColors.Surface, shape)
            .border(1.dp, HyleColors.Hairline, shape),
        contentAlignment = Alignment.Center,
    ) {
        val loaded = images ?: return@Box
        when {
            loaded.preview != null -> Image(
                bitmap = loaded.preview,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().padding(4.dp),
            )

            item.info.previewLayout != 0 && !layoutFailed ->
                PreviewLayout(item, onFailed = { layoutFailed = true })

            loaded.icon != null -> Image(
                bitmap = loaded.icon,
                contentDescription = null,
                modifier = Modifier.size(44.dp),
            )
        }
    }
}

@Composable
private fun PreviewLayout(item: PickerItem, onFailed: () -> Unit) {
    val context = LocalContext.current
    val view = remember(item) { buildPreviewLayoutView(context, item.info) }
    if (view == null) {
        LaunchedEffect(item) { onFailed() }
        return
    }
    val density = LocalDensity.current.density
    val widthDp = (item.info.minWidth / density).coerceIn(40f, 480f)
    val heightDp = (item.info.minHeight / density).coerceIn(40f, 480f)
    BoxWithConstraints(Modifier.fillMaxSize().clipToBounds(), contentAlignment = Alignment.Center) {
        val scale = min(maxWidth.value / widthDp, maxHeight.value / heightDp).coerceAtMost(1f)
        AndroidView(
            factory = { ctx -> TouchGateLayout(ctx).apply { host(view) } },
            modifier = Modifier
                .requiredSize(widthDp.dp, heightDp.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                },
        )
    }
}

private fun loadPreviewImages(context: Context, item: PickerItem): PreviewImages {
    val dpi = context.resources.displayMetrics.densityDpi
    val maxPx = (160 * context.resources.displayMetrics.density).toInt()
    val preview = runCatching { item.info.loadPreviewImage(context, dpi) }.getOrNull()
        ?.toBoundedBitmap(maxPx)
    val icon = runCatching {
        val raw = item.info.loadIcon(context, dpi)
        context.packageManager.getUserBadgedIcon(raw, item.user)
    }.getOrNull()?.toBoundedBitmap((48 * context.resources.displayMetrics.density).toInt())
    return PreviewImages(preview, icon)
}

private fun Drawable.toBoundedBitmap(maxPx: Int): ImageBitmap? = runCatching {
    val w = intrinsicWidth.takeIf { it > 0 } ?: maxPx
    val h = intrinsicHeight.takeIf { it > 0 } ?: maxPx
    val scale = min(1f, maxPx / max(w, h).toFloat())
    toBitmap((w * scale).toInt().coerceAtLeast(1), (h * scale).toInt().coerceAtLeast(1)).asImageBitmap()
}.getOrNull()

// --- widget menu ----------------------------------------------------------------------------------

@Composable
private fun WidgetHostUi.WidgetMenu(
    appWidgetId: Int,
    focusedEquatorIndex: () -> Int,
    onDismiss: () -> Unit,
) {
    val placements by placementsState()
    val status by statusState()
    val placement = placements.firstOrNull { it.appWidgetId == appWidgetId }
    if (placement == null) {
        LaunchedEffect(appWidgetId) { onDismiss() }
        return
    }
    val widgetStatus = status[appWidgetId]
    val label = labelOf(widgetStatus)
    val where = stringResource(
        if (placement.surface == WidgetSurface.Equator) R.string.widgets_menu_on_equator else R.string.widgets_menu_in_hub,
    )
    val appLabel = (widgetStatus as? WidgetStatus.Available)?.appLabel
    val shape = RoundedCornerShape(16.dp)

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .width(272.dp)
                .background(HyleColors.SurfaceTranslucent, shape)
                .border(1.dp, HyleColors.Hairline, shape)
                .padding(vertical = 8.dp),
        ) {
            Text(
                label,
                color = HyleColors.InkBright,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp),
            )
            Text(
                listOfNotNull(appLabel?.takeIf { it != label }, where).joinToString(" · "),
                color = HyleColors.InkDim,
                fontSize = 12.sp,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 6.dp),
            )
            if ((widgetStatus as? WidgetStatus.Available)?.reconfigurable == true) {
                MenuRow(stringResource(R.string.widgets_menu_reconfigure)) {
                    onDismiss()
                    binding?.reconfigure(appWidgetId)
                }
            }
            if (placement.surface == WidgetSurface.Page) {
                // A page's widget is managed with its page (Settings → Pages).
            } else if (placement.surface == WidgetSurface.Equator) {
                MenuRow(stringResource(R.string.widgets_menu_move_to_hub)) {
                    hosting?.moveToHub(appWidgetId)
                    onDismiss()
                }
            } else {
                MenuRow(stringResource(R.string.widgets_menu_move_to_equator)) {
                    hosting?.moveToEquator(appWidgetId, focusedEquatorIndex())
                    onDismiss()
                }
                Text(
                    stringResource(R.string.widgets_menu_height),
                    color = HyleColors.InkDim,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 6.dp),
                )
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    HubSize.entries.forEach { size ->
                        val name = stringResource(
                            when (size) {
                                HubSize.Small -> R.string.widgets_size_small
                                HubSize.Medium -> R.string.widgets_size_medium
                                HubSize.Large -> R.string.widgets_size_large
                            },
                        )
                        val isSelected = size == placement.hubSize
                        Chip(
                            label = if (isSelected) "✓ $name" else name,
                            contentDescription = if (isSelected) {
                                stringResource(R.string.widgets_surface_selected_cd, name)
                            } else {
                                name
                            },
                            selected = isSelected,
                            onClick = { hosting?.setHubSize(appWidgetId, size) },
                        )
                    }
                }
            }
            MenuRow(stringResource(R.string.widgets_menu_remove), emphasis = true) {
                hosting?.remove(appWidgetId)
                onDismiss()
            }
        }
    }
}

@Composable
private fun MenuRow(label: String, emphasis: Boolean = false, onClick: () -> Unit) {
    Text(
        text = label,
        color = if (emphasis) HyleColors.Violet else HyleColors.Ink,
        fontSize = 14.sp,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    )
}
