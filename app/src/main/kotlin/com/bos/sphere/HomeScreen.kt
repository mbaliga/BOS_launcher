package com.bos.sphere

import android.content.Intent
import android.content.res.Configuration
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bos.sphere.core.data.ActionRunner
import com.bos.sphere.core.data.AppEntry
import com.bos.sphere.core.data.AppSuggester
import com.bos.sphere.core.data.Corner
import com.bos.sphere.core.data.CornerActionId
import com.bos.sphere.core.data.CornerItem
import com.bos.sphere.core.data.CornerSlot
import com.bos.sphere.core.data.DrawerStyle
import com.bos.sphere.core.data.HiddenAppsStore
import com.bos.sphere.core.data.HomeGesture
import com.bos.sphere.core.data.HomeLayoutMath
import com.bos.sphere.core.data.HomeStyle
import com.bos.sphere.core.data.HomeTiles
import com.bos.sphere.core.data.LauncherAppsAppRepository
import com.bos.sphere.core.data.LauncherStores
import com.bos.sphere.core.data.MotionGestureSensors
import com.bos.sphere.core.data.NetworkSignal
import com.bos.sphere.core.data.PageLayout
import com.bos.sphere.core.data.VoiceInputManager
import com.bos.sphere.core.design.HyleColors
import com.bos.sphere.feature.grid.AppDrawer
import com.bos.sphere.feature.grid.ComplicationCluster
import com.bos.sphere.feature.grid.ComplicationView
import com.bos.sphere.feature.grid.CornerEditorDialog
import com.bos.sphere.feature.grid.CornerLayer
import com.bos.sphere.feature.grid.GestureBindingDialog
import com.bos.sphere.feature.grid.GridHome
import com.bos.sphere.feature.grid.IconCache
import com.bos.sphere.feature.grid.NetworkDotGrid
import com.bos.sphere.feature.grid.PeekWidget
import com.bos.sphere.feature.grid.SettingsSpace
import com.bos.sphere.feature.grid.TileMenuDialog
import com.bos.sphere.feature.grid.UiSounds
import com.bos.sphere.feature.grid.WheelCategoryCache
import com.bos.sphere.feature.grid.WheelDrawer
import com.bos.sphere.feature.grid.cornerPlacements
import com.bos.sphere.feature.grid.rememberDisplayRotation
import com.bos.sphere.feature.grid.rememberGridHomeState
import com.bos.sphere.feature.grid.rememberLauncherWidgetHost
import com.bos.sphere.feature.grid.rememberWidgetPicker
import com.bos.sphere.feature.search.AppSearch
import com.bos.sphere.feature.search.SearchKeyboard
import com.bos.sphere.feature.sphere.LauncherSurface
import com.bos.sphere.feature.sphere.RowMode
import com.bos.sphere.feature.sphere.SphereParams
import com.bos.sphere.feature.sphere.rememberLauncherSurfaceState
import com.bos.sphere.ui.AppContextMenu
import com.bos.sphere.ui.CATEGORY_HOME
import com.bos.sphere.ui.CATEGORY_THIS_PAGE
import com.bos.sphere.ui.Chrome
import com.bos.sphere.ui.DefaultLauncherBanner
import com.bos.sphere.ui.DotGridBackground
import com.bos.sphere.ui.HubPanel
import com.bos.sphere.ui.SettingsModel
import com.bos.sphere.ui.SphereSettings
import com.bos.sphere.ui.SystemLinks
import com.bos.sphere.ui.buildSettingsCategories
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * The home root. Two surfaces share one set of overlays:
 * - **Pages** (default): the reference grid — clock, paged icons (each page a grid of its own
 *   density, a circle, or a coverflow), four configurable corners (actions, apps, radial
 *   palettes, complications, peek widgets) and touch gestures, over the network-aware dot grid.
 * - **Sphere**: the original coverflow-equator surface.
 * Long-pressing empty space opens the settings space (on the "this page" category); motion
 * gestures run while home is resumed (or from [MotionGestureService] when opted in).
 */
@Composable
fun HomeScreen(homeSignal: Int, externalAction: ExternalAction? = null) {
    val context = LocalContext.current
    val repository = remember { LauncherAppsAppRepository(context) }
    val hiddenStore = remember { HiddenAppsStore(context) }
    val stores = remember { LauncherStores.get(context) }
    val scope = rememberCoroutineScope()

    val allApps by repository.apps.collectAsStateWithLifecycle(initialValue = emptyList())
    val hidden by hiddenStore.hidden.collectAsStateWithLifecycle()
    val apps = remember(allApps, hidden) { allApps.filter { it.key !in hidden } }
    val appsByKey = remember(apps) { apps.associateBy { it.key } }
    // Package changes can change icons and categories.
    LaunchedEffect(allApps) { IconCache.clear(); WheelCategoryCache.invalidate() }

    val prefs by stores.prefs.prefs.collectAsStateWithLifecycle()
    val layout by stores.layout.layout.collectAsStateWithLifecycle()
    val corners by stores.shortcuts.corners.collectAsStateWithLifecycle()
    val gestures by stores.shortcuts.gestures.collectAsStateWithLifecycle()
    val history by stores.history.events.collectAsStateWithLifecycle()

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val isPortrait = !isLandscape
    val capacityOf: (PageLayout) -> Int = { it.capacity(isLandscape, prefs) }
    val pages = remember(layout, apps, isLandscape, prefs) {
        HomeLayoutMath.visualPages(layout, apps.map { it.key }, capacityOf)
    }

    // ---- overlay state
    var settingsOpen by remember { mutableStateOf(false) }
    var settingsCategory by remember { mutableIntStateOf(CATEGORY_HOME) }
    var settingsPage by remember { mutableIntStateOf(0) }
    var settingsSession by remember { mutableIntStateOf(0) }
    var drawerOpen by remember { mutableStateOf(false) }
    var drawerFocus by remember { mutableStateOf(false) }
    var wheelOpen by remember { mutableStateOf(false) }
    var wheelAnchor by remember { mutableStateOf(Corner.BottomLeft) }
    var hubOpen by remember { mutableStateOf(false) }
    var menuApp by remember { mutableStateOf<AppEntry?>(null) }
    var editCorner by remember { mutableStateOf<Corner?>(null) }
    var editGesture by remember { mutableStateOf<HomeGesture?>(null) }
    var tileMenu by remember { mutableStateOf<String?>(null) }
    var expandedWidget by remember { mutableStateOf<Corner?>(null) }

    val gridState = rememberGridHomeState()
    val sounds = remember { UiSounds() }
    DisposableEffect(Unit) { onDispose { sounds.release(); VoiceInputManager.release() } }
    sounds.enabled = prefs.uiSounds
    sounds.volume = prefs.uiSoundVolume

    fun launch(app: AppEntry) {
        repository.launch(app)
        stores.history.record(app.key)
        drawerOpen = false
        wheelOpen = false
    }

    fun openSettings(category: Int, page: Int = gridState.currentPage) {
        settingsPage = page.coerceIn(0, (pages.size - 1).coerceAtLeast(0))
        settingsCategory = category
        settingsSession++
        settingsOpen = true
    }

    val rotation = rememberDisplayRotation()
    val placements = remember(corners, rotation, prefs.cornersFollowDevice) {
        cornerPlacements(corners, rotation, prefs.cornersFollowDevice)
    }

    fun openAllApps(anchor: Corner?) {
        if (prefs.drawerStyle == DrawerStyle.Wheel) {
            wheelAnchor = anchor
                ?: placements.firstOrNull { p ->
                    val s = p.slot
                    s is CornerSlot.Single && (s.item as? CornerItem.Action)?.id == CornerActionId.AllApps
                }?.screen
                ?: Corner.BottomLeft
            wheelOpen = true
        } else {
            drawerFocus = false
            drawerOpen = true
        }
    }

    fun internal(id: CornerActionId, anchor: Corner?) {
        when (id) {
            CornerActionId.Search -> { wheelOpen = false; drawerFocus = true; drawerOpen = true }
            CornerActionId.AllApps -> openAllApps(anchor)
            CornerActionId.Notifications -> hubOpen = true
            CornerActionId.LauncherSettings -> openSettings(CATEGORY_HOME)
            else -> Unit
        }
    }

    val internalRef by rememberUpdatedState(::internal)
    val runner = remember {
        ActionRunner(context, onInternal = { internalRef(it, null) }, onAppLaunched = { stores.history.record(it) })
    }

    fun runItem(item: CornerItem, anchor: Corner?) {
        if (item is CornerItem.Action && item.id.internal) internal(item.id, anchor) else runner.run(item)
    }

    fun onGesture(g: HomeGesture) {
        gestures[g]?.let { runItem(it, null) }
    }

    // ---- widgets
    val widgetHost = rememberLauncherWidgetHost()
    var widgetFor by remember { mutableStateOf<Corner?>(null) }

    fun setCorner(c: Corner, slot: CornerSlot) {
        val old = corners[c]
        if (old is CornerSlot.Widget && (slot !is CornerSlot.Widget || slot.appWidgetId != old.appWidgetId)) {
            widgetHost.deleteWidget(old.appWidgetId)
        }
        stores.shortcuts.setCorner(c, slot)
    }
    val widgetPicker = rememberWidgetPicker(widgetHost) { id, label ->
        widgetFor?.let { setCorner(it, CornerSlot.Widget(id, label)) }
        widgetFor = null
    }

    // ---- motion gestures (foreground). The background service takes over when opted in.
    val gestureRef by rememberUpdatedState(::onGesture)
    val motion = remember { MotionGestureSensors(context) { g -> gestureRef(g) } }
    motion.sensitivity = prefs.motionSensitivity
    val lifecycleOwner = LocalLifecycleOwner.current
    val motionHere = prefs.motionEnabled && !prefs.motionInBackground
    DisposableEffect(lifecycleOwner, motionHere) {
        val observer = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME && motionHere) motion.start()
            if (e == Lifecycle.Event.ON_PAUSE) motion.stop()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (motionHere && lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) motion.start()
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer); motion.stop() }
    }
    LaunchedEffect(prefs.motionEnabled, prefs.motionInBackground) {
        if (prefs.motionEnabled && prefs.motionInBackground) MotionGestureService.start(context) else MotionGestureService.stop(context)
    }

    // ---- network-aware dots
    val network = remember { NetworkSignal(context) }
    val networkStatus by network.status.collectAsStateWithLifecycle()
    DisposableEffect(lifecycleOwner, prefs.networkDots) {
        val observer = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_START && prefs.networkDots) network.start()
            if (e == Lifecycle.Event.ON_STOP) network.stop()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (prefs.networkDots && lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) network.start()
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer); network.stop() }
    }

    // ---- suggestion
    val suggestion = remember(history, pages.firstOrNull(), appsByKey) {
        val cal = Calendar.getInstance()
        AppSuggester.suggest(
            events = history,
            nowMillis = cal.timeInMillis,
            nowHour = cal.get(Calendar.HOUR_OF_DAY),
            candidates = appsByKey.keys,
            exclude = pages.firstOrNull()?.keys?.toSet().orEmpty(),
        )?.let(appsByKey::get)
    }

    // ---- Home press: collapse everything, back to the first page.
    LaunchedEffect(homeSignal) {
        if (homeSignal > 0) {
            settingsOpen = false; drawerOpen = false; wheelOpen = false; hubOpen = false
            menuApp = null; editCorner = null; editGesture = null; tileMenu = null; expandedWidget = null
            gridState.goToPage(0)
        }
    }
    LaunchedEffect(externalAction) { externalAction?.let { internal(it.id, null) } }

    // ---- sphere-style state (unchanged surface, settings now live in the settings space)
    val rowsFor = { mode: RowMode -> when (mode) { RowMode.One -> 1; RowMode.Two -> 2; RowMode.Auto -> if (isPortrait) 2 else 1 } }
    var rowMode by remember { mutableStateOf(RowMode.Auto) }
    var sphereParams by remember { mutableStateOf(SphereParams()) }
    var tilesMode by remember { mutableStateOf(false) }
    var rubikMode by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        when (prefs.homeStyle) {
            HomeStyle.Grid -> {
                if (!prefs.showWallpaper) Box(Modifier.fillMaxSize().background(Color.Black))
                NetworkDotGrid(networkStatus, enabled = prefs.networkDots, modifier = Modifier.fillMaxSize())
                GridHome(
                    pages = pages,
                    appsByKey = appsByKey,
                    prefs = prefs,
                    isLandscape = isLandscape,
                    state = gridState,
                    suggestion = suggestion,
                    gesturesBound = gestures.keys,
                    capacityOf = capacityOf,
                    onLaunch = ::launch,
                    onAppLongPress = { menuApp = it },
                    onPagesChange = { stores.layout.setPages(it) },
                    onEmptyLongPress = { page -> openSettings(CATEGORY_THIS_PAGE, page) },
                    onGesture = ::onGesture,
                    onClockTap = { runner.run(CornerActionId.Clock) },
                    onTileLongPress = { key, _ -> tileMenu = key },
                )
                CornerLayer(
                    placements = placements,
                    style = prefs.cornerStyle,
                    appsByKey = appsByKey,
                    onRun = { item, screen -> runItem(item, screen) },
                    onEdit = { editCorner = it },
                    complication = { placement, slot, mod ->
                        ComplicationView(slot.kind, 48.dp, onClick = { slot.kind.action?.let { runItem(CornerItem.Action(it), placement.screen) } }, modifier = mod)
                    },
                    cluster = { placement, slot, mod ->
                        ComplicationCluster(
                            kinds = slot.kinds,
                            anchor = placement.screen,
                            itemSize = 42.dp,
                            radius = 92.dp,
                            onClick = { k -> k.action?.let { runItem(CornerItem.Action(it), placement.screen) } },
                            modifier = mod,
                        )
                    },
                    widget = { placement, slot ->
                        PeekWidget(
                            host = widgetHost,
                            appWidgetId = slot.appWidgetId,
                            label = slot.label,
                            anchor = placement.screen,
                            expanded = expandedWidget == placement.physical,
                            onExpandedChange = { expandedWidget = if (it) placement.physical else null },
                            onRemove = { setCorner(placement.physical, CornerSlot.Empty) },
                            modifier = Modifier.fillMaxSize(),
                        )
                    },
                )
            }

            HomeStyle.Sphere -> {
                val surface = rememberLauncherSurfaceState(itemCount = apps.size, rows = rowsFor(rowMode), params = sphereParams)
                var centred by remember { mutableStateOf(false) }
                LaunchedEffect(apps.size) {
                    if (!centred && apps.isNotEmpty()) {
                        surface.sphereState.position = (surface.sphereState.maxIndex / 2).toFloat()
                        centred = true
                    }
                }
                LaunchedEffect(homeSignal) {
                    if (homeSignal > 0) { surface.zoom.animateTo(0f); surface.hub.animateTo(0f) }
                }
                val chromeAlpha = (1f - smooth(surface.zoomValue)) * (1f - surface.hubValue)
                DotGridBackground(Modifier.fillMaxSize())
                LauncherSurface(
                    apps = apps,
                    state = surface,
                    onLaunch = ::launch,
                    onLongPressApp = { menuApp = it },
                    onOpenSettings = { openSettings(CATEGORY_HOME) },
                    modifier = Modifier.fillMaxSize().padding(bottom = if (isPortrait) 300.dp else 0.dp),
                ) { fraction -> HubPanel(fraction = fraction) }
                Chrome(
                    modifier = Modifier.fillMaxSize().graphicsLayer { alpha = chromeAlpha },
                    onOpenSettings = { openSettings(CATEGORY_HOME) },
                    onVoiceInput = { query ->
                        AppSearch.rank(apps, query, limit = 1).firstOrNull()?.let { app ->
                            if (query.equals(app.label, ignoreCase = true)) launch(app)
                            else apps.indexOf(app).takeIf { it >= 0 }?.let { surface.sphereState.position = it.toFloat() }
                        }
                    },
                )
                if (isPortrait && surface.zoomValue < 0.4f && surface.hubValue < 0.5f && !settingsOpen) {
                    SearchKeyboard(apps = apps, onLaunch = ::launch, modifier = Modifier.align(Alignment.BottomCenter))
                }
            }
        }

        // Notifications hub (grid style opens it from a corner / gesture).
        val hubFraction by animateFloatAsState(if (hubOpen) 1f else 0f, tween(260), label = "hub")
        if (hubFraction > 0.001f) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f * hubFraction))
                    .pointerInput(Unit) { detectTapGestures { hubOpen = false } },
            ) { HubPanel(fraction = hubFraction) }
            if (hubOpen) BackHandler { hubOpen = false }
        }

        AppDrawer(
            visible = drawerOpen,
            focusSearch = drawerFocus,
            apps = apps,
            onLaunch = ::launch,
            onLongPressApp = { menuApp = it },
            onDismiss = { drawerOpen = false },
            modifier = Modifier.fillMaxSize(),
        )

        WheelDrawer(
            visible = wheelOpen,
            anchor = wheelAnchor,
            apps = apps,
            grouping = prefs.wheelGrouping,
            onGroupingChange = { g -> stores.prefs.update { it.copy(wheelGrouping = g) } },
            recentKeys = remember(history) { history.asReversed().map { it.key }.distinct() },
            onLaunch = ::launch,
            onLongPressApp = { menuApp = it },
            onSearch = { wheelOpen = false; drawerFocus = true; drawerOpen = true },
            onDismiss = { wheelOpen = false },
            modifier = Modifier.fillMaxSize(),
        )

        DefaultLauncherBanner(
            modifier = Modifier.align(Alignment.TopCenter).safeDrawingPadding().padding(top = 56.dp),
        )

        val categories = buildSettingsCategories(
            SettingsModel(
                prefs = prefs,
                updatePrefs = stores.prefs::update,
                pageIndex = settingsPage,
                pages = pages,
                setPageLayout = { i, l -> stores.layout.setPages(HomeLayoutMath.setLayout(pages, i, l, capacityOf)) },
                addPageAfter = { i ->
                    stores.layout.setPages(HomeLayoutMath.insertPage(pages, i))
                    scope.launch { gridState.goToPage(i + 1) }
                    settingsPage = i + 1
                },
                deletePage = { i ->
                    stores.layout.setPages(HomeLayoutMath.deletePage(pages, i, capacityOf))
                    settingsPage = (i - 1).coerceAtLeast(0)
                },
                setClockTile = { page, present ->
                    stores.layout.setPages(
                        if (present) HomeLayoutMath.add(pages, HomeTiles.CLOCK, page, capacityOf)
                        else HomeLayoutMath.remove(pages, HomeTiles.CLOCK),
                    )
                },
                corners = corners,
                gestures = gestures,
                appsByKey = appsByKey,
                isLandscape = isLandscape,
                motionAvailable = motion.available,
                editCorner = { editCorner = it },
                editGesture = { editGesture = it },
                sphere = SphereSettings(
                    sphereParams, rowMode, tilesMode, rubikMode,
                    onParams = { sphereParams = it }, onRowMode = { rowMode = it },
                    onTiles = { tilesMode = it }, onRubik = { rubikMode = it },
                ),
                system = SystemLinks(
                    setDefaultHome = {
                        val i = DefaultLauncher.requestRoleIntent(context) ?: DefaultLauncher.homeSettingsIntent()
                        runCatching { context.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    },
                    notificationAccess = {
                        runCatching { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    },
                    dndAccess = {
                        runCatching { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    },
                    androidSettings = { runner.run(CornerActionId.SystemSettings) },
                ),
            ),
        )
        androidx.compose.runtime.key(settingsSession) {
            SettingsSpace(
                visible = settingsOpen,
                categories = categories,
                initialCategory = settingsCategory,
                sounds = sounds,
                onClose = { settingsOpen = false },
                modifier = Modifier.fillMaxSize(),
            )
        }

        // ---- dialogs
        menuApp?.let { app ->
            AppContextMenu(
                app = app,
                isHidden = app.key in hidden,
                onDismiss = { menuApp = null },
                onAppInfo = { repository.openAppDetails(app); menuApp = null },
                onUninstall = { repository.requestUninstall(app); menuApp = null },
                onToggleHidden = { hiddenStore.setHidden(app.key, app.key !in hidden); menuApp = null },
                isOnHome = if (prefs.homeStyle == HomeStyle.Grid) pages.any { app.key in it.keys } else null,
                onToggleHome = {
                    if (pages.any { app.key in it.keys }) stores.layout.removeFromHome(app.key, pages)
                    else stores.layout.addToHome(app.key, pages, gridState.currentPage, capacityOf)
                    menuApp = null
                },
            )
        }
        editCorner?.let { c ->
            CornerEditorDialog(
                corner = c,
                current = corners[c] ?: CornerSlot.Empty,
                apps = apps,
                appsByKey = appsByKey,
                onSave = { setCorner(c, it); editCorner = null },
                onPickWidget = { widgetFor = c; editCorner = null; widgetPicker.launch() },
                onDismiss = { editCorner = null },
            )
        }
        editGesture?.let { g ->
            GestureBindingDialog(
                gesture = g,
                current = gestures[g],
                apps = apps,
                onSave = { stores.shortcuts.setGesture(g, it); editGesture = null },
                onDismiss = { editGesture = null },
            )
        }
        tileMenu?.let { key ->
            TileMenuDialog(
                title = if (key == HomeTiles.CLOCK) "Clock tile" else "Tile",
                onRemove = { stores.layout.setPages(HomeLayoutMath.remove(pages, key)); tileMenu = null },
                onDismiss = { tileMenu = null },
            )
        }
    }
}

private fun smooth(t: Float): Float = (t * t * (3 - 2 * t)).coerceIn(0f, 1f)
