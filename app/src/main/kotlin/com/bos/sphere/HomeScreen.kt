package com.bos.sphere

import android.content.Intent
import android.content.res.Configuration
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.bos.sphere.core.data.AppsOnHome
import com.bos.sphere.core.data.GridSpec
import com.bos.sphere.core.data.PageShape
import com.bos.sphere.core.data.StripItem
import com.bos.sphere.core.data.IndicatorColors
import com.bos.sphere.core.data.IndicatorSource
import com.bos.sphere.core.data.PageMode
import com.bos.sphere.core.data.ThemeMode
import com.bos.sphere.core.data.UsageStats
import com.bos.sphere.core.data.WeatherSource
import com.bos.sphere.core.design.HyleTheme
import com.bos.sphere.core.design.L10n
import com.bos.sphere.core.design.ThemeVariant
import com.bos.sphere.feature.grid.AppDrawerSpace
import com.bos.sphere.feature.grid.ColorPicker
import com.bos.sphere.feature.grid.ConfirmDialog
import com.bos.sphere.feature.grid.GlassDialog
import com.bos.sphere.feature.grid.IconTheme
import com.bos.sphere.feature.grid.IndicatorLayer
import com.bos.sphere.feature.grid.LocalIconTheme
import com.bos.sphere.feature.grid.SearchOverlay
import com.bos.sphere.feature.grid.StripHost
import com.bos.sphere.feature.grid.TextButtonPill
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.layout.fillMaxHeight
import com.bos.sphere.core.data.HomePage
import com.bos.sphere.core.data.ActionApps
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.positionChange
import com.bos.sphere.core.data.ActionCandidates
import com.bos.sphere.core.data.ActionPrefs
import com.bos.sphere.feature.grid.observeBackgroundTouch
import com.bos.sphere.feature.dock.dockShortcuts
import com.bos.sphere.ui.DockLayer
import com.bos.sphere.feature.dock.trackLastTouch
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
import com.bos.sphere.core.data.PageLayout
import com.bos.sphere.core.data.VoiceInputManager
import com.bos.sphere.core.design.HyleColors
import com.bos.sphere.feature.grid.ComplicationCluster
import com.bos.sphere.feature.grid.ComplicationView
import com.bos.sphere.feature.grid.CornerEditorDialog
import com.bos.sphere.feature.grid.CornerLayer
import com.bos.sphere.feature.grid.GestureBindingDialog
import com.bos.sphere.feature.grid.GridHome
import com.bos.sphere.feature.grid.IconCache
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
import com.bos.sphere.feature.grid.HomeGestureSpec
import com.bos.sphere.core.data.isPaged
import com.bos.sphere.core.data.scrollsHorizontally
import com.bos.sphere.core.data.surface
import com.bos.sphere.ui.AppContextMenu
import com.bos.sphere.ui.CATEGORY_HOME
import com.bos.sphere.ui.CATEGORY_THIS_PAGE
import com.bos.sphere.ui.DefaultLauncherBanner
import com.bos.sphere.ui.HubPanel
import com.bos.sphere.ui.SettingsModel
import com.bos.sphere.ui.SystemLinks
import com.bos.sphere.ui.buildSettingsCategories
import com.bos.sphere.ui.settingsCategoryIndex
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.snapshotFlow
import com.bos.sphere.core.data.Torch
import java.util.Calendar

/**
 * The home root. Four home styles share one set of overlays (corners, status strip, Launchpad,
 * drawer, Radar, Settings) and one gesture vocabulary:
 * - **Pages** (default): the paged grid — each page a grid of its own density, a circle or a
 *   coverflow — with its own gesture arbiter ([GridHome]); or, with continuous scrolling, one long grid.
 * - **List**, **PSP** (XMB) and **DSi**: scrolling homes, which get the bound touch gestures from
 *   the shared [com.bos.sphere.feature.grid.homeGestures] layer (pinch, swipes across the free
 *   axis, double tap and long press on empty space).
 * Long-pressing empty space opens Settings (page settings on the paged home); motion gestures
 * run while home is resumed (or from [MotionGestureService] when opted in).
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
    // Package changes can change icons and categories; uninstalled apps forget "removed from home".
    LaunchedEffect(allApps) {
        IconCache.clear()
        WheelCategoryCache.invalidate()
        if (allApps.isNotEmpty()) stores.layout.pruneRemoved(allApps.mapTo(HashSet()) { it.key })
    }
    LaunchedEffect(Unit) { Torch.init(context) }
    // Search signals: categories (resolved off the main thread) and launch stats.
    LaunchedEffect(allApps) {
        if (allApps.isNotEmpty()) {
            WheelCategoryCache.warm(context, allApps.map { it.packageName })
            com.bos.sphere.feature.search.AppSearch.categoryNameOf = { WheelCategoryCache.nameOf(it) }
        }
    }

    val prefs by stores.prefs.prefs.collectAsStateWithLifecycle()
    val layout by stores.layout.layout.collectAsStateWithLifecycle()
    val corners by stores.shortcuts.corners.collectAsStateWithLifecycle()
    val gestures by stores.shortcuts.gestures.collectAsStateWithLifecycle()
    val history by stores.history.events.collectAsStateWithLifecycle()

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val isPortrait = !isLandscape
    val capacityOf: (PageLayout) -> Int = { it.capacity(isLandscape, prefs) }
    val allPages = remember(layout, apps, isLandscape, prefs) {
        HomeLayoutMath.visualPages(layout, apps.map { it.key }, capacityOf, autoPlace = prefs.appsOnHome == AppsOnHome.Everything)
    }
    // A fresh "only the ones I add" home isn't blank: Settings, Phone and Messages are put there once.
    LaunchedEffect(prefs.appsOnHome, apps.isNotEmpty()) {
        val misc = context.getSharedPreferences("liftoff_misc", android.content.Context.MODE_PRIVATE)
        if (prefs.appsOnHome == AppsOnHome.PinnedOnly && apps.isNotEmpty() && !misc.getBoolean("defaultHomeSeeded", false) &&
            layout.pages.all { it.keys.isEmpty() }
        ) {
            val keys = mutableListOf(HomeTiles.SETTINGS)
            listOf(CornerActionId.Phone, CornerActionId.Messages).forEach { id ->
                ActionApps.resolve(context, id)?.packageName?.let { pkg -> apps.firstOrNull { it.packageName == pkg }?.let { keys += it.key } }
            }
            stores.layout.setPages(listOf(HomePage(keys = keys)))
            misc.edit().putBoolean("defaultHomeSeeded", true).apply()
        }
    }
    // "One page" shows only the first page; the rest of the apps stay in the drawer.
    val pages = remember(allPages, prefs.pageMode) { if (prefs.pageMode == PageMode.Single) allPages.take(1) else allPages }
    // Stored keys that can't be drawn right now (hidden apps, a paused work profile, pages hidden by
    // "one page"): every save keeps them.
    val preserve = remember(layout, apps, allPages, pages) {
        layout.pages.flatMapTo(HashSet()) { it.keys } - appsByKey.keys - HomeTiles.ALL.toSet() +
            allPages.drop(pages.size).flatMap { it.keys }
    }
    fun savePages(visual: List<com.bos.sphere.core.data.HomePage>) = stores.layout.setPages(visual, preserve)

    // ---- overlay state
    var settingsOpen by remember { mutableStateOf(false) }
    var settingsCategory by remember { mutableIntStateOf(CATEGORY_HOME) }
    var settingsPage by remember { mutableIntStateOf(0) }
    var settingsSession by remember { mutableIntStateOf(0) }
    var drawerOpen by remember { mutableStateOf(false) }
    var searchOpen by remember { mutableStateOf(false) }
    var colorApp by remember { mutableStateOf<AppEntry?>(null) }
    var fillPrompt by remember { mutableStateOf(false) }
    var wheelOpen by remember { mutableStateOf(false) }
    var wheelAnchor by remember { mutableStateOf(Corner.BottomLeft) }
    var hubOpen by remember { mutableStateOf(false) }
    var leftOpen by remember { mutableStateOf(false) }
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

    // Launch control and dock (WSP-01, WSP-06, DCK): every launch goes through the launch layer.
    val rootView = androidx.compose.ui.platform.LocalView.current
    val appLauncher = remember { com.bos.sphere.feature.dock.AppLauncher(context, rootView = { rootView }) }
    val lastTouch = remember { com.bos.sphere.feature.dock.LastTouch { IntArray(2).also { rootView.getLocationOnScreen(it) }.let { it[0] to it[1] } } }
    val dockController = remember { com.bos.sphere.feature.dock.DockController() }
    val shortcutRegistry = remember { com.bos.sphere.core.input.ShortcutRegistry() }
    fun launch(app: AppEntry) {
        com.bos.sphere.feature.dock.SessionUse.mark(app.key)
        appLauncher.launch(app, lastTouch.sourceRect())
        stores.history.record(app.key)
        drawerOpen = false
        wheelOpen = false
        searchOpen = false
    }

    var quickPage by remember { mutableStateOf<Int?>(null) }
    var quickOpen by remember { mutableStateOf(false) }
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
            drawerOpen = true
        }
    }

    // Pinch (or any bound gesture) steps the grid denser / sparser: the current page's own density
    // if it has one, otherwise the home default for this orientation. Denser also pulls icons forward.
    // The continuous grid steps its "icons across"; List / PSP / DSi have no density to change.
    fun changeDensity(delta: Int) {
        if (!prefs.isPaged) {
            if (prefs.homeStyle == HomeStyle.Grid) {
                val n = (prefs.continuousColumns + delta).coerceIn(2, 8)
                if (n != prefs.continuousColumns) {
                    stores.prefs.update { it.copy(continuousColumns = n) }
                    android.widget.Toast.makeText(context, L10n.tr("%d across", n), android.widget.Toast.LENGTH_SHORT).show()
                }
            } else {
                android.widget.Toast.makeText(context, L10n.tr("This home has no grid density to change"), android.widget.Toast.LENGTH_SHORT).show()
            }
            return
        }
        fun step(g: GridSpec) = GridSpec(g.columns + delta, g.rows + delta).coerced()
        val idx = gridState.currentPage.coerceIn(0, (pages.size - 1).coerceAtLeast(0))
        val page = pages.getOrNull(idx)
        val l = page?.layout
        if (page != null && l != null && l.shape == PageShape.Grid && (l.portrait != null || l.landscape != null)) {
            val cur = l.gridFor(isLandscape, prefs)
            val ns = step(cur)
            val nl = if (isLandscape) l.copy(landscape = ns, portrait = l.portrait ?: prefs.portraitGrid)
            else l.copy(portrait = ns, landscape = l.landscape ?: prefs.landscapeGrid)
            val capNew: (PageLayout) -> Int = { pl -> if (pl == l) nl.capacity(isLandscape, prefs) else pl.capacity(isLandscape, prefs) }
            val relaid = HomeLayoutMath.setLayout(pages, idx, nl, capNew)
            savePages(if (delta > 0) HomeLayoutMath.reflow(relaid, capNew) else relaid)
            android.widget.Toast.makeText(context, "${ns.columns} × ${ns.rows}", android.widget.Toast.LENGTH_SHORT).show()
        } else {
            val cur = prefs.gridFor(isLandscape)
            val ns = step(cur)
            if (ns == cur) return
            val newPrefs = if (isLandscape) prefs.copy(landscapeGrid = ns) else prefs.copy(portraitGrid = ns)
            stores.prefs.update { newPrefs }
            if (delta > 0) {
                val capNew: (PageLayout) -> Int = { it.capacity(isLandscape, newPrefs) }
                val vis = HomeLayoutMath.visualPages(layout, apps.map { it.key }, capNew, autoPlace = prefs.appsOnHome == AppsOnHome.Everything)
                savePages(HomeLayoutMath.reflow(vis, capNew))
            }
            android.widget.Toast.makeText(context, "${ns.columns} × ${ns.rows}", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    fun internal(id: CornerActionId, anchor: Corner?) {
        when (id) {
            CornerActionId.GridDenser -> changeDensity(+1)
            CornerActionId.GridSparser -> changeDensity(-1)
            CornerActionId.Search -> { wheelOpen = false; drawerOpen = false; searchOpen = true }
            CornerActionId.AllApps -> openAllApps(anchor)
            CornerActionId.Notifications -> if (prefs.leftPage != com.bos.sphere.core.data.LeftPage.Off) leftOpen = true else hubOpen = true
            CornerActionId.LauncherSettings -> openSettings(CATEGORY_HOME)
            else -> Unit
        }
    }

    val internalRef by rememberUpdatedState(::internal)
    var pickFor by remember { mutableStateOf<CornerActionId?>(null) }
    var actionAppsRev by remember { mutableIntStateOf(0) }
    val pickAppForRef by rememberUpdatedState({ id: CornerActionId -> pickFor = id })
    val runner = remember {
        ActionRunner(context, onInternal = { internalRef(it, null) }, onAppLaunched = { stores.history.record(it) }, onPickApp = { pickAppForRef(it) })
    }
    runner.launchHook = { key ->
        com.bos.sphere.feature.dock.AppKeys.target(key)?.let { t ->
            com.bos.sphere.feature.dock.SessionUse.mark(key)
            appLauncher.launch(com.bos.sphere.core.launch.LaunchRequest(t, source = lastTouch.sourceRect()?.let { com.bos.sphere.core.launch.Bounds(it.left, it.top, it.right, it.bottom) })).started
        } ?: false
    }

    fun runItem(item: CornerItem, anchor: Corner?) {
        if (item is CornerItem.Action && item.id.internal) internal(item.id, anchor) else runner.run(item)
    }

    fun onGesture(g: HomeGesture) {
        gestures[g]?.let { runItem(it, null) }
    }
    // The shared gesture layer of the scrolling homes (the paged grid has its own arbiter).
    val surface = prefs.surface(isLandscape)
    val gestureRefHolder = rememberUpdatedState(::onGesture)
    val openSettingsRef = rememberUpdatedState<(Int) -> Unit> { c -> openSettings(c) }
    val homeGestureSpec = remember(surface, gestures.keys) {
        HomeGestureSpec(surface, gestures.keys, onGesture = { g -> gestureRefHolder.value(g) }, onEmptyLongPress = { openSettingsRef.value(CATEGORY_HOME) })
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
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val dest = java.io.File(context.filesDir, "background_${System.currentTimeMillis()}.img")
            val ok = runCatching { context.contentResolver.openInputStream(uri)?.use { i -> dest.outputStream().use { o -> i.copyTo(o) } } }.isSuccess
            if (ok && dest.length() > 0) {
                val old = prefs.backgroundPicturePath
                stores.prefs.update { it.copy(backgroundKind = com.bos.sphere.core.data.BackgroundKind.Picture, backgroundPreset = "photo", backgroundPicturePath = dest.absolutePath) }
                if (old.isNotEmpty()) runCatching { java.io.File(old).delete() }
            }
        }
    }
    var pickingLeft by remember { mutableStateOf(false) }
    var leftWidgetLabel by remember { mutableStateOf<String?>(null) }
    val widgetPicker = rememberWidgetPicker(widgetHost) { id, label ->
        if (pickingLeft) {
            val old = prefs.leftWidgetId
            if (old >= 0 && old != id) widgetHost.deleteWidget(old)
            leftWidgetLabel = label
            stores.prefs.update { it.copy(leftWidgetId = id, leftPage = com.bos.sphere.core.data.LeftPage.Widget) }
            pickingLeft = false
        } else {
            widgetFor?.let { setCorner(it, CornerSlot.Widget(id, label)) }
            widgetFor = null
        }
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
    // The background service shows an ongoing notification; on Android 13+ that needs permission.
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) {
            android.widget.Toast.makeText(context, "Motion gestures still run; their notification is hidden", android.widget.Toast.LENGTH_SHORT).show()
        }
    }
    LaunchedEffect(prefs.motionEnabled, prefs.motionInBackground) {
        if (prefs.motionEnabled && prefs.motionInBackground) {
            if (android.os.Build.VERSION.SDK_INT >= 33 &&
                context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
            MotionGestureService.start(context)
        } else {
            MotionGestureService.stop(context)
        }
    }

    // ---- theme and language (also applied before first composition in MainActivity)
    SideEffect {
        HyleTheme.apply(
            when (prefs.themeMode) { ThemeMode.Dark -> ThemeVariant.Dark; ThemeMode.Amoled -> ThemeVariant.Amoled; ThemeMode.Light -> ThemeVariant.Light },
            prefs.accentColor,
        )
        L10n.setLanguage(prefs.language.code)
    }
    val iconColors by stores.iconColors.colors.collectAsStateWithLifecycle()
    val iconTheme = remember(prefs.iconShape, prefs.iconTint, prefs.iconPlate, prefs.accentColor, iconColors) {
        IconTheme(prefs.iconShape, prefs.iconTint, prefs.iconPlate, prefs.accentColor, iconColors)
    }

    // ---- indicator: the background dots / effects read out any measurement
    val indicator = remember { IndicatorSource(context) }
    LaunchedEffect(prefs.indicatorMetric) { indicator.setMetric(prefs.indicatorMetric) }
    val reading by indicator.reading.collectAsStateWithLifecycle()
    val indicatorTarget = IndicatorColors.argb(reading, prefs.indicatorLow, prefs.indicatorMid, prefs.indicatorHigh, prefs.indicatorIdle, prefs.indicatorInvert)
    val indicatorColor by androidx.compose.animation.animateColorAsState(Color(indicatorTarget.toInt()), androidx.compose.animation.core.tween(800), label = "indicator")

    // ---- weather (opt-in; needs approximate location)
    val weatherSource = remember { WeatherSource(context) }
    val weather by weatherSource.weather.collectAsStateWithLifecycle()
    val locationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) weatherSource.refresh(force = true)
    }
    LaunchedEffect(prefs.clockShowWeather) {
        if (prefs.clockShowWeather) {
            if (weatherSource.hasPermission()) weatherSource.refresh() else locationPermission.launch(android.Manifest.permission.ACCESS_COARSE_LOCATION)
        }
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_START) {
                indicator.start()
                if (prefs.clockShowWeather) weatherSource.refresh()
            }
            if (e == Lifecycle.Event.ON_STOP) indicator.stop()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) indicator.start()
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer); indicator.stop() }
    }

    // ---- status bar: flush-to-top home (swipe down to peek)
    val view = LocalView.current
    LaunchedEffect(prefs.hideStatusBar, homeSignal) {
        var c: android.content.Context = view.context
        while (c is android.content.ContextWrapper && c !is android.app.Activity) c = c.baseContext
        val window = (c as? android.app.Activity)?.window ?: return@LaunchedEffect
        val controller = WindowCompat.getInsetsController(window, view)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (prefs.hideStatusBar) controller.hide(WindowInsetsCompat.Type.statusBars()) else controller.show(WindowInsetsCompat.Type.statusBars())
    }

    // ---- Health Connect: steps, floors, elevation, distance, calories
    val healthSource = remember { com.bos.sphere.core.data.HealthSource(context) }
    val health by healthSource.today.collectAsStateWithLifecycle()
    val healthPermission = rememberLauncherForActivityResult(
        androidx.health.connect.client.PermissionController.createRequestPermissionResultContract(),
    ) { healthSource.refresh() }
    val stripNeedsHealth = prefs.strip.keys.any { it in setOf(StripItem.Floors, StripItem.Elevation, StripItem.Distance, StripItem.Calories) }
    val wantsHealth = prefs.healthSource == com.bos.sphere.core.data.HealthSourcePref.HealthConnect || stripNeedsHealth
    fun requestHealth() {
        if (healthSource.available()) healthPermission.launch(com.bos.sphere.core.data.HealthSource.PERMISSIONS)
        else runCatching {
            context.startActivity(
                Intent(androidx.health.connect.client.HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }
    LaunchedEffect(wantsHealth) {
        if (wantsHealth && healthSource.available() && !healthSource.hasAllPermissions()) requestHealth()
    }
    LaunchedEffect(wantsHealth, lifecycleOwner) {
        if (!wantsHealth) return@LaunchedEffect
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) { healthSource.refresh(); kotlinx.coroutines.delay(60_000) }
        }
    }

    val contactSource = remember { if (true) com.bos.sphere.core.data.ContactSource(context) else null }
    val searchHub = remember { com.bos.sphere.core.data.AndroidSearchHub(context) }
    val filePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }
    val contactsPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    // ---- steps need the activity-recognition permission
    val stepsPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(StripItem.Steps in prefs.strip) {
        if (StripItem.Steps in prefs.strip &&
            context.checkSelfPermission(android.Manifest.permission.ACTIVITY_RECOGNITION) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) stepsPermission.launch(android.Manifest.permission.ACTIVITY_RECOGNITION)
    }

    // ---- density change: offer to fill the gaps it leaves
    var capSignature by remember { mutableStateOf<List<Int>?>(null) }
    LaunchedEffect(settingsOpen) {
        val sig = pages.map { capacityOf(it.layout) }
        if (settingsOpen) {
            capSignature = sig
        } else {
            val before = capSignature
            capSignature = null
            if (before != null && before != sig && HomeLayoutMath.hasGaps(pages, capacityOf)) fillPrompt = true
        }
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
            settingsOpen = false; drawerOpen = false; wheelOpen = false; hubOpen = false; searchOpen = false; leftOpen = false
            menuApp = null; editCorner = null; editGesture = null; tileMenu = null; expandedWidget = null
            gridState.goToPage(prefs.homePage.coerceIn(0, (pages.size - 1).coerceAtLeast(0)))
        }
    }
    LaunchedEffect(externalAction) { externalAction?.let { internal(it.id, null) } }

    // Shared by both home styles: the four corners (kept clear of the always-on keyboard).
    val kbHeight = remember { mutableStateOf(0.dp) }
    val bottomClear = if (prefs.alwaysKeyboard) kbHeight.value else 0.dp
    @Composable
    fun Corners(alpha: Float = 1f) {
        Box(Modifier.fillMaxSize().padding(bottom = bottomClear).graphicsLayer { this.alpha = alpha }) {
            CornerLayer(
                placements = placements,
                style = prefs.cornerStyle,
                iconSize = prefs.cornerIconSize.dp,
                iconMode = prefs.cornerIconMode,
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
    }
    val bgTouch = remember { mutableStateOf<com.bos.sphere.feature.grid.TouchPoint?>(null) }
    @Composable
    fun Background() {
        com.bos.sphere.feature.grid.BackgroundLayer(
            kind = prefs.backgroundKind,
            preset = prefs.backgroundPreset,
            colorA = prefs.backgroundColorA,
            colorB = prefs.backgroundColorB,
            modifier = Modifier.fillMaxSize(),
            touch = bgTouch,
            picturePath = prefs.backgroundPicturePath,
        )
        IndicatorLayer(
            reading = reading,
            effects = prefs.indicatorEffects,
            colorArgb = indicatorColor.toArgb().toLong() and 0xFFFFFFFFL,
            intensity = prefs.indicatorIntensity,
            speed = prefs.indicatorSpeed,
            modifier = Modifier.fillMaxSize(),
        )
    }

    // Breathing room at the sides of the scrolling homes (plus whatever "Sides" margin is set).
    val scrollHorizontal = prefs.scrollsHorizontally(isLandscape)
    val cfg = androidx.compose.ui.platform.LocalConfiguration.current
    // One frame on every side, in either orientation (about 15% of the short side): apps live inside it;
    // the corner icons, clock and page dots sit in the margin band around it.
    val frame = (minOf(cfg.screenWidthDp, cfg.screenHeightDp) * 0.148f).dp
    val sideGap = frame + (cfg.screenWidthDp * prefs.gridInsetX).dp
    val stripH = com.bos.sphere.feature.grid.stripHeight(prefs)
    val scrollTop = if (prefs.clockPosition == com.bos.sphere.core.data.ClockPosition.Top) maxOf(frame, stripH + 14.dp) else frame
    val scrollBottom = bottomClear + frame
    val scrollPadding = androidx.compose.foundation.layout.PaddingValues(start = sideGap, end = sideGap, top = scrollTop, bottom = scrollBottom)
    // The PSP bar has no reliable empty space to long-press: holding the top band (clock area) opens Settings.
    @Composable
    fun BoxScope.SettingsHoldBand() {
        Box(
            Modifier.align(Alignment.TopCenter).fillMaxWidth()
                .height(stripH + 44.dp)
                .pointerInput(Unit) { detectTapGestures(onLongPress = { quickPage = null; quickOpen = true }) },
        )
    }
    CompositionLocalProvider(LocalIconTheme provides iconTheme) {
    Box(
        modifier = Modifier.fillMaxSize().observeBackgroundTouch(bgTouch).trackLastTouch(lastTouch).dockShortcuts(shortcutRegistry, dockController).leftSwipe(
            enabled = prefs.leftPage != com.bos.sphere.core.data.LeftPage.Off && !leftOpen &&
                (prefs.homeStyle != HomeStyle.Grid || prefs.homeScroll == com.bos.sphere.core.data.HomeScroll.Continuous),
            atFirstPage = { true },
            onOpen = { leftOpen = true },
            edgeDp = 28f,
        ),
    ) {
        // The left page lies UNDER the home: the home slides right, casting a shadow onto it.
        val leftProgress by animateFloatAsState(if (leftOpen) 1f else 0f, tween(300), label = "leftPage")
        val leftActive = leftOpen || leftProgress > 0.001f
        when (prefs.leftPage) {
            com.bos.sphere.core.data.LeftPage.Launchpad -> if (leftActive) com.bos.sphere.ui.LaunchpadPanel(
                visible = true,
                onDismiss = { leftOpen = false },
                onOpenApp = { pkg ->
                    leftOpen = false
                    apps.firstOrNull { it.packageName == pkg }?.let(::launch)
                        ?: context.packageManager.getLaunchIntentForPackage(pkg)?.let { runCatching { context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
                },
                modifier = Modifier.fillMaxSize(),
                theme = prefs.launchpadTheme,
                underlay = true,
                endInset = (androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp * 0.14f).dp,
            )
            com.bos.sphere.core.data.LeftPage.Widget -> if (leftActive && prefs.leftWidgetId >= 0 && !com.bos.sphere.core.data.CrashLoopGuard.safeMode) LeftWidgetPage(true, widgetHost, prefs.leftWidgetId) { leftOpen = false }
            else -> Unit
        }
        val slideDp = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp * 0.86f
        Box(
            Modifier.fillMaxSize().graphicsLayer {
                // Home is the floating surface: it slides right, shrinks a touch and rounds its corners,
                // casting a shadow on the notifications layer beneath.
                translationX = leftProgress * slideDp * density
                val sc = 1f - 0.05f * leftProgress
                scaleX = sc; scaleY = sc
                if (leftProgress > 0.001f) {
                    shadowElevation = 36.dp.toPx()
                    shape = androidx.compose.foundation.shape.RoundedCornerShape((30f * leftProgress).dp)
                    clip = true
                    ambientShadowColor = Color.Black
                    spotShadowColor = Color.Black
                }
            }
                .then(if (leftActive) Modifier.background(Color.Black) else Modifier),
        ) {
            when (prefs.homeStyle) {
                HomeStyle.Grid -> if (prefs.homeScroll == com.bos.sphere.core.data.HomeScroll.Continuous) {
                    Background()
                    com.bos.sphere.feature.grid.ContinuousHome(
                        apps = apps, crossCount = prefs.continuousColumns, landscape = scrollHorizontal, style = com.bos.sphere.feature.grid.ContinuousStyle.Plain,
                        onLaunch = ::launch, onLongPress = { menuApp = it },
                        modifier = Modifier.fillMaxSize().safeDrawingPadding(),
                        contentPadding = scrollPadding,
                        iconScale = prefs.iconScale, spacing = prefs.iconSpacing, showLabels = prefs.showLabels, sounds = sounds,
                        gestures = homeGestureSpec,
                    )
                    StripHost(prefs, weather, health) { runner.run(CornerActionId.Clock) }
                    Corners()
                } else {
                    Background()
                    Box(
                        Modifier.fillMaxSize().padding(bottom = bottomClear).leftSwipe(
                            enabled = prefs.leftPage != com.bos.sphere.core.data.LeftPage.Off && !leftOpen,
                            atFirstPage = { gridState.pager.currentPage == 0 && !gridState.pager.isScrollInProgress },
                            onOpen = { leftOpen = true },
                        ),
                    ) {
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
                            onPagesChange = { savePages(it) },
                            onEmptyLongPress = { page -> quickPage = page; quickOpen = true },
                            onGesture = ::onGesture,
                            onClockTap = { runner.run(CornerActionId.Clock) },
                            onTileTap = { key -> if (key == HomeTiles.SETTINGS) openSettings(CATEGORY_HOME) },
                            onTileLongPress = { key, _ -> tileMenu = key },
                            weather = weather,
                            health = health,
                        )
                    }
                    Corners()
                }

                HomeStyle.Psp -> {
                    Background()
                    val recentForXmb = remember(history, appsByKey) { history.asReversed().map { it.key }.distinct().mapNotNull(appsByKey::get).take(12) }
                    com.bos.sphere.feature.grid.XmbHome(
                        apps = apps, recent = recentForXmb,
                        categoryOf = { com.bos.sphere.feature.search.AppSearch.categoryNameOf(it.packageName) },
                        onLaunch = ::launch, onLongPress = { menuApp = it },
                        modifier = Modifier.fillMaxSize().safeDrawingPadding(),
                        contentPadding = scrollPadding,
                        showLabels = prefs.showLabels, sounds = sounds,
                        gestures = homeGestureSpec,
                    )
                    SettingsHoldBand()
                    StripHost(prefs, weather, health) { runner.run(CornerActionId.Clock) }
                    Corners()
                }
                HomeStyle.Dsi -> {
                    Background()
                    com.bos.sphere.feature.grid.ContinuousHome(
                        apps = apps, crossCount = 3, landscape = scrollHorizontal, style = com.bos.sphere.feature.grid.ContinuousStyle.Dsi,
                        onLaunch = ::launch, onLongPress = { menuApp = it },
                        modifier = Modifier.fillMaxSize().safeDrawingPadding(),
                        contentPadding = scrollPadding,
                        iconScale = prefs.iconScale, spacing = prefs.iconSpacing, showLabels = prefs.showLabels, sounds = sounds,
                        gestures = homeGestureSpec,
                    )
                    StripHost(prefs, weather, health) { runner.run(CornerActionId.Clock) }
                    Corners()
                }
                HomeStyle.List -> {
                    Background()
                    com.bos.sphere.feature.grid.ListHome(
                        apps = apps, onLaunch = ::launch, onLongPress = { menuApp = it },
                        modifier = Modifier.fillMaxSize().safeDrawingPadding(),
                        contentPadding = scrollPadding,
                        iconScale = prefs.iconScale, showLabels = prefs.showLabels,
                        gestures = homeGestureSpec,
                    )
                    StripHost(prefs, weather, health) { runner.run(CornerActionId.Clock) }
                    Corners()
                }
            }

        }
        // The strip of home still showing: tap it, or drag it left, to bring the home back.
        if (leftOpen) {
            Box(
                Modifier.align(Alignment.CenterEnd).fillMaxHeight().width((androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp * 0.14f).dp)
                    .pointerInput(Unit) {
                        detectTapGestures(onTap = { leftOpen = false })
                    }
                    .pointerInput(Unit) {
                        var total = 0f
                        detectHorizontalDragGestures(onDragStart = { total = 0f }, onDragEnd = { if (total < -40f) leftOpen = false }) { _, d -> total += d }
                    },
            )
        }

        // Media controls: a track + prev / play / next strip while something is playing.
        val mediaInfo by com.bos.sphere.feature.grid.rememberMediaInfo(prefs.mediaControls)
        androidx.compose.animation.AnimatedVisibility(
            visible = mediaInfo != null && !leftOpen && !settingsOpen && !searchOpen && !drawerOpen,
            enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.slideInVertically { it / 2 },
            exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.slideOutVertically { it / 2 },
            modifier = Modifier.align(Alignment.BottomCenter).safeDrawingPadding().padding(start = 76.dp, end = 76.dp, bottom = 66.dp + bottomClear),
        ) {
            mediaInfo?.let { mi ->
                com.bos.sphere.feature.grid.MediaStrip(
                    info = mi,
                    onPrev = { com.bos.sphere.core.data.MediaNow.previous() },
                    onPlayPause = { com.bos.sphere.core.data.MediaNow.playPause() },
                    onNext = { com.bos.sphere.core.data.MediaNow.next() },
                    onOpen = { com.bos.sphere.core.data.MediaNow.openPlayer(context) },
                )
            }
        }

        // Dock rail (DCK-01 to DCK-08): off by default on phones, on by default on large windows and desktop setups.
        DockLayer(
            apps = apps, launcher = appLauncher, controller = dockController, homeSignal = homeSignal,
            visible = !leftOpen && !settingsOpen && !searchOpen && !drawerOpen && !wheelOpen,
            keyboardMode = appLauncher.inputs().hasKeyboard, onLaunch = ::launch,
        )

        // The way into the left page on every home style: the unread strip, plus a slim edge handle
        // (tap it, or swipe in from the left edge) so it is always discoverable.
        if (prefs.leftPage != com.bos.sphere.core.data.LeftPage.Off && !leftOpen) {
            if (prefs.leftPage == com.bos.sphere.core.data.LeftPage.Launchpad && prefs.launchpadRail) {
                com.bos.sphere.ui.LaunchpadRail(onOpen = { leftOpen = true }, modifier = Modifier.align(Alignment.CenterStart))
            }
            Box(
                Modifier.align(Alignment.CenterStart).padding(start = 2.dp).width(5.dp).height(64.dp)
                    .background(Color.White.copy(alpha = 0.28f), androidx.compose.foundation.shape.RoundedCornerShape(3.dp)),
            )
            // Kept narrow so it never sits over the first column of icons; tall so it is still easy to hit.
            Box(
                Modifier.align(Alignment.CenterStart).width(24.dp).height(180.dp)
                    .semantics { contentDescription = L10n.tr(prefs.leftPage.label) }
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClickLabel = L10n.tr("Open"),
                        role = Role.Button,
                    ) { leftOpen = true },
            )
        }

        // Old-school mode: an always-open keyboard that searches as you type.
        if (prefs.alwaysKeyboard && !settingsOpen && !searchOpen && !drawerOpen && !wheelOpen) {
            val kbDensity = androidx.compose.ui.platform.LocalDensity.current
            SearchKeyboard(
                apps = apps,
                onLaunch = ::launch,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .androidx_navPadding()
                    .onSizeChanged { kbHeight.value = with(kbDensity) { it.height.toDp() } },
            )
        }

        // Notifications hub (grid style opens it from a corner / gesture).
        val hubFraction by animateFloatAsState(if (hubOpen) 1f else 0f, tween(260), label = "hub")
        if (hubFraction > 0.001f) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f * hubFraction))
                    .pointerInput(Unit) { detectTapGestures { hubOpen = false } },
            ) {
                HubPanel(fraction = hubFraction, onRequestAccess = {
                    hubOpen = false
                    runCatching { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                })
            }
            if (hubOpen) BackHandler { hubOpen = false }
        }

        val usage = remember(history) { UsageStats.of(history) }
        androidx.compose.runtime.SideEffect { com.bos.sphere.feature.search.AppSearch.usage = usage }
        AppDrawerSpace(
            visible = drawerOpen,
            apps = apps,
            usage = usage,
            view = prefs.drawerView,
            sort = prefs.drawerSort,
            rail = prefs.drawerRail,
            railVisible = prefs.drawerRailVisible,
            viewSwitch = prefs.drawerViewSwitch,
            onViewChange = { v -> stores.prefs.update { it.copy(drawerView = v) } },
            onSortChange = { v -> stores.prefs.update { it.copy(drawerSort = v) } },
            onLaunch = ::launch,
            onLongPress = { menuApp = it },
            onDismiss = { drawerOpen = false },
            sounds = sounds,
            modifier = Modifier.fillMaxSize(),
        )

        val recentApps = remember(history, appsByKey) { history.asReversed().map { it.key }.distinct().mapNotNull(appsByKey::get) }
        SearchOverlay(
            visible = searchOpen,
            apps = apps,
            recent = recentApps,
            prefs = prefs,
            contacts = contactSource,
            hub = searchHub,
            onRunAction = { runner.run(it) },
            onRequestFilePermission = { filePermission.launch(arrayOf(android.Manifest.permission.READ_MEDIA_IMAGES, android.Manifest.permission.READ_MEDIA_VIDEO, android.Manifest.permission.READ_MEDIA_AUDIO)) },
            sounds = sounds,
            onLaunch = ::launch,
            onLongPressApp = { menuApp = it },
            onOpenIntent = { i -> runCatching { context.startActivity(i.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) } },
            onRequestContactsPermission = { contactsPermission.launch(android.Manifest.permission.READ_CONTACTS) },
            onDismiss = { searchOpen = false },
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
            onSearch = { wheelOpen = false; searchOpen = true },
            actions = listOf(
                com.bos.sphere.feature.grid.PuckAction("Home settings", com.bos.sphere.feature.grid.Glyphs.Gear) { wheelOpen = false; runner.run(CornerActionId.LauncherSettings) },
                com.bos.sphere.feature.grid.PuckAction("Notifications", com.bos.sphere.feature.grid.Glyphs.Bell) { wheelOpen = false; runner.run(CornerActionId.Notifications) },
                com.bos.sphere.feature.grid.PuckAction("Flashlight", com.bos.sphere.feature.grid.Glyphs.Flashlight) { runner.run(CornerActionId.Flashlight) },
                com.bos.sphere.feature.grid.PuckAction("Ring, vibrate, silent", com.bos.sphere.feature.grid.Glyphs.Ring) { runner.run(CornerActionId.RingerCycle) },
                com.bos.sphere.feature.grid.PuckAction("Do not disturb", com.bos.sphere.feature.grid.Glyphs.Moon) { runner.run(CornerActionId.DoNotDisturb) },
            ),
            onDismiss = { wheelOpen = false },
            modifier = Modifier.fillMaxSize(),
        )

        DefaultLauncherBanner(
            modifier = Modifier.align(Alignment.BottomCenter).safeDrawingPadding(),
        )
        var safeDismissed by remember { mutableStateOf(false) }
        if (com.bos.sphere.core.data.CrashLoopGuard.safeMode && !safeDismissed) {
            androidx.compose.material3.Text(
                L10n.tr("Safe mode: Liftoff restarted twice in a row, so widgets are off. Tap to carry on."),
                color = Color.White, fontSize = 13f.let { androidx.compose.ui.unit.TextUnit(it, androidx.compose.ui.unit.TextUnitType.Sp) },
                modifier = Modifier.align(Alignment.TopCenter).safeDrawingPadding().padding(12.dp)
                    .background(Color(0xE6B3261E), androidx.compose.foundation.shape.RoundedCornerShape(14.dp))
                    .clickable { com.bos.sphere.core.data.CrashLoopGuard.clear(context); safeDismissed = true }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            )
        }

        // Built once per relevant change, not on every recomposition of the home (it is ~100 items
        // with lambdas, plus binder calls for the widget label and Health Connect availability).
        val leftWidgetName = remember(leftWidgetLabel, prefs.leftWidgetId) {
            leftWidgetLabel ?: prefs.leftWidgetId.takeIf { it >= 0 }?.let { widgetHost.providerInfo(it)?.loadLabel(context.packageManager)?.toString() }
        }
        val healthAvailable = remember(settingsOpen) { healthSource.available() }
        val about = remember {
            val pi = runCatching { context.packageManager.getPackageInfo(context.packageName, 0) }.getOrNull()
            listOf(
                "Version" to (pi?.versionName ?: "?"),
                "Build number" to (pi?.longVersionCode?.toString() ?: "?"),
                "Commit" to com.bos.sphere.BuildConfig.GIT_SHA,
                "Commit date" to com.bos.sphere.BuildConfig.GIT_DATE,
                "Build type" to if (com.bos.sphere.BuildConfig.DEBUG) "debug" else "release",
                "Package" to context.packageName,
                "Android" to "${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})",
                "Device" to "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}",
            )
        }
        val capInputs = remember(settingsOpen, isLandscape) { com.bos.sphere.core.capabilities.CapabilityRegistry(context).snapshot() }
        val capabilityRows = remember(capInputs) { com.bos.sphere.core.capabilities.CapabilityEvaluator.evaluate(capInputs) }
        val labResults = remember { androidx.compose.runtime.mutableStateMapOf<String, String>() }
        val labEntries = com.bos.sphere.ui.rememberLabEntries(capInputs, labResults)
        val categories = remember(prefs, pages, layout, apps, corners, gestures, settingsPage, isLandscape, leftWidgetName, actionAppsRev, healthAvailable, capabilityRows, labEntries.map { it.result }) {
            buildSettingsCategories(
            SettingsModel(
                chosenApp = { id -> actionAppsRev.let { ActionPrefs.get(context, id)?.let(appsByKey::get)?.label } },
                pickAppFor = { id -> pickFor = id },
                pickBackgroundPhoto = { photoPicker.launch("image/*") },
                pickLeftWidget = { pickingLeft = true; widgetPicker.launch() },
                leftWidgetLabel = leftWidgetName,
                prefs = prefs,
                updatePrefs = stores.prefs::update,
                pageIndex = settingsPage,
                pages = pages,
                setPageLayout = { i, l -> savePages(HomeLayoutMath.setLayout(pages, i, l, capacityOf)) },
                addPageAfter = { i ->
                    savePages(HomeLayoutMath.insertPage(pages, i))
                    settingsPage = i + 1
                    // The pager only learns about the page after the next composition; wait for it.
                    scope.launch {
                        withTimeoutOrNull(1_000) { snapshotFlow { gridState.pager.pageCount }.first { it > i + 1 } }
                        gridState.goToPage(i + 1)
                    }
                },
                deletePage = { i ->
                    savePages(HomeLayoutMath.deletePage(pages, i, capacityOf))
                    settingsPage = (i - 1).coerceAtLeast(0)
                },
                setClockTile = { page, present ->
                    // Put it first on that page; a full page spills its last app forward.
                    savePages(
                        if (present) HomeLayoutMath.move(pages, HomeTiles.CLOCK, page, 0, capacityOf)
                        else HomeLayoutMath.remove(pages, HomeTiles.CLOCK),
                    )
                },
                corners = corners,
                gestures = gestures,
                setGesture = { g, item -> stores.shortcuts.setGesture(g, item) },
                requestHealth = { requestHealth() },
                about = about,
                capabilities = capabilityRows,
                copyText = { t -> (context.getSystemService(android.content.ClipboardManager::class.java)).setPrimaryClip(android.content.ClipData.newPlainText("Liftoff", t)) },
                lab = labEntries,
                healthAvailable = healthAvailable,
                appsByKey = appsByKey,
                isLandscape = isLandscape,
                motionAvailable = motion.available,
                editCorner = { editCorner = it },
                editGesture = { editGesture = it },
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
        }
        SettingsSpace(
            visible = settingsOpen,
            categories = categories,
            initialCategory = settingsCategoryIndex(categories, settingsCategory),
            sounds = sounds,
            onClose = { settingsOpen = false },
            modifier = Modifier.fillMaxSize(),
            session = settingsSession,
            backdrop = {
                if (prefs.settingsBackdrop == com.bos.sphere.core.data.SettingsBackdrop.Black) Box(Modifier.fillMaxSize().background(Color.Black))
                else Box(Modifier.fillMaxSize()) {
                    com.bos.sphere.feature.grid.BackgroundLayer(
                        kind = prefs.backgroundKind, preset = prefs.backgroundPreset,
                        colorA = prefs.backgroundColorA, colorB = prefs.backgroundColorB,
                        modifier = Modifier.fillMaxSize(), animate = false, picturePath = prefs.backgroundPicturePath,
                    )
                    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = prefs.settingsDim.coerceIn(0f, 1f))))
                }
            },
        )

        // ---- dialogs
        menuApp?.let { app ->
            AppContextMenu(
                app = app,
                isHidden = app.key in hidden,
                onDismiss = { menuApp = null },
                onAppInfo = { repository.openAppDetails(app); menuApp = null },
                onUninstall = { repository.requestUninstall(app); menuApp = null },
                onToggleHidden = { hiddenStore.setHidden(app.key, app.key !in hidden); menuApp = null },
                onIconColor = { colorApp = app; menuApp = null },
                extraEntries = com.bos.sphere.feature.dock.AppActionList.build(appLauncher, com.bos.sphere.feature.dock.WorkspaceRepository.get(context), app, lastTouch.sourceRect()) { menuApp = null }
                    .map { it.label to it.run },
                onHomeSettings = if (!prefs.isPaged) {
                    { menuApp = null; openSettings(CATEGORY_HOME) }
                } else null,
                // Only the paged home has pages to add to; the scrolling homes show every app.
                isOnHome = if (prefs.isPaged) pages.any { app.key in it.keys } else null,
                onToggleHome = {
                    if (pages.any { app.key in it.keys }) stores.layout.removeFromHome(app.key, pages, preserve)
                    else stores.layout.addToHome(app.key, pages, gridState.currentPage, capacityOf, preserve)
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
                unavailable = g.unavailableReason(surface)?.let { L10n.tr("Won't fire on the current home: %s. The binding is kept for other styles.", L10n.tr(it)) },
            )
        }
        colorApp?.let { app ->
            val current = iconColors[app.key] ?: prefs.accentColor
            GlassDialog(
                title = app.label,
                onDismiss = { colorApp = null },
                footer = {
                    TextButtonPill(L10n.tr("Reset")) { stores.iconColors.set(app.key, null); colorApp = null }
                    TextButtonPill(L10n.tr("Done"), primary = true) { colorApp = null }
                },
            ) {
                ColorPicker(current, { v -> stores.iconColors.set(app.key, v) })
            }
        }
        if (fillPrompt) {
            ConfirmDialog(
                title = L10n.tr("Fill the empty space?"),
                text = L10n.tr("Your new density left gaps on some pages. Pull icons forward from the next pages to fill them?"),
                confirm = L10n.tr("Fill"),
                dismiss = L10n.tr("Keep"),
                onConfirm = { savePages(HomeLayoutMath.reflow(pages, capacityOf)); fillPrompt = false },
                onDismiss = { fillPrompt = false },
            )
        }
        pickFor?.let { id ->
            val suggested = remember(id) { ActionCandidates.packages(context, id) }
            com.bos.sphere.feature.grid.AppPickerDialog(
                title = L10n.tr("Open %s with", L10n.tr(id.label)),
                apps = apps, suggested = suggested,
                onPick = { app -> ActionPrefs.set(context, id, app.key); actionAppsRev++; pickFor = null; launch(app) },
                onDismiss = { pickFor = null },
                resetLabel = if (ActionPrefs.get(context, id) != null) L10n.tr("Ask me each time") else null,
                onReset = { ActionPrefs.set(context, id, null); actionAppsRev++; pickFor = null },
            )
        }
        if (quickOpen) {
            com.bos.sphere.ui.HomeQuickSheet(
                prefs = prefs, update = { f -> stores.prefs.update(f) },
                page = quickPage?.takeIf { prefs.homeStyle == HomeStyle.Grid && prefs.homeScroll == com.bos.sphere.core.data.HomeScroll.Paged },
                pageCount = pages.size,
                onAddPage = { val i = quickPage ?: 0; savePages(HomeLayoutMath.insertPage(pages, i)); quickOpen = false },
                onDeletePage = { val i = quickPage ?: 0; savePages(HomeLayoutMath.deletePage(pages, i, capacityOf)); quickOpen = false },
                onWallpaper = { quickOpen = false; openSettings(categories.indexOfFirst { it.title == L10n.tr("Look") }.coerceAtLeast(0)) },
                onGroundControl = { quickOpen = false; openSettings(settingsCategoryIndex(categories, CATEGORY_HOME)) },
                onDismiss = { quickOpen = false },
            )
        }
        if (!prefs.onboardingDone) {
            com.bos.sphere.ui.Onboarding(
                prefs = prefs,
                update = { f -> stores.prefs.update(f) },
                setDefaultHome = {
                    val i = DefaultLauncher.requestRoleIntent(context) ?: DefaultLauncher.homeSettingsIntent()
                    runCatching { context.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                },
                onDone = { stores.prefs.update { it.copy(onboardingDone = true) } },
            )
        }
        tileMenu?.let { key ->
            TileMenuDialog(
                title = if (key == HomeTiles.CLOCK) "Clock tile" else "Tile",
                onRemove = { savePages(HomeLayoutMath.remove(pages, key)); tileMenu = null },
                onDismiss = { tileMenu = null },
            )
        }
    }
    }
}

/** Keeps the keyboard above the system navigation bar. */
private fun Modifier.androidx_navPadding(): Modifier = this.then(Modifier.navigationBarsPadding())

private fun smooth(t: Float): Float = (t * t * (3 - 2 * t)).coerceIn(0f, 1f)

/** Full-screen host for the widget chosen as the left page; slides in from the left, swipe left or Back to close. */
@Composable
private fun LeftWidgetPage(
    visible: Boolean,
    host: com.bos.sphere.feature.grid.LauncherWidgetHost,
    appWidgetId: Int,
    onClose: () -> Unit,
) {
    if (visible) {
        BackHandler(onBack = onClose)
        var dragX by remember { mutableStateOf(0f) }
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black)
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = { if (dragX < -120f) onClose(); dragX = 0f },
                        onDragCancel = { dragX = 0f },
                    ) { _, d -> dragX += d }
                }
                .androidx_navPadding(),
        ) {
            com.bos.sphere.feature.grid.HostedWidget(host, appWidgetId, Modifier.fillMaxSize().safeDrawingPadding())
        }
    }
}

/** Opens the left page on a rightward swipe that starts while the first home page is settled. */
private fun Modifier.leftSwipe(enabled: Boolean, atFirstPage: () -> Boolean, onOpen: () -> Unit, edgeDp: Float = 0f): Modifier =
    if (!enabled) this else this.pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = androidx.compose.ui.input.pointer.PointerEventPass.Initial)
            if (!atFirstPage()) return@awaitEachGesture
            if (edgeDp > 0f && down.position.x > edgeDp * density) return@awaitEachGesture
            val slop = viewConfiguration.touchSlop
            var dx = 0f
            var dy = 0f
            while (true) {
                val ev = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                if (!ch.pressed) break
                val d = ch.positionChange()
                dx += d.x; dy += d.y
                if (dx > slop * 3 && dx > kotlin.math.abs(dy) * 2) {
                    ch.consume()
                    onOpen()
                    // Swallow the rest of this gesture so the pager doesn't also react.
                    while (true) {
                        val e2 = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                        e2.changes.forEach { it.consume() }
                        if (e2.changes.none { it.pressed }) break
                    }
                    break
                }
                if (kotlin.math.abs(dy) > slop * 3 && kotlin.math.abs(dy) > kotlin.math.abs(dx)) break
            }
        }
    }
