package com.bos.sphere

import android.content.res.Configuration
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bos.sphere.core.data.HiddenAppsStore
import com.bos.sphere.core.data.LauncherAppsAppRepository
import com.bos.sphere.core.data.VoiceInputManager
import com.bos.sphere.core.design.HyleMotion
import com.bos.sphere.feature.pages.ui.AppWidgetPageHooks
import com.bos.sphere.feature.pages.ui.PageDots
import com.bos.sphere.feature.pages.ui.PagesDialogs
import com.bos.sphere.feature.pages.ui.PagesHost
import com.bos.sphere.feature.pages.ui.PagesSettingsSection
import com.bos.sphere.feature.pages.ui.PastEndHint
import com.bos.sphere.feature.pages.ui.SyncWidgetPages
import com.bos.sphere.feature.pages.ui.rememberPagesUi
import com.bos.sphere.feature.search.AppSearch
import com.bos.sphere.feature.sphere.EquatorItem
import com.bos.sphere.feature.sphere.RowMode
import com.bos.sphere.feature.sphere.SphereParams
import com.bos.sphere.feature.sphere.LauncherSurface
import com.bos.sphere.feature.sphere.rememberLauncherSurfaceState
import com.bos.sphere.feature.search.SearchKeyboard
import com.bos.sphere.feature.widgets.WidgetHostActivityBinding
import com.bos.sphere.feature.widgets.WidgetSurface
import com.bos.sphere.feature.widgets.ui.Dialogs
import com.bos.sphere.feature.widgets.ui.EquatorTile
import com.bos.sphere.feature.widgets.ui.FocusNewWidgets
import com.bos.sphere.feature.widgets.ui.HubStack
import com.bos.sphere.feature.widgets.ui.PageWidget
import com.bos.sphere.feature.widgets.ui.equatorItems
import com.bos.sphere.feature.widgets.ui.pageSlots
import com.bos.sphere.feature.widgets.ui.rememberWidgetHostUi
import com.bos.sphere.ui.AppContextMenu
import com.bos.sphere.ui.Chrome
import com.bos.sphere.ui.DefaultLauncherBanner
import com.bos.sphere.ui.DotGridBackground
import com.bos.sphere.ui.HubPanel
import com.bos.sphere.ui.SettingsPanel
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The home root. Builds the (hidden-filtered) inventory, resolves the row split, hosts the
 * interactive [LauncherSurface], and overlays chrome, the hub, settings, the long-press menu and
 * the set-default prompt. Surface params are hoisted here so the settings panel tunes them live.
 *
 * Hosted App Widgets ([widgetHost]) join the equator as tiles between apps, or sit in the hub's
 * widget stack; the globe and search stay apps-only.
 *
 * Pages (`:feature-pages`): all of the above is page 0 of an ordered set of pages; the others are
 * full-page App Widgets, HTML widgets / pages run by the widget runtime, and websites. The pager
 * reads touches before the coverflow and claims only page gestures (per the chosen navigation
 * mode); the coverflow yields a claimed gesture ([LauncherSurface]'s `gestureClaimed`).
 */
@Composable
fun HomeScreen(homeSignal: Int, hubSignal: Int = 0, widgetHost: WidgetHostActivityBinding? = null) {
    val context = LocalContext.current
    val repository = remember { LauncherAppsAppRepository(context) }
    val hiddenStore = remember { HiddenAppsStore(context) }

    val allApps by repository.apps.collectAsStateWithLifecycle(initialValue = emptyList())
    val hidden by hiddenStore.hidden.collectAsStateWithLifecycle()
    val apps = remember(allApps, hidden) { allApps.filter { it.key !in hidden } }

    val configuration = LocalConfiguration.current
    val isPortrait = configuration.orientation == Configuration.ORIENTATION_PORTRAIT

    var rowMode by remember { mutableStateOf(RowMode.Auto) }
    val rows = when (rowMode) {
        RowMode.One -> 1
        RowMode.Two -> 2
        RowMode.Auto -> if (isPortrait) 2 else 1
    }

    var params by remember { mutableStateOf(SphereParams()) }
    var tilesMode by remember { mutableStateOf(false) }
    var rubikMode by remember { mutableStateOf(false) }
    var settingsOpen by remember { mutableStateOf(false) }
    var menuApp by remember { mutableStateOf<com.bos.sphere.core.data.AppEntry?>(null) }

    // Equator = apps with hosted widgets merged in at their slots.
    val widgetUi = rememberWidgetHostUi(widgetHost)
    val equatorItems = widgetUi.equatorItems(apps)
    val scope = rememberCoroutineScope()
    // Packages added / removed / changed reach us through the repository's LauncherApps.Callback;
    // re-check hosted widgets on the same signal.
    LaunchedEffect(allApps) { widgetUi.onPackagesChanged() }

    val surface = rememberLauncherSurfaceState(itemCount = equatorItems.size, rows = rows, params = params)

    // Pages: page 0 is everything below; App Widget pages are hosted by widgetUi (placements on the
    // Page surface, linked to their page by slot).
    val pagesUi = rememberPagesUi()
    val pageSlots = widgetUi.pageSlots()
    val slotsState = rememberUpdatedState(pageSlots)
    val pageHooks: AppWidgetPageHooks? = if (widgetUi.enabled) {
        remember(widgetUi) {
            object : AppWidgetPageHooks {
                override val placedSlots: Set<Int> get() = slotsState.value
                override fun label(slot: Int) = widgetUi.pageLabel(slot)
                override fun add(slot: Int) = widgetUi.openPagePicker(slot)
                override fun remove(slot: Int) = widgetUi.removePageWidget(slot)
                override fun canReconfigure(slot: Int) = widgetUi.canReconfigurePage(slot)
                override fun reconfigure(slot: Int) = widgetUi.reconfigurePage(slot)
            }
        }
    } else {
        null
    }
    if (pageHooks != null) pagesUi.SyncWidgetPages(pageSlots)
    val homeRegion = remember { arrayOfNulls<LayoutCoordinates>(1) }
    val homeAtRest = { surface.hubValue < 0.01f && surface.zoomValue < 0.01f }
    val nearHome by remember { derivedStateOf { abs(pagesUi.position.value) < 0.999f } }
    // The settings panel belongs to home: going to a page (from the page manager opened in it, the
    // dots, a gesture) closes it, so Back on that page doesn't first meet a panel left open behind it.
    LaunchedEffect(nearHome) { if (!nearHome) settingsOpen = false }

    // Centre the equator on the middle app the first time the inventory arrives.
    var centred by remember { mutableStateOf(false) }
    LaunchedEffect(apps.size) {
        if (!centred && apps.isNotEmpty()) {
            surface.sphereState.position = (surface.sphereState.maxIndex / 2).toFloat()
            centred = true
        }
    }

    // Home-key: collapse globe + hub back to the equator.
    LaunchedEffect(homeSignal) {
        if (homeSignal > 0) {
            settingsOpen = false
            menuApp = null
            widgetUi.dismissAll()
            pagesUi.goHome()
            surface.zoom.animateTo(0f)
            surface.hub.animateTo(0f)
        }
    }

    // A Needs-you home-screen widget asked for the hub: home, as the home key leaves it, then the
    // hub pulled down (its notifications are what the widget counts).
    LaunchedEffect(hubSignal) {
        if (hubSignal > 0) {
            settingsOpen = false
            menuApp = null
            widgetUi.dismissAll()
            pagesUi.goHome()
            surface.zoom.animateTo(0f)
            surface.hub.animateTo(1f, HyleMotion.settle())
        }
    }

    val chromeAlpha = (1f - smooth(surface.zoomValue)) * (1f - surface.hubValue)

    // Clean up voice resources on disposal
    DisposableEffect(Unit) {
        onDispose {
            VoiceInputManager.release()
        }
    }

    // Handle voice input: search for apps and launch or focus the first match
    val handleVoiceInput = { query: String ->
        val results = AppSearch.rank(apps, query, limit = 1)
        if (results.isNotEmpty()) {
            val app = results[0]
            // If it's a unique/clear match, launch directly. Otherwise focus on equator.
            if (query.lowercase() == app.label.lowercase()) {
                repository.launch(app)
            } else {
                val itemIndex = equatorItems.indexOfFirst { it is EquatorItem.App && it.entry.key == app.key }
                if (itemIndex >= 0) {
                    surface.sphereState.position = surface.sphereState.columnOf(itemIndex).toFloat()
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        DotGridBackground(Modifier.fillMaxSize())

        pagesUi.PagesHost(
            homeAtRest = homeAtRest,
            homeLongPressOpened = { surface.longPressOpened },
            homeRegion = { homeRegion[0] },
            appWidgets = pageHooks,
            appWidgetPage = { page, interactive -> widgetUi.PageWidget(page.widgetSlot, interactive) },
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                LauncherSurface(
                    apps = apps,
                    state = surface,
                    onLaunch = { repository.launch(it) },
                    onLongPressApp = { menuApp = it },
                    onOpenSettings = { settingsOpen = true },
                    modifier = Modifier
                        .fillMaxSize()
                        // In portrait, leave room for the always-keyboard so the equator stays clear.
                        .padding(bottom = if (isPortrait) 300.dp else 0.dp)
                        .onGloballyPositioned { homeRegion[0] = it },
                    equatorItems = equatorItems,
                    onLongPressWidget = { widgetUi.openMenu(it.appWidgetId) },
                    onLongPressEmpty = {
                        widgetUi.openPicker(WidgetSurface.Equator, surface.sphereState.focusedItemIndex())
                    },
                    touchPassThrough = widgetUi.passThrough,
                    widgetTile = { item, interactive ->
                        widgetUi.EquatorTile(item, interactive, lens = surface.sphereState.focusScale)
                    },
                    gestureClaimed = pagesUi::gestureClaimed,
                    onScrubReleased = { past -> pagesUi.onScrubReleased(past, homeAtRest()) },
                ) { fraction ->
                    HubPanel(fraction = fraction) { sharesPanel ->
                        widgetUi.HubStack(hubFraction = fraction, sharesPanel = sharesPanel)
                    }
                }

                // Chrome (clock / weather / battery), fading out of the way for globe + hub. Part of the
                // home page, so it slides away with it.
                Chrome(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = chromeAlpha },
                    onOpenSettings = { settingsOpen = true },
                    onVoiceInput = handleVoiceInput,
                )

                // Mode 2: names the page a further pull past the coverflow's end opens.
                pagesUi.PastEndHint(
                    pastEndColumns = { surface.scrubPastEnd },
                    hooks = pageHooks,
                    modifier = Modifier.align(Alignment.Center).padding(top = 220.dp),
                )
            }
        }

        // Bring a just-added widget into view: scrub to it, or open the hub it went to. An equator
        // widget added or moved from inside the hub would sit behind it, so the hub (and globe)
        // close first.
        widgetUi.FocusNewWidgets(
            items = equatorItems,
            onFocusEquatorIndex = { index ->
                scope.launch { surface.hub.animateTo(0f, HyleMotion.give()) }
                scope.launch { surface.zoom.animateTo(0f, HyleMotion.give()) }
                scope.launch {
                    Animatable(surface.sphereState.position)
                        .animateTo(surface.sphereState.columnOf(index).toFloat(), HyleMotion.settle()) {
                            surface.sphereState.position = value
                        }
                }
            },
            onOpenHub = { scope.launch { surface.hub.animateTo(1f, HyleMotion.settle()) } },
            onOpenPage = { slot -> pagesUi.showWidgetPage(slot) },
        )

        // Page dots: the fallback in every mode; hidden while only home exists, and while the globe or
        // hub covers home (so an invisible dot can't take a tap). Bottom centre in every mode, clear of
        // the equator in both orientations; on home in portrait they sit above the keyboard and glide
        // down with the turn (a layout-phase offset, so nothing jumps when the turn ends).
        val keyboardShown = isPortrait && surface.zoomValue < 0.4f && surface.hubValue < 0.5f
        if (!nearHome || chromeAlpha > 0.05f) {
            pagesUi.PageDots(
                hooks = pageHooks,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 8.dp)
                    .offset {
                        val lift = if (keyboardShown) (1f - abs(pagesUi.position.value)).coerceIn(0f, 1f) else 0f
                        IntOffset(0, -(296.dp.toPx() * lift).roundToInt())
                    }
                    .graphicsLayer { alpha = if (nearHome) chromeAlpha.coerceAtLeast(abs(pagesUi.position.value)) else 1f },
            )
        }

        DefaultLauncherBanner(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .safeDrawingPadding()
                .padding(top = 56.dp),
        )

        SettingsPanel(
            visible = settingsOpen,
            params = params,
            rowMode = rowMode,
            tilesMode = tilesMode,
            rubikMode = rubikMode,
            onParamsChange = { params = it },
            onRowModeChange = { rowMode = it },
            onTilesModeChange = { tilesMode = it },
            onRubikModeChange = { rubikMode = it },
            onClose = { settingsOpen = false },
            modifier = Modifier.align(Alignment.TopEnd),
            onAddWidget = if (widgetUi.enabled) {
                {
                    settingsOpen = false
                    widgetUi.openPicker(WidgetSurface.Equator, surface.sphereState.focusedItemIndex())
                }
            } else {
                null
            },
            pagesSection = {
                pagesUi.PagesSettingsSection()
            },
        )

        // Portrait: the always-open keyboard for type-to-search (hidden in globe / hub, and on full
        // pages: it belongs to home and fades out as home slides away).
        if (nearHome && isPortrait && surface.zoomValue < 0.4f && surface.hubValue < 0.5f) {
            SearchKeyboard(
                apps = apps,
                onLaunch = { repository.launch(it) },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .graphicsLayer { alpha = (1f - abs(pagesUi.position.value)).coerceIn(0f, 1f) },
            )
        }

        menuApp?.let { app ->
            AppContextMenu(
                app = app,
                isHidden = app.key in hidden,
                onDismiss = { menuApp = null },
                onAppInfo = { repository.openAppDetails(app); menuApp = null },
                onUninstall = { repository.requestUninstall(app); menuApp = null },
                onToggleHidden = {
                    hiddenStore.setHidden(app.key, app.key !in hidden)
                    menuApp = null
                },
                onAddWidgetHere = if (widgetUi.enabled) {
                    {
                        val index = equatorItems.indexOfFirst { it is EquatorItem.App && it.entry.key == app.key }
                        widgetUi.openPicker(WidgetSurface.Equator, index)
                        menuApp = null
                    }
                } else {
                    null
                },
            )
        }

        // Widget picker and widget menu (remove / reconfigure / move / hub height).
        widgetUi.Dialogs(
            lens = surface.sphereState.focusScale,
            focusedEquatorIndex = { surface.sphereState.focusedItemIndex() },
        )

        // Page manager, add / import / address, rename, remove, page info.
        pagesUi.PagesDialogs(pageHooks)
    }
}

private fun smooth(t: Float): Float = (t * t * (3 - 2 * t)).coerceIn(0f, 1f)
