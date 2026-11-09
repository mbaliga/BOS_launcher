package com.bos.sphere.feature.grid

import android.content.Intent
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
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
 * (including Fylz / Fotoz deep hits) and the web (DuckDuckGo by default so !bangs work).
 *
 * The layout follows [LauncherPrefs.searchLayout]: Filmstrip, Orbit (both with the category wheel)
 * or the macOS-style Flat list. The keyboard is the system IME; the layout reserves its space with
 * imePadding and works the same without it.
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
) {
    if (visible) BackHandler(onBack = onDismiss)
    AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut(), modifier = modifier) {
        SearchScreen(
            apps = apps, recent = recent, prefs = prefs, contacts = contacts,
            onLaunch = onLaunch, onLongPressApp = onLongPressApp, onOpenIntent = onOpenIntent,
            onRequestContactsPermission = onRequestContactsPermission, onDismiss = onDismiss,
            hub = hub, onOpenHit = onOpenHit, onRunAction = onRunAction,
            onRequestFilePermission = onRequestFilePermission, sounds = sounds,
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
        if (!want || q.length < 2 || contacts == null) {
            rows = emptyList()
            return@LaunchedEffect
        }
        delay(120)
        val (ok, found) = withContext(Dispatchers.IO) {
            val ok = contacts.hasPermission()
            ok to if (ok) contacts.search(q).map { ContactRow(it, ContactActions.availableChannels(context, it)) } else emptyList()
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
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = remember { FocusRequester() }
    var field by remember { mutableStateOf(TextFieldValue(initialQuery, TextRange(initialQuery.length))) }
    var userCat by remember { mutableStateOf(initialCategory) }
    var expandedContact by remember { mutableStateOf<Long?>(null) }
    var dismissedFor by remember { mutableStateOf<String?>(null) }
    var touched by remember { mutableStateOf(false) }
    val query = field.text
    val trimmed = query.trim()
    val layout = prefs.searchLayout

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
    val (contactRows, contactsPermitted) = rememberContactRows(query, contacts, wantContacts)
    var hubMap by remember { mutableStateOf<Map<SearchCategory, List<SearchHit>>>(emptyMap()) }
    var fileAccess by remember { mutableStateOf(hub?.hasFileAccess() ?: true) }
    LaunchedEffect(hub, query, prefs.searchFiles) {
        if (hub == null) { hubMap = emptyMap(); return@LaunchedEffect }
        if (query.isNotBlank()) delay(90)
        fileAccess = hub.hasFileAccess()
        hub.search(trimmed, prefs).collectLatest { hubMap = it }
    }
    val ranked = remember(apps, recent, trimmed) {
        if (trimmed.isEmpty()) recent.take(24) else AppSearch.rank(apps, trimmed, limit = 24)
    }
    val web = remember(trimmed, prefs.webEngine, prefs.searchWeb) {
        if (trimmed.isEmpty()) emptyList() else sections(
            trimmed, prefs.webEngine, emptyList<AppEntry>(), emptyList<ContactRow>(),
            searchWeb = prefs.searchWeb, searchContacts = false,
        ).web
    }
    val webFirst = remember(trimmed, prefs.searchWeb) {
        prefs.searchWeb && trimmed.isNotEmpty() && (WebSearch.bang(trimmed) != null || WebSearch.looksLikeUrl(trimmed) || WebSearch.hasBang(trimmed))
    }
    val data = remember(trimmed, ranked, contactRows, contactsPermitted, wantContacts, web, webFirst, hubMap, fileAccess, hub, prefs.searchFiles) {
        SearchData(
            query = trimmed, apps = ranked,
            contacts = if (wantContacts) contactRows else emptyList(),
            askContacts = wantContacts && !contactsPermitted && trimmed.length >= 2,
            web = web, webFirst = webFirst,
            hits = hubMap,
            askFiles = hub != null && prefs.searchFiles && !fileAccess,
            deepHint = hub != null && prefs.searchFiles && hub.deepSources().isEmpty(),
        )
    }
    val selected = userCat ?: data.best
    val counts = remember(data) { if (data.blank) null else SearchCategory.entries.associateWith { data.count(it) }.takeIf { m -> m.values.any { it > 0 } } }

    // ---- suggestions
    val appCategories = remember(apps) { apps.map { AppSearch.categoryNameOf(it.packageName) }.distinct().sorted() }
    val groups = remember(query, appCategories, layout) {
        buildSuggestionGroups(query, appCategories, maxRows = if (layout == SearchLayout.Flat) 8 else 5)
    }
    val showSuggestions = groups.isNotEmpty() && !touched && dismissedFor != query

    // ---- actions
    fun tick() { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); sounds?.play(UiSound.Tick) }
    fun setQuery(text: String) { field = TextFieldValue(text, TextRange(text.length)) }
    fun openHit(hit: SearchHit) {
        val id = hit.actionId
        when {
            id != null -> onRunAction(id)
            onOpenHit != null -> onOpenHit(hit)
            else -> openHitIntent(context, hit)?.let(onOpenIntent)
        }
    }
    fun tap(tile: Tile) {
        when (tile) {
            is Tile.App -> onLaunch(tile.app)
            is Tile.Person -> { tick(); expandedContact = if (expandedContact == tile.c.hit.id) null else tile.c.hit.id }
            is Tile.Hit -> openHit(tile.hit)
            is Tile.Web -> when (val r = tile.row) {
                is WebRow.Open -> onOpenIntent(WebSearch.intentFor(r.url))
                is WebRow.BangSearch -> onOpenIntent(WebSearch.intentFor(r.url))
                is WebRow.Search -> onOpenIntent(WebSearch.intentFor(r.url))
                is WebRow.PlayStore -> onOpenIntent(WebSearch.intentFor(r.url))
                is WebRow.BangHints -> Unit
            }
            is Tile.Bang -> setQuery(tile.rewritten)
            is Tile.Allow -> if (tile.files) onRequestFilePermission() else onRequestContactsPermission()
        }
    }
    val tileActions = TileActions(onTap = { tap(it) }, onLongPress = { t -> if (t is Tile.App) onLongPressApp(t.app) }, onTouch = { touched = true })
    val pickSuggestion: (SuggItem) -> Unit = { item ->
        tick()
        item.scope?.let { userCat = it }
        item.insertion?.let { setQuery(it) }
        dismissedFor = field.text
    }
    val go = {
        val first = data.tiles(selected).firstOrNull()
        if (first != null && first !is Tile.Person) tap(first) else submitSearch(query, apps, prefs, onLaunch, onOpenIntent)
    }
    val showKeyboard = { runCatching { focus.requestFocus(); keyboard?.show() }; Unit }

    Box(Modifier.fillMaxSize()) {
        SearchBackdrop(prefs.searchBackdrop, prefs.searchMotion, Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).imePadding()) {
            if (layout == SearchLayout.Flat) {
                Column(Modifier.fillMaxSize()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 8.dp)
                            .fillMaxWidth()
                            .background(Color(0xE61C1D21), RoundedCornerShape(12.dp))
                            .border(2.dp, HyleColors.Violet.copy(alpha = 0.8f), RoundedCornerShape(12.dp))
                            .padding(start = 12.dp, end = 8.dp, top = 9.dp, bottom = 9.dp),
                    ) {
                        Icon(Glyphs.Search, null, tint = Color(0xB3FFFFFF), modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        ScopeToken(userCat, userCat, { userCat = it })
                        Box(Modifier.weight(1f).padding(start = 8.dp)) {
                            if (query.isEmpty()) Text(L10n.tr("Search"), color = Color(0x80FFFFFF), fontSize = 17.sp)
                            QueryInput(field, { field = it }, go, focus, Color.White, 17.sp, Modifier.fillMaxWidth())
                        }
                        Icon(
                            Glyphs.Close, L10n.tr(if (query.isEmpty()) "Close" else "Clear"), tint = Color(0xB3FFFFFF),
                            modifier = Modifier.size(32.dp).clip(CircleShape).clickable { if (query.isEmpty()) onDismiss() else setQuery("") }.padding(7.dp),
                        )
                    }
                    FlatResults(
                        data = data, scope = userCat, suggestions = if (showSuggestions) groups else emptyList(),
                        onPickSuggestion = pickSuggestion, onTileTap = { tap(it) }, onLongPressApp = onLongPressApp,
                        onOpenIntent = onOpenIntent, onQueryChange = { setQuery(it) }, onTouch = { touched = true },
                        modifier = Modifier.padding(horizontal = 12.dp).fillMaxWidth().weight(1f, fill = false),
                    )
                }
            } else {
                // Hidden field: the system IME types into it; the big query beside the wheel mirrors it.
                QueryInput(field, { field = it }, go, focus, Color.Transparent, 1.sp, Modifier.size(1.dp).alpha(0f))
                val expanded = contactRows.firstOrNull { it.hit.id == expandedContact }?.takeIf { selected == SearchCategory.Contacts }
                Column(Modifier.fillMaxSize()) {
                    Spacer(Modifier.height(52.dp))
                    if (layout == SearchLayout.Orbit) {
                        Orbit(
                            tiles = data.tiles(selected), motion = prefs.searchMotion, footnote = data.footnote(selected),
                            actions = tileActions, modifier = Modifier.weight(1f).fillMaxWidth(),
                        )
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                    Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                        QueryDisplay(query, onClear = { setQuery("") }, onTap = { showKeyboard() }, modifier = Modifier.weight(1f).padding(end = 14.dp))
                        CategoryWheel(
                            selected = selected, counts = counts,
                            onSelect = { userCat = it; touched = true; expandedContact = null },
                            onTick = { tick() }, onTouch = { touched = true },
                            modifier = Modifier.width(124.dp),
                        )
                        Spacer(Modifier.width(76.dp))
                    }
                    if (layout == SearchLayout.Filmstrip) {
                        Filmstrip(
                            tiles = data.tiles(selected), motion = prefs.searchMotion, query = trimmed,
                            expanded = expanded, onOpenIntent = onOpenIntent, footnote = data.footnote(selected),
                            actions = tileActions, modifier = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 8.dp),
                        )
                    } else {
                        Box(Modifier.fillMaxWidth().height(48.dp), contentAlignment = Alignment.Center) {
                            if (expanded != null) ContactChannelBar(expanded, onOpenIntent)
                        }
                    }
                }
                // Top bar: the scope token and close; the suggestion card hangs under it while typing.
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    ScopeToken(userCat, selected, { userCat = it; expandedContact = null })
                    Spacer(Modifier.weight(1f))
                    Icon(
                        Glyphs.Close, L10n.tr("Close"), tint = Color.White,
                        modifier = Modifier.size(36.dp).clip(CircleShape).clickable(onClick = onDismiss).padding(9.dp),
                    )
                }
                if (showSuggestions) {
                    SuggestionCard(groups, pickSuggestion, Modifier.padding(start = 16.dp, top = 56.dp).widthIn(max = 320.dp))
                }
            }
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
        cursorBrush = SolidColor(if (color == Color.Transparent) Color.Transparent else Color.White),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
        keyboardActions = KeyboardActions(onGo = { onGo() }),
        modifier = modifier.focusRequester(focus),
    )
}

/**
 * Enter / IME-Go: launch the top app result, otherwise open the first web row (a URL, a bang, or
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
    val bangOrUrl = WebSearch.bang(q) != null || WebSearch.looksLikeUrl(q)
    val top = if (bangOrUrl && prefs.searchWeb) null else AppSearch.rank(apps, q, limit = 1).firstOrNull()
    if (top != null) { onLaunch(top); return }
    if (!prefs.searchWeb) return
    val s = sections(q, prefs.webEngine, emptyList<AppEntry>(), emptyList<ContactHit>())
    val url = when (val first = s.web.firstOrNull()) {
        is WebRow.Open -> first.url
        is WebRow.BangSearch -> first.url
        is WebRow.Search -> first.url
        is WebRow.PlayStore -> first.url
        else -> null
    }
    if (url != null) onOpenIntent(WebSearch.intentFor(url))
}

internal class ContactRow(val hit: ContactHit, val channels: List<Channel>)

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
            .clickable(onClick = onClick),
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
        is WebRow.BangSearch -> SimpleRow(Glyphs.Search, row.label) { onOpenIntent(WebSearch.intentFor(row.url)) }
        is WebRow.Search -> SimpleRow(Glyphs.Search, row.label) { onOpenIntent(WebSearch.intentFor(row.url)) }
        is WebRow.PlayStore -> SimpleRow(Glyphs.Apps, row.label) { onOpenIntent(WebSearch.intentFor(row.url)) }
        is WebRow.BangHints -> Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 6.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(L10n.tr("Try"), color = HyleColors.InkDim, fontSize = 12.sp)
            Spacer(Modifier.width(8.dp))
            row.chips.forEach { chip ->
                Text(
                    "!${chip.code}",
                    color = HyleColors.Violet, fontSize = 13.sp,
                    modifier = Modifier
                        .padding(end = 6.dp)
                        .background(HyleColors.Surface, RoundedCornerShape(50))
                        .border(1.dp, HyleColors.Hairline, RoundedCornerShape(50))
                        .clickable { onQueryChange(chip.rewritten) }
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                )
            }
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
