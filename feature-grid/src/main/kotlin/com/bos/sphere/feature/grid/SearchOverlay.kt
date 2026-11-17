package com.bos.sphere.feature.grid

import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.isTraversalGroup
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
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.focus.FocusRequester
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
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
            initialQuery = initialQuery, onRunCommand = onRunCommand,
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
    val haptic = LocalHapticFeedback.current
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = remember { FocusRequester() }
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
    LaunchedEffect(trimmed) {
        touched = false
        expandedContact = null
        if (trimmed.isEmpty()) userCat = null // an explicit choice sticks until the query is cleared
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
    fun menuFor(t: Tile) = NestedShortcuts.menuFor(t, ResultMenus.forKind(tileKind(t), otherDisplays = otherDisplays, adjacentSupported = adjacentSupported))
    fun runMenu(tile: Tile, entry: ResultMenuEntry) {
        when {
            entry.shortcut != null -> tap(Tile.Hit(entry.shortcut)) // PAL-03: a nested app shortcut
            entry.preview -> previewTile = tile
            entry.alias && tile is Tile.App -> aliasFor = tile.app
            entry.action == MenuAction.Open -> tap(tile)
            entry.action == MenuAction.AppInfo && tile is Tile.App -> onOpenIntent(appInfoIntent(tile.app.packageName))
            entry.action == MenuAction.Uninstall && tile is Tile.App -> onOpenIntent(appUninstallIntent(tile.app))
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

    // Wave 21: the glass is darkened only as far as the measured background behind it needs for AA text (NFR-15), usually not at all.
    val cfgNow = androidx.compose.ui.platform.LocalConfiguration.current
    val shades = rememberGlassShades(com.bos.sphere.core.data.BackgroundChoice(prefs.backgroundSlot1, prefs.backgroundSlot2), prefs.radarIntensity, cfgNow.screenWidthDp, cfgNow.screenHeightDp)
    CompositionLocalProvider(LocalResultScale provides scale.floatValue, LocalRadarSelection provides selectedKey) {
    Box(Modifier.fillMaxSize().radarScaleGestures(scale).trackPointerMode(tracker).onPreviewKeyEvent(onRadarKey)) {
        SearchBackdrop(Modifier.fillMaxSize())
        SignalBackground(prefs, Modifier.fillMaxSize(), dark = true) // Radar is black in every theme; HOM-16, PAL-24: the two background items, the same on every surface
        // Wave 18 (owner): the query line is always at the top, below the status bar and cutout, in every layout and both
        // orientations. The results fill the space below it and end where the keyboard starts (imePadding), so none hides under it.
        Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).imePadding()) {
            Box(Modifier.fillMaxSize().padding(top = RadarQueryBarHeight, bottom = if (voiceOn) RadarMicBarHeight else 0.dp).clipToBounds().semantics { isTraversalGroup = true }) {
                val emptyHint = if (data.blank && (selected == null || selected == SearchCategory.Apps) && data.tiles(selected).isEmpty()) L10n.tr("Rings fill up as you use Radar") else null
                if (flatMode) {
                    // Typing, or the Flat layout at rest (what you open most): a plain results list below the query line.
                    FlatResults(
                        data = data, scope = userCat, suggestions = if (showSuggestions) groups else emptyList(),
                        onPickSuggestion = pickSuggestion, onTileTap = { tap(it) }, onLongPressApp = onLongPressApp,
                        onOpenIntent = onOpenIntent, onQueryChange = { setQuery(it) }, onTouch = { touched = true },
                        plain = true, onPreview = onPreview,
                        modifier = Modifier.fillMaxSize().padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 8.dp),
                    )
                } else {
                    // Idle browsing: the category wheel with Filmstrip or Orbit results.
                    val expanded = contactRows.firstOrNull { it.hit.id == expandedContact }?.takeIf { selected == SearchCategory.Contacts || selected == null }
                    // The wheel sits in the middle of the free area below the query line; the Orbit's rings are centred on it
                    // and the Filmstrip runs beneath it.
                    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
                        val availH = maxHeight
                        val below = if (layout == SearchLayout.Orbit) (if (orbitTyped) RadarStripHeight else 0.dp) else 182.dp // room the strip needs under the wheel
                        val centre = ((availH - below) / 2).coerceAtLeast(WheelHeight / 2 + 8.dp)
                        val wheelRow: @Composable () -> Unit = {
                            // "SEARCH" ends just left of the centre line and the wheel starts just right of it.
                            Row(Modifier.fillMaxWidth()) {
                                QueryDisplay("", onClear = { setQuery("") }, onTap = { showKeyboard() }, modifier = Modifier.weight(1f).padding(end = 10.dp))
                                Box(Modifier.weight(1f)) {
                                    CategoryWheel(
                                        selected = selected, counts = counts,
                                        onSelect = { setWheel(it); touched = true },
                                        onTick = { tick() }, onTouch = { touched = true },
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                            }
                        }
                        if (layout == SearchLayout.Orbit) {
                            Orbit(
                                tiles = if (orbitTyped) ringTiles else data.tiles(selected), motionRequested = prefs.searchMotion, footnote = data.footnote(selected),
                                actions = tileActions, centreFromBottom = centre,
                                modifier = Modifier.fillMaxSize().padding(bottom = if (orbitTyped) RadarStripHeight else 0.dp),
                                onVisibleCount = { orbitVisible = it }, emptyHint = emptyHint,
                                tierNames = if (orbitTyped) RadarRings.queryTierNames else RadarRings.tierNames,
                            )
                            if (orbitTyped) ResultStrip(
                                tiles = stripTiles,
                                filenames = if (filesAvailable) L10n.tr("Filenames") else null,
                                actions = tileActions, onFilenames = { setWheel(SearchCategory.Files); touched = true },
                                modifier = Modifier.align(Alignment.BottomCenter),
                            )
                            Box(Modifier.offset(y = centre - WheelHeight / 2)) { wheelRow() }
                            expanded?.let { Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp)) { ContactChannelBar(it, onOpenIntent) } }
                        } else {
                            Box(Modifier.offset(y = centre - WheelHeight / 2)) { wheelRow() }
                            // The strip rides the top of the keyboard so nothing is left hanging under its caption.
                            Filmstrip(
                                tiles = data.tiles(selected), motion = prefs.searchMotion, query = trimmed,
                                expanded = expanded, onOpenIntent = onOpenIntent, footnote = data.footnote(selected),
                                actions = tileActions, modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(bottom = 4.dp),
                                emptyHint = emptyHint,
                            )
                        }
                    }
                }
            }
            // Wave 22 (NFR-13): the query line, the results and the mic are three traversal groups, so TalkBack walks them top to bottom (field, results, mic) whatever the composition order.
            // The one text field, always visible at the top: scope menu (Flat), placeholder, query and the Clear X inside it, glass (wave 21);
            // Close stays outside as a glass circle while the query is empty. Push-to-talk (INP-13) is at the bottom centre.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.align(Alignment.TopStart).fillMaxWidth().height(RadarQueryBarHeight).semantics { isTraversalGroup = true }.padding(start = 16.dp, end = 12.dp, top = 8.dp, bottom = 4.dp),
            ) {
                GlassField(Modifier.weight(1f).fillMaxHeight().testTag(RADAR_FIELD_TAG), shade = shades.top) {
                    if (flatMode) ScopeToken(selected, selected, { setWheel(it) }, plain = true, webLabel = prefs.webEngine.label, fanDown = true, showLabel = selected != null, glass = true, modifier = Modifier.padding(start = 6.dp))
                    Box(Modifier.weight(1f).padding(start = if (flatMode) 8.dp else 20.dp, end = if (query.isEmpty()) 20.dp else 0.dp), contentAlignment = Alignment.CenterStart) {
                        if (query.isEmpty()) Text(L10n.tr("Search"), color = GlassSpec.hint(true), fontSize = 20.sp, maxLines = 1)
                        QueryInput(field, { field = it }, go, focus, GlassSpec.content(true), 20.sp, Modifier.fillMaxWidth())
                    }
                    if (query.isNotEmpty()) {
                        // Wave 21 (owner): the X is inside the field at its trailing end, 48 dp target, 20 dp glyph, only with text.
                        GlassButton(onClick = { setQuery(""); runCatching { focus.requestFocus() } }, description = L10n.tr("Clear"), filled = false, modifier = Modifier.padding(end = 2.dp)) {
                            Icon(Glyphs.Close, null, tint = GlassSpec.content(true), modifier = Modifier.size(20.dp))
                        }
                    }
                }
                if (query.isEmpty()) {
                    // Close and Clear are never both visible: Close is outside the field, a glass circle, while there is nothing to clear.
                    GlassButton(onClick = onDismiss, description = L10n.tr("Close"), shade = shades.top, modifier = Modifier.padding(start = 8.dp)) {
                        Icon(Glyphs.Close, null, tint = GlassSpec.content(true), modifier = Modifier.size(22.dp))
                    }
                }
            }
            // Wave 21 (owner): the mic for voice input sits at the centre of the bottom, above the keyboard and the gesture bar
            // (the inset padding above), 56 dp, in every layout and orientation; space is reserved for it so it never covers a result.
            if (voiceOn) Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(RadarMicBarHeight).semantics { isTraversalGroup = true }, contentAlignment = Alignment.Center) {
                PushToTalkButton(onText = { setQuery(it) }, availableOverride = true, shade = shades.bottom)
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
                modifier = Modifier.align(Alignment.BottomEnd).windowInsetsPadding(WindowInsets.safeDrawing).imePadding().padding(end = 16.dp, bottom = 64.dp + (if (voiceOn) RadarMicBarHeight else 0.dp)),
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

/** The height of the query line at the top of Radar, below the status bar and cutout. */
internal val RadarQueryBarHeight = 64.dp

/** Wave 21: the bottom strip that holds the mic (a 56 dp button and 8 dp above and below); reserved only when the mic exists. */
internal val RadarMicBarHeight = 72.dp


/** Test tag of the glass field (its bounds hold the scope menu, the text and the Clear X). */
internal const val RADAR_FIELD_TAG = "radarField"

/** How many of the apps you open most the resting Radar lists (the four rings hold 5 + 9 + 13 + 17). */
private const val RADAR_RESTING_APPS = 44

private sealed interface Row {
    val key: String
    data class Header(val title: String) : Row { override val key get() = "h:$title" }
    data class App(val app: AppEntry) : Row { override val key get() = "a:${app.key}" }
    data class Person(val c: ContactRow) : Row { override val key get() = "c:${c.hit.id}" }
    data object AllowContacts : Row { override val key get() = "allow" }
    data class Web(val row: WebRow, val index: Int) : Row { override val key get() = "w:$index" }
    data class Hint(val text: String) : Row { override val key get() = "hint:$text" }
}

/**
 * Chips + the sectioned result list, for the overlay and for the always-on keyboard (which owns the
 * field and passes its text as [query]; [onQueryChange] is called when a chip rewrites it).
 */
@Composable
fun SearchResults(
    query: String,
    onQueryChange: (String) -> Unit,
    apps: List<AppEntry>,
    recent: List<AppEntry>,
    prefs: LauncherPrefs,
    contacts: ContactSource?,
    onLaunch: (AppEntry) -> Unit,
    onLongPressApp: (AppEntry) -> Unit,
    onOpenIntent: (Intent) -> Unit,
    onRequestContactsPermission: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val wantContacts = prefs.searchContacts && contacts != null
    val (rows, permitted) = rememberContactRows(query, contacts, wantContacts)

    val trimmed = query.trim()
    val ranked = remember(apps, query) { if (trimmed.isEmpty()) emptyList() else AppSearch.rank(apps, trimmed, limit = 8) }
    val categories = remember(apps) { apps.map { AppSearch.categoryNameOf(it.packageName) }.distinct().sorted() }
    val chips = remember(query, categories) { suggest(query, categories) }
    val data = remember(query, ranked, rows, prefs.webEngine, prefs.searchWeb, prefs.searchContacts, permitted, wantContacts) {
        sections(
            trimmed, prefs.webEngine, ranked, if (wantContacts) rows else emptyList(),
            searchWeb = prefs.searchWeb, searchContacts = wantContacts,
            hasContactsPermission = permitted,
        )
    }
    val list = remember(data, recent, trimmed) {
        buildList<Row> {
            if (trimmed.isEmpty()) {
                if (recent.isNotEmpty()) {
                    add(Row.Header(L10n.tr("Recent")))
                    recent.take(8).forEach { add(Row.App(it)) }
                }
                add(Row.Hint(L10n.tr("Type to find apps, people and the web. Try kind: or used:today")))
                return@buildList
            }
            for (kind in data.order) when (kind) {
                SectionKind.Apps -> { add(Row.Header(L10n.tr("Apps"))); data.apps.forEach { add(Row.App(it)) } }
                SectionKind.Contacts -> {
                    add(Row.Header(L10n.tr("Contacts")))
                    data.contacts.forEach { add(Row.Person(it)) }
                    if (data.askContactsPermission) add(Row.AllowContacts)
                }
                SectionKind.Web -> { add(Row.Header(L10n.tr("Web"))); data.web.forEachIndexed { i, w -> add(Row.Web(w, i)) } }
            }
            if (data.order.isEmpty()) add(Row.Hint(L10n.tr("No matches for “%s”", trimmed)))
        }
    }

    Column(modifier) {
        if (chips.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
            ) {
                chips.forEach { s -> SuggestionChip(s) { onQueryChange(s.insertion) } }
            }
        }
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            items(list, key = { it.key }) { row ->
                when (row) {
                    is Row.Header -> Text(
                        row.title.uppercase(), color = HyleColors.InkDim, fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold, letterSpacing = 1.2.sp,
                        modifier = Modifier.padding(top = 14.dp, bottom = 4.dp, start = 4.dp),
                    )
                    is Row.Hint -> Text(row.text, color = HyleColors.InkDim, fontSize = 13.sp, modifier = Modifier.padding(top = 16.dp, start = 4.dp))
                    is Row.App -> AppRow(row.app, onLaunch, onLongPressApp)
                    is Row.Person -> PersonRow(row.c, onOpenIntent)
                    Row.AllowContacts -> SimpleRow(Glyphs.Person, L10n.tr("Allow contacts to find people"), dim = true, onClick = onRequestContactsPermission)
                    is Row.Web -> WebRowView(row.row, onOpenIntent, onQueryChange, query)
                }
            }
        }
    }
}

@Composable
private fun SuggestionChip(s: Suggestion, onClick: () -> Unit) {
    val accent = s.kind == SuggestionKind.Key || s.kind == SuggestionKind.Value
    Text(
        s.label,
        color = if (accent) HyleColors.Violet else HyleColors.Ink,
        fontSize = 13.sp,
        maxLines = 1,
        modifier = Modifier
            .padding(end = 8.dp)
            .background(HyleColors.Surface, RoundedCornerShape(50))
            .border(1.dp, if (accent) HyleColors.Violet.copy(alpha = 0.5f) else HyleColors.Hairline, RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .semantics { role = androidx.compose.ui.semantics.Role.Button }
            .padding(horizontal = 12.dp, vertical = 7.dp),
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppRow(app: AppEntry, onLaunch: (AppEntry) -> Unit, onLong: (AppEntry) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = { onLaunch(app) }, onLongClick = { onLong(app) })
            .padding(vertical = 8.dp, horizontal = 4.dp),
    ) {
        AppIcon(app, 42.dp)
        Text(app.label, color = HyleColors.InkBright, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 14.dp))
    }
}

@Composable
private fun PersonRow(c: ContactRow, onOpenIntent: (Intent) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp, horizontal = 4.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f).clickable { onOpenIntent(ContactActions.openContact(c.hit)) },
        ) {
            Box(
                Modifier.size(38.dp).background(HyleColors.VioletSoft, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(ContactLogic.initials(c.hit.name), color = HyleColors.InkBright, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
            Text(
                c.hit.name, color = HyleColors.InkBright, fontSize = 15.sp, maxLines = 1,
                overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 12.dp, end = 6.dp),
            )
        }
        c.channels.forEach { ch ->
            ChannelButton(ch) { ContactActions.intentFor(ch, c.hit)?.let(onOpenIntent) }
        }
    }
}

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
    val desc = when (ch) {
        Channel.Call -> "Call"; Channel.Sms -> "Message"; Channel.Email -> "Email"
        Channel.WhatsApp -> "WhatsApp"; Channel.Telegram -> "Telegram"; Channel.Signal -> "Signal"
    }
    Box(
        Modifier
            .padding(start = 5.dp)
            .size(32.dp)
            .background(HyleColors.Surface, CircleShape)
            .border(1.dp, HyleColors.Hairline, CircleShape)
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = desc; role = androidx.compose.ui.semantics.Role.Button },
        contentAlignment = Alignment.Center,
    ) {
        Icon(glyph, desc, tint = HyleColors.Violet, modifier = Modifier.size(16.dp))
    }
}

@Composable
internal fun SimpleRow(icon: ImageVector, text: String, dim: Boolean = false, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp, horizontal = 4.dp),
    ) {
        Icon(icon, null, tint = if (dim) HyleColors.InkDim else HyleColors.Violet, modifier = Modifier.size(20.dp))
        Text(
            text, color = if (dim) HyleColors.InkDim else HyleColors.InkBright, fontSize = 14.sp, maxLines = 1,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 14.dp),
        )
    }
}

@Composable
private fun WebRowView(row: WebRow, onOpenIntent: (Intent) -> Unit, onQueryChange: (String) -> Unit, query: String) {
    when (row) {
        is WebRow.Open -> SimpleRow(Glyphs.Globe, row.label) { onOpenIntent(WebSearch.intentFor(row.url)) }
        is WebRow.Search -> SimpleRow(Glyphs.Search, row.label) { onOpenIntent(WebSearch.intentFor(row.url)) }
        is WebRow.PlayStore -> SimpleRow(Glyphs.Apps, row.label) { onOpenIntent(WebSearch.intentFor(row.url)) }
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
