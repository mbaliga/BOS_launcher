package com.bos.sphere

import android.content.Intent
import com.bos.sphere.feature.grid.SettingsPreviewDock
import com.bos.sphere.core.data.sharesWallpaper
import com.bos.sphere.feature.grid.captureHome
import com.bos.sphere.feature.grid.captureWallpaper
import com.bos.sphere.feature.grid.homeGestures
import com.bos.sphere.ui.launchpadCloseDrag
import com.bos.sphere.ui.launchpadEdgeDrag
import com.bos.sphere.ui.launchpadShortcut
import com.bos.sphere.ui.launchpadMenuAside
import android.content.res.Configuration
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import com.bos.sphere.core.design.gate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.bos.sphere.ui.homeCardLayer
import com.bos.sphere.ui.LaunchpadCardOverlay
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.absolutePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.safeDrawing
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
import com.bos.sphere.core.data.HomeShown
import com.bos.sphere.core.data.HomeSurface
import com.bos.sphere.core.data.SurfaceScroll
import com.bos.sphere.core.data.HomeShownRules
import com.bos.sphere.core.data.MinimalHotspots
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import com.bos.sphere.feature.grid.editCardLayer
import kotlinx.coroutines.withContext
import com.bos.sphere.core.data.GridSpec
import com.bos.sphere.core.data.PageShape
import com.bos.sphere.core.data.IndicatorColors
import com.bos.sphere.core.data.IndicatorSource
import com.bos.sphere.core.data.wallpaper
import com.bos.sphere.core.data.PageMode
import com.bos.sphere.core.data.ThemeMode
import com.bos.sphere.core.data.UsageStats
import com.bos.sphere.core.design.HyleTheme
import com.bos.sphere.core.design.L10n
import com.bos.sphere.core.design.ThemeVariant
import com.bos.sphere.feature.grid.ColorPicker
import com.bos.sphere.feature.grid.ConfirmDialog
import com.bos.sphere.feature.grid.GlassDialog
import com.bos.sphere.feature.grid.IconTheme
import com.bos.sphere.feature.grid.IndicatorLayer
import com.bos.sphere.feature.grid.LocalIconTheme
import com.bos.sphere.feature.grid.SearchOverlay
import com.bos.sphere.feature.grid.TextButtonPill
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import com.bos.sphere.core.data.ChromeElement
import com.bos.sphere.feature.grid.glanceWakeOnTouch
import com.bos.sphere.core.data.HomeChromeRules
import androidx.compose.foundation.layout.offset
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
import com.bos.sphere.feature.dock.dockShortcuts
import com.bos.sphere.ui.DockLayer
import com.bos.sphere.feature.dock.trackLastTouch
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
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
import com.bos.sphere.core.data.Corner
import com.bos.sphere.core.data.CornerActionId
import com.bos.sphere.core.data.CornerItem
import com.bos.sphere.core.data.CornerSlot
import com.bos.sphere.core.data.HiddenAppsStore
import com.bos.sphere.core.data.HomeGesture
import com.bos.sphere.core.data.HomeLayoutMath
import com.bos.sphere.core.data.HomeStyle
import com.bos.sphere.core.data.HomeTiles
import com.bos.sphere.core.data.LauncherAppsAppRepository
import com.bos.sphere.core.data.LauncherStores
import com.bos.sphere.core.data.MotionGestureSensors
import com.bos.sphere.core.data.PageLayout
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
import com.bos.sphere.feature.grid.UiSounds
import com.bos.sphere.feature.grid.WheelCategoryCache
import com.bos.sphere.feature.grid.WheelDrawer
import com.bos.sphere.feature.grid.cornerPlacements
import com.bos.sphere.feature.grid.toCorner
import com.bos.sphere.feature.grid.slotSummary
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

/** The focus layer Launchpad sits on while open: above the home (0), the drawer (2) and the puck (3). */
private const val LAUNCHPAD_FOCUS_LAYER = 4

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
    // NFR-01 / NFR-02: interactive home = the first frame drawn once the app list is in; tells the benchmarks (time to full display).
    LaunchedEffect(apps.isNotEmpty()) { if (apps.isNotEmpty()) HomeTiming.homeDrawn(context) }
    val appsByKey = remember(apps) { apps.associateBy { it.key } }
    // Package changes can change icons and categories; uninstalled apps forget "removed from home".
    val liveReconcile = remember { com.bos.sphere.core.catalog.LiveReconcile() }
    LaunchedEffect(allApps) {
        // HOM-10: only the icons of updated or removed apps are dropped; the surface follows the snapshot at once.
        val diff = liveReconcile.onSnapshot(allApps)
        if (allApps.isEmpty()) IconCache.clear() else IconCache.evict(diff.iconKeysToEvict())
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

    val storedPrefs by stores.prefs.prefs.collectAsStateWithLifecycle()
    // HOM-11: with the adaptive grid on, the grids follow the window size class and the density setting.
    val windowCfg = LocalConfiguration.current
    val prefs = remember(storedPrefs, windowCfg.screenWidthDp, windowCfg.screenHeightDp) {
        com.bos.sphere.core.catalog.AdaptivePrefs.effective(storedPrefs, windowCfg.screenWidthDp, windowCfg.screenHeightDp)
    }
    val layout by stores.layout.layout.collectAsStateWithLifecycle()
    var layoutEpoch by remember { mutableIntStateOf(0) } // wave 30: bumped by the one-time reset of an auto-filled home so every size class starts over
    // HOM-22: the live Recent apps (only collected while that landing is on) and whether usage access is granted.
    var menuRecents by remember { mutableStateOf(false) } // the open app menu was opened from the Recent apps landing
    val homeEdit = rememberHomeEdit(stores.layout, remember(context) { WorkspaceHomeDockPins(com.bos.sphere.feature.dock.WorkspaceRepository.get(context)) }) // HOM-12 (layout and dock pins share one undo)
    SizeClassLayoutBinding(stores.layout, remember(allApps) { allApps.mapTo(HashSet()) { it.key } }, epoch = layoutEpoch, onRebased = { homeEdit.rebase() }) // HOM-11: one arrangement per window size class; HOM-12: a swap is not an undoable edit
    var hubStandalone by remember { mutableStateOf(false) } // wave 25: the hub shown by itself when the left page is not Launchpad
    val portraitCorners by stores.shortcuts.corners.collectAsStateWithLifecycle()
    val landscapeCorners by stores.shortcuts.landscapeCorners.collectAsStateWithLifecycle()
    val gestures by stores.shortcuts.gestures.collectAsStateWithLifecycle()
    val history by stores.history.events.collectAsStateWithLifecycle()
    /** HOM-18: the timeline reads the larger log (up to 5000 opens), not the 400-entry history. */
    val timelineLog by stores.timelineLog.events.collectAsStateWithLifecycle()

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    // INP-15: one set of corner assignments, or a separate landscape set; every corner style draws whichever is active.
    val corners = com.bos.sphere.core.data.CornerSets.select(portraitCorners, landscapeCorners, prefs.cornersPerOrientation, isLandscape)
    // Wave 26: the home is Glance followed by the pages. Which of the two is on screen is runtime state (not a setting): Glance on start when it is on,
    // else the pages; a forward swipe from Glance goes to page 1, a backward swipe at the first page comes back.
    // Wave 28 (homeUnify): the home is ONE pager; its stops are Glance (when on) and the pages stop (the pages, or the scroll, when Pages is on; the first page when Glance is off).
    // `landing` is derived from where the pager is, never stored: swiping, the page dots and the Home button all move the same pager.
    val homePagerCount = com.bos.sphere.core.data.HomeDeck.outerCount(prefs.glanceEnabled, prefs.homePages)
    val homePager = androidx.compose.foundation.pager.rememberPagerState(0) { homePagerCount }
    val landing = com.bos.sphere.core.data.HomeDeck.surfaceAt(prefs.glanceEnabled, homePager.currentPage)
    val pagerMotion = com.bos.sphere.core.design.rememberMotionPrefs()
    suspend fun goToStop(index: Int) { val i = index.coerceIn(0, (homePagerCount - 1).coerceAtLeast(0)); if (pagerMotion.reduced) homePager.scrollToPage(i) else homePager.animateScrollToPage(i) }
    fun showStop(index: Int) { scope.launch { goToStop(index) } }
    fun showSurface(s: HomeShown) = showStop(if (s == HomeShown.Glance && prefs.glanceEnabled) 0 else com.bos.sphere.core.data.HomeDeck.pagesIndex(prefs.glanceEnabled, prefs.homePages) ?: 0)
    var indicatorAwake by remember { mutableIntStateOf(0) }
    // Turning Glance on (from Ground Control or the quick sheet) while on the pages keeps the user on the pages: the stops shift by one.
    var glanceWas by remember { mutableStateOf(prefs.glanceEnabled) }
    LaunchedEffect(prefs.glanceEnabled) { if (prefs.glanceEnabled != glanceWas) { glanceWas = prefs.glanceEnabled; if (prefs.glanceEnabled && homePager.pageCount > 1) homePager.scrollToPage(1) } }
    val homeOrder = prefs.homeOrder
    // HOM-23 (wave 16): the Glance home uses the corner assignments as they are (the factory set is the owner's reference: camera, music, apps, search).
    val activeCorners = corners
    val capacityOf: (PageLayout) -> Int = { it.capacity(isLandscape, prefs) }
    // HOM-01 / HOM-02: folders, shortcuts, links and widget tiles share the pages with apps on every home style.
    val extrasStore = remember { com.bos.sphere.core.catalog.HomeExtrasStore.get(context) }
    val extrasState by extrasStore.state.collectAsStateWithLifecycle()
    val allPages = remember(layout, apps, isLandscape, prefs, extrasState) {
        com.bos.sphere.core.catalog.SpanReflow.reflow(
            com.bos.sphere.core.catalog.HomeResolve.visual(layout, apps.map { it.key }, extrasState, capacityOf),
            extrasState::spanOf,
        ) { l -> l.gridFor(isLandscape, prefs).let { it.columns to it.rows } }
    }
    // HOM-01: a fresh install starts with ONE EMPTY page and no apps placed (they live behind the add carousel, the drawer, Radar and
    // search). The decision is the absence of a saved arrangement; an existing user's layout is never touched. The old three-app seed
    // below is then marked done so it cannot fill the page.
    var freshDecided by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val misc = context.getSharedPreferences("liftoff_misc", android.content.Context.MODE_PRIVATE)
        val legacy = withContext(kotlinx.coroutines.Dispatchers.IO) { misc.getBoolean("defaultHomeSeeded", false) }
        if (com.bos.sphere.core.data.FreshHome.isFresh(stores.layout.layout.value, stores.prefs.prefs.value.onboardingDone, legacy)) {
            stores.layout.replace(com.bos.sphere.core.data.FreshHome.emptyLayout())
            stores.prefs.update { com.bos.sphere.core.data.FreshHome.prefs(it) }
            misc.edit().putBoolean("defaultHomeSeeded", true).apply()
        }
        freshDecided = true
    }
    // Wave 30 (pagesExplicit): nothing is placed for the user, not even three starter apps: the old "Settings, Phone and Messages are put there once" seed is gone.
    // One-time migration of an auto-filled legacy home (see PagesExplicitMigration): ONE empty page and the old arrangement kept for "Restore previous pages".
    var resetNotice by remember { mutableStateOf(stores.layout.noticePending()) }
    var legacyAvailable by remember { mutableStateOf(false) }
    var confirmRestore by remember { mutableStateOf(false) }
    LaunchedEffect(layoutEpoch) {
        legacyAvailable = withContext(kotlinx.coroutines.Dispatchers.IO) { com.bos.sphere.core.data.LegacyHomeBackup(context.filesDir).available(System.currentTimeMillis()) != null }
        // The pending flag can arrive with a backup or transfer without the (excluded) backup file: then there is nothing to offer.
        if (resetNotice && !legacyAvailable) { resetNotice = false; stores.layout.setNoticePending(false) }
    }
    fun restorePreviousPages() {
        scope.launch {
            val ok = withContext(kotlinx.coroutines.Dispatchers.IO) {
                com.bos.sphere.core.data.PagesExplicitMigration.restore(stores.layout, com.bos.sphere.core.data.LegacyHomeBackup(context.filesDir), System.currentTimeMillis())
            }
            if (ok) withContext(kotlinx.coroutines.Dispatchers.Main) { layoutEpoch++; resetNotice = false; android.widget.Toast.makeText(context, L10n.tr("Previous pages restored"), android.widget.Toast.LENGTH_SHORT).show() }
        }
    }
    LaunchedEffect(freshDecided, apps.isNotEmpty()) {
        if (!freshDecided || apps.isEmpty()) return@LaunchedEffect
        val installedOrders = listOf(apps.map { it.key }, apps.sortedBy { it.label.lowercase() }.map { it.key })
        val result = withContext(kotlinx.coroutines.Dispatchers.IO) {
            com.bos.sphere.core.data.PagesExplicitMigration.run(
                stores.layout, com.bos.sphere.core.data.LegacyHomeBackup(context.filesDir), installedOrders, capacityOf,
                legacyEverything = storedPrefs.appsOnHome == com.bos.sphere.core.data.AppsOnHome.Everything, nowMillis = System.currentTimeMillis(),
            )
        }
        if (storedPrefs.appsOnHome != com.bos.sphere.core.data.AppsOnHome.PinnedOnly) stores.prefs.update { it.copy(appsOnHome = com.bos.sphere.core.data.AppsOnHome.PinnedOnly) }
        if (result == com.bos.sphere.core.data.PagesMigrationResult.Reset) { layoutEpoch++; resetNotice = true }
    }
    // "One page" shows only the first page; the rest of the apps stay in the drawer.
    // Wave 28: Pages Off shows the first page only (Glance off: that page is home); the other pages stay stored and come back when Pages is turned on.
    val pages = remember(allPages, prefs.pageMode, prefs.homePages) { allPages.take(if (prefs.pageMode == PageMode.Single) 1 else com.bos.sphere.core.data.HomeDeck.visiblePages(prefs.homePages, allPages.size)) } // wave 28: one rule, HomeDeck.visiblePages
    // Stored keys that can't be drawn right now (hidden apps, a paused work profile, pages hidden by
    // "one page"): every save keeps them.
    val preserve = remember(layout, apps, allPages, pages, extrasState) {
        com.bos.sphere.core.catalog.HomeResolve.preserve(
            layout.pages.flatMapTo(HashSet()) { it.keys } - appsByKey.keys - HomeTiles.ALL.toSet() +
                allPages.drop(pages.size).flatMap { it.keys },
            extrasState,
            allPages.drop(pages.size).flatMapTo(HashSet()) { it.keys },
        )
    }
    fun savePages(visual: List<com.bos.sphere.core.data.HomePage>) = stores.layout.setPages(visual, preserve)

    // ---- overlay state
    var settingsOpen by remember { mutableStateOf(false) }
    var settingsCategory by remember { mutableIntStateOf(CATEGORY_HOME) }
    var settingsPage by remember { mutableIntStateOf(0) }
    var settingsSession by remember { mutableIntStateOf(0) }
    // NFR-11: opt-in restore of the surface that was open before a restart (off by default; never in safe mode).
    val openSurfaces = remember { com.bos.sphere.core.data.OpenSurfaceStore.get(context) }
    val restoredSurface = remember { openSurfaces.takeRestore(prefs.restoreOpenSurfaces, com.bos.sphere.core.data.CrashLoopGuard.safeMode) }
    var searchOpen by remember { mutableStateOf(restoredSurface == com.bos.sphere.core.data.OpenSurface.Search) }
    var wheelOpen by remember { mutableStateOf(restoredSurface == com.bos.sphere.core.data.OpenSurface.Drawer) } // wave 38: the app drawer is the disc, a restored "drawer" reopens it
    LaunchedEffect(wheelOpen, searchOpen) {
        openSurfaces.record(if (wheelOpen) com.bos.sphere.core.data.OpenSurface.Drawer else if (searchOpen) com.bos.sphere.core.data.OpenSurface.Search else null)
    }
    var colorApp by remember { mutableStateOf<AppEntry?>(null) }
    var fillPrompt by remember { mutableStateOf(false) }
    var wheelAnchor by remember { mutableStateOf(Corner.BottomLeft) }
    var hubOpen by remember { mutableStateOf(false) }
    var leftOpen by rememberCommsOpen(homeSignal) // wave 33: saved with the activity state, so Comms (which stays open under an opened app) survives the process being killed; a Home press closes it
    // STR-14: the Recents panel, the right-hand mirror of Launchpad. Warm once opened so the list is ready on the next swipe.
    var rightOpen by remember { mutableStateOf(false) }
    var recentsWarm by remember { mutableStateOf(false) }
    if (rightOpen) recentsWarm = true
    val recentRows by rememberRecentRows(context, stores, apps, hidden, storedPrefs, active = storedPrefs.rightSide == com.bos.sphere.core.data.RightSide.RecentsPanel && recentsWarm)
    val recentsUsageOn by rememberUsageAccess(context, rightOpen)
    var menuApp by remember { mutableStateOf<AppEntry?>(null) }
    var editCorner by remember { mutableStateOf<Corner?>(null) }
    var editGesture by remember { mutableStateOf<HomeGesture?>(null) }
    val editSelection = remember { com.bos.sphere.feature.grid.EditSelectionState().also { it.fallbackBar = false } } // W47: THE selection of edit mode; the item action bar exists, so the 48 dp fallback Remove button is off
    val itemEdit = rememberItemEditState(editSelection) // wave 45 (editSheet): the selected item's action bar and sheet replace the old full-screen tile menu
    var expandedWidget by remember { mutableStateOf<Corner?>(null) }

    val gridState = rememberGridHomeState()
    // HOM-12: while editing, the home is a card pushed to the start side; the page carousel and the add carousel fill the background.
    // W45 (editChrome): edit mode is the single-page home (the page scaled to 0.88 with the top bar and the tray); the page editor (strip, tabs) is a step inside it, opened from the title or by the empty home.
    var pageEditorOn by remember { mutableStateOf(false) }
    LaunchedEffect(homeEdit.active) { if (!homeEdit.active) pageEditorOn = false }
    val editCard = rememberEditCardState(homeEdit.active && pageEditorOn, continuous = prefs.homePages == com.bos.sphere.core.data.HomePages.Continuous, orderRtl = prefs.homeOrder.rtl) // wave 36: the page editor's strip follows the Pages mode and the page order
    var editFocusAdd by remember { mutableStateOf(false) }
    LaunchedEffect(homeEdit.active) { if (!homeEdit.active) editFocusAdd = false }
    val chromeProgress = com.bos.sphere.feature.grid.rememberEditChromeProgress(homeEdit.active)
    var homeSizePx by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) } // the home's real size (the scaled page's rectangle is derived from it)
    val chromePage = com.bos.sphere.feature.grid.rememberEditChromePage(homeSizePx)
    val editChromeLive by remember { androidx.compose.runtime.derivedStateOf { chromeProgress.value > 0.001f } } // flips twice per edit: not a per-frame recomposition
    val chromeMotion = com.bos.sphere.core.design.rememberMotionPrefs()
    val chromeState = remember { com.bos.sphere.feature.grid.HomeEditChromeState() }
    LaunchedEffect(homeEdit.active) { if (!homeEdit.active) chromeState.dismissConfirm() }
    val editGrid = remember { com.bos.sphere.feature.grid.EditGridState() }
    val editHintStore = remember(context) { com.bos.sphere.core.data.EditChromeStore(com.bos.sphere.core.data.TolerantPrefs.of(context.applicationContext, com.bos.sphere.core.data.EditChromeStore.FILE)) }
    var showEditHint by remember { mutableStateOf(false) }
    LaunchedEffect(homeEdit.active) { showEditHint = if (homeEdit.active) editHintStore.registerEntry() else false } // the counter is written on entry (a handler), never in composition
    LaunchedEffect(pageEditorOn) { if (pageEditorOn) showEditHint = false }
    val sounds = remember { UiSounds() }
    DisposableEffect(Unit) { onDispose { sounds.release() } }
    sounds.enabled = prefs.uiSounds
    sounds.volume = prefs.uiSoundVolume

    // Launch control and dock (WSP-01, WSP-06, DCK): every launch goes through the launch layer.
    val rootView = androidx.compose.ui.platform.LocalView.current
    val appLauncher = remember { com.bos.sphere.feature.dock.AppLauncher(context, rootView = { rootView }) }
    val lastTouch = remember { com.bos.sphere.ui.homeLastTouch { rootView } }
    val dockController = remember { com.bos.sphere.feature.dock.DockController() }
    val shortcutRegistry = remember { com.bos.sphere.feature.grid.LauncherInput.registry }
    val puckSlots by remember { com.bos.sphere.core.data.PuckActionsStore.get(context).slots }.collectAsStateWithLifecycle()
    val puckSegments by remember { com.bos.sphere.core.data.PuckActionsStore.get(context).segments }.collectAsStateWithLifecycle()
    val puckLabels by remember { com.bos.sphere.core.data.PuckActionsStore.get(context).showLabels }.collectAsStateWithLifecycle()
    val torchOk = remember(wheelOpen) { !wheelOpen || com.bos.sphere.core.data.Torch.available(context) } // asked only while the disc is open
    val dndOk = remember(wheelOpen) { !wheelOpen || com.bos.sphere.core.data.PuckAvailability.policyAccess(context) }
    /** Wave 45 (editSheet): a long press on a home item selects it (action bar + item sheet) while editing; [enterEdit] lets a press outside edit mode start editing first. False = not handled (the old menu opens). */
    fun selectItem(key: String, enterEdit: Boolean = false): Boolean {
        if (!homeEdit.active) { if (!enterEdit) return false; homeEdit.enter() }
        itemEdit.select(key, lastTouch.sourceRect()?.let { androidx.compose.ui.geometry.Rect(it.left.toFloat(), it.top.toFloat(), it.right.toFloat(), it.bottom.toFloat()) })
        return true
    }
    fun launch(app: AppEntry) {
        com.bos.sphere.feature.dock.SessionUse.mark(app.key)
        appLauncher.launch(app, lastTouch.sourceRect())
        stores.history.record(app.key)
        wheelOpen = false
        searchOpen = false
    }

    var quickPage by remember { mutableStateOf<Int?>(null) }
    var quickOpen by remember { mutableStateOf(false) }
    var trayRequest by remember { mutableStateOf(com.bos.sphere.feature.grid.TrayPanel.None) }
    fun openSettings(category: Int, page: Int = gridState.currentPage) {
        settingsPage = page.coerceIn(0, (pages.size - 1).coerceAtLeast(0))
        settingsCategory = category
        settingsSession++
        settingsOpen = true
    }

    // Wave 28 (HOM-01): "Add page" belongs to Paginated mode (the edit card, the quick sheet): there is no "Scroll or Paginated" question any more, the Pages
    // setting is the choice. From Glance the page goes after the last one; with Pages Off or Continuous it first turns Paginated on.
    var pendingPages by remember { mutableIntStateOf(0) } // 1 = show the pages stop once it exists, 2 = show it in edit mode (see turnPagesOn)
    fun addPageNow(afterIndex: Int?) {
        val wasOn = prefs.homePages == com.bos.sphere.core.data.HomePages.Paginated
        if (!wasOn) stores.prefs.update { it.copy(homePages = com.bos.sphere.core.data.HomePages.Paginated) }
        val added = com.bos.sphere.core.data.HomeDeck.addPage(pages, afterIndex, pagesAlreadyOn = wasOn)
        if (added.pages !== pages) savePages(added.pages)
        pendingPages = 1
        settingsPage = added.index
        scope.launch {
            withTimeoutOrNull(1_000) { snapshotFlow { gridState.pager.pageCount }.first { it > added.index } }
            gridState.goToPage(added.index, animate = false)
        }
    }
    fun requestAddPage(afterIndex: Int?) = addPageNow(afterIndex)
    // Edit mode lives on the first page: with Pages Off the setting is turned on first (Paginated) and the user is told so in one line. The same goes for
    // anything that must be SEEN on the pages (an item placed from Glance): pending 1 = show the pages, 2 = show them in edit mode.
    fun turnPagesOn(then: Int) {
        stores.prefs.update { it.copy(homePages = com.bos.sphere.core.data.HomePages.Paginated) }
        android.widget.Toast.makeText(context, L10n.tr("Pages are now on (Paginated). Pick apps and widgets to add to the first page."), android.widget.Toast.LENGTH_LONG).show()
        pendingPages = then
    }
    fun editHome() {
        if (com.bos.sphere.core.data.HomeDeck.pagesIndex(prefs.glanceEnabled, prefs.homePages) == null) turnPagesOn(2)
        else { homeEdit.enter(); showSurface(HomeShown.Pages) } // edit mode ends only when the pager has SETTLED on Glance (see the effect below), so it can start while the pager moves
    }
    LaunchedEffect(pendingPages, homePagerCount) {
        val idx = com.bos.sphere.core.data.HomeDeck.pagesIndex(prefs.glanceEnabled, prefs.homePages)
        if (pendingPages != 0 && idx != null) { val edit = pendingPages == 2; pendingPages = 0; if (edit) homeEdit.enter(); homePager.scrollToPage(idx) }
    }
    fun showDot(dot: Int) {
        val (stop, page) = com.bos.sphere.core.data.HomeDeck.target(prefs.glanceEnabled, prefs.homePages, dot)
        scope.launch { if (stop != homePager.currentPage) goToStop(stop); if (page != null) gridState.goToPage(page, animate = !pagerMotion.reduced) }
    }

    val rotation = rememberDisplayRotation()
    val placements = remember(activeCorners, rotation, prefs.cornersFollowDevice) {
        cornerPlacements(activeCorners, rotation, prefs.cornersFollowDevice)
    }

    // HOM-23: the screen corners whose hotspot is on (the physical corner under each decides; a disabled hotspot draws nothing and does nothing).
    val glanceOn = remember(prefs.glanceHotspots, rotation, prefs.cornersFollowDevice) { com.bos.sphere.core.data.GlanceHotspots.enabledOnScreen(prefs.glanceHotspots, rotation, prefs.cornersFollowDevice) }
    val hotspotPlacements = remember(placements, glanceOn) { placements.filter { it.screen in glanceOn } } // wave 38: the Hotspots chips pick which corners draw, on every page

    fun openAllApps(anchor: Corner?) {
        // Wave 38: the app drawer is always the disc.
        wheelAnchor = anchor
            ?: placements.firstOrNull { p ->
                val s = p.slot
                s is CornerSlot.Single && (s.item as? CornerItem.Action)?.id == CornerActionId.AllApps
            }?.screen
            ?: Corner.BottomLeft
        wheelOpen = true
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
        if (storedPrefs.adaptiveGrid) {
            // HOM-11: the grid follows the window, so a pinch moves the density setting instead of the fixed grids.
            val next = com.bos.sphere.core.catalog.AdaptivePrefs.stepDensity(storedPrefs.gridDensity, delta, windowCfg.screenWidthDp, windowCfg.screenHeightDp)
            if (next != storedPrefs.gridDensity) {
                stores.prefs.update { it.copy(gridDensity = next) }
                android.widget.Toast.makeText(context, L10n.tr("Grid density %d%%", Math.round(next * 100)), android.widget.Toast.LENGTH_SHORT).show()
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
                val vis = HomeLayoutMath.visualPages(layout, apps.map { it.key }, capNew)
                savePages(HomeLayoutMath.reflow(vis, capNew))
            }
            android.widget.Toast.makeText(context, "${ns.columns} × ${ns.rows}", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    // INP-05, STR-14 (wave 24): "Focus the dock" (Ctrl+H) and "Open recents" with Right side = Dock rail. Whatever hides the rail
    // (DockLayer's visible flags, the Recents panel included) is closed first, unless no rail can appear: then nothing closes and a toast says why.
    fun focusDockFromHome(c: com.bos.sphere.core.input.LauncherCommand): Boolean = com.bos.sphere.ui.FocusDockRouting.focusDock(
        c, overlayOpen = searchOpen || wheelOpen || leftOpen || rightOpen || settingsOpen,
        closeOverlays = { searchOpen = false; wheelOpen = false; leftOpen = false; rightOpen = false; settingsOpen = false },
        dock = dockController::handle,
        noDock = com.bos.sphere.ui.FocusDockRouting.noDock(
            landing, com.bos.sphere.core.data.ChromeSettings.of(prefs),
            com.bos.sphere.feature.dock.WorkspaceRepository.get(context).current.dock.isEnabled(com.bos.sphere.core.capabilities.CapabilityEvaluator.tier(appLauncher.inputs())),
            editing = false, // W45: the rail stays (dimmed) while the single-page home is edited
        ),
        onNoDock = { why -> android.widget.Toast.makeText(context, L10n.tr(why.message), android.widget.Toast.LENGTH_SHORT).show() },
    )

    // STR-14: "Open recents" (edge swipe, handle, shortcut, corner or gesture). Recents panel: opens the right-hand card; Dock rail: reveals the
    // existing dock rail; Off: says so (never nothing).
    fun openRecents(): Boolean {
        return when (prefs.rightSide) {
            com.bos.sphere.core.data.RightSide.RecentsPanel -> { leftOpen = false; rightOpen = true; true }
            // Wave 24: the same route as Ctrl+H, so what hides the rail closes first (the drawer, Radar...) and, with no rail to come, a toast says why.
            com.bos.sphere.core.data.RightSide.DockRail -> { focusDockFromHome(com.bos.sphere.core.input.LauncherCommand.FocusDock); true }
            com.bos.sphere.core.data.RightSide.Off -> {
                android.widget.Toast.makeText(context, L10n.tr("The right side is off. Turn it on in Ground Control, Home screen, Right side."), android.widget.Toast.LENGTH_SHORT).show()
                false
            }
        }
    }

    fun internal(id: CornerActionId, anchor: Corner?) {
        when (id) {
            CornerActionId.GridDenser -> changeDensity(+1)
            CornerActionId.GridSparser -> changeDensity(-1)
            CornerActionId.Search -> { wheelOpen = false; searchOpen = true }
            CornerActionId.AllApps -> openAllApps(anchor)
            CornerActionId.Notifications -> if (prefs.leftPage != com.bos.sphere.core.data.LeftPage.Off) leftOpen = true else hubOpen = true
            CornerActionId.LauncherSettings -> openSettings(com.bos.sphere.ui.CATEGORY_ROOT)
            CornerActionId.OpenLaunchpad -> if (prefs.leftPage != com.bos.sphere.core.data.LeftPage.Off) leftOpen = true else hubOpen = true // Launchpad off: the Minimal top-right hotspot (and a corner) opens the notification hub like "Notifications" does, never nothing
            CornerActionId.OpenRecents -> openRecents()
            else -> Unit
        }
    }

    val internalRef by rememberUpdatedState(::internal)
    // PAL-01: a printable key on the home opens Radar with that character typed.
    var typedQuery by remember { mutableStateOf("") }
    var radarScope by remember { mutableStateOf<com.bos.sphere.core.data.SearchCategory?>(null) } // wave 25: the scope Radar opens on (Launchpad's compose button asks for People)
    androidx.compose.runtime.DisposableEffect(Unit) {
        val hook: (android.view.KeyEvent) -> Boolean = { e ->
            val entry = com.bos.sphere.feature.search.palette.TypeToSearch.onKey(
                e.keyCode, e.unicodeChar, e.isCtrlPressed, e.isAltPressed, e.isShiftPressed, e.isMetaPressed, com.bos.sphere.feature.grid.LauncherInput.registry,
            )
            if (entry is com.bos.sphere.feature.search.palette.PaletteEntry.Open) { typedQuery = entry.initial; internalRef(CornerActionId.Search, null); true } else false
        }
        com.bos.sphere.feature.grid.LauncherInput.typeHook = hook
        onDispose { if (com.bos.sphere.feature.grid.LauncherInput.typeHook === hook) com.bos.sphere.feature.grid.LauncherInput.typeHook = null }
    }
    LaunchedEffect(searchOpen) { if (!searchOpen) { typedQuery = ""; radarScope = null } }
    var pickFor by remember { mutableStateOf<CornerActionId?>(null) }
    var actionAppsRev by remember { mutableIntStateOf(0) }
    val pickAppForRef by rememberUpdatedState({ id: CornerActionId -> pickFor = id })
    val runner = remember {
        ActionRunner(context, onInternal = { internalRef(it, null) }, onAppLaunched = { stores.history.record(it) }, onPickApp = { pickAppForRef(it) }, translate = { com.bos.sphere.core.design.L10n.tr(it) })
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
    val gestureKeysRef = rememberUpdatedState(gestures.keys)
    val openSettingsRef = rememberUpdatedState<(Int) -> Unit> { c -> openSettings(c) }
    // HOM-22: outside Selected apps a long press on empty space opens the quick sheet (where the landing is changed), never edit mode.
    val landingRef = rememberUpdatedState(landing)
    val prefsRef = rememberUpdatedState(prefs)
    val turnPagesOnRef = rememberUpdatedState<(Int) -> Unit> { turnPagesOn(it) }
    val homeGestureSpec = remember(surface, gestures.keys) {
        HomeGestureSpec(surface, gestures.keys, onGesture = { g -> gestureRefHolder.value(g) }, onEmptyLongPress = {
            if (HomeGesture.LongPress in gestureKeysRef.value) gestureRefHolder.value(HomeGesture.LongPress)
            else if (HomeShownRules.longPress(landingRef.value) == HomeShownRules.LongPress.QuickSheet) { quickPage = null; quickOpen = true }
            else openSettingsRef.value(CATEGORY_HOME)
        })
    }
    // Wave 28: Glance is a stop of the horizontal pager, so its gestures use the paged surface: a sideways swipe is the pager's, only up / down swipes are free for gestures.
    var glanceClockEditing by remember { mutableStateOf(false) } // wave 50 (glanceClockEdit): the Glance clock is being edited in place: the pager, the dots and the fixed chrome stand still
    val glanceGestureSpec = remember(homeGestureSpec) {
        HomeGestureSpec(HomeSurface(SurfaceScroll.Horizontal, paged = true, hasDensity = false, leftPageOn = false), homeGestureSpec.bound, homeGestureSpec.onGesture, homeGestureSpec.onEmptyLongPress)
    }
    // HOM-22: edit mode is only for Selected apps; leaving that landing ends it.
    LaunchedEffect(landing, homePager.isScrollInProgress) { if (!HomeShownRules.allowsEditMode(landing) && !homePager.isScrollInProgress && homeEdit.active) homeEdit.exit() }

    // INP-11: an app dragged out of the drawer with a mouse and dropped on the home is added to the current page.
    // ---- keyboard, pointer and controller (INP-04, INP-05, INP-16, HOM-21): shortcuts go to the same openers the touch paths use
    com.bos.sphere.feature.grid.LauncherInputWiring(
        overlayOpen = settingsOpen || searchOpen || hubOpen || hubStandalone || leftOpen || rightOpen || menuApp != null || quickOpen || editCorner != null || editGesture != null,
        onCommand = { c ->
            when (c) {
                com.bos.sphere.core.input.LauncherCommand.OpenPalette -> { internal(CornerActionId.Search, null); true }
                com.bos.sphere.core.input.LauncherCommand.OpenDrawer -> { wheelAnchor = Corner.BottomLeft; wheelOpen = true; true } // wave 38: the drawer is the disc
                com.bos.sphere.core.input.LauncherCommand.OpenPuck -> { wheelAnchor = Corner.BottomLeft; wheelOpen = true; true }
                com.bos.sphere.core.input.LauncherCommand.OpenSettings -> { openSettings(com.bos.sphere.ui.CATEGORY_ROOT); true }
                com.bos.sphere.core.input.LauncherCommand.OpenLaunchpad -> if (prefs.leftPage != com.bos.sphere.core.data.LeftPage.Off) { leftOpen = true; true } else false
                com.bos.sphere.core.input.LauncherCommand.OpenRecents -> openRecents()
                com.bos.sphere.core.input.LauncherCommand.Dismiss -> if (rightOpen) { rightOpen = false; true } else dockController.handle(c)
                // Workspace switching and the dock's focus and number shortcuts belong to the dock controller (DCK-05, WSP-07).
                // INP-05: Dock 1 to 9 and the workspace step act with Radar, the drawer, Ground Control or the puck open (the controller
                // falls back to the pin list when no rail is shown); "focus dock" closes whatever hides the rail so the rail can take focus.
                com.bos.sphere.core.input.LauncherCommand.FocusDock -> focusDockFromHome(c)
                else -> dockController.handle(c)
            }
        },
        onEmptyMenu = { quickPage = gridState.currentPage; quickOpen = true },
        surfaceOpen = searchOpen,
        raiseKeyboard = prefs.keyboardOnOpen(isLandscape),
        hotCornerDwellMs = if (prefs.hotCornersOn) prefs.hotCornerDwellMs.toLong() else 0L,
        cornerLabel = { hot ->
            val slot = placements.firstOrNull { it.screen == hot.toCorner() }?.slot
            slot?.takeIf { hot.toCorner() in glanceOn }?.let(MinimalHotspots::itemOf)?.let { com.bos.sphere.feature.grid.itemLabel(it, appsByKey) } // W38: the hotspots are the same on every page
        },
        onCornerActivate = { hot ->
            placements.firstOrNull { it.screen == hot.toCorner() }?.let { p ->
                if (p.screen in glanceOn) MinimalHotspots.itemOf(p.slot)?.let { runItem(it, p.screen) }
            }
        },
    )

    // ---- widgets
    val widgetHost = rememberLauncherWidgetHost()
    var widgetFor by remember { mutableStateOf<Corner?>(null) }

    fun setCorner(c: Corner, slot: CornerSlot) {
        val old = corners[c]
        if (old is CornerSlot.Widget && (slot !is CornerSlot.Widget || slot.appWidgetId != old.appWidgetId)) {
            widgetHost.deleteWidget(old.appWidgetId)
        }
        if (com.bos.sphere.core.data.CornerSets.writesLandscape(prefs.cornersPerOrientation, isLandscape)) stores.shortcuts.setLandscapeCorner(c, slot) else stores.shortcuts.setCorner(c, slot)
    }
    androidx.compose.runtime.SideEffect { com.bos.sphere.feature.search.palette.PaletteWorkspaces.names = { com.bos.sphere.feature.dock.WorkspaceRepository.get(context).names() } }
    var pickingLeft by remember { mutableStateOf(false) }
    val homeItems = rememberHomeItems(
        store = extrasStore, pages = pages, savePages = ::savePages, capacityOf = capacityOf, currentPage = { gridState.currentPage },
        host = widgetHost, allApps = allApps, appsByKey = appsByKey, launchApp = ::launch, accentArgb = prefs.accentColor.toInt(),
        gridDims = { pages.getOrNull(gridState.currentPage)?.layout?.gridFor(isLandscape, prefs)?.let { it.columns to it.rows } ?: (prefs.gridFor(isLandscape).let { it.columns to it.rows }) },
        selection = editSelection,
    )
    androidx.compose.runtime.SideEffect {
        homeEdit.hooks = homeItems
        // W48: Cancel also puts the clock tile's settings back (the widget/tile options that live in the prefs).
        homeEdit.configSnapshot = {
            val c = stores.prefs.prefs.value
            fun same(p: com.bos.sphere.core.data.LauncherPrefs) = p.clockTileAnalog == c.clockTileAnalog && p.clockTileFormat == c.clockTileFormat && p.clockTileDate == c.clockTileDate
            EditConfigEntry(
                restore = { if (!same(stores.prefs.prefs.value)) stores.prefs.update { it.copy(clockTileAnalog = c.clockTileAnalog, clockTileFormat = c.clockTileFormat, clockTileDate = c.clockTileDate) } },
                changed = { !same(stores.prefs.prefs.value) },
            )
        }
    }
    // Wave 30 (pagesExplicit): "Delete page" really removes the page and what the user put on it. A page that holds something asks first
    // ("N items will be removed from this page; they stay in the drawer"); the last page is emptied (Pages stays on with one empty page).
    var pendingDelete by remember { mutableStateOf<Int?>(null) }
    fun requestDeletePage(i: Int) {
        if (com.bos.sphere.core.data.HomePageOps.itemCount(pages, i) == 0) deletePageNow(i, pages, layout, preserve, appsByKey, homeItems, stores) else pendingDelete = i
    }
    com.bos.sphere.feature.grid.HomeDropBinding(onApp = { key ->
        if (appsByKey[key] != null && pages.none { key in it.keys }) { if (stores.layout.addToHome(key, pages, gridState.currentPage, capacityOf, preserve)) homeItems.onPlaced() else android.widget.Toast.makeText(context, L10n.tr("Page full. Add a page or remove an item."), android.widget.Toast.LENGTH_SHORT).show() }
    }, onItems = { defs -> homeItems.addDropped(defs) }, onTouchDrag = { menuApp = null })
    // HOM-10: folder members and dock pins of packages that are gone are unavailable first, then removed after the grace period.
    LaunchedEffect(allApps) { if (allApps.isNotEmpty()) homeItems.reconcileMembers(allApps.mapTo(HashSet()) { it.key }) }
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
            android.widget.Toast.makeText(context, com.bos.sphere.core.design.L10n.tr("Motion gestures still run; their notification is hidden"), android.widget.Toast.LENGTH_SHORT).show()
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
        com.bos.sphere.core.design.HyleFont.apply(prefs.uiFont)
    }
    com.bos.sphere.ui.ApplyTheming(prefs.themeMode, prefs.accentColor)
    val iconColors by stores.iconColors.colors.collectAsStateWithLifecycle()
    // HOM-16: with home over the system wallpaper, the accent is derived from it.
    val homeAccent = prefs.accentColor
    val iconTheme = remember(prefs.iconShape, prefs.iconTint, prefs.iconPlate, prefs.accentColor, iconColors, homeAccent) {
        IconTheme(prefs.iconShape, prefs.iconTint, prefs.iconPlate, homeAccent, iconColors)
    }

    // ---- indicator (wave 55): the calm style reads out the chosen metric, the regular indicators show both signal and battery. Each source runs only while something uses it.
    val signalSource = remember { IndicatorSource(context).also { it.setMetric(com.bos.sphere.core.data.IndicatorMetric.Signal) } }
    val batterySource = remember { IndicatorSource(context).also { it.setMetric(com.bos.sphere.core.data.IndicatorMetric.Battery) } }
    val signalReading by signalSource.reading.collectAsStateWithLifecycle()
    val batteryReading by batterySource.reading.collectAsStateWithLifecycle()
    val reading = if (prefs.indicatorMetric == com.bos.sphere.core.data.IndicatorMetric.Battery) batteryReading else signalReading
    val glanceStyle = com.bos.sphere.core.data.CalmStyles.resolve(prefs.calmStyle, prefs.wallpaper(com.bos.sphere.core.data.WallpaperSurface.Glance).kind)
    val pagesStyle = com.bos.sphere.core.data.CalmStyles.resolve(prefs.calmStyle, prefs.wallpaper(com.bos.sphere.core.data.WallpaperSurface.Pages).kind)
    val regularShown = com.bos.sphere.core.data.CalmStyles.showsRegular(glanceStyle, prefs.calmAlsoRegular)
    val ambientNeeded = glanceStyle.ambient || pagesStyle.ambient
    val needSignal = regularShown || (ambientNeeded && prefs.indicatorMetric == com.bos.sphere.core.data.IndicatorMetric.Signal)
    val needBattery = regularShown || (ambientNeeded && prefs.indicatorMetric == com.bos.sphere.core.data.IndicatorMetric.Battery)
    androidx.lifecycle.compose.LifecycleStartEffect(needSignal) { if (needSignal) signalSource.start(); onStopOrDispose { signalSource.stop() } }
    androidx.lifecycle.compose.LifecycleStartEffect(needBattery) { if (needBattery) batterySource.start(); onStopOrDispose { batterySource.stop() } }
    val indicatorTarget = IndicatorColors.argb(reading, prefs.indicatorLow, prefs.indicatorMid, prefs.indicatorHigh, prefs.indicatorIdle, false)
    val indicatorColor by androidx.compose.animation.animateColorAsState(Color(indicatorTarget.toInt()), com.bos.sphere.core.design.rememberMotionPrefs().gate(androidx.compose.animation.core.tween(800)), label = "indicator")

    val mediaInfo by com.bos.sphere.feature.grid.rememberMediaInfo(com.bos.sphere.feature.grid.FixedHomeChrome.listensToMedia(prefs.showMusic)) // STR-06, HOM-23: W38: the media line and its controls are part of the fixed chrome on every page, no strip and nothing in the dock

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

    // WSP-05: a group placed on the page grid runs like the dock's group item.
    homeItems.runGroup = { g -> scope.launch { appLauncher.runGroup(g) } }
    // Wave 24: an item placed from Glance, Recents or All apps lands on pages; show the landing that draws them.
    homeItems.onPlaced = { com.bos.sphere.core.data.HomePlacementLanding.after(landingRef.value)?.let { l -> if (com.bos.sphere.core.data.HomeDeck.pagesIndex(prefsRef.value.glanceEnabled, prefsRef.value.homePages) == null) turnPagesOnRef.value(1) else showSurface(l) } }
    val contactSource = remember { com.bos.sphere.core.data.ContactSource(context) }
    val searchHub = remember {
        val palette = com.bos.sphere.feature.search.palette.PaletteHost.get(context)
        com.bos.sphere.feature.search.palette.FileIndexScheduler.schedule(context)
        com.bos.sphere.core.data.AndroidSearchHub(context, palette.hubProviders() + com.bos.sphere.ui.dockSearchProvider(context) + homeItems.folderSearchProvider()) { palette.prefs.approvedSources() }
    }
    val appCtx = androidx.compose.ui.platform.LocalContext.current
    val filePermission = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        r.data?.data?.let { runCatching { com.bos.sphere.feature.search.palette.FileGrants.persist(appCtx, it) } }
    }
    val contactsPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
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

    // ---- Home press: collapse everything, back to the first page.
    LaunchedEffect(homeSignal) {
        if (homeSignal > 0) {
            settingsOpen = false; wheelOpen = false; hubOpen = false; searchOpen = false; leftOpen = false; rightOpen = false
            menuApp = null; editCorner = null; editGesture = null; itemEdit.clear(); expandedWidget = null
            // Wave 26: the Home button returns to what home starts on: Glance when it is on, else the first page.
            showStop(0) // stop 0 is Glance when it is on, else the first page
            gridState.goToPage(prefs.homePage.coerceIn(0, (pages.size - 1).coerceAtLeast(0)))
        }
    }
    LaunchedEffect(externalAction) { externalAction?.let { internal(it.id, null) } }

    // Shared by both home styles: the four corners (kept clear of the always-on keyboard).
    val kbHeight = remember { mutableStateOf(0.dp) }
    val bottomClear = if (prefs.alwaysKeyboard) kbHeight.value else 0.dp
    @Composable
    fun Corners(alpha: Float = 1f) { // wave 38: ONE corner layer for every page, drawn above the pager with the chosen corner style; only the enabled hotspots
        Box(Modifier.fillMaxSize().padding(bottom = bottomClear).graphicsLayer { this.alpha = alpha }) {
            CornerLayer(
                placements = hotspotPlacements,
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
                        focusIdPrefix = "corner.${placement.screen.name}",
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
    // W55 (fixedBackdrop): the wallpaper is drawn ONCE, fixed, below the pager (see FixedHomeBackdrop at the top of the home box); the indicator effects (calm and alert) ride with it.
    @Composable
    fun BackdropIndicator() {
        IndicatorLayer(
            reading = reading,
            style = com.bos.sphere.core.data.CalmStyles.resolve(prefs.calmStyle, prefs.wallpaper(com.bos.sphere.core.data.WallpaperSurface.Glance).kind),
            metric = prefs.indicatorMetric,
            colorArgb = indicatorColor.toArgb().toLong() and 0xFFFFFFFFL,
            intensity = prefs.indicatorIntensity,
            speed = prefs.indicatorSpeed,
            modifier = Modifier.fillMaxSize(),
            low = prefs.indicatorLow, mid = prefs.indicatorMid,
        )
    }

    // Breathing room at the sides of the scrolling homes (plus whatever "Sides" margin is set).
    val scrollHorizontal = prefs.scrollsHorizontally(isLandscape)
    val cfg = androidx.compose.ui.platform.LocalConfiguration.current
    // One frame on every side, in either orientation (about 15% of the short side): apps live inside it;
    // the corner icons, clock and page dots sit in the margin band around it.
    val frame = com.bos.sphere.feature.grid.HomeFrame.band(cfg.screenWidthDp.toFloat(), cfg.screenHeightDp.toFloat()).dp
    val sideGap = frame + (cfg.screenWidthDp * prefs.gridInsetX).dp
    val scrollTop = frame // wave 38: no clock or status strip on the pages
    // HOM-19: where the dock rail is thicker than the frame band, the app area gives up the difference.
    val dockState by com.bos.sphere.feature.dock.WorkspaceRepository.get(context).state.collectAsState()
    val dockTier = remember(cfg.screenWidthDp) { com.bos.sphere.core.capabilities.CapabilityEvaluator.tier(appLauncher.inputs()) }
    // HOM-19: the rail sits above the bottom clock and page dots, and the app area gives up what the rail reaches past the band.
    val dockOn = (dockState.dock.isEnabled(dockTier) || HomeChromeRules.forcesDock(landing, com.bos.sphere.core.data.ChromeSettings.of(prefs))) && HomeChromeRules.shows(landing, ChromeElement.Dock, com.bos.sphere.core.data.ChromeSettings.of(prefs))
    val pageDotsAtBottom = com.bos.sphere.core.data.HomeDeck.stops(prefs.glanceEnabled, prefs.homePages, pages.size) > 1 // wave 28: the one indicator is always the bottom row
    val bottomPlan = com.bos.sphere.feature.dock.HomeBottomStack.plan(
        // W26: an empty dock (nothing pinned, nothing recent) is not drawn and reserves nothing.
        enabled = com.bos.sphere.feature.dock.DockVisibility.shown(dockOn, com.bos.sphere.feature.dock.DockPresence.hasItems),
        edge = dockState.dock.edgeFor(isLandscape),
        collapsed = dockState.dock.mode == com.bos.sphere.feature.dock.DockMode.Collapsed, bandDp = frame.value,
        lowerStackDp = com.bos.sphere.feature.dock.HomeBottomStack.lowerStackDp(
            stackDp = com.bos.sphere.feature.grid.bottomStackDp(prefs, cfg.screenWidthDp.toFloat(), cfg.screenHeightDp.toFloat()),
            glance = landing == HomeShown.Glance, glanceIndicator = prefs.glanceIndicator || pageDotsAtBottom, // the one indicator is drawn on Glance whenever there are several stops, whatever the Glance indicator preference
            pageDotsAtBottom = pageDotsAtBottom, clockAtBottom = false,
        ),
        shelfShown = com.bos.sphere.feature.dock.GroupShelfModel.visible(dockState.groups, dockState.dock.groupShelf).isNotEmpty(),
    )
    val dockReserve = bottomPlan.reserve
    val scrollBottom = bottomClear + frame + dockReserve.bottom.dp
    val scrollPadding = homeItems.padFor(
        androidx.compose.foundation.layout.PaddingValues(start = sideGap + dockReserve.start.dp, end = sideGap + dockReserve.end.dp, top = scrollTop, bottom = scrollBottom),
        cfg.screenWidthDp.toFloat(), cfg.screenHeightDp.toFloat(), homeItems.topLevelKeys(layout.pages), prefs.homeStyle,
    )
    // The PSP bar has no reliable empty space to long-press: holding the top band (clock area) opens Settings.
    @Composable
    fun BoxScope.SettingsHoldBand() {
        Box(
            Modifier.align(Alignment.TopCenter).fillMaxWidth()
                .height(44.dp)
                .pointerInput(Unit) { detectTapGestures(onLongPress = { quickPage = null; quickOpen = true }) },
        )
    }
    // STR-01: the home card follows a drag from the left edge, peeks and springs back, or opens Launchpad.
    val reveal = com.bos.sphere.ui.rememberLaunchpadReveal(leftOpen) { leftOpen = it }
    // STR-01: opening Launchpad shrinks the home into a rounded card pushed to one side (the right; the left in right-to-left layouts),
    // in both orientations. The card is one graphicsLayer on the live home (NFR-05); reduced motion snaps it and cross-fades the backdrop.
    val launchpadRtl = homeOrder.rtl // wave 26: the home order (not the app language) puts the Launchpad on the right edge
    val launchpadMotion = com.bos.sphere.core.design.rememberMotionPrefs()
    val launchpadDensity = androidx.compose.ui.platform.LocalDensity.current.density
    val launchpadGeometry = com.bos.sphere.ui.LaunchpadCardGeometry(
        androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp * launchpadDensity,
        androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp * launchpadDensity,
        com.bos.sphere.ui.CardEdge.forLaunchpad(launchpadRtl), launchpadDensity, launchpadMotion.reduced,
    )
    val revealSlideDp = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp * launchpadGeometry.dragTravelFraction
    // Wave 25 (STR-01): Launchpad's menu is a side panel; while it is open the home card slides fully out of view (and back with it).
    val lpMenu = com.bos.sphere.ui.rememberLaunchpadMenu()
    val popupSheetHost = com.bos.sphere.ui.rememberPopupSheetHost() // wave 38: Comms' Sound and Connectivity sheets are drawn full width above the home card
    LaunchedEffect(leftOpen) { if (!leftOpen) lpMenu.open = false }
    // STR-14: the Recents panel reuses the same geometry, card layer, scrim and settle with the opposite edge (not a fork).
    val rightReveal = com.bos.sphere.ui.rememberLaunchpadReveal(rightOpen) { rightOpen = it }
    val recentsGeometry = com.bos.sphere.ui.LaunchpadCardGeometry(
        launchpadGeometry.widthPx, launchpadGeometry.heightPx, com.bos.sphere.ui.CardEdge.forRecents(launchpadRtl), launchpadDensity, launchpadMotion.reduced,
    )
    val rightSlideDp = revealSlideDp
    val recentsEnabled = prefs.rightSide == com.bos.sphere.core.data.RightSide.RecentsPanel
    val chromeSettings = com.bos.sphere.core.data.ChromeSettings.of(prefs) // wave 17: the per-landing chrome table
    val glanceWake = remember { mutableIntStateOf(0) } // wave 17: any touch wakes the quiet Glance handles
    LaunchedEffect(leftOpen) { if (leftOpen) rightOpen = false }
    LaunchedEffect(rightOpen) { if (rightOpen) leftOpen = false }
    LaunchedEffect(recentsEnabled) { if (!recentsEnabled) rightOpen = false }
    // Back returns from the Recents panel (the system's predictive back, as Launchpad does).
    com.bos.sphere.core.design.rememberPredictiveBackProgress(rightOpen) { rightOpen = false }
    com.bos.sphere.feature.grid.FocusLayer(layer = com.bos.sphere.feature.grid.RECENTS_FOCUS_LAYER, active = rightOpen) {}
    // INP-01: Launchpad's open chord comes from the one shared registry (a rebinding in Ground Control reaches it), and while it is open it is a
    // layer of the shared focus graph (STR-08 keeps its own arrow, collapse and Esc handling inside the panel, so its rows are not graph nodes).
    val launchpadShortcuts = shortcutRegistry
    com.bos.sphere.feature.grid.FocusLayer(layer = LAUNCHPAD_FOCUS_LAYER, active = leftOpen) {}
    val grantPrompter = com.bos.sphere.ui.rememberGrantPrompter()
    val homeCapture = com.bos.sphere.feature.grid.rememberHomeCapture()
    // CFG-07: features in other modules ask for grants through GrantHooks; the home shows the explanation first.
    DisposableEffect(grantPrompter) {
        com.bos.sphere.core.design.GrantHooks.interceptor = { kind, proceed -> grantPrompter.ask(kind, proceed) }
        onDispose { com.bos.sphere.core.design.GrantHooks.interceptor = null }
    }
    // STR-08: Esc in Launchpad gives focus back to the home element that held it (the input stream's focus registry).
    val launchpadFocusTrail = remember {
        com.bos.sphere.ui.LaunchpadFocusTrail().also {
            it.currentId = { com.bos.sphere.feature.grid.LauncherInput.focus.currentId }
            it.requestId = { id -> com.bos.sphere.feature.grid.LauncherInput.focus.request(id) }
        }
    }
    CompositionLocalProvider(com.bos.sphere.feature.grid.LocalEditGrid provides (if (homeEdit.active) editGrid else null), LocalIconTheme provides iconTheme, com.bos.sphere.feature.grid.LocalIconSources provides homeItems.iconSources, com.bos.sphere.ui.LocalLaunchpadFocusTrail provides launchpadFocusTrail, com.bos.sphere.feature.grid.LocalItemSettingsOpener provides remember(itemEdit, homeEdit) { { k: String -> if (homeEdit.active) itemEdit.openSheet(k) } }, com.bos.sphere.feature.grid.LocalClockTileOptions provides remember(prefs.clockTileAnalog, prefs.clockTileFormat, prefs.clockTileDate) { com.bos.sphere.feature.grid.ClockTileOptions.of(prefs) }) {
    Box(
        modifier = Modifier.fillMaxSize().onSizeChanged { homeSizePx = it }.onKeyEvent { e -> if (homeEdit.active && e.type == KeyEventType.KeyDown && e.key == Key.Escape) { chromeState.requestCancel(homeEdit.hasChanges) { homeEdit.cancel() }; true } else false }.trackLastTouch(lastTouch).dockShortcuts(shortcutRegistry, dockController)
            .launchpadShortcut(launchpadShortcuts) { if (prefs.leftPage != com.bos.sphere.core.data.LeftPage.Off) leftOpen = true }
            .launchpadEdgeDrag(reveal, prefs.leftPage != com.bos.sphere.core.data.LeftPage.Off && !rightOpen && !homeEdit.active, revealSlideDp, mirrored = launchpadRtl, edgeDp = HomeChromeRules.edgeZoneDp(landing))
            .launchpadEdgeDrag(rightReveal, recentsEnabled && !leftOpen && !homeEdit.active, rightSlideDp, mirrored = !launchpadRtl, edgeDp = HomeChromeRules.edgeZoneDp(landing))
            .glanceWakeOnTouch { glanceWake.intValue++ }
            .rightEdgeSwipe(prefs.rightSide == com.bos.sphere.core.data.RightSide.DockRail && !leftOpen && !homeEdit.active, rtl = launchpadRtl) { openRecents() },
    ) {
        // W55 (fixedBackdrop): the wallpaper and the indicator effects, once, BELOW the pager, the edit-mode page scale and the Comms/Recents card: nothing here is
        // translated by a swipe or a panel drag. With a different Pages wallpaper it only cross-fades with the pager position (read in a graphicsLayer lambda).
        com.bos.sphere.feature.grid.FixedHomeBackdrop(
            prefs = prefs,
            position = { homePager.currentPage + homePager.currentPageOffsetFraction },
            modifier = Modifier.captureWallpaper(homeCapture),
            overlay = { BackdropIndicator() },
        )
        // W45: the scrim and the backdrop round the scaled page (nothing while the page editor's card is up: it draws its own background).
        run {
            val backdrop = remember { androidx.compose.runtime.derivedStateOf { chromeProgress.value * (1f - editCard.progress.value) } }
            com.bos.sphere.feature.grid.EditChromeBackdrop(backdrop, chromePage)
        }
        EditCardOverlay(
            card = editCard, homeEdit = homeEdit, stores = stores, homeItems = homeItems, pages = pages, layout = layout, preserve = preserve,
            capacityOf = capacityOf, gridState = gridState, appsByKey = appsByKey, apps = apps,
            folderKeys = remember(extrasState) { extrasState.folders.values.flatMapTo(HashSet()) { it.keys } },
            prefs = prefs, isLandscape = isLandscape, focusAdd = editFocusAdd, savePages = { savePages(it) },
            // W47: the page editor uses the same bar as the single-page edit mode; Esc and Back there are Cancel (the confirm only when something changed).
            onExit = { chromeState.requestCancel(homeEdit.hasChanges) { homeEdit.cancel() } },
            topBar = {
                val say = com.bos.sphere.feature.grid.rememberAnnouncer()
                com.bos.sphere.feature.grid.EditTopBar(
                    title = L10n.tr("Editing pages"), undoCount = homeEdit.undoDepth, redoCount = homeEdit.redoDepth, canUndo = homeEdit.canUndo, canRedo = homeEdit.canRedo,
                    onCancel = { chromeState.requestCancel(homeEdit.hasChanges) { homeEdit.cancel() } }, onUndo = { homeEdit.undo() }, onRedo = { homeEdit.redo() },
                    onDone = { say(L10n.tr("Saved")); homeEdit.exit() }, insets = false,
                )
            },
        )
        // The left page lies UNDER the home: the home slides right, casting a shadow onto it.
        val leftProgress = reveal.progress
        val leftActive = leftOpen || leftProgress > 0.001f
        val hubPanelBody: @Composable (com.bos.sphere.ui.HubView) -> Unit = { v ->
            HomePanelsBody(v, timelineLog, appsByKey, onHide = { a -> hiddenStore.setHidden(a.key, true) }) { a -> launch(a) } // W31: Comms (and the standalone hub) stay open under the app, so Back returns to them
        }
        if (hubStandalone && prefs.leftPage != com.bos.sphere.core.data.LeftPage.Launchpad) com.bos.sphere.ui.LaunchpadPanel(
            visible = true, onDismiss = { hubStandalone = false },
            onOpenApp = { pkg -> apps.firstOrNull { it.packageName == pkg }?.let(::launch) ?: appLauncher.launch(com.bos.sphere.core.launch.LaunchRequest(com.bos.sphere.core.launch.LaunchTarget(pkg))) },
            modifier = Modifier.fillMaxSize(), theme = prefs.launchpadTheme,
            launchApp = { pkg -> appLauncher.launch(com.bos.sphere.core.launch.LaunchRequest(com.bos.sphere.core.launch.LaunchTarget(pkg))).started },
            panelBody = hubPanelBody, haptics = com.bos.sphere.feature.grid.rememberLauncherHaptics(), sheetHost = popupSheetHost,
        )
        when (prefs.leftPage) {
            com.bos.sphere.core.data.LeftPage.Launchpad -> if (leftActive) com.bos.sphere.ui.LaunchpadPanel(
                visible = true,
                onDismiss = { leftOpen = false },
                onOpenApp = { pkg -> // W31: Comms stays open under the app
                    apps.firstOrNull { it.packageName == pkg }?.let(::launch)
                        ?: appLauncher.launch(com.bos.sphere.core.launch.LaunchRequest(com.bos.sphere.core.launch.LaunchTarget(pkg)))
                },
                modifier = Modifier.fillMaxSize(),
                theme = prefs.launchpadTheme,
                underlay = true,
                cardFraction = { reveal.progress },
                // STR-02: reports whether the app started, so a failed open keeps Launchpad up with a message.
                launchApp = { pkg -> appLauncher.launch(com.bos.sphere.core.launch.LaunchRequest(com.bos.sphere.core.launch.LaunchTarget(pkg))).started },
                menu = lpMenu,
                panelBody = hubPanelBody, haptics = com.bos.sphere.feature.grid.rememberLauncherHaptics(), sheetHost = popupSheetHost,
            )
            com.bos.sphere.core.data.LeftPage.Widget -> if (leftActive && prefs.leftWidgetId >= 0 && !com.bos.sphere.core.data.CrashLoopGuard.safeMode) LeftWidgetPage(true, widgetHost, prefs.leftWidgetId) { leftOpen = false }
            else -> Unit
        }
        // STR-14: the Recents panel lies under the home card too (the card sits on the left with a sliver; the panel is full size and static).
        val rightProgress = rightReveal.progress
        val rightActive = recentsEnabled && (rightOpen || rightProgress > 0.001f)
        if (rightActive) {
            val rows = recentRows
            com.bos.sphere.feature.grid.RecentsPanel(
                rows = rows, now = remember(rows, rightOpen) { System.currentTimeMillis() }, usageAccessOn = recentsUsageOn,
                sliverInsetDp = (recentsGeometry.returnZonePx / launchpadDensity).dp,
                sliverOnLeft = recentsGeometry.edge == com.bos.sphere.ui.CardEdge.Left,
                onLaunch = { app -> rightOpen = false; launch(app) },
                onMenu = { menuRecents = true; menuApp = it },
                onClearAll = { stores.recentsState.clear() },
                onGrantUsage = { com.bos.sphere.core.design.GrantHooks.ask(com.bos.sphere.core.design.GrantKind.UsageAccess) { runCatching { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } } },
                modifier = Modifier.graphicsLayer { alpha = recentsGeometry.pose(rightReveal.progress).backdropAlpha },
                contentAlpha = { recentsGeometry.pose(rightReveal.progress).contentAlpha },
            )
        }
        val slideDp = revealSlideDp
        // Wave 26: at the first page a swipe back (towards the Launchpad edge) returns to Glance when it is on, else it opens the Launchpad as before.
        var scrollAtStart by remember { mutableStateOf(true) }
        val pagesEdgePx = with(androidx.compose.ui.platform.LocalDensity.current) { HomeChromeRules.edgeZoneDp(HomeShown.Pages).dp.toPx() }
        // Wave 28: a swipe back (towards the Comms edge) at the first page or the start of the scroll goes to Glance when it is on (the previous stop of the one pager;
        // the grid and the scroll are scrollables inside the pager and keep that drag for themselves, so this hand-over is taken here), else it opens the Comms as before.
        val backSwipeEnabled = com.bos.sphere.core.data.HomeDeck.backSwipeToGlance(prefs.glanceEnabled, homeEdit.active, leftOpen || rightOpen) // Comms opens from its edge only
        val backSwipeAccepts: (Float) -> Boolean = { dx -> homeOrder.isTowardsLaunchpad(dx) }
        val backSwipeAction: () -> Unit = { showStop(0) }
        val indicatorShown = com.bos.sphere.core.data.HomeDeck.indicatorShown(prefs.glanceEnabled, prefs.glanceIndicator, prefs.homePages, pages.size)
        val dotsOutside = editChromeLive && !pageEditorOn
        val pageDots: @Composable (Modifier) -> Unit = { modifier ->
            val dots = com.bos.sphere.core.data.HomeDeck.stops(prefs.glanceEnabled, prefs.homePages, pages.size) - (if (prefs.glanceEnabled) 1 else 0)
            com.bos.sphere.feature.grid.PageIndicator(
                count = dots, current = gridState.currentPage.coerceIn(0, (dots - 1).coerceAtLeast(0)), vertical = false,
                onSelect = { page -> if (!glanceClockEditing) showDot(page + (if (prefs.glanceEnabled) 1 else 0)) }, // the indicator reports the page; the dot index counts Glance as 0 (W45: while editing, a dot is the page switcher)
                modifier = modifier
                    .graphicsLayer { alpha = 1f - (1f - com.bos.sphere.feature.grid.EditChromeGeometry.FIXED_DIM) * chromeProgress.value }, // W45: dimmed to 40% while editing, still the page switcher
                onMenu = if (prefs.leftPage != com.bos.sphere.core.data.LeftPage.Off) ({ if (!homeEdit.active && !glanceClockEditing) leftOpen = true }) else null,
                menuLabel = L10n.tr("Open Comms"), menuTag = "glance.indicator.launchpad", rtl = homeOrder.rtl,
                homeIndex = prefs.homePage.coerceIn(0, (dots - 1).coerceAtLeast(0)),
                onGlance = if (prefs.glanceEnabled) ({ if (!homeEdit.active && !glanceClockEditing) showSurface(HomeShown.Glance) }) else null, glanceCurrent = landing == HomeShown.Glance,
                quiet = landing == HomeShown.Glance, awakeNonce = indicatorAwake + homePager.currentPage,
                hiddenFromAccessibility = homeEdit.active && pageEditorOn, // wave 37: the editor's strip cards already say "Page N of M"
            )
        }
        Box(
            Modifier.fillMaxSize().launchpadMenuAside(lpMenu, launchpadGeometry.widthPx, launchpadRtl).editCardLayer { com.bos.sphere.feature.grid.EditChromeGeometry.blend(com.bos.sphere.feature.grid.EditChromeGeometry.frame(chromePage, chromeProgress.value, chromeMotion.reduced), editCard.frame(), editCard.progress.value) }.homeCardLayer(launchpadGeometry) { reveal.progress }
                .homeCardLayer(recentsGeometry) { if (recentsEnabled) rightReveal.progress else 0f }
                // Behind the card the home is inert for accessibility services; the card overlay below is the one control.
                .then(if (leftOpen || rightOpen) Modifier.clearAndSetSemantics { } else Modifier)
                .then(if (leftActive || rightActive) Modifier.background(Color.Black) else Modifier)
                .captureHome(homeCapture),
        ) {
            // W55: while a Comms/Recents panel shows, the home is a card over the (opaque) panel and needs a face of its own: a still copy of the wallpaper on the card. The fixed
            // backdrop itself is below the panels and never moves; the card face is part of the card, not the backdrop.
            if (leftActive || rightActive) com.bos.sphere.feature.grid.HomeSurfaceBackground(
                if (prefs.glanceEnabled && landing == HomeShown.Glance || prefs.sharesWallpaper(com.bos.sphere.core.data.WallpaperSurface.Pages)) com.bos.sphere.core.data.WallpaperSurface.Glance else com.bos.sphere.core.data.WallpaperSurface.Pages,
                prefs, Modifier.fillMaxSize(), animate = false, announce = false, play = false, alert = false,
            )
            // Wave 28 (homeUnify): the home is ONE horizontal pager, [Glance (when on)] then the pages stop: the pages (paginated, a grid pager nested in it) or the
            // scroll surface (continuous). Swiping, the indicator and the Home button all work on these stops; there is no second surface and no second set of dots.
            val pagerHostDir = androidx.compose.ui.platform.LocalLayoutDirection.current
            androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Ltr) {
                androidx.compose.foundation.pager.HorizontalPager(
                    state = homePager, reverseLayout = homeOrder.rtl,
                    userScrollEnabled = !homeEdit.active && !glanceClockEditing && !leftOpen && !rightOpen,
                    modifier = Modifier.fillMaxSize().testTag("home.pager"),
                ) { stop ->
                    // Only the stop on screen (or moving into view) is composed: a pager prefetches its neighbour while idle, and a prefetched
                    // Glance or grid would register its keyboard focus nodes and run its effects (media) while it is not shown.
                    val stopLive = stop == homePager.currentPage || homePager.isScrollInProgress || homePager.currentPageOffsetFraction != 0f
                    if (!stopLive) Box(Modifier.fillMaxSize()) else
                    androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides pagerHostDir) {
                        if (com.bos.sphere.core.data.HomeDeck.surfaceAt(prefs.glanceEnabled, stop) == HomeShown.Glance) {
                androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides if (homeOrder.rtl) androidx.compose.ui.unit.LayoutDirection.Rtl else androidx.compose.ui.unit.LayoutDirection.Ltr) {
                    Box(Modifier.fillMaxSize()) {
                        // W38: the Glance is the calm page: the wallpaper only; the hotspots and the music line are the fixed chrome below.
                        com.bos.sphere.feature.grid.GlanceSurface(
                            prefs = prefs,
                            hasLaunchpad = prefs.leftPage != com.bos.sphere.core.data.LeftPage.Off,
                            gestures = glanceGestureSpec,
                            onClockTap = { runner.run(CornerActionId.Clock) },
                            onPrefsEdit = { f -> stores.prefs.update(f) }, onEditingChange = { glanceClockEditing = it },
                            onOpenLaunchpad = { if (prefs.leftPage != com.bos.sphere.core.data.LeftPage.Off && !glanceClockEditing) leftOpen = true },
                            indicator = false, onAwake = { indicatorAwake++ }, // wave 28: the one page indicator is drawn by the home, above the pager
                            transparentBase = true, // W55: the wallpaper is the fixed backdrop below the pager, not part of this page
                        )
                    }
                }
                        } else {
                            // one root per pager page (a lazy page with several roots is not placed): the pages stop is one Box
                            Box(Modifier.fillMaxSize()) {
                            when (prefs.homeStyle) {
                HomeStyle.Grid -> if (prefs.homePages == com.bos.sphere.core.data.HomePages.Continuous) {
                    // Wave 26: the SCROLL pages: one continuous surface, column by column (ColumnScrollHome).
                    Box(Modifier.fillMaxSize().safeDrawingPadding().deckSwipe(enabled = backSwipeEnabled && !leftOpen, accepts = backSwipeAccepts, ready = { scrollAtStart }, edgeExcludePx = pagesEdgePx, onTrigger = backSwipeAction)) {
                        com.bos.sphere.feature.grid.ColumnScrollHome(
                            keys = pages.flatMap { it.keys }, appsByKey = appsByKey, extras = homeItems.homeExtras, order = homeOrder,
                            topBand = scrollTop, bottomBand = scrollBottom, onLaunch = ::launch, onAppLongPress = { if (!selectItem(it.key)) menuApp = it },
                            iconScale = prefs.iconScale, showLabels = prefs.showLabels, editing = homeEdit.active,
                            gestures = homeGestureSpec,
                            onAtStart = { scrollAtStart = it },
                            pageKeys = pages.map { it.keys }, pageScroll = editCard.pageScroll.value,
                            selectedPage = if (homeEdit.active) editCard.contSel.intValue else -1, onPageInView = { editCard.contSel.intValue = it },
                        )
                    }
                } else {
                    Box(
                        Modifier.fillMaxSize().padding(bottom = bottomClear).deckSwipe(
                            enabled = backSwipeEnabled && !leftOpen, accepts = backSwipeAccepts, ready = { gridState.pager.currentPage == 0 && !gridState.pager.isScrollInProgress },
                            edgeExcludePx = pagesEdgePx, onTrigger = backSwipeAction,
                        ),
                    ) {
                        GridHome(
                            pages = pages,
                            appsByKey = appsByKey,
                            prefs = prefs,
                            isLandscape = isLandscape,
                            reserveStart = dockReserve.start.dp, reserveEnd = dockReserve.end.dp, reserveBottom = dockReserve.bottom.dp, // W26: only the app area gives up the dock's room; the dots stay in the band
                            state = gridState,
                            gesturesBound = gestures.keys,
                            capacityOf = capacityOf,
                            onLaunch = ::launch,
                            onAppLongPress = { if (!selectItem(it.key)) menuApp = it },
                            onPagesChange = { savePages(it) },
                            onEmptyLongPress = { page -> if (!homeEdit.active && com.bos.sphere.core.data.FreshHome.isEmpty(pages)) { editFocusAdd = true; pageEditorOn = true; homeEdit.enter() } else if (HomeGesture.LongPress in gestures) onGesture(HomeGesture.LongPress) else { quickPage = page; quickOpen = true } },
                            onGesture = ::onGesture,
                            onClockTap = { runner.run(CornerActionId.Clock) },
                            onTileTap = { key -> if (key == HomeTiles.SETTINGS) openSettings(com.bos.sphere.ui.CATEGORY_ROOT) },
                            onTileLongPress = { key, _ -> selectItem(key, enterEdit = true) },
                            extras = homeItems.homeExtras,
                            editMode = homeEdit.active,
                            onRemoveItem = { key ->
                                val a = appsByKey[key]
                                if (a != null) stores.layout.removeFromHome(a.key, pages, preserve) else homeItems.removeFromHome(key)
                            },
                            drawIndicator = false, // wave 28: the home draws the one page indicator above its pager
                        )
                    }
                }

                HomeStyle.Psp -> {
                    val recentForXmb = remember(history, appsByKey) { history.asReversed().map { it.key }.distinct().mapNotNull(appsByKey::get).take(12) }
                    com.bos.sphere.feature.grid.XmbHome(
                        apps = apps, recent = recentForXmb,
                        categoryOf = { com.bos.sphere.feature.search.AppSearch.categoryNameOf(it.packageName) },
                        onLaunch = ::launch, onLongPress = { menuApp = it },
                        modifier = Modifier.fillMaxSize().safeDrawingPadding(),
                        contentPadding = scrollPadding,
                        showLabels = prefs.showLabels, sounds = sounds,
                        gestures = homeGestureSpec,
                        extras = homeItems.homeExtras, extraKeys = homeItems.interleavedKeys(layout.pages), topLevel = layout.pages.flatMap { it.keys },
                    )
                    SettingsHoldBand()
                }
                HomeStyle.Dsi -> {
                    com.bos.sphere.feature.grid.ContinuousHome(
                        apps = apps, crossCount = 3, landscape = scrollHorizontal, style = com.bos.sphere.feature.grid.ContinuousStyle.Dsi,
                        onLaunch = ::launch, onLongPress = { menuApp = it },
                        modifier = Modifier.fillMaxSize().safeDrawingPadding(),
                        contentPadding = scrollPadding,
                        iconScale = prefs.iconScale, spacing = prefs.iconSpacing, showLabels = prefs.showLabels, sounds = sounds,
                        gestures = homeGestureSpec,
                        extras = homeItems.homeExtras, extraKeys = homeItems.interleavedKeys(layout.pages), topLevel = layout.pages.flatMap { it.keys },
                    )
                }
                HomeStyle.List -> {
                    com.bos.sphere.feature.grid.ListHome(
                        apps = apps, onLaunch = ::launch, onLongPress = { menuApp = it },
                        modifier = Modifier.fillMaxSize().safeDrawingPadding(),
                        contentPadding = scrollPadding,
                        iconScale = prefs.iconScale, showLabels = prefs.showLabels,
                        gestures = homeGestureSpec,
                        extras = homeItems.homeExtras, extraKeys = homeItems.interleavedKeys(layout.pages),
                    )
                }
            }
                            }
                        }
                    }
                }
            }
            // Wave 38 (glanceFixed): the FIXED chrome above the pager: the four hotspots and the music line stay exactly in place on every page and during a swipe.
            Box(
                Modifier.fillMaxSize().padding(bottom = bottomClear)
                    .graphicsLayer { alpha = 1f - (1f - com.bos.sphere.feature.grid.EditChromeGeometry.FIXED_DIM) * (if (glanceClockEditing) 1f else chromeProgress.value) } // W45: the hotspots and the music line dim to 40% while editing (inert: see EditFixedChromeShield); W50: the same while the Glance clock is edited
                    .then(if (homeEdit.active || glanceClockEditing) Modifier.clearAndSetSemantics { } else Modifier),
            ) {
                com.bos.sphere.feature.grid.FixedHomeChrome(
                    media = mediaInfo, showMusic = prefs.showMusic, controlsSeconds = prefs.glanceControlsSeconds,
                    onPrevious = { com.bos.sphere.core.data.MediaNow.previous() }, onPlayPause = { com.bos.sphere.core.data.MediaNow.playPause() }, onNext = { com.bos.sphere.core.data.MediaNow.next() },
                    onAwake = { indicatorAwake++ },
                    corners = { Corners() },
                    topLeftHotspot = com.bos.sphere.core.data.Corner.TopLeft in glanceOn, topRightHotspot = com.bos.sphere.core.data.Corner.TopRight in glanceOn,
                    regular = if (regularShown) signalReading to batteryReading else null, low = prefs.indicatorLow, // wave 55: the regular battery and signal indicators
                )
                if (glanceClockEditing) com.bos.sphere.feature.grid.GlanceClockEditShield() // wave 50: dimmed like the home editor does, and inert
            }
            // Wave 28: the ONE page indicator, at one fixed place above the pager: Glance is its first stop, the pages (or the scroll) follow.
            // W45: while the single-page edit mode is up it is drawn OUTSIDE the scaled, clipped page (see the call after this layer), where the dock already leaves room for it.
            if (indicatorShown && !dotsOutside) pageDots(Modifier.align(Alignment.BottomCenter).then(Modifier.windowInsetsPadding(androidx.compose.foundation.layout.WindowInsets.Companion.safeDrawing)).padding(bottom = 4.dp))
            // HOM-01: an empty home says how to fill it; the hint opens edit mode with the add carousel focused.
            if (prefs.homeStyle == HomeStyle.Grid && !homeEdit.active && !leftActive && // wave 28: the empty Continuous scroll says how to start too
                HomeShownRules.showsEmptyHomeHint(landing, com.bos.sphere.core.data.FreshHome.isEmpty(pages))
            ) {
                com.bos.sphere.feature.grid.EmptyHomeHint(
                    onOpen = { editFocusAdd = true; pageEditorOn = true; homeEdit.enter() }, modifier = Modifier.align(Alignment.Center),
                )
            }
            // HOM-08: widgets and the item strip live in a band on the scrolling homes, never over their tiles.
            if (HomeShownRules.hasPages(landing) && prefs.homeStyle != HomeStyle.Grid) { // W47: the Grid style with Pages Continuous is the column scroll surface, whose widgets are its own cells (the band drew every widget a second time, with a second frame and a second set of handles in edit mode)
                homeItems.Band(layout.pages, cfg.screenWidthDp.toFloat(), cfg.screenHeightDp.toFloat(), prefs.homeStyle)
            }

        }
        if (indicatorShown && dotsOutside) pageDots(Modifier.align(Alignment.BottomCenter).then(Modifier.windowInsetsPadding(androidx.compose.foundation.layout.WindowInsets.Companion.safeDrawing)).padding(bottom = 4.dp))
        // The card of home still showing: tap it, press Enter on it, or drag it back, to bring the home back.
        if (leftOpen) {
            LaunchpadCardOverlay(launchpadGeometry, reveal, onReturn = { leftOpen = false }, slideDp = revealSlideDp, modifier = Modifier.launchpadMenuAside(lpMenu, launchpadGeometry.widthPx, launchpadRtl))
        }
        // STR-14: the same slice of the home card on the left while the Recents panel is open.
        if (rightOpen) {
            LaunchpadCardOverlay(recentsGeometry, rightReveal, onReturn = { rightOpen = false }, slideDp = rightSlideDp)
        }

        // Dock rail (DCK-01 to DCK-08): off by default on phones, on by default on large windows and desktop setups.
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = 1f - (1f - com.bos.sphere.feature.grid.EditChromeGeometry.DOCK_DIM) * chromeProgress.value }) { // W45: the dock stays while editing (a drop target), dimmed to 70%
        DockLayer(
            apps = apps, launcher = appLauncher, controller = dockController, homeSignal = homeSignal,
            visible = !leftOpen && !rightOpen && !settingsOpen && !searchOpen && !wheelOpen && !(homeEdit.active && pageEditorOn) && HomeChromeRules.shows(landing, ChromeElement.Dock, chromeSettings), // HOM-12, HOM-23: no dock on Glance unless the right side is the dock rail; W45: it steps aside only for the page editor's card
            keyboardMode = appLauncher.inputs().hasKeyboard, onLaunch = ::launch,
            homeExtras = homeItems.homeExtras,
            unavailableApps = homeItems.unavailableKeys,
            bottomPlan = bottomPlan,
            alwaysShown = HomeChromeRules.forcesDock(landing, chromeSettings),
            onGoHome = { scope.launch { gridState.goToPage(prefs.homePage.coerceIn(0, (pages.size - 1).coerceAtLeast(0))) } }, // W26: the Home glyph goes home; the dock panel is the long-press menu's
        )
        }

        // Without notification access the media strip cannot exist: say so and offer the grant (STR-05).
        com.bos.sphere.ui.MediaStripGrantPrompt(
            visible = landing == HomeShown.Glance && prefs.showMusic && !leftOpen && !rightOpen && !settingsOpen && !searchOpen && !wheelOpen,
            modifier = Modifier.align(Alignment.BottomCenter).safeDrawingPadding().padding(bottom = bottomPlan.mediaBottomDp.dp + bottomClear),
        )

        grantPrompter.Host()

        // STR-01/STR-09: the Launchpad opens from the physical left edge in every language, so these sit there too (not at the layout start, which is the right in RTL).
        val leftEdge = androidx.compose.ui.BiasAbsoluteAlignment(if (launchpadRtl) 1f else -1f, 0f)
        // The way into the left page on every home style: the unread strip, plus a slim edge handle
        // (tap it, or swipe in from the left edge) so it is always discoverable.
        // Wave 17 (owner bug report on build 48): every landing draws its handles from the explicit table HomeChromeRules; Glance draws the quiet form
        // (low alpha, fading when idle, a dot instead of the pill) and no rail unless the rail setting is on.
        val quietLeft = HomeChromeRules.quiet(landing, ChromeElement.LeftHandle)
        val glanceMotionReduced = com.bos.sphere.core.design.rememberMotionPrefs().reduced
        // Wave 22 (tour): also not while the Recents panel is open: the pushed-aside home card's sliver sits on the left edge, and the handle drawn over it opened Launchpad instead of returning home.
        if (!leftOpen && !rightOpen && !homeEdit.active && HomeChromeRules.shows(landing, ChromeElement.LeftHandle, chromeSettings)) { // W45: the left notification tray (and the handles) are hidden while editing
            if (HomeChromeRules.shows(landing, ChromeElement.Rail, chromeSettings)) {
                com.bos.sphere.ui.LaunchpadRail(onOpen = { leftOpen = true }, modifier = Modifier.align(leftEdge))
            }
            if (HomeChromeRules.shows(landing, ChromeElement.WaitingIndicator, chromeSettings)) {
                if (quietLeft) com.bos.sphere.ui.LaunchpadWaitingIndicator(onOpen = { leftOpen = true }, quiet = true, modifier = Modifier.align(leftEdge).offset(y = (-(HomeChromeRules.HANDLE_HEIGHT_DP / 2f + 20f)).dp))
                else com.bos.sphere.ui.LaunchpadWaitingIndicator(onOpen = { leftOpen = true }, modifier = Modifier.align(leftEdge).padding(bottom = 260.dp))
            }
            if (quietLeft) {
                com.bos.sphere.feature.grid.GlanceEdgeHandle(
                    label = L10n.tr("Open Comms"), tag = "glance.handle.left", rightEdge = launchpadRtl, wake = { glanceWake.intValue }, reduced = glanceMotionReduced, tint = com.bos.sphere.feature.grid.GlanceHandleFade.tint(com.bos.sphere.feature.grid.isDarkSurface()),
                    onOpen = { leftOpen = true }, modifier = Modifier.align(leftEdge),
                )
            } else {
            Box(
                Modifier.align(leftEdge).absolutePadding(left = 2.dp).width(5.dp).height(64.dp)
                    .background(com.bos.sphere.feature.grid.HomeHandleBar.color(com.bos.sphere.feature.grid.isDarkSurface()), androidx.compose.foundation.shape.RoundedCornerShape(3.dp)),
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
        }

        // STR-14: the right-hand handle (a slim bar, tap it or swipe in from the right edge). Physical right edge; the left in right-to-left.
        if (!rightOpen && !leftOpen && !homeEdit.active && HomeChromeRules.shows(landing, ChromeElement.RightHandle, chromeSettings)) {
            val rightEdge = androidx.compose.ui.BiasAbsoluteAlignment(if (launchpadRtl) -1f else 1f, 0f)
            if (HomeChromeRules.quiet(landing, ChromeElement.RightHandle)) {
                com.bos.sphere.feature.grid.GlanceEdgeHandle(
                    label = L10n.tr("Open recents"), tag = "recents.handle", rightEdge = !launchpadRtl, wake = { glanceWake.intValue }, reduced = glanceMotionReduced, tint = com.bos.sphere.feature.grid.GlanceHandleFade.tint(com.bos.sphere.feature.grid.isDarkSurface()),
                    onOpen = { openRecents() }, modifier = Modifier.align(rightEdge),
                )
            } else {
            Box(
                Modifier.align(rightEdge).absolutePadding(right = if (launchpadRtl) 0.dp else 2.dp, left = if (launchpadRtl) 2.dp else 0.dp).width(5.dp).height(64.dp)
                    .background(com.bos.sphere.feature.grid.HomeHandleBar.color(com.bos.sphere.feature.grid.isDarkSurface()), androidx.compose.foundation.shape.RoundedCornerShape(3.dp)),
            )
            Box(
                Modifier.align(rightEdge).width(24.dp).height(180.dp).then(Modifier.testTag("recents.handle"))
                    .semantics { contentDescription = L10n.tr("Open recents") }
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClickLabel = L10n.tr("Open recents"), role = Role.Button) { openRecents() },
            )
            }
        }

        // Old-school mode: an always-open keyboard that searches as you type.
        if (prefs.alwaysKeyboard && !settingsOpen && !searchOpen && !wheelOpen) {
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
        val hubMotion = com.bos.sphere.core.design.rememberMotionPrefs()
        val hubRaw by animateFloatAsState(if (hubOpen) 1f else 0f, tween(com.bos.sphere.core.quality.MorphPolicy.progressMs(hubMotion, 260)), label = "hub")
        // NFR-14: with animations off the panel does not slide; it is placed and the scrim cross-fades.
        val hubFraction = if (hubMotion.reduced && hubRaw > 0.001f) 1f else hubRaw
        if (hubFraction > 0.001f) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f * hubRaw))
                    .pointerInput(Unit) { detectTapGestures { hubOpen = false } },
            ) {
                HubPanel(fraction = hubFraction, onRequestAccess = {
                    hubOpen = false
                    com.bos.sphere.core.design.GrantHooks.ask(com.bos.sphere.core.design.GrantKind.Notifications) { // CFG-07
                        runCatching { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    }
                })
            }
            if (hubOpen) BackHandler { hubOpen = false }
        }

        val usage = remember(history) { UsageStats.of(history) }
        androidx.compose.runtime.SideEffect { com.bos.sphere.feature.search.AppSearch.usage = usage }

        // CFG-05: every Ground Control setting is also a Radar result; the list is filled once the categories are built below.
        val settingsForRadar = remember { androidx.compose.runtime.mutableStateOf(emptyList<com.bos.sphere.feature.grid.SettingCategory>()) }
        val settingsSource = remember { com.bos.sphere.feature.grid.CategorySettingsSource({ settingsForRadar.value }) { e -> searchOpen = false; com.bos.sphere.feature.grid.SettingsFocus.request(e.itemIndex); openSettings(com.bos.sphere.ui.CATEGORY_INDEX_BASE + e.categoryIndex) } }
        val recentApps = remember(history, appsByKey) { history.asReversed().map { it.key }.distinct().mapNotNull(appsByKey::get) }
        SearchOverlay(
            visible = searchOpen,
            hiddenApps = remember(allApps, hidden) { allApps.filter { it.key in hidden } }, // wave 38: the quiet "N hidden apps match: Show" row
            onUnhideApp = { a ->
                hiddenStore.setHidden(a.key, false)
                com.bos.sphere.core.data.HiddenAppsModel.rulesToClear(a.packageName, com.bos.sphere.core.data.LaunchpadSettings.rules.value).forEach { com.bos.sphere.core.data.LaunchpadSettings.removeRule(it.id) }
            },
            apps = apps,
            recent = recentApps,
            prefs = prefs,
            contacts = contactSource,
            hub = searchHub,
            onRunAction = { runner.run(it) },
            onRequestFilePermission = { grantPrompter.ask(com.bos.sphere.core.design.GrantKind.Folders) { filePermission.launch(com.bos.sphere.feature.search.palette.FileGrants.pickerIntent()) } },
            sounds = sounds,
            onLaunch = ::launch,
            onLongPressApp = { menuApp = it },
            onOpenIntent = { i -> runCatching { context.startActivity(i.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) } },
            onRequestContactsPermission = { grantPrompter.ask(com.bos.sphere.core.design.GrantKind.Contacts) { contactsPermission.launch(android.Manifest.permission.READ_CONTACTS) } },
            onDismiss = { searchOpen = false },
            modifier = Modifier.fillMaxSize(),
            settingsSource = settingsSource,
            initialQuery = typedQuery,
            initialCategory = radarScope,
            launchHistory = history, // wave 18: which ring an app sits on before anything is typed
            onRunCommand = { name, index ->
                // PAL-03: "Switch to workspace Work" names a workspace by position; every other command is a launcher command.
                if (name == "GoToWorkspace" && index != null) com.bos.sphere.feature.dock.WorkspaceRepository.get(context).switchToIndex(index)
                else runCatching { com.bos.sphere.core.input.LauncherCommand.valueOf(name) }.getOrNull()?.let { com.bos.sphere.feature.grid.LauncherInput.dispatch(it) }
            },
            // PAL-08: Open on display, Open beside and Add to dock run straight from Radar's action list.
            onRunAppAction = { app, action -> com.bos.sphere.feature.dock.AppActionList.runDirect(action, appLauncher, com.bos.sphere.feature.dock.WorkspaceRepository.get(context), app, lastTouch.sourceRect()).also { if (it) searchOpen = false } },
            adjacentSupported = remember(searchOpen) { com.bos.sphere.core.launch.LaunchResolver.offersAdjacent(appLauncher.caps()) },
            // PAL-08: "Open on display" with several displays and "Add to workspace" ask in Radar's own chooser.
            onAppActionChoices = { app, action ->
                com.bos.sphere.feature.dock.AppActionList.pickerChoices(action, appLauncher, com.bos.sphere.feature.dock.WorkspaceRepository.get(context), app, lastTouch.sourceRect())
                    ?.map { c -> com.bos.sphere.feature.grid.RadarChoice(c.label) { c.run(); searchOpen = false } }
            },
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
            // CFG-15 (waves 30, 38): the segments of the white ring around the hub (6 to 8) show what the user chose in Ground Control > App drawer > Drawer shortcuts.
            ringSegments = puckSegments,
            showRingLabels = puckLabels,
            // Wave 48 (workProfile): the Work scope label (only with a work profile) and the pause pill; the state is the system's quiet mode.
            work = run {
                val discPaused by com.bos.sphere.core.catalog.WorkProfileState.workPaused.collectAsStateWithLifecycle()
                val kinds by com.bos.sphere.core.catalog.WorkProfileState.profileKinds.collectAsStateWithLifecycle()
                val quiet by com.bos.sphere.core.catalog.WorkProfileState.quietProfileIds.collectAsStateWithLifecycle()
                val p = discPaused
                if (p == null) null else com.bos.sphere.feature.grid.WheelWork(
                    on = prefs.wheelWorkOnly, paused = p,
                    workUsers = kinds.filterValues { it == com.bos.sphere.core.catalog.ProfileKind.Work }.keys, quietUsers = quiet,
                    onChange = { on -> stores.prefs.update { it.copy(wheelWorkOnly = on) } },
                    onTogglePause = { com.bos.sphere.core.catalog.WorkProfileState.setWorkPaused(context, !p) },
                )
            },
            actions = puckSlots.take(puckSegments).mapIndexed { i, slot ->
                when (slot) {
                    com.bos.sphere.core.data.PuckSlot.Empty ->
                        com.bos.sphere.feature.grid.PuckAction(L10n.tr("Drawer shortcut %d, empty", i + 1), onLongClick = { wheelOpen = false; openSettings(com.bos.sphere.ui.CATEGORY_APP_DRAWER) }) {}
                    is com.bos.sphere.core.data.PuckSlot.Action -> {
                        val id = slot.id
                        com.bos.sphere.feature.grid.PuckAction(
                            L10n.tr(id.label), com.bos.sphere.feature.grid.Glyphs.forAction(id),
                            available = com.bos.sphere.core.data.PuckAvailability.of(id, hasFlash = torchOk, hasPolicyAccess = dndOk),
                        ) {
                            if (id == CornerActionId.Search) { wheelOpen = false; searchOpen = true }
                            else { if (id.internal) wheelOpen = false; runner.run(id) }
                        }
                    }
                    is com.bos.sphere.core.data.PuckSlot.App -> {
                        val app = appsByKey[slot.key]
                        com.bos.sphere.feature.grid.PuckAction(
                            app?.label ?: L10n.tr("App not installed"), if (app == null) com.bos.sphere.feature.grid.Glyphs.Apps else null, app, available = app != null,
                        ) { wheelOpen = false; runner.launchKey(slot.key) }
                    }
                }
            },
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
        remember(context) { com.bos.sphere.core.data.CommsScaleSettings.init(context); com.bos.sphere.ui.CommsOptions.init(context); true }
        val commsDefaultView by com.bos.sphere.ui.CommsOptions.defaultView.collectAsState() // wave 29: the Default view row shows the live value
        val commsClearShade by com.bos.sphere.ui.CommsOptions.clearFromShade.collectAsState() // W42: the shade toggle shows the live value
        val commsWidgetsOn by com.bos.sphere.ui.CommsOptions.widgetsSection.collectAsState() // W52: the Widgets section switch and its place show the live values
        val commsWidgetsSlot by com.bos.sphere.ui.CommsOptions.widgetsSlot.collectAsState()
        val commsScalePct by com.bos.sphere.core.data.CommsScaleSettings.percent.collectAsState() // wave 28: the Comms size row shows the live value
        remember(context) { com.bos.sphere.core.data.LaunchpadSettings.init(context); true }
        val commsPrefs by com.bos.sphere.core.data.LaunchpadSettings.prefs.collectAsState() // wave 53: Show pinned at the top and the default category show the live values
        val commsHideRules by com.bos.sphere.core.data.LaunchpadSettings.rules.collectAsState() // wave 38: Hidden apps also lists the Comms Hide rules
        val hiddenAppsAccess = remember(allApps, hidden, commsHideRules) {
            com.bos.sphere.ui.HiddenAppsAccess(
                count = com.bos.sphere.core.data.HiddenAppsModel.rows(com.bos.sphere.ui.candidates(allApps), hidden, commsHideRules).size,
                hiddenKeys = hiddenStore.hidden, allApps = allApps, setHidden = hiddenStore::setHidden,
            )
        }
        val setupState = com.bos.sphere.ui.rememberSetupState(corners, gestures, active = settingsOpen) // wave 56 (CFG-26): the Finish setup row
        val categories = remember(setupState, hiddenAppsAccess, prefs, pages, layout, apps, corners, gestures, settingsPage, isLandscape, leftWidgetName, actionAppsRev, capabilityRows, labEntries.map { it.result }, commsScalePct, commsDefaultView, commsPrefs, commsClearShade, commsWidgetsOn, commsWidgetsSlot, legacyAvailable, puckSegments, puckLabels) {
            buildSettingsCategories(
            SettingsModel(
                setup = setupState,
                hiddenApps = hiddenAppsAccess,
                puckSegments = puckSegments,
                setPuckSegments = { n -> com.bos.sphere.core.data.PuckActionsStore.get(context).setSegments(n) },
                puckLabels = puckLabels,
                setPuckLabels = { v -> com.bos.sphere.core.data.PuckActionsStore.get(context).setShowLabels(v) },
                chosenApp = { id -> actionAppsRev.let { ActionPrefs.get(context, id)?.let(appsByKey::get)?.label } },
                pickAppFor = { id -> pickFor = id },
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
                deletePage = { i -> requestDeletePage(i); settingsPage = (i - 1).coerceAtLeast(0) },
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
                about = about,
                capabilities = capabilityRows,
                copyText = { t -> (context.getSystemService(android.content.ClipboardManager::class.java)).setPrimaryClip(android.content.ClipData.newPlainText("Liftoff", t)) },
                lab = labEntries,
                addHomePage = { settingsOpen = false; requestAddPage(null) },
                restorePages = if (legacyAvailable) ({ if (com.bos.sphere.core.data.PagesExplicitMigration.restoreReplacesEdits(stores.layout)) { settingsOpen = false; confirmRestore = true } else restorePreviousPages() }) else null,
                editHomePages = { settingsOpen = false; editHome() },
                addHomeItems = { settingsOpen = false; homeItems.openSheet() }, // wave 34: the item picker left the long-press sheet
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
                        com.bos.sphere.core.design.GrantHooks.ask(com.bos.sphere.core.design.GrantKind.Notifications) { // CFG-07
                            runCatching { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                        }
                    },
                    dndAccess = {
                        runCatching { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    },
                    androidSettings = { runner.run(CornerActionId.SystemSettings) },
                    usageAccess = { com.bos.sphere.core.design.GrantHooks.ask(com.bos.sphere.core.design.GrantKind.UsageAccess) { runCatching { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } } },
                ),
            ),
            )
        }
        androidx.compose.runtime.SideEffect { settingsForRadar.value = categories }
        SettingsSpace(
            visible = settingsOpen,
            categories = categories,
            initialCategory = settingsCategoryIndex(categories, settingsCategory),
            sounds = sounds,
            onClose = { settingsOpen = false },
            modifier = Modifier.fillMaxSize(),
            session = settingsSession,
            previewDock = { SettingsPreviewDock(homeCapture) },
            snapshots = remember(context) { com.bos.sphere.ui.StateSnapshots(context) },
            summaries = com.bos.sphere.ui.settingsSummaries(prefs.homePages, pages.size, gestures.size, hiddenAppsAccess.count, categories, pageIndex = settingsPage, workspaceCount = dockState.workspaces.size,
                sidePanelWidgets = runCatching { com.bos.sphere.ui.HubPrefs.of(context).let { if (it.widgetsSection) it.widgetList.size else 0 } }.getOrDefault(0)),
            backdrop = {
                if (prefs.settingsBackdrop == com.bos.sphere.core.data.SettingsBackdrop.Black) Box(Modifier.fillMaxSize().background(Color.Black))
                else Box(Modifier.fillMaxSize()) {
                    com.bos.sphere.feature.grid.HomeSurfaceBackground(com.bos.sphere.core.data.WallpaperSurface.Glance, prefs, Modifier.fillMaxSize(), baseFill = true, animate = false, announce = false, scrim = false, play = false, alert = false, dark = true) // wave 34: the wallpaper of any kind
                    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = prefs.settingsDim.coerceIn(0f, 1f))))
                }
            },
        )

        // ---- dialogs
        LaunchedEffect(menuApp) { if (menuApp == null) menuRecents = false }
        menuApp?.let { app ->
            AppContextMenu(
                app = app,
                isHidden = app.key in hidden,
                onDismiss = { menuApp = null },
                onOpen = { menuApp = null; launch(app) },
                onAppInfo = { repository.openAppDetails(app); menuApp = null },
                onUninstall = { repository.requestUninstall(app); menuApp = null },
                onToggleHidden = { hiddenStore.setHidden(app.key, app.key !in hidden); menuApp = null },
                onIconColor = { colorApp = app; menuApp = null },
                extraEntries = com.bos.sphere.feature.dock.AppActionList.build(appLauncher, com.bos.sphere.feature.dock.WorkspaceRepository.get(context), app, lastTouch.sourceRect()) { menuApp = null }
                    .map { it.label to it.run } + homeItems.appMenuEntries(app) { menuApp = null } +
                    // HOM-22: on the Recent apps landing the app menu also offers to take the app off the list, or to clear the list.
                    (if (menuRecents && rightOpen) listOf( // STR-14: the Recents panel offers the same two entries
                        L10n.tr("Remove from recents") to { stores.recentsState.remove(app.key); menuApp = null },
                        L10n.tr("Clear recents") to { stores.recentsState.clear(); menuApp = null },
                    ) else emptyList()),
                onHomeSettings = if (!prefs.isPaged) {
                    { menuApp = null; openSettings(com.bos.sphere.ui.CATEGORY_ROOT) }
                } else null,
                // Only the paged home has pages to add to; the scrolling homes show every app.
                isOnHome = if (prefs.isPaged) pages.any { app.key in it.keys } else null,
                onToggleHome = {
                    if (pages.any { app.key in it.keys }) stores.layout.removeFromHome(app.key, pages, preserve)
                    else if (stores.layout.addToHome(app.key, pages, gridState.currentPage, capacityOf, preserve)) homeItems.onPlaced() else android.widget.Toast.makeText(context, L10n.tr("Page full. Add a page or remove an item."), android.widget.Toast.LENGTH_SHORT).show() // wave 24: Glance shows the pages it was placed on
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
                    TextButtonPill(L10n.tr("Done"), primary = true) { colorApp = null }
                    TextButtonPill(L10n.tr("Reset")) { stores.iconColors.set(app.key, null); colorApp = null }
                },
            ) {
                ColorPicker(current, { v -> stores.iconColors.set(app.key, v) })
            }
        }
        pendingDelete?.let { i ->
            PageDeleteConfirm(
                com.bos.sphere.core.data.HomePageOps.itemCount(pages, i),
                onConfirm = { pendingDelete = null; deletePageNow(i, pages, layout, preserve, appsByKey, homeItems, stores) },
                onDismiss = { pendingDelete = null },
            )
        }
        if (confirmRestore) {
            ConfirmDialog(
                title = L10n.tr("Restore previous pages"),
                text = L10n.tr("Your current pages will be replaced by the previous ones."),
                confirm = L10n.tr("Restore previous pages"),
                dismiss = L10n.tr("Cancel"),
                onConfirm = { confirmRestore = false; restorePreviousPages() },
                onDismiss = { confirmRestore = false },
            )
        }
        if (resetNotice) {
            // Wave 30: the one-time notice after an auto-filled home was reset. Restoring is also in Settings, Home screen, for 30 days.
            ConfirmDialog(
                title = L10n.tr("Home pages were reset"),
                text = L10n.tr("Home pages were reset: they held every app automatically. Add your own in Edit home."),
                confirm = L10n.tr("Restore previous pages"),
                dismiss = L10n.tr("OK"),
                onConfirm = { restorePreviousPages(); resetNotice = false },
                onDismiss = { stores.layout.setNoticePending(false); resetNotice = false },
            )
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
                page = quickPage?.takeIf { landing == HomeShown.Pages && prefs.homeStyle == HomeStyle.Grid && prefs.homePages == com.bos.sphere.core.data.HomePages.Paginated },
                pageCount = pages.size,
                onDeletePage = { requestDeletePage(quickPage ?: 0); quickOpen = false },
                onWallpaper = { quickOpen = false; openSettings(settingsCategoryIndex(categories, CATEGORY_HOME)) }, // HOM-16 (wave 26): the wallpaper rows are in Home screen
                onGroundControl = { quickOpen = false; openSettings(com.bos.sphere.ui.CATEGORY_ROOT) }, // wave 40: the root list of every category
                onDismiss = { quickOpen = false },
                // W51: the same entry points as the edit tray: Edit home, the app picker and the widget picker (Pages turned on first when they are off).
                onEditHome = { quickOpen = false; editHome() },
                onAddApps = { quickOpen = false; editHome(); trayRequest = com.bos.sphere.feature.grid.TrayPanel.Apps },
                onAddWidgets = { quickOpen = false; editHome(); trayRequest = com.bos.sphere.feature.grid.TrayPanel.Widgets },
                profiles = dockState, onProfile = { id -> quickOpen = false; com.bos.sphere.feature.dock.WorkspaceRepository.get(context).switchTo(id) }, // wave 57 (CFG-48)
            )
        }
        if (homeItems.panelRequest >= 0) {
            // Wave 25 (hubData): Files and Timeline are views of the Launchpad hub; the old sheet is gone and every entry point lands there (Usage opens Timeline, wave 28).
            LaunchedEffect(homeItems.panelRequest) {
                val tab = com.bos.sphere.core.catalog.PanelTab.entries.getOrNull(homeItems.panelRequest) ?: com.bos.sphere.core.catalog.PanelTab.Files
                com.bos.sphere.ui.LaunchpadHubNav.request(com.bos.sphere.ui.HubView.forPanel(tab.uriName))
                homeItems.panelRequest = -1
                if (prefs.leftPage == com.bos.sphere.core.data.LeftPage.Launchpad) leftOpen = true else hubStandalone = true
            }
        }
        // Wave 29: Ground Control's Comms rows (Read history, Groups and rules) bring Comms up through the same request the panel tiles use.
        val commsOpenSignal by com.bos.sphere.ui.LaunchpadHubNav.openSignal.collectAsState()
        LaunchedEffect(commsOpenSignal) {
            if (commsOpenSignal) {
                com.bos.sphere.ui.LaunchpadHubNav.openSignal.value = false
                settingsOpen = false
                if (prefs.leftPage == com.bos.sphere.core.data.LeftPage.Launchpad) leftOpen = true else hubStandalone = true
            }
        }
        // HOM-06: the work profile pause chip, shown while a work profile exists.
        val workPaused by com.bos.sphere.core.catalog.WorkProfileState.workPaused.collectAsStateWithLifecycle()
        androidx.compose.runtime.DisposableEffect(Unit) {
            val reg = com.bos.sphere.core.catalog.WorkProfileState.start(context)
            onDispose { reg.close() }
        }
        // Wave 48: while the disc is open it carries its own pause pill (the Work scope), so the home's chip steps aside.
        if (workPaused != null && !wheelOpen && !homeEdit.active && HomeChromeRules.shows(landing, ChromeElement.WorkPausedBanner, chromeSettings)) {
            Box(Modifier.fillMaxSize().safeDrawingPadding().padding(top = 8.dp), contentAlignment = Alignment.TopCenter) {
                com.bos.sphere.feature.grid.WorkPauseChip(workPaused, onToggle = { com.bos.sphere.core.catalog.WorkProfileState.setWorkPaused(context, workPaused != true) })
            }
        }
        val editDensity = androidx.compose.ui.platform.LocalDensity.current
        val safeTopDp = with(editDensity) { androidx.compose.foundation.layout.WindowInsets.safeDrawing.getTop(this).toDp().value }
        // W48: the tray never lies over the page's dimmed bottom hotspots and the hint never over the top ones (the chrome uses the same two functions).
        val editTrayBottomDp = com.bos.sphere.feature.grid.EditChromeGeometry.trayBottomDp(chromePage, editDensity.density, maxOf(if (bottomPlan.railTopDp > 0f) bottomPlan.railTopDp + 8f else 8f, if (indicatorShown) 60f else 8f))
        val editHintTopDp = com.bos.sphere.feature.grid.EditChromeGeometry.hintTopPx(chromePage, safeTopDp * editDensity.density, editDensity.density) / editDensity.density
        if (homeEdit.active) {
            if (pageEditorOn) {
                BackHandler { chromeState.requestCancel(homeEdit.hasChanges) { homeEdit.cancel() } } // W47: Back in the page editor is Cancel, as in the single-page edit mode
                com.bos.sphere.feature.grid.EditCancelDialog(chromeState) { homeEdit.cancel() }
            } else {
                com.bos.sphere.feature.grid.EditFixedChromeShield(chromePage)
            }
            com.bos.sphere.feature.grid.PageEditorGhost(editCard.drag) // wave 36: the dragged app's icon above the live page
            ItemEditHost(
                itemEdit,
                ItemEditOps(
                    appOf = { appsByKey[it] },
                    labelOf = { k -> appsByKey[k]?.label ?: if (k == HomeTiles.CLOCK) L10n.tr("Clock") else if (k == HomeTiles.SETTINGS) L10n.tr("Settings") else homeItems.labelOf(k) },
                    hidden = hidden,
                    clock = com.bos.sphere.feature.grid.ClockTileOptions.of(prefs),
                    onClock = { c -> stores.prefs.update { it.copy(clockTileAnalog = c.analog, clockTileFormat = c.format, clockTileDate = c.date) } },
                    remove = { key -> val a = appsByKey[key]; if (a != null) stores.layout.removeFromHome(a.key, pages, preserve) else if (HomeTiles.isTile(key)) savePages(HomeLayoutMath.remove(pages, key)) else homeItems.removeFromHome(key) },
                    undo = { homeEdit.undo() },
                    launch = { a -> homeEdit.exit(); launch(a) },
                    appInfo = { repository.openAppDetails(it) },
                    changeIcon = { homeItems.iconFor = it },
                    setHidden = { a, h -> hiddenStore.setHidden(a.key, h) },
                    uninstall = { repository.requestUninstall(it) },
                    openItem = { homeItems.openItem(it) },
                    startResize = { homeItems.startResize(it) },
                    canReconfigure = { homeItems.canReconfigure(it) },
                    reconfigure = { homeItems.reconfigureItem(it) },
                    resizeTo = { k, c, r -> homeItems.resizeTo(k, c, r) },
                    presetBlocked = { k, c, r -> homeItems.presetBlocked(k, c, r) },
                    widgetLimits = { homeItems.widgetLimits(it) },
                    spanOf = { k -> extrasState.spanOf(k).let { it.cols to it.rows } },
                    grid = { homeItems.gridSize() },
                    dissolveFolder = { homeItems.dissolveFolder(it) },
                    editFolder = { homeItems.editFolder(it) },
                ),
                // W47: the room the bar may use is what the chrome leaves: below the top bar (and the hint), above the tray (which sits above the dock), clear of a side dock.
                reserveTop = com.bos.sphere.feature.grid.EditChromeGeometry.actionBarReserveDp(safeTopDp, showEditHint && !pageEditorOn, editTrayBottomDp, tray = !pageEditorOn, hintTopDp = editHintTopDp).first.dp,
                reserveBottom = com.bos.sphere.feature.grid.EditChromeGeometry.actionBarReserveDp(safeTopDp, showEditHint && !pageEditorOn, editTrayBottomDp, tray = !pageEditorOn, hintTopDp = editHintTopDp).second.dp,
                scrollSurface = prefs.homePages == com.bos.sphere.core.data.HomePages.Continuous && !pageEditorOn,
                avoid = remember(dockReserve.start, dockReserve.end, homeSizePx) {
                    val dd = editDensity.density
                    buildList {
                        if (dockReserve.start > 0f) add(androidx.compose.ui.geometry.Rect(0f, 0f, dockReserve.start * dd, homeSizePx.height.toFloat()))
                        if (dockReserve.end > 0f) add(androidx.compose.ui.geometry.Rect(homeSizePx.width - dockReserve.end * dd, 0f, homeSizePx.width.toFloat(), homeSizePx.height.toFloat()))
                    }
                },
            )
        }
        // W45 (editChrome): the single-page edit mode's top bar, hint and tray. Composed only while editing the page itself.
        com.bos.sphere.feature.grid.HomeEditChrome(
            panelRequest = trayRequest, onPanelRequestHandled = { trayRequest = com.bos.sphere.feature.grid.TrayPanel.None },
            visible = homeEdit.active && !pageEditorOn,
            progress = chromeProgress, page = chromePage,
            pageIndex = if (prefs.homePages == com.bos.sphere.core.data.HomePages.Continuous) editCard.contSel.intValue else gridState.currentPage,
            pageCount = pages.size,
            undoCount = homeEdit.undoDepth, redoCount = homeEdit.redoDepth, canUndo = homeEdit.canUndo, canRedo = homeEdit.canRedo,
            hasChanges = homeEdit.hasChanges, showHint = showEditHint,
            showAddPage = prefs.homePages != com.bos.sphere.core.data.HomePages.Off,
            trayBottomDp = editTrayBottomDp, // above the dock, and above the page dots when there is no dock
            state = chromeState,
            onUndo = { homeEdit.undo() }, onRedo = { homeEdit.redo() },
            onDone = { homeEdit.exit() }, onCancel = { homeEdit.cancel() },
            onFolders = { homeItems.folderOpen = true },
            onAddPage = { editCard.addPage() },
            onOpenPageEditor = { pageEditorOn = true },
            touchSignal = glanceWake.intValue,
            // W51 (addBrowse): the tray's Apps and Widgets chips open the carousel in place; a tap places the app on the page being edited, a widget goes through the picker's bind and configure flow.
            apps = apps,
            onHome = remember(layout, extrasState) { layout.pages.flatMapTo(HashSet()) { it.keys } + extrasState.folders.values.flatMapTo(HashSet()) { it.keys } },
            hasWork = remember(apps) { apps.any { it.user != android.os.Process.myUserHandle() } },
            isWork = { it.user != android.os.Process.myUserHandle() },
            onAddApp = { app ->
                val at = (if (prefs.homePages == com.bos.sphere.core.data.HomePages.Continuous) editCard.contSel.intValue else gridState.currentPage).coerceIn(0, (pages.size - 1).coerceAtLeast(0))
                val placed = com.bos.sphere.core.data.AddCarouselModel.place(pages, app.key, at, capacityOf)
                if (placed == null) android.widget.Toast.makeText(context, L10n.tr("Page full. Add a page or remove an item."), android.widget.Toast.LENGTH_SHORT).show()
                else {
                    stores.layout.replace(layout.copy(pages = com.bos.sphere.core.data.HomeLayoutMath.toStored(placed, layout.pages, preserve), removed = layout.removed - app.key, seeded = true))
                    stores.layout.markExplicit()
                }
            },
            onAddWidget = { homeItems.widgetChoice = it },
        )
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
        popupSheetHost.Layer() // wave 38: above the sliding Comms underlay and the shrunken home card, across the whole window
        homeItems.Overlays(hiddenStore, hidden)
        androidx.compose.runtime.SideEffect { homeItems.selectHook = { key -> selectItem(key) } }
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

/**
 * Wave 26: a horizontal swipe on the home deck. [accepts] says which direction counts (from [com.bos.sphere.core.data.HomeOrder]: forward = towards the next
 * page, or towards the Launchpad edge), [ready] says whether the surface is settled, and a gesture that starts inside [edgeExcludePx] of either edge is left to the
 * edge drags (Launchpad, Recents). The rest of the gesture is swallowed so the pager does not also react.
 */
private fun Modifier.deckSwipe(enabled: Boolean, accepts: (Float) -> Boolean, ready: () -> Boolean, edgeExcludePx: Float = 0f, onTrigger: () -> Unit): Modifier =
    if (!enabled) this else this.pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = androidx.compose.ui.input.pointer.PointerEventPass.Initial)
            if (!ready()) return@awaitEachGesture
            if (edgeExcludePx > 0f && (down.position.x < edgeExcludePx || down.position.x > size.width - edgeExcludePx)) return@awaitEachGesture
            val slop = viewConfiguration.touchSlop
            var dx = 0f
            var dy = 0f
            while (true) {
                val ev = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                if (!ch.pressed) break
                val d = ch.positionChange()
                dx += d.x; dy += d.y
                if (kotlin.math.abs(dx) > slop * 3 && kotlin.math.abs(dx) > kotlin.math.abs(dy) * 2 && accepts(dx)) {
                    ch.consume()
                    while (true) {
                        val e2 = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                        e2.changes.forEach { it.consume() }
                        if (e2.changes.none { it.pressed }) break
                    }
                    // Triggered on release: a pager that took the first pixels of this same drag settles back first, so the move to the previous stop is not interrupted by it.
                    onTrigger()
                    break
                }
                if (kotlin.math.abs(dy) > slop * 3 && kotlin.math.abs(dy) > kotlin.math.abs(dx)) break
            }
        }
    }
