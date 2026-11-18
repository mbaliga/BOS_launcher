package com.bos.sphere.feature.grid

import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.platform.testTag
import android.content.Intent
import com.bos.sphere.core.design.predictiveBackLayer
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bos.sphere.core.data.AppEntry
import androidx.compose.runtime.DisposableEffect
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import com.bos.sphere.core.data.CornerActionId
import com.bos.sphere.core.data.SearchCategory
import com.bos.sphere.core.data.SearchHit
import com.bos.sphere.core.data.SearchHub
import com.bos.sphere.core.data.SearchLayout
import kotlinx.coroutines.flow.collectLatest
import com.bos.sphere.core.data.Channel
import com.bos.sphere.core.data.ContactActions
import com.bos.sphere.core.data.ContactHit
import com.bos.sphere.core.data.ContactLogic
import com.bos.sphere.core.data.ContactSource
import com.bos.sphere.core.data.LauncherPrefs
import com.bos.sphere.core.data.WebSearch
import com.bos.sphere.core.design.HyleColors
import com.bos.sphere.core.design.L10n
import com.bos.sphere.feature.search.AppSearch
import com.bos.sphere.feature.search.palette.PaletteHitRunner
import com.bos.sphere.feature.search.palette.PaletteHost
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.onPreviewKeyEvent
import com.bos.sphere.core.input.InputMode
import com.bos.sphere.core.input.InputModeTracker
import com.bos.sphere.core.input.MenuAction
import com.bos.sphere.feature.search.SectionKind
import com.bos.sphere.feature.search.Suggestion
import com.bos.sphere.feature.search.SuggestionKind
import com.bos.sphere.feature.search.WebRow
import com.bos.sphere.feature.search.sections
import com.bos.sphere.feature.search.suggest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Universal search: apps, people (with the ways to reach them, BlackBerry style), actions, files
 * (including Fylz / Fotoz deep hits) and the web (the engine you chose; a leading letter and a space is just text).
 *
 * The layout follows [LauncherPrefs.searchLayout]: Filmstrip, Orbit (both with the category wheel)
 * or the macOS-style Flat list. The query line is always at the top, below the status bar and cutout; the results are
 * below it and the system keyboard is at the bottom (the layout reserves its space with imePadding and works the
 * same without it).
 *
 * New parameters sit after [modifier] so existing callers keep compiling. [onOpenHit] is the host's
 * chance to open a file hit (default: [openHitIntent] through [onOpenIntent]); action hits always
 * go to [onRunAction].
 */
@Composable
fun SearchOverlay(
    visible: Boolean,
    apps: List<AppEntry>,
    recent: List<AppEntry>,
    prefs: LauncherPrefs,
    contacts: ContactSource?,
    onLaunch: (AppEntry) -> Unit,
    onLongPressApp: (AppEntry) -> Unit,
    onOpenIntent: (Intent) -> Unit,
    onRequestContactsPermission: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    hub: SearchHub? = null,
    onOpenHit: ((SearchHit) -> Unit)? = null,
    onRunAction: (CornerActionId) -> Unit = {},
    onRequestFilePermission: () -> Unit = {},
    sounds: UiSounds? = null,
    /** CFG-05: Ground Control's settings as Radar results; null = none. */
    settingsSource: SettingsResultSource? = null,
    /** PAL-01: text already typed when Radar opens (type-to-search from home). */
    initialQuery: String = "",
    /** Wave 25: the scope Radar opens on (Launchpad's compose button opens it on People); null = all. */
    initialCategory: SearchCategory? = null,
    /** PAL-03: a launcher command a result asked for (a core-input LauncherCommand name, and a workspace index if any). */
    onRunCommand: (String, Int?) -> Unit = { _, _ -> },
    /** PAL-08: run a shared menu action (display, beside, dock) straight from the Radar list; false = the host cannot, so the app sheet opens. */
    onRunAppAction: (AppEntry, MenuAction) -> Boolean = { _, _ -> false },
    /** PAL-08: whether "Open beside" is proven on this device (SPK-02); Radar offers it only then. */
    adjacentSupported: Boolean = false,
    /** PAL-08: the choices of an action that needs a pick ("Open on display" with several displays, "Add to workspace"); null = none to offer. */
    onAppActionChoices: (AppEntry, MenuAction) -> List<RadarChoice>? = { _, _ -> null },
    /** Wave 18: the home's launch log; with what Radar learned it decides which ring an app sits on before anything is typed. */
    launchHistory: List<com.bos.sphere.core.data.LaunchEvent> = emptyList(),
) {
    val backProgress = com.bos.sphere.core.design.rememberPredictiveBackProgress(visible, onDismiss)
    AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut(), modifier = modifier.predictiveBackLayer(backProgress)) {
        SearchScreen(
            apps = apps, recent = recent, prefs = prefs, contacts = contacts,
            onLaunch = onLaunch, onLongPressApp = onLongPressApp, onOpenIntent = onOpenIntent,
            onRequestContactsPermission = onRequestContactsPermission, onDismiss = onDismiss,
            hub = hub, onOpenHit = onOpenHit, onRunAction = onRunAction,
            onRequestFilePermission = onRequestFilePermission, sounds = sounds, settingsSource = settingsSource,
            initialQuery = initialQuery, initialCategory = initialCategory, onRunCommand = onRunCommand,
            onRunAppAction = onRunAppAction, adjacentSupported = adjacentSupported, onAppActionChoices = onAppActionChoices,
            launchHistory = launchHistory,
        )
    }
}

/** Contact lookup for [query]: debounced, off the main thread, stale lookups cancelled. */
@Composable
private fun rememberContactRows(query: String, contacts: ContactSource?, want: Boolean): Pair<List<ContactRow>, Boolean> {
    val context = LocalContext.current
    var rows by remember { mutableStateOf<List<ContactRow>>(emptyList()) }
    var permitted by remember { mutableStateOf(true) }
    LaunchedEffect(query, contacts, want) {
        val q = query.trim()
        if (!want || (q.isNotEmpty() && q.length < 2) || contacts == null) {
            rows = emptyList()
            return@LaunchedEffect
        }
        if (q.isNotEmpty()) delay(120)
        val (ok, found) = withContext(Dispatchers.IO) {
            val ok = contacts.hasPermission()
            ok to if (ok) (if (q.isEmpty()) (contacts.byIds(ContactHabits.recentIds(context)) + contacts.recent()).distinctBy { it.id }.take(12) else contacts.search(q)).map {
                val ch = ContactActions.availableChannels(context, it)
                ContactRow(it, ch, ContactHabits.primaryFor(context, it.id, ch))
            } else emptyList()
        }
        permitted = ok
        rows = found
    }
    return rows to permitted
}

/** The search screen proper; internal so tests can start it with a query and a category. */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class) // requestInputMode: a navigation key is keyboard mode (wave 22)
@Composable
internal fun SearchScreen(
    apps: List<AppEntry>,
    recent: List<AppEntry>,
    prefs: LauncherPrefs,
    contacts: ContactSource?,
    onLaunch: (AppEntry) -> Unit,
    onLongPressApp: (AppEntry) -> Unit,
    onOpenIntent: (Intent) -> Unit,
    onRequestContactsPermission: () -> Unit,
    onDismiss: () -> Unit,
    hub: SearchHub?,
    onOpenHit: ((SearchHit) -> Unit)?,
    onRunAction: (CornerActionId) -> Unit,
    onRequestFilePermission: () -> Unit,
    sounds: UiSounds?,
    initialQuery: String = "",
    initialCategory: SearchCategory? = null,
    settingsSource: SettingsResultSource? = null,
    onRunCommand: (String, Int?) -> Unit = { _, _ -> },
    onRunAppAction: (AppEntry, MenuAction) -> Boolean = { _, _ -> false },
    adjacentSupported: Boolean = false,
    onAppActionChoices: (AppEntry, MenuAction) -> List<RadarChoice>? = { _, _ -> null },
    /** Test seam (PAL-08): the number of other displays that can host an app; null asks the capability registry. */
    otherDisplaysOverride: Int? = null,
    launchHistory: List<com.bos.sphere.core.data.LaunchEvent> = emptyList(),
    /** Test seam (wave 18): what Radar learned, key to use; null reads the palette's own store. */
    learnedOverride: Map<String, dev.aarso.search.palette.UsageRecord>? = null,
    /** Test seam (wave 18): the clock the decay of old habits is measured against. */
    nowMillis: () -> Long = { System.currentTimeMillis() },
    /** Test seam (wave 21): whether the push-to-talk button exists; null asks the device (an on-device recogniser is needed, INP-13). */
    voiceAvailable: Boolean? = null,
) {
    val context = LocalContext.current
    val voiceOn = remember(context, voiceAvailable) { voiceAvailable ?: PushToTalk.visible(onDeviceSpeechAvailable(context)) }
    // PAL-08: the chooser of an action that needs a pick (which display, which workspace), shown over Radar.
    var picker by remember { mutableStateOf<RadarPickerState?>(null) }
    // PAL-02, PAL-09: plain app queries also match aliases, initials and a typo, and learn from selections.
    androidx.compose.runtime.SideEffect { AppSearch.appContext = context.applicationContext }
    val palette = remember { PaletteHost.get(context) }
    var aliasRev by remember { mutableStateOf(0) }
    var aliasFor by remember { mutableStateOf<AppEntry?>(null) }
    val otherDisplays = remember { otherDisplaysOverride ?: runCatching { com.bos.sphere.core.capabilities.CapabilityEvaluator.externalDisplays(com.bos.sphere.core.capabilities.CapabilityRegistry(context).snapshot()).count { it.canHostTasks == true } }.getOrDefault(0) }
    val selectedHitHolder = remember { arrayOfNulls<String>(1) }
    val haptic = rememberLauncherHaptics()
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = remember { FocusRequester() }
    // Wave 22 (audit): a hardware keyboard or d-pad reaches the Clear/Close button and the mic (Shift+Tab or Up from the field; Tab or Down
    // when there is nothing to walk); while one of them has focus the keys belong to the focus system and the button (Enter, Space, Tab).
    val inputModeManager = androidx.compose.ui.platform.LocalInputModeManager.current
    val controlFocus = remember { FocusRequester() }
    val micFocus = remember { FocusRequester() }
    var micStripNow by remember { mutableStateOf(0.dp) }
    var barFocused by remember { mutableStateOf(false) }
    // Wave 29 (radarFull): the Orbit is its own layer over the whole display area; where the category wheel sits comes from the layout of the region under the field.
    var orbitGeo by remember { mutableStateOf<OrbitGeo?>(null) }
    var rootCoords by remember { mutableStateOf<androidx.compose.ui.layout.LayoutCoordinates?>(null) }
    var micFocused by remember { mutableStateOf(false) }
    var field by remember { mutableStateOf(TextFieldValue(initialQuery, TextRange(initialQuery.length))) }
    var userCat by remember { mutableStateOf(initialCategory) }
    var expandedContact by remember { mutableStateOf<Long?>(null) }
    var dismissedFor by remember { mutableStateOf<String?>(null) }
    var touched by remember { mutableStateOf(false) }
    val query = field.text
    // A plain query simply searches: no typed prefix is read from it (PAL-13 was removed in wave 18); the wheel is the scope.
    val searchText = query
    val trimmed = searchText.trim()
    // PAL-18: a keyboard or pointer in use means Flat, unless the user pinned a layout.
    val hostMode = LocalInputMode.current.value.let { m -> if (m == InputMode.Touch) LauncherInput.tracker.mode.value else m }
    val tracker = remember { InputModeTracker(hostMode) }
    val inputMode by tracker.mode.collectAsState()
    val layout = RadarLayoutPolicy.effective(prefs.searchLayout, prefs.searchLayoutPinned, inputMode)
    val scale = rememberRadarScale()
    var previewTile by remember { mutableStateOf<Tile?>(null) }
    var nav by remember { mutableStateOf(RadarNav(0)) }
    var orbitVisible by remember { mutableStateOf(Int.MAX_VALUE) }
    val typing = trimmed.isNotEmpty()
    val flatMode = RadarLayoutPolicy.showsFlatList(layout, typing)

    PaletteTraceEffects(query) // NFR-03 / NFR-04: trace sections for the macrobenchmarks
    LaunchedEffect(Unit) {
        delay(60) // let the overlay draw its first frame before the IME animates
        runCatching { focus.requestFocus(); keyboard?.show() }
    }
    DisposableEffect(Unit) { onDispose { runCatching { keyboard?.hide() } } }
    var firstQuery by remember { mutableStateOf(true) } // wave 25: the scope Radar was opened on (initialCategory) survives the first, empty query
    LaunchedEffect(trimmed) {
        touched = false
        expandedContact = null
        if (trimmed.isEmpty() && !firstQuery) userCat = null // an explicit choice sticks until the query is cleared
        firstQuery = false
    }

    // ---- results
    val wantContacts = prefs.searchContacts && contacts != null
    val (contactRows, contactsPermitted) = rememberContactRows(searchText, contacts, wantContacts)
    var hubMap by remember { mutableStateOf<Map<SearchCategory, List<SearchHit>>>(emptyMap()) }
    var fileAccess by remember { mutableStateOf(hub?.hasFileAccess() ?: true) }
    LaunchedEffect(hub, query, prefs.searchFiles) {
        if (hub == null) { hubMap = emptyMap(); return@LaunchedEffect }
        if (query.isNotBlank()) delay(90)
        fileAccess = hub.hasFileAccess()
        // PAL-12: same query as before restores its results at once; late providers never move the selected row; closing cancels this collector.
        RadarMemory.hits.get(trimmed)?.let { hubMap = it }
        val ranker = PaletteHost.ranker(context)
        hub.search(trimmed, prefs).collectLatest { fresh ->
            val ordered = HubOrdering.order(hubMap, fresh, selectedHitHolder[0], ranker)
            hubMap = ordered
            RadarMemory.hits.put(trimmed, ordered)
        }
    }
    // Wave 18: before anything is typed the order is how much you open each thing (decayed, so recent habits count more).
    var learned by remember { mutableStateOf(learnedOverride ?: emptyMap()) }
    LaunchedEffect(learnedOverride) {
        learned = learnedOverride ?: withContext(Dispatchers.IO) { PaletteHost.usage(context).entries() }
    }
    var forgottenAt by remember { mutableStateOf(0L) }
    LaunchedEffect(Unit) { forgottenAt = withContext(Dispatchers.IO) { RadarLearning.forgottenAt(context) } }
    val habit = remember(launchHistory, learned, forgottenAt) { val now = nowMillis(); RadarHabits.scores(launchHistory, learned, now, since = forgottenAt.coerceAtMost(now)) }
    val ranked = remember(apps, recent, trimmed, aliasRev, habit) {
        if (trimmed.isEmpty()) RadarHabits.rankApps(apps, recent, habit).take(RADAR_RESTING_APPS) else AppSearch.rank(apps, trimmed, limit = 24)
    }
    val web = remember(trimmed, prefs.webEngine, prefs.searchWeb) {
        if (trimmed.isEmpty()) emptyList() else sections(
            trimmed, prefs.webEngine, emptyList<AppEntry>(), emptyList<ContactRow>(),
            searchWeb = prefs.searchWeb, searchContacts = false,
        ).web
    }
    val webFirst = remember(trimmed, prefs.searchWeb) {
        prefs.searchWeb && trimmed.isNotEmpty() && WebSearch.looksLikeUrl(trimmed)
    }
    val settingEntries = remember(settingsSource, trimmed, prefs.searchActions) {
        if (settingsSource == null || !prefs.searchActions || trimmed.length < 2) emptyList() else settingsSource.results(trimmed, 8)
    }
    val data = remember(trimmed, ranked, contactRows, contactsPermitted, wantContacts, web, webFirst, hubMap, fileAccess, hub, prefs.searchFiles, settingEntries, habit) {
        SearchData(
            query = trimmed, apps = ranked, habit = habit,
            contacts = if (!wantContacts) emptyList() else if (trimmed.isEmpty()) contactRows.sortedByDescending { habit["contact:${it.hit.id}"] ?: 0.0 } else contactRows,
            askContacts = wantContacts && !contactsPermitted && (trimmed.length >= 2 || trimmed.isEmpty()),
            web = web, webFirst = webFirst,
            hits = hubMap,
            askFiles = hub != null && prefs.searchFiles && !fileAccess,
            deepHint = hub != null && prefs.searchFiles && hub.deepSources().isEmpty(),
            settings = settingEntries,
        )
    }
    val selected: SearchCategory? = userCat
    // Wave 20: the Orbit stays while typing: results on the rings by rank (stable between keystrokes), web hand-offs on the strip.
    val orbitTyped = typing && layout == SearchLayout.Orbit
    val lastRingKeys = remember { arrayOf<List<String>>(emptyList()) }
    val ringTiles = remember(data, selected, orbitTyped) {
        if (!orbitTyped) emptyList() else RadarRings.stableOrder(lastRingKeys[0], data.ringTiles(selected), { it.key })
    }
    val stripTiles = remember(data, orbitTyped) { if (orbitTyped) data.stripTiles() else emptyList() }
    androidx.compose.runtime.SideEffect { lastRingKeys[0] = if (orbitTyped) ringTiles.map { it.key } else emptyList() }
    val counts = remember(data) { if (data.blank) null else SearchCategory.entries.associateWith { data.count(it) }.takeIf { m -> m.values.any { it > 0 } } }

    // ---- suggestions
    val appCategories = remember(apps) { apps.map { AppSearch.categoryNameOf(it.packageName) }.distinct().sorted() }
    val filesAvailable = hub != null && prefs.searchFiles && fileAccess
    val deepAvailable = hub != null && prefs.searchDeep && hub.deepSources().isNotEmpty()
    val groups = remember(searchText, appCategories, layout, filesAvailable, deepAvailable, ranked.isNotEmpty()) {
        buildSuggestionGroups(searchText, appCategories, maxRows = if (layout == SearchLayout.Flat) 8 else 5, filesAvailable = filesAvailable, deepAvailable = deepAvailable, appHits = ranked.isNotEmpty())
    }
    val showSuggestions = groups.isNotEmpty() && !touched && dismissedFor != query

    // ---- actions
    fun tick() { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); sounds?.play(UiSound.Tick) }
    fun setQuery(text: String) { field = TextFieldValue(text, TextRange(text.length)) }
    fun openHit(hit: SearchHit) {
        PaletteHost.learnHit(context, hit)
        // PAL-05 copy, PAL-03 app shortcut and launcher command run here; plain files and web hits fall through to the host.
        when (val o = PaletteHitRunner.run(context, hit, palette)) {
            PaletteHitRunner.Outcome.Done -> { if (hit.copyText != null) copiedToast(context); return }
            is PaletteHitRunner.Outcome.Command -> { onDismiss(); onRunCommand(o.name, o.index); return }
            PaletteHitRunner.Outcome.NotHandled -> Unit
        }
        val id = hit.actionId
        when {
            id != null -> onRunAction(id)
            onOpenHit != null -> onOpenHit(hit)
            else -> openHitIntent(context, hit)?.let(onOpenIntent)
        }
    }
    fun tap(tile: Tile) {
        when (tile) {
            is Tile.App -> { PaletteHost.learn(context, "app:${tile.app.key}"); onLaunch(tile.app) }
            is Tile.Person -> {
                PaletteHost.learn(context, "contact:${tile.c.hit.id}")
                val ch = tile.c.primary
                val i = ch?.let { ContactActions.intentFor(it, tile.c.hit) }
                if (ch != null && i != null) { ContactHabits.record(context, tile.c.hit.id, ch); onOpenIntent(i) }
                else { tick(); expandedContact = if (expandedContact == tile.c.hit.id) null else tile.c.hit.id }
            }
            is Tile.Hit -> openHit(tile.hit)
            is Tile.Web -> when (val r = tile.row) {
                is WebRow.Open -> onOpenIntent(WebSearch.intentFor(r.url))
                is WebRow.Search -> onOpenIntent(WebSearch.intentFor(r.url))
                // PAL-15: the installed app store first (market link); the store's web page when none is visible to us.
                is WebRow.PlayStore -> onOpenIntent(
                    Intent(Intent.ACTION_VIEW, android.net.Uri.parse(r.marketUri)).takeIf { it.resolveActivity(context.packageManager) != null } ?: WebSearch.intentFor(r.url),
                )
            }
            is Tile.Allow -> if (tile.files) onRequestFilePermission() else onRequestContactsPermission()
            is Tile.Setting -> settingsSource?.open(tile.entry)
        }
    }
    val dismissForDrag by androidx.compose.runtime.rememberUpdatedState(onDismiss)
    androidx.compose.runtime.DisposableEffect(Unit) {
        val h: () -> Unit = { dismissForDrag() }
        LauncherDrops.onDragStart = h
        onDispose { if (LauncherDrops.onDragStart === h) LauncherDrops.onDragStart = null }
    }
    // PAL-20: long-press and right-click open the same preview on every result, in all three layouts.
    val tileActions = TileActions(onTap = { tap(it) }, onLongPress = { t -> if (t !is Tile.Allow) { tick(); previewTile = t } }, onTouch = { touched = true })
    val onPreview: (Tile) -> Unit = { t -> if (t !is Tile.Allow) { tick(); previewTile = t } }
    val setWheel: (SearchCategory?) -> Unit = { c -> userCat = c; expandedContact = null }

    // ---- keyboard (PAL-07): the same model walks Flat, Orbit and Filmstrip results.
    val navTiles = remember(data, selected, flatMode, layout, orbitVisible, orbitTyped, ringTiles, stripTiles) {
        if (flatMode) flatTiles(data, selected)
        else if (orbitTyped) ringTiles.take(orbitVisible) + stripTiles
        else data.tiles(selected).let { if (layout == SearchLayout.Orbit) it.take(orbitVisible) else it }
    }
    val navNow = nav.withCount(navTiles.size)
    LaunchedEffect(trimmed, selected) { nav = RadarNav(navTiles.size) }
    val selectedKey = if (inputMode == InputMode.Keyboard && navNow.active) navTiles.getOrNull(navNow.selected)?.key else null
    selectedHitHolder[0] = selectedKey?.takeIf { it.startsWith("h:") }?.removePrefix("h:")
    fun menuFor(t: Tile) = NestedShortcuts.menuFor(t, ResultMenus.forKind(tileKind(t), canUninstall = !(t is Tile.App && t.app.isSystem), otherDisplays = otherDisplays, adjacentSupported = adjacentSupported))
    fun runMenu(tile: Tile, entry: ResultMenuEntry) {
        when {
            entry.shortcut != null -> tap(Tile.Hit(entry.shortcut)) // PAL-03: a nested app shortcut
            entry.preview -> previewTile = tile
            entry.alias && tile is Tile.App -> aliasFor = tile.app
            entry.action == MenuAction.Open -> tap(tile)
            entry.action == MenuAction.AppInfo && tile is Tile.App -> onOpenIntent(appInfoIntent(tile.app.packageName))
            entry.action == MenuAction.Uninstall && tile is Tile.App -> com.bos.sphere.core.data.AppUninstall.start(context, tile.app)
            // PAL-08: display, beside and dock run from here when the host can; a display or workspace pick opens Radar's own chooser; "more" opens the host's sheet.
            tile is Tile.App -> if (entry.action == null || !onRunAppAction(tile.app, entry.action)) {
                val choices = entry.action?.let { onAppActionChoices(tile.app, it) }?.takeIf { it.isNotEmpty() }
                if (choices != null) picker = RadarPickerState(RadarPicker.titleFor(entry.action), choices) else onLongPressApp(tile.app)
            }
        }
    }
    val onRadarKey: (KeyEvent) -> Boolean = handler@{ ev ->
        if (previewTile != null) return@handler false
        if (ev.type == androidx.compose.ui.input.key.KeyEventType.KeyDown && RadarKeyMap.hardware(ev)) tracker.onKey()
        val key = RadarKeyMap.radarKey(ev) ?: return@handler false
        if ((barFocused || micFocused) && key != RKey.Escape) return@handler false // a button has focus: Tab, arrows, Enter and Space are theirs
        if (!navNow.menuOpen) {
            // A navigation key means keyboard mode: a clickable only takes focus in keyboard mode, and the system switches a moment after this handler.
            val toBar = key == RKey.ShiftTab || (key == RKey.Tab && navNow.count == 0) || (key == RKey.Up && !navNow.active)
            val toMic = voiceOn && key == RKey.Down && navNow.count == 0
            if (toBar || toMic) {
                inputModeManager.requestInputMode(androidx.compose.ui.input.InputMode.Keyboard)
                val to = if (toBar) controlFocus else micFocus
                if (runCatching { to.requestFocus(); true }.getOrDefault(false)) return@handler true
            }
        }
        val sel = field.selection
        val caret = CaretContext(queryBlank = query.isBlank(), atStart = sel.collapsed && sel.start == 0, atEnd = sel.collapsed && sel.end == query.length)
        val r = RadarKeyboard.reduce(navNow, key, caret) { i -> navTiles.getOrNull(i)?.let { menuFor(it).size } ?: ResultMenus.forKind(ResultKind.Other).size }
        nav = r.nav
        when (val e = r.effect) {
            is RadarEffect.Run -> navTiles.getOrNull(e.index)?.let { tap(it) }
            is RadarEffect.RunMenu -> navTiles.getOrNull(e.index)?.let { t -> menuFor(t).getOrNull(e.menuIndex)?.let { runMenu(t, it) } }
            RadarEffect.Dismiss -> onDismiss()
            is RadarEffect.WheelMove -> { val to = ScopeWheel.step(selected, e.delta); if (to != selected) { tick(); setWheel(to) } }
            else -> Unit
        }
        r.consumed
    }
    // The field keeps focus: after a preview closes, or if something steals it, it comes back.
    LaunchedEffect(previewTile) { if (previewTile == null) { delay(80); runCatching { focus.requestFocus() } } }
    val pickSuggestion: (SuggItem) -> Unit = { item ->
        tick()
        item.scope?.let { userCat = it }
        item.insertion?.let { setQuery(it) }
        dismissedFor = field.text
    }
    val go = {
        val first = data.enterTile(selected)
        if (first != null && first !is Tile.Person) tap(first) else submitSearch(searchText, apps, prefs, onLaunch, onOpenIntent)
    }
    val showKeyboard = { runCatching { focus.requestFocus(); keyboard?.show() }; Unit }
    // Wave 22 (second audit): Close and Clear swap places as the text appears or empties (also by voice); a keyboard focus that sat on the one
    // that left is gone with it, so the "a button has focus" flag is dropped and the field takes the focus back, otherwise every key is
    // passed on to a button that no longer exists.
    LaunchedEffect(query.isEmpty()) { if (barFocused) { barFocused = false; runCatching { focus.requestFocus() } } }

    CompositionLocalProvider(LocalResultScale provides scale.floatValue, LocalRadarSelection provides selectedKey) {
    // Wave 25 (owner: "There is still a dead touch area"): a tap that no control or result takes (the gap between the rings, the strip beside the mic, the
    // blank part of a list) is never swallowed: with nothing typed it closes Radar, with a query it puts the keyboard away. Children take their own taps first.
    val emptyTap = { if (query.isEmpty()) onDismiss() else { keyboard?.hide(); Unit } }
    val emptyTapNow by androidx.compose.runtime.rememberUpdatedState(emptyTap)
    Box(Modifier.fillMaxSize().onGloballyPositioned { rootCoords = it }.radarScaleGestures(scale).trackPointerMode(tracker).onPreviewKeyEvent(onRadarKey).pointerInput(Unit) { detectTapGestures { emptyTapNow() } }) {
        SearchBackdrop(Modifier.fillMaxSize())
        SignalBackground(prefs, Modifier.fillMaxSize(), dark = true) // Radar is black in every theme; HOM-16, PAL-24: the two background items, the same on every surface
        // Wave 29 (radarFull, owner: the rings and icons are cropped and part of them cannot be touched): the Orbit's drawing and touch layer is the whole display area, edge to
        // edge and from the top of the screen to the top of the keyboard (the bottom edge when it is down). The field, the buttons, the mic, the strip circles and the wheel are
        // drawn above it and each takes only its own bounds; the only clip is the true screen or keyboard edge (docs/decisions/radarFull.md).
        val geo = orbitGeo
        if (!flatMode && layout == SearchLayout.Orbit && geo != null) {
            Box(Modifier.fillMaxSize().imePadding()) {
                Orbit(
                    tiles = if (orbitTyped) ringTiles else data.tiles(selected), motionRequested = prefs.searchMotion, footnote = data.footnote(selected),
                    actions = tileActions, centreFromBottom = geo.centre, wheel = geo.wheel,
                    modifier = Modifier.fillMaxSize().semantics { isTraversalGroup = true; traversalIndex = 1f },
                    onVisibleCount = { orbitVisible = it },
                    emptyHint = if (data.blank && (selected == null || selected == SearchCategory.Apps) && data.tiles(selected).isEmpty()) L10n.tr("Rings fill up as you use Radar") else null,
                    tierNames = if (orbitTyped) RadarRings.queryTierNames else RadarRings.tierNames,
                    region = geo.region, reserveBottom = if (orbitTyped) RadarStripHeight else 0.dp,
                )
            }
        }
        // Typing, or the Flat layout at rest (what you open most): a plain results list. Wave 29 (review): it is a layer of its own like the Orbit, from the top edge of the screen to the
        // keyboard (the bottom edge when it is down), so no row is cut by a line at the status bar, the cutout or the navigation bar; the bars, the field and the mic are content padding.
        if (flatMode) {
            val bars = WindowInsets.safeDrawing.exclude(WindowInsets.ime).asPaddingValues() // the keyboard itself is the imePadding below
            val dir = androidx.compose.ui.platform.LocalLayoutDirection.current
            Box(Modifier.fillMaxSize().imePadding().semantics { isTraversalGroup = true; traversalIndex = 1f }) {
                FlatResults(
                    data = data, scope = userCat, suggestions = if (showSuggestions) groups else emptyList(),
                    onPickSuggestion = pickSuggestion, onTileTap = { tap(it) }, onLongPressApp = onLongPressApp,
                    onOpenIntent = onOpenIntent, onQueryChange = { setQuery(it) }, onTouch = { touched = true },
                    plain = true, onPreview = onPreview,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 16.dp + bars.calculateStartPadding(dir), end = 16.dp + bars.calculateEndPadding(dir),
                        top = bars.calculateTopPadding() + RadarQueryBarHeight + 4.dp, bottom = bars.calculateBottomPadding() + micStripNow + 8.dp,
                    ),
                )
            }
        }
        // Wave 18 (owner): the query line is always at the top, below the status bar and cutout, in every layout and both
        // orientations. The results fill the space below it and end where the keyboard starts (imePadding), so none hides under it.
        androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).imePadding()) {
            // Wave 23 (PAL-18): with little free height under the field (landscape, keyboard up) the mic rides in the field row, not in its own strip.
            val micInline = voiceOn && RadarMicPlacement.inline(maxHeight - RadarQueryBarHeight)
            val micStrip = if (voiceOn && !micInline) RadarMicBarHeight else 0.dp
            // The mic is recomposed at a new place when the keyboard opens or closes; a focus flag the old one held would block the key handler for good.
            LaunchedEffect(micInline) { micFocused = false }
            SideEffect { micStripNow = micStrip } // the action list below sits outside this box and needs the same number
            // Wave 22 (audit): the results are one reading group, so TalkBack reads the field, its buttons, the whole results area and the mic last
            // (the Orbit rings are a group inside it); without the groups the ring indices 1, 2 ... sorted after the mic, which has index 0.
            // Wave 29: no clip here (it was a straight line across the rings at the field's bottom and the mic's top); the Orbit has its own full-screen layer above, and the lists
            // scroll under the field and above the mic with their own content padding.
            Box(Modifier.fillMaxSize().padding(top = RadarQueryBarHeight, bottom = micStrip).onGloballyPositioned { c ->
                val root = rootCoords ?: c.findRootCoordinates()
                if (root.isAttached && c.isAttached) {
                    val r = androidx.compose.ui.geometry.Rect(root.localPositionOf(c, Offset.Zero), androidx.compose.ui.geometry.Size(c.size.width.toFloat(), c.size.height.toFloat()))
                    val old = orbitGeo
                    if (old == null || old.region != r) orbitGeo = OrbitGeo(r, old?.centre ?: 0.dp, old?.wheel ?: WheelSpec.Default)
                }
            }.semantics { isTraversalGroup = true; traversalIndex = 1f }) {
                val emptyHint = if (data.blank && (selected == null || selected == SearchCategory.Apps) && data.tiles(selected).isEmpty()) L10n.tr("Rings fill up as you use Radar") else null
                if (flatMode) Unit else {
                    // Idle browsing: the category wheel with Filmstrip or Orbit results.
                    val expanded = contactRows.firstOrNull { it.hit.id == expandedContact }?.takeIf { selected == SearchCategory.Contacts || selected == null }
                    // The wheel sits in the middle of the free area below the query line; the Orbit's rings are centred on it
                    // and the Filmstrip runs beneath it.
                    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
                        val availH = maxHeight
                        val fontScale = androidx.compose.ui.platform.LocalDensity.current.fontScale
                        // Room the strip needs under the wheel (the Filmstrip: 182 dp). When the free area is short (landscape, keyboard up, big text)
                        // the strip gives way, down to nothing, before the wheel loses its one row, so the two never overlap.
                        val below = if (layout == SearchLayout.Orbit) (if (orbitTyped) RadarStripHeight else 0.dp) else minOf(182.dp, (availH - WheelSpec.rowFor(fontScale) - 8.dp).coerceAtLeast(0.dp))
                        // Wave 22: the wheel takes only the rows (1, 3 or 5, taller with a bigger font) that fit in what the results leave
                        // free, so its box, its labels and the "SEARCH" caption always sit inside the results area, which itself ends above the mic strip.
                        val wheel = WheelSpec.fit((availH - below).value.let { if (layout == SearchLayout.Orbit) OrbitFit.wheelRoom(it) else it }, fontScale)
                        val centre = ((availH - below) / 2).coerceAtLeast(wheel.height / 2)
                        val wheelRow: @Composable () -> Unit = {
                            // "SEARCH" ends just left of the centre line and the wheel starts just right of it.
                            Row(Modifier.fillMaxWidth()) {
                                // Wave 25 (shortcutForm): its tap area is only the word and the visible part of the wheel's height, so a result beside it never lies under it.
                                Box(Modifier.weight(1f).align(Alignment.CenterVertically).padding(end = 10.dp), contentAlignment = Alignment.CenterEnd) { QueryDisplay(onTap = { showKeyboard() }, modifier = Modifier.widthIn(min = 48.dp), height = minOf(wheel.height, wheel.halfVisible * 2)) }
                                Box(Modifier.weight(1f)) {
                                    CategoryWheel(
                                        selected = selected, counts = counts,
                                        onSelect = { setWheel(it); touched = true },
                                        onTick = { tick() }, onTouch = { touched = true },
                                        modifier = Modifier.testTag(RADAR_WHEEL_TAG), spec = wheel, onBlankTap = { showKeyboard() },
                                    )
                                }
                            }
                        }
                        if (layout == SearchLayout.Orbit) {
                            // The rings themselves are the full-screen layer above (wave 29); this box only holds the overlays and says where the wheel's centre is.
                            SideEffect { val g = orbitGeo; if (g != null && (g.centre != centre || g.wheel != wheel)) orbitGeo = OrbitGeo(g.region, centre, wheel) }
                            if (orbitTyped) ResultStrip(
                                tiles = stripTiles,
                                filenames = if (filesAvailable) L10n.tr("Filenames") else null,
                                actions = tileActions, onFilenames = { setWheel(SearchCategory.Files); touched = true },
                                modifier = Modifier.align(Alignment.BottomCenter),
                            )
                            Box(Modifier.offset(y = centre - wheel.height / 2)) { wheelRow() }
                            expanded?.let { Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp)) { ContactChannelBar(it, onOpenIntent) } }
                        } else {
                            Box(Modifier.offset(y = centre - wheel.height / 2)) { wheelRow() }
                            // The strip rides the top of the keyboard so nothing is left hanging under its caption.
                            Filmstrip(
                                tiles = data.tiles(selected), motion = prefs.searchMotion, query = trimmed,
                                expanded = expanded, onOpenIntent = onOpenIntent, footnote = data.footnote(selected),
                                actions = tileActions, modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().heightIn(max = below).padding(bottom = 4.dp),
                                emptyHint = emptyHint, fit = StripFit.of(below.value, withRow = data.tiles(selected).any { it is Tile.App && it.shortcuts.isNotEmpty() }),
                            )
                        }
                    }
                }
            }
            // Wave 22 (NFR-13): the query line, the results and the mic are three traversal groups, so TalkBack walks them top to bottom (field, results, mic) whatever the composition order.
            // The one text field, always visible at the top: scope menu (Flat), placeholder, query and the Clear X inside it, black gradient (wave 25);
            // Close stays outside as a black circle while the query is empty. Push-to-talk (INP-13) is at the bottom centre.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.align(Alignment.TopStart).fillMaxWidth().height(RadarQueryBarHeight).semantics { isTraversalGroup = true }.padding(start = 16.dp, end = 12.dp, top = 8.dp, bottom = 4.dp),
            ) {
                // A tap anywhere on the field (its padding too, not only the text line) puts the cursor in it; it is never a dead strip.
                BlackField(Modifier.weight(1f).fillMaxHeight().testTag(RADAR_FIELD_TAG).pointerInput(Unit) { detectTapGestures { showKeyboard() } }) {
                    if (flatMode) ScopeToken(selected, selected, { setWheel(it) }, plain = true, webLabel = prefs.webEngine.label, fanDown = true, showLabel = selected != null, onField = true, modifier = Modifier.padding(start = 6.dp))
                    Box(Modifier.weight(1f).padding(start = if (flatMode) 8.dp else 20.dp, end = if (query.isEmpty()) 20.dp else 0.dp), contentAlignment = Alignment.CenterStart) {
                        if (query.isEmpty()) Text(L10n.tr("Search"), color = BlackSpec.Hint, fontSize = 20.sp, maxLines = 1)
                        QueryInput(field, { field = it }, go, focus, BlackSpec.Content, 20.sp, Modifier.fillMaxWidth().onFocusChanged { if (it.hasFocus) { barFocused = false; micFocused = false } })
                    }
                    if (query.isNotEmpty()) {
                        // Wave 21 (owner): the X is inside the field at its trailing end, 48 dp target, 20 dp glyph, only with text.
                        BlackButton(onClick = { setQuery(""); barFocused = false; showKeyboard() }, description = L10n.tr("Clear"), filled = false, modifier = Modifier.padding(end = 2.dp).focusRequester(controlFocus).onFocusChanged { barFocused = it.hasFocus }) {
                            Icon(Glyphs.Close, null, tint = BlackSpec.Content, modifier = Modifier.size(20.dp))
                        }
                    }
                }
                if (micInline) PushToTalkButton(onText = { setQuery(it) }, availableOverride = true, size = RadarMicPlacement.InlineSize, modifier = Modifier.padding(start = 8.dp).testTag(RADAR_MIC_TAG).focusRequester(micFocus), onFocusChange = { micFocused = it })
                if (query.isEmpty() && !flatMode) {
                    // Close and Clear are never both visible: Close is outside the field, a black circle, while there is nothing to clear.
                    // Wave 22: not in Flat. The owner asked only for the X inside the field; Flat is the keyboard and pointer layout, where Esc and Back leave
                    // (PAL-07) and nothing in the spec asks for a visible Close, so the field gets the whole width (docs/decisions/radarGlass.md).
                    BlackButton(onClick = onDismiss, description = L10n.tr("Close"), modifier = Modifier.padding(start = 8.dp).focusRequester(controlFocus).onFocusChanged { barFocused = it.hasFocus }) {
                        Icon(Glyphs.Close, null, tint = BlackSpec.Content, modifier = Modifier.size(22.dp))
                    }
                }
            }
            // Wave 21 (owner): the mic for voice input sits at the centre of the bottom, above the keyboard and the gesture bar
            // (the inset padding above), 56 dp, in every layout and orientation; space is reserved for it so it never covers a result.
            if (voiceOn && !micInline) Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(RadarMicBarHeight).semantics { isTraversalGroup = true; traversalIndex = 2f }, contentAlignment = Alignment.Center) {
                PushToTalkButton(onText = { setQuery(it) }, availableOverride = true, modifier = Modifier.testTag(RADAR_MIC_TAG).focusRequester(micFocus), onFocusChange = { micFocused = it })
            }
        }
        aliasFor?.let { a ->
            AliasDialog(a, PaletteHost.aliases(context)[a.packageName].orEmpty(), onSave = { v -> PaletteHost.setAlias(context, a.packageName, v); aliasRev++; aliasFor = null }, onDismiss = { aliasFor = null })
        }
        // The action list of the keyboard-selected result (Tab / Right / Menu).
        if (navNow.menuOpen) navTiles.getOrNull(navNow.selected)?.let { t ->
            RadarActionList(
                menuFor(t), navNow.menuIndex,
                onPick = { i -> nav = navNow.copy(menuOpen = false); menuFor(t).getOrNull(i)?.let { runMenu(t, it) } },
                modifier = Modifier.align(Alignment.BottomEnd).windowInsetsPadding(WindowInsets.safeDrawing).imePadding().padding(end = 16.dp, bottom = 64.dp + micStripNow),
            )
        }
        previewTile?.let { t ->
            ResultPreview(t, trimmed, onDismiss = { previewTile = null }, onOpen = { previewTile = null; tap(t) }, onLongPressApp = { previewTile = null; onLongPressApp(it) }, onOpenIntent = onOpenIntent, onShortcut = { previewTile = null; tap(Tile.Hit(it)) })
        }
        picker?.let { p -> RadarChoicePicker(p.title, p.choices, onDismiss = { picker = null }, onPick = { c -> picker = null; c.run() }) }
    }
    }
}

/** The one real text field. Visible in Flat, invisible (but focused, feeding the IME) elsewhere. */
@Composable
private fun QueryInput(
    value: TextFieldValue,
    onChange: (TextFieldValue) -> Unit,
    onGo: () -> Unit,
    focus: FocusRequester,
    color: Color,
    size: androidx.compose.ui.unit.TextUnit,
    modifier: Modifier,
) {
    BasicTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        textStyle = TextStyle(color = color, fontSize = size),
        cursorBrush = SolidColor(Color.White),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
        keyboardActions = KeyboardActions(onGo = { onGo() }),
        modifier = modifier.focusRequester(focus),
    )
}

/**
 * Enter / IME-Go: launch the top app result, otherwise open the first web row (a URL, or
 * the web search). Does nothing for a blank query.
 */
fun submitSearch(
    query: String,
    apps: List<AppEntry>,
    prefs: LauncherPrefs,
    onLaunch: (AppEntry) -> Unit,
    onOpenIntent: (Intent) -> Unit,
) {
    val q = query.trim()
    if (q.isEmpty()) return
    val top = if (WebSearch.looksLikeUrl(q) && prefs.searchWeb) null else AppSearch.rank(apps, q, limit = 1).firstOrNull()
    if (top != null) { onLaunch(top); return }
    if (!prefs.searchWeb) return
    val s = sections(q, prefs.webEngine, emptyList<AppEntry>(), emptyList<ContactHit>())
    val url = when (val first = s.web.firstOrNull()) {
        is WebRow.Open -> first.url
        is WebRow.Search -> first.url
        is WebRow.PlayStore -> first.url
        else -> null
    }
    if (url != null) onOpenIntent(WebSearch.intentFor(url))
}

internal class ContactRow(val hit: ContactHit, val channels: List<Channel>, val primary: Channel? = badgeChannel(channels))

/** Remembers which way you last reached each person, so their tile wears that badge (a phone bubble after a call). */
internal object ContactHabits {
    private fun sp(c: android.content.Context) = c.getSharedPreferences("search_habits", android.content.Context.MODE_PRIVATE)
    fun last(c: android.content.Context, id: Long): Channel? =
        sp(c).getString("ch:$id", null)?.let { n -> Channel.entries.firstOrNull { it.name == n } }
    fun record(c: android.content.Context, id: Long, ch: Channel) {
        sp(c).edit().putString("ch:$id", ch.name).putLong("at:$id", System.currentTimeMillis()).apply()
    }
    /** PAL-09 reset: forget the channel and the time of every person reached through Radar. */
    fun clear(c: android.content.Context) { runCatching { sp(c).edit().clear().apply() } }
    /** People reached through Radar, newest first. */
    fun recentIds(c: android.content.Context): List<Long> =
        sp(c).all.keys.filter { it.startsWith("at:") }.mapNotNull { k -> (sp(c).getLong(k, 0L)).takeIf { it > 0 }?.let { k.removePrefix("at:").toLongOrNull()?.to(it) } }
            .sortedByDescending { it.second }.map { it.first }
    fun primaryFor(c: android.content.Context, id: Long, channels: List<Channel>): Channel? =
        last(c, id)?.takeIf { it in channels } ?: Channel.Call.takeIf { it in channels } ?: badgeChannel(channels)
}

/** Wave 29: where the Orbit's rings sit: the free region under the field (in the screen's pixels), the wheel's centre in it (from its top) and the wheel's size. */
internal data class OrbitGeo(val region: androidx.compose.ui.geometry.Rect, val centre: androidx.compose.ui.unit.Dp, val wheel: WheelSpec)

/** The height of the query line at the top of Radar, below the status bar and cutout. */
internal val RadarQueryBarHeight = 64.dp

/** Wave 21: the bottom strip that holds the mic (a 56 dp button and 8 dp above and below); reserved only when the mic exists. */
internal val RadarMicBarHeight = 72.dp

/** Wave 23: test tag of the mic button, wherever it sits. */
internal const val RADAR_MIC_TAG = "radarMic"

/** Wave 23 (PAL-18): the mic leaves its bottom strip for the field row when the free height under the field is below [INLINE_BELOW]. */
internal object RadarMicPlacement {
    val INLINE_BELOW = 220.dp
    val InlineSize = 48.dp
    fun inline(freeHeight: androidx.compose.ui.unit.Dp): Boolean = freeHeight < INLINE_BELOW
}

/** Wave 22: test tag of the category wheel's own box (its labels are drawn only inside it). */
internal const val RADAR_WHEEL_TAG = "radarWheel"

/** Test tag of the black field (its bounds hold the scope menu, the text and the Clear X). */
internal const val RADAR_FIELD_TAG = "radarField"

/** How many of the apps you open most the resting Radar lists (the four rings hold 5 + 9 + 13 + 17). */
private const val RADAR_RESTING_APPS = 44

@Composable
internal fun ChannelButton(ch: Channel, onClick: () -> Unit) {
    val glyph = when (ch) {
        Channel.Call -> Glyphs.Phone
        Channel.Sms -> Glyphs.Message
        Channel.Email -> Glyphs.Mail
        Channel.WhatsApp -> WhatsAppGlyph
        Channel.Telegram -> TelegramGlyph
        Channel.Signal -> SignalGlyph
    }
    val desc = L10n.tr(when (ch) {
        Channel.Call -> "Call"; Channel.Sms -> "Message"; Channel.Email -> "Email"
        Channel.WhatsApp -> "WhatsApp"; Channel.Telegram -> "Telegram"; Channel.Signal -> "Signal"
    })
    // Wave 22 (second audit): a 48 dp touch target around the 32 dp look (NFR-13).
    Box(
        Modifier
            .padding(start = 1.dp)
            .size(48.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = desc; role = androidx.compose.ui.semantics.Role.Button },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(32.dp).background(HyleColors.Surface, CircleShape).border(1.dp, HyleColors.Hairline, CircleShape), contentAlignment = Alignment.Center) {
            Icon(glyph, desc, tint = HyleColors.Violet, modifier = Modifier.size(16.dp))
        }
    }
}

// ---- private glyphs (stroke style matching Glyphs) ----

private fun glyph(name: String, block: PathBuilder.() -> Unit): ImageVector =
    ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
        path(
            fill = null, stroke = SolidColor(Color.White), strokeLineWidth = 2.1f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round, pathBuilder = block,
        )
    }.build()

internal val WhatsAppGlyph = glyph("whatsapp") {
    // Speech bubble with a tail, and a small handset curve inside.
    moveTo(4f, 20f); lineTo(5.4f, 16f)
    arcTo(8.4f, 8.4f, 0f, isMoreThanHalf = true, isPositiveArc = true, x1 = 8.2f, y1 = 18.6f)
    close()
    moveTo(9.5f, 8.8f); quadTo(9f, 11f, 12.2f, 14.2f); quadTo(14.5f, 15.3f, 15.3f, 14.5f)
    lineTo(14.6f, 13.2f); lineTo(13.2f, 13.5f); lineTo(10.5f, 10.8f); lineTo(10.8f, 9.4f); close()
}

internal val TelegramGlyph = glyph("telegram") {
    moveTo(20.5f, 4f); lineTo(3.5f, 10.8f); lineTo(9f, 13f); lineTo(11.2f, 19.5f); lineTo(14f, 15.2f)
    lineTo(18.5f, 18.5f); close()
    moveTo(9f, 13f); lineTo(20.5f, 4f)
}

internal val SignalGlyph = glyph("signal") {
    moveTo(4f, 20f); lineTo(5.2f, 16.2f)
    arcTo(8.5f, 8.5f, 0f, isMoreThanHalf = true, isPositiveArc = true, x1 = 8f, y1 = 18.8f)
    close()
    moveTo(9f, 11.5f); lineTo(9.1f, 11.5f); moveTo(12f, 11.5f); lineTo(12.1f, 11.5f); moveTo(15f, 11.5f); lineTo(15.1f, 11.5f)
}
