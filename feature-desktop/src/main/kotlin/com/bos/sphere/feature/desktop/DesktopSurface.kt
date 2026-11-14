package com.bos.sphere.feature.desktop

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.bos.sphere.core.data.AppEntry
import com.bos.sphere.core.data.LauncherAppsAppRepository
import com.bos.sphere.core.data.LauncherStores
import com.bos.sphere.core.design.L10n
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private class Target(val rect: FRect, val layer: Int, val action: () -> Unit, val secondary: (() -> Unit)?)

/**
 * The desktop surface for one display (EXT-02 to EXT-05, EXT-08, EXT-10, HOM-13). Every size comes from this
 * window's own metrics; arrangement, dock and focus belong to this display's profile only (EXT-03).
 *
 * [displayId] is where the activity currently runs, [identity] keys its profile (EXT-09).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DesktopSurface(controller: DesktopController, displayId: () -> Int, identity: String, displayName: String, token: Any? = null) {
    val context = LocalContext.current
    val view = androidx.compose.ui.platform.LocalView.current
    var settingsOpen by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val repo = remember { LauncherAppsAppRepository(context) }
    val apps by repo.apps.collectAsState(initial = emptyList())
    val history by LauncherStores.get(context).history.events.collectAsState()
    var profile by remember(identity) { mutableStateOf(controller.profiles.load(identity)) }
    fun commit(p: DesktopProfile) { profile = p; controller.profiles.save(identity, p) }

    var sizePx by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
    val layoutDir = LocalLayoutDirection.current
    val insetSource = WindowInsets.systemBars.union(WindowInsets.displayCutout)
    val insets = InsetsPx(
        insetSource.getLeft(density, layoutDir), insetSource.getTop(density), insetSource.getRight(density, layoutDir), insetSource.getBottom(density),
    )
    val homeHere = DesktopSurfaces.homeDisplayIds().contains(displayId())
    val layout = DesktopMetrics.compute(
        sizePx.width.coerceAtLeast(1), sizePx.height.coerceAtLeast(1), (density.density * 160f).roundToInt(),
        insets, profile.dockMode, systemHomePresent = !homeHere, densityOverrideDpi = profile.densityOverrideDpi,
    )
    val s = layout.scale / density.density // px per layout-dp relative to compose dp (1 unless overridden)
    fun dp(v: Float) = (v * s).dp
    val container = FRect(
        layout.workArea.left / layout.scale, layout.workArea.top / layout.scale,
        layout.workArea.right / layout.scale, layout.workArea.bottom / layout.scale,
    )

    var drawerOpen by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var menuKey by remember { mutableStateOf<String?>(null) }
    var drag by remember { mutableStateOf<Pair<String, FRect>?>(null) }
    var guides by remember { mutableStateOf<List<Guide>>(emptyList()) }
    var cursorPos by remember { mutableStateOf<Offset?>(null) }
    val cursor = remember { CursorModel(1f, 1f) }
    val targets = remember { LinkedHashMap<String, Target>() }
    val gridState = rememberLazyGridState()

    fun appFor(key: String) = apps.firstOrNull { it.key == key }
    fun launchHere(a: AppEntry) {
        LauncherStores.get(context).history.record(a.key)
        controller.launchApp(a.packageName, a.componentName.className, 0, displayId())
    }
    fun launchOnOther(a: AppEntry) {
        val other = DesktopLaunch.otherDisplay(controller.state.value, displayId()) ?: DesktopLaunch.PRIMARY
        controller.launchApp(a.packageName, a.componentName.className, 0, other)
    }
    fun placeApp(a: AppEntry) {
        if (profile.items.any { it.id == a.key }) return
        val cell = layout.cellDp
        var spot: FRect? = null
        var y = container.top + 8f
        while (spot == null && y + cell <= container.bottom) {
            var x = container.left + 8f
            while (spot == null && x + cell <= container.right) {
                val c = FRect(x, y, x + cell, y + cell)
                if (profile.items.none { it.rect().overlaps(c) }) spot = c
                x += cell
            }
            y += cell
        }
        val r = spot ?: FRect(container.left, container.top, container.left + cell, container.top + cell)
        commit(profile.copy(items = profile.items + PlacedItem(PlacedItem.KIND_APP, a.key, r.left, r.top, cell, cell)))
    }
    fun placeClock() {
        if (profile.items.any { it.id == "clock" }) return
        val w = layout.cellDp * 3; val h = layout.cellDp * 1.6f
        val others = profile.items.filter { it.isWidget }.map { it.rect() }
        val placed = FreeformPlacement.placeWidget(FRect(container.left + 8f, container.top + 8f, container.left + 8f + w, container.top + 8f + h), others, container, 6f)
        if (placed != null) commit(profile.copy(items = profile.items + PlacedItem(PlacedItem.KIND_WIDGET, "clock", placed.rect.left, placed.rect.top, w, h)))
    }

    // Commands from the phone's trackpad and keyboard panel (EXT-10).
    LaunchedEffect(sizePx) { cursor.resize(sizePx.width.toFloat().coerceAtLeast(1f), sizePx.height.toFloat().coerceAtLeast(1f)) }
    LaunchedEffect(Unit) {
        DesktopInputBus.commands.collect { cmd ->
            // Only the focused surface reacts, so two displays never both follow the phone's trackpad (EXT-03).
            if (!DesktopSurfaces.isFocused(token)) return@collect
            val layer = if (drawerOpen) 1 else 0
            fun hit() = HitTest.itemAt(cursor.x, cursor.y, targets.filter { it.value.layer == layer }.map { it.key to it.value.rect })
            when (cmd) {
                is InputCommand.Move -> { cursor.move(cmd.dx, cmd.dy); cursorPos = Offset(cursor.x, cursor.y) }
                // Anything the surface registered is clicked directly; everything else (the dock, menus) gets a real touch at the cursor,
                // delivered to this window's own views only (EXT-10).
                InputCommand.Click -> { menuKey = null; hit()?.let { targets[it]?.action?.invoke() } ?: run { if (drawerOpen) drawerOpen = false else SyntheticTouch.tap(view, cursor.x, cursor.y) } }
                InputCommand.SecondaryClick -> hit()?.let { k -> targets[k]?.secondary?.invoke() } ?: run { if (!drawerOpen) SyntheticTouch.longPress(view, cursor.x, cursor.y) }
                is InputCommand.Scroll -> if (drawerOpen) scope.launch { gridState.scrollBy(cmd.dy * 3f) }
                is InputCommand.Text -> { if (!drawerOpen) drawerOpen = true; query += cmd.text }
                is InputCommand.Key -> when (cmd.key) {
                    PanelKey.Escape -> { if (menuKey != null) menuKey = null else if (drawerOpen) { drawerOpen = false; query = "" } }
                    PanelKey.Backspace -> query = query.dropLast(1)
                    PanelKey.Enter -> if (drawerOpen) filtered(apps, query).firstOrNull()?.let { launchHere(it); drawerOpen = false; query = "" }
                    else -> Unit
                }
            }
        }
    }

    Box(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).onSizeChanged { sizePx = it },
    ) {
        // Placed items (HOM-13). Positions are stored in dp and clamped to the current work area.
        for (item in profile.items) {
            val live = drag?.takeIf { it.first == item.id }?.second ?: FreeformPlacement.clamp(item.rect(), container)
            val entry = if (item.isWidget) null else appFor(item.id)
            if (!item.isWidget && entry == null) continue
            // A removed or recomposed-away item must not stay clickable through the phone trackpad.
            DisposableEffect(item.id) { onDispose { targets.remove(item.id) } }
            Box(
                Modifier
                    .offset { IntOffset((live.left * layout.scale).roundToInt(), (live.top * layout.scale).roundToInt()) }
                    .size(dp(live.width), dp(live.height))
                    .onGloballyPositioned { c ->
                        val b = c.boundsInRoot()
                        targets[item.id] = Target(
                            FRect(b.left, b.top, b.right, b.bottom), 0,
                            { entry?.let { launchHere(it) } }, { menuKey = item.id },
                        )
                    }
                    .pointerInput(item.id, layout.scale, profile.items) {
                        detectDragGestures(
                            onDragStart = { drag = item.id to item.rect() },
                            onDragEnd = {
                                drag?.let { (id, r) ->
                                    commit(profile.copy(items = profile.items.map { if (it.id == id) it.copy(x = r.left, y = r.top) else it }))
                                }
                                drag = null; guides = emptyList()
                            },
                            onDragCancel = { drag = null; guides = emptyList() },
                        ) { change, delta ->
                            change.consume()
                            val cur = drag?.second ?: item.rect()
                            val moved = cur.moved(delta.x / layout.scale, delta.y / layout.scale)
                            val others = profile.items.filter { it.id != item.id }
                            if (item.isWidget) {
                                val res = FreeformPlacement.placeWidget(moved, others.filter { it.isWidget }.map { it.rect() }, container, 6f)
                                if (res != null) { drag = item.id to res.rect; guides = res.guides }
                            } else {
                                val res = FreeformPlacement.snap(moved, others.map { it.rect() }, container, 6f)
                                drag = item.id to res.rect; guides = res.guides
                            }
                        }
                    }
                    .combinedClickable(onClick = { entry?.let { launchHere(it) } }, onLongClick = { menuKey = item.id }),
                contentAlignment = Alignment.Center,
            ) {
                if (entry != null) AppIcon(entry, layout.iconDp, dp(layout.iconDp), showLabel = true)
                else ClockWidget()
                DropdownMenu(expanded = menuKey == item.id, onDismissRequest = { menuKey = null }) {
                    if (entry != null) {
                        DropdownMenuItem(text = { Text(L10n.tr("Open on phone")) }, onClick = { menuKey = null; launchOnOther(entry) })
                    }
                    DropdownMenuItem(text = { Text(L10n.tr("Remove from desktop")) }, onClick = {
                        menuKey = null; commit(profile.copy(items = profile.items.filter { it.id != item.id }))
                    })
                }
            }
        }

        // Snap guides.
        for (g in guides) {
            val p = (g.pos * layout.scale)
            Box(
                Modifier.offset { if (g.vertical) IntOffset(p.roundToInt(), 0) else IntOffset(0, p.roundToInt()) }
                    .then(if (g.vertical) Modifier.size(1.dp, with(density) { sizePx.height.toDp() }) else Modifier.size(with(density) { sizePx.width.toDp() }, 1.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)),
            )
        }

        // Dock (EXT-08): placed in the area the layout left free of system insets.
        layout.dock?.let { d ->
            val entries = recentEntries(apps, history.map { it.key }, 7).map { a -> DockEntry(a.key, a.label, { launchHere(a) }, { a.loadIcon() }) }
            val state = DesktopDockState(displayId(), when (profile.dockMode) { DockMode.Left -> DockEdge.Left; DockMode.Right -> DockEdge.Right; else -> DockEdge.Bottom }, entries, layout.iconDp) { drawerOpen = true }
            Box(
                Modifier.offset { IntOffset(d.left, d.top) }.size(with(density) { d.width.toDp() }, with(density) { d.height.toDp() })
                    .onGloballyPositioned { c -> targets["dock"] = Target(c.boundsInRoot().let { FRect(it.left, it.top, it.right, it.bottom) }, -1, {}, null) },
            ) {
                (DesktopDockSlot.provider ?: BasicRecentDock).Content(state, Modifier.fillMaxSize())
                // The "all apps" button is always reachable, whichever rail draws the dock.
            }
        }
        AllAppsButton(layout, density, onClick = { drawerOpen = true }) { c -> targets["allapps"] = Target(c, 0, { drawerOpen = true }, null) }
        StatusChip(displayName, layout.sizeClass, Modifier.align(Alignment.TopEnd).padding(top = with(density) { insets.top.toDp() } + 8.dp, end = with(density) { insets.right.toDp() } + 12.dp)
            .clip(RoundedCornerShape(8.dp)).combinedClickable(onClick = { settingsOpen = !settingsOpen })
            .onGloballyPositioned { c -> targets["status"] = Target(c.boundsInRoot().let { FRect(it.left, it.top, it.right, it.bottom) }, 0, { settingsOpen = !settingsOpen }, null) })
        if (settingsOpen) {
            DisposableEffect(Unit) { onDispose { targets.remove("status-close") } }
            // EXT-09: density and dock position of this display, remembered per display identity.
            Column(
                Modifier.align(Alignment.TopEnd).padding(top = with(density) { insets.top.toDp() } + 40.dp, end = with(density) { insets.right.toDp() } + 12.dp)
                    .clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surface).padding(14.dp),
            ) {
                DisplayProfileEditor(displayName, profile, { commit(it) })
                Text(L10n.tr("Close"), color = MaterialTheme.colorScheme.primary, fontSize = 13.sp,
                    modifier = Modifier.padding(top = 8.dp).combinedClickable(onClick = { settingsOpen = false })
                        .onGloballyPositioned { c -> targets["status-close"] = Target(c.boundsInRoot().let { FRect(it.left, it.top, it.right, it.bottom) }, 0, { settingsOpen = false }, null) })
            }
        }

        if (drawerOpen) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)).combinedClickable(onClick = { drawerOpen = false; query = "" })) {
                Column(
                    Modifier.align(Alignment.Center).fillMaxSize(0.82f).clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.surface).padding(16.dp)
                        .combinedClickable(onClick = {}),
                ) {
                    BasicTextField(
                        value = query, onValueChange = { query = it }, singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = 18.sp),
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(12.dp),
                        decorationBox = { inner -> if (query.isEmpty()) Text(L10n.tr("Search apps"), color = MaterialTheme.colorScheme.onSurfaceVariant); inner() },
                    )
                    DrawerGrid(filtered(apps, query), layout, gridState, ::launchHere, onLong = { menuKey = "drawer:" + it.key }, menuKey, { menuKey = null }, ::placeApp, ::launchOnOther, unregister = { k -> targets.remove("drawer:$k") }) { k, r, a, sec ->
                        targets["drawer:$k"] = Target(r, 1, a, sec)
                    }
                }
            }
        }

        cursorPos?.let { p ->
            Box(
                Modifier.offset { IntOffset(p.x.roundToInt(), p.y.roundToInt()) }.size(14.dp).clip(CircleShape)
                    .background(Color.White).border(2.dp, Color.Black, CircleShape),
            )
        }
        if (profile.items.none { it.isWidget }) {
            // Offered once so the free-form widget placement can be tried; real widget hosting is a separate stream.
            Box(Modifier.align(Alignment.TopStart).padding(12.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceVariant)
                .combinedClickable(onClick = { placeClock() }).padding(horizontal = 10.dp, vertical = 6.dp)) {
                Text(L10n.tr("Add clock"), color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp)
            }
        }
    }
}

private fun filtered(apps: List<AppEntry>, q: String) =
    if (q.isBlank()) apps else apps.filter { it.label.contains(q.trim(), ignoreCase = true) }

/** Most recently launched apps first, padded with the alphabetical list; pinned/recent from the existing history store. */
internal fun recentEntries(apps: List<AppEntry>, recentKeys: List<String>, n: Int): List<AppEntry> {
    val byKey = apps.associateBy { it.key }
    val recent = recentKeys.asReversed().distinct().mapNotNull { byKey[it] }
    return (recent + apps).distinctBy { it.key }.take(n)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DrawerGrid(
    list: List<AppEntry>, layout: DesktopLayout, state: LazyGridState,
    onLaunch: (AppEntry) -> Unit, onLong: (AppEntry) -> Unit, menuKey: String?, dismiss: () -> Unit,
    place: (AppEntry) -> Unit, openOther: (AppEntry) -> Unit,
    unregister: (String) -> Unit,
    register: (String, FRect, () -> Unit, (() -> Unit)?) -> Unit,
) {
    LazyVerticalGrid(GridCells.Adaptive(layout.cellDp.dp), state = state, modifier = Modifier.padding(top = 12.dp)) {
        items(list, key = { it.key }) { a ->
            DisposableEffect(a.key) { onDispose { unregister(a.key) } }
            Box(
                Modifier.padding(4.dp)
                    .onGloballyPositioned { c -> c.boundsInRoot().let { register(a.key, FRect(it.left, it.top, it.right, it.bottom), { onLaunch(a) }, { onLong(a) }) } }
                    .combinedClickable(onClick = { onLaunch(a) }, onLongClick = { onLong(a) }),
                contentAlignment = Alignment.Center,
            ) {
                AppIcon(a, layout.iconDp, layout.iconDp.dp, showLabel = true)
                DropdownMenu(expanded = menuKey == "drawer:" + a.key, onDismissRequest = dismiss) {
                    DropdownMenuItem(text = { Text(L10n.tr("Place on desktop")) }, onClick = { dismiss(); place(a) })
                    DropdownMenuItem(text = { Text(L10n.tr("Open on phone")) }, onClick = { dismiss(); openOther(a) })
                }
            }
        }
    }
}

@Composable
internal fun AppIcon(entry: AppEntry, iconDp: Float, iconSize: androidx.compose.ui.unit.Dp, showLabel: Boolean) {
    val bmp by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, entry.key) {
        value = withContext(Dispatchers.Default) { runCatching { entry.loadIcon()?.toBitmap(160, 160)?.asImageBitmap() }.getOrNull() }
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Box(Modifier.size(iconSize).clip(RoundedCornerShape(iconSize / 4)).background(if (bmp == null) Color.White.copy(alpha = 0.12f) else Color.Transparent)) {
            bmp?.let { Image(it, entry.label, Modifier.fillMaxSize()) }
        }
        if (showLabel) Text(entry.label, color = MaterialTheme.colorScheme.onBackground, fontSize = (iconDp / 4.4f).coerceIn(9f, 14f).sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AllAppsButton(layout: DesktopLayout, density: androidx.compose.ui.unit.Density, onClick: () -> Unit, register: (FRect) -> Unit) {
    val d = layout.dock
    val size = (layout.iconDp * 0.9f).dp
    val x = d?.let { it.right - with(density) { size.roundToPx() } - 12 } ?: layout.workArea.right - with(density) { size.roundToPx() } - 12
    val y = d?.let { it.top + (it.height - with(density) { size.roundToPx() }) / 2 } ?: layout.workArea.bottom - with(density) { size.roundToPx() } - 12
    Box(
        Modifier.offset { IntOffset(x, y) }.size(size).clip(CircleShape).background(MaterialTheme.colorScheme.primary)
            .onGloballyPositioned { c -> c.boundsInRoot().let { register(FRect(it.left, it.top, it.right, it.bottom)) } }
            .combinedClickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text("☰", color = MaterialTheme.colorScheme.onPrimary, fontSize = (layout.iconDp * 0.4f).sp) }
}

@Composable
private fun StatusChip(name: String, size: SizeClass, modifier: Modifier) {
    Text("$name · ${size.name}", modifier = modifier, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
}

@Composable
private fun ClockWidget() {
    val now = produceState("") {
        while (true) { value = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date()); kotlinx.coroutines.delay(15_000) }
    }
    Box(Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surface), contentAlignment = Alignment.Center) {
        Text(now.value, color = MaterialTheme.colorScheme.onSurface, fontSize = 28.sp)
    }
}

/** Default dock until the dock stream's rail replaces it through [DesktopDockSlot]. */
internal object BasicRecentDock : DesktopDock {
    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    override fun Content(state: DesktopDockState, modifier: Modifier) {
        val horizontal = state.edge == DockEdge.Bottom
        val body: @Composable () -> Unit = {
            state.entries.forEach { e ->
                val bmp by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, e.key) {
                    value = withContext(Dispatchers.Default) { runCatching { e.icon?.invoke()?.toBitmap(128, 128)?.asImageBitmap() }.getOrNull() }
                }
                Box(Modifier.padding(4.dp).size(state.iconDp.dp).clip(RoundedCornerShape((state.iconDp / 4).dp)).combinedClickable(onClick = e.activate)) {
                    bmp?.let { Image(it, e.label, Modifier.fillMaxSize()) }
                }
            }
        }
        Box(modifier.background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)), contentAlignment = Alignment.Center) {
            if (horizontal) Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) { body() }
            else Column(verticalArrangement = Arrangement.spacedBy(6.dp), horizontalAlignment = Alignment.CenterHorizontally) { body() }
        }
    }
}
