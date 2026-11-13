package com.bos.sphere.ui

import androidx.compose.foundation.layout.fillMaxHeight
import android.content.Context
import android.content.Intent
import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bos.sphere.core.data.GroupMode
import com.bos.sphere.core.data.HubNotification
import com.bos.sphere.core.data.LaunchpadLogic
import com.bos.sphere.core.data.LaunchpadPrefs
import com.bos.sphere.core.data.LaunchpadSettings
import com.bos.sphere.core.data.NotificationProvider
import com.bos.sphere.core.data.Rule
import com.bos.sphere.core.data.RuleAction
import com.bos.sphere.core.data.Section
import com.bos.sphere.core.data.SphereNotificationListener
import com.bos.sphere.core.design.HyleColors
import com.bos.sphere.core.design.L10n
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToInt

/** Launchpad palette: dark by default, or the light BB10-hub look. */
internal class LpColors(val bg: Color, val surface: Color, val ink: Color, val ink2: Color, val dim: Color, val line: Color)
private val LpDark = LpColors(Color(0xFF0B0D12), Color(0xFF1B1E27), Color(0xFFF1F3F8), Color(0xFFC9CED9), Color(0xFF8A91A3), Color(0x22FFFFFF))
private val LpLight = LpColors(Color(0xFFF7F8FA), Color(0xFFE4E8EF), Color(0xFF14171F), Color(0xFF3B4150), Color(0xFF6C7384), Color(0x1F000000))
private val LocalLp = androidx.compose.runtime.compositionLocalOf { LpDark }

private val BlueDark = Color(0xFF0A5C95)
private val BlueLight = Color(0xFF0BA6E0)

// ---------------------------------------------------------------------------------------------
// Shared state helpers
// ---------------------------------------------------------------------------------------------

@Composable
private fun rememberLaunchpadInit() {
    val ctx = LocalContext.current
    remember(ctx) { LaunchpadSettings.init(ctx); true }
}

/** True while the user has granted notification access; re-checked every time the app resumes. */
@Composable
private fun rememberNotificationAccess(): Boolean {
    val ctx = LocalContext.current
    var access by remember { mutableStateOf(SphereNotificationListener.isEnabled(ctx)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { access = SphereNotificationListener.isEnabled(ctx) }
    return access
}

/** Wall clock that ticks every 30 s so auto-clear rules and relative days stay current. */
@Composable
private fun rememberNow(): Long {
    val now by produceState(System.currentTimeMillis()) {
        while (true) { delay(30_000); value = System.currentTimeMillis() }
    }
    return now
}

private val iconCache = ConcurrentHashMap<String, ImageBitmap>()

@Composable
private fun rememberAppIcon(pkg: String): ImageBitmap? {
    val ctx = LocalContext.current
    val icon by produceState(iconCache[pkg], pkg) {
        if (pkg.isBlank()) return@produceState
        value = iconCache[pkg] ?: withContext(Dispatchers.IO) {
            runCatching { ctx.packageManager.getApplicationIcon(pkg).toBitmap(96, 96).asImageBitmap() }.getOrNull()
        }?.also { iconCache[pkg] = it }
    }
    return icon
}

@Composable
private fun AppIcon(pkg: String, label: String, size: Int, modifier: Modifier = Modifier) {
    val icon = rememberAppIcon(pkg)
    val shape = RoundedCornerShape((size * 0.24f).dp)
    if (icon != null) {
        Image(icon, null, modifier.size(size.dp).clip(shape))
    } else {
        Box(modifier.size(size.dp).clip(shape).background(LocalLp.current.surface), Alignment.Center) {
            Text(
                label.take(1).uppercase(), color = LocalLp.current.dim,
                fontSize = (size * 0.42f).sp, fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

private fun formatStamp(ts: Long, now: Long, is24: Boolean, zone: ZoneId): String {
    val d = Instant.ofEpochMilli(ts).atZone(zone)
    val same = d.toLocalDate() == Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    val pattern = if (is24) "HH:mm" else "h:mm a"
    val time = DateTimeFormatter.ofPattern(pattern, Locale.getDefault()).format(d)
    return if (same) time else DateTimeFormatter.ofPattern("d MMM", Locale.getDefault()).format(d)
}

private fun groupModeLabel(m: GroupMode) = when (m) {
    GroupMode.Time -> L10n.tr("Time")
    GroupMode.App -> L10n.tr("App")
    GroupMode.Sender -> L10n.tr("Sender")
    GroupMode.Conversation -> L10n.tr("Conversation")
    GroupMode.Flat -> L10n.tr("Flat")
}

// ---------------------------------------------------------------------------------------------
// Panel
// ---------------------------------------------------------------------------------------------

/**
 * The Launchpad: full-screen notifications centre that slides in from the left edge.
 * Back press and a swipe to the left close it ([onDismiss]).
 */
@Composable
fun LaunchpadPanel(
    visible: Boolean,
    onDismiss: () -> Unit,
    onOpenApp: (packageName: String) -> Unit,
    modifier: Modifier = Modifier,
    theme: com.bos.sphere.core.data.LaunchpadTheme = com.bos.sphere.core.data.LaunchpadTheme.Dark,
    /**
     * True when the home screen slides away to uncover the Launchpad beneath it (the host moves the
     * home and casts the shadow): the panel then just sits there instead of sliding in itself.
     */
    underlay: Boolean = false,
    /** Room on the right that the floating home still covers; the list lays out in what is left. */
    endInset: androidx.compose.ui.unit.Dp = 0.dp,
) {
    rememberLaunchpadInit()
    val dark = when (theme) {
        com.bos.sphere.core.data.LaunchpadTheme.Dark -> true
        com.bos.sphere.core.data.LaunchpadTheme.Light -> false
        com.bos.sphere.core.data.LaunchpadTheme.System -> androidx.compose.foundation.isSystemInDarkTheme()
    }
    androidx.compose.runtime.CompositionLocalProvider(LocalLp provides if (dark) LpDark else LpLight) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = if (underlay) androidx.compose.animation.EnterTransition.None else slideInHorizontally { -it } + fadeIn(),
        exit = if (underlay) androidx.compose.animation.ExitTransition.None else slideOutHorizontally { -it } + fadeOut(),
    ) {
        BackHandler(onBack = onDismiss)
        val ctx = LocalContext.current
        val items by NotificationProvider.notifications.collectAsStateWithLifecycle(emptyList())
        val rules by LaunchpadSettings.rules.collectAsStateWithLifecycle()
        val prefs by LaunchpadSettings.prefs.collectAsStateWithLifecycle()
        val read by LaunchpadSettings.readKeys.collectAsStateWithLifecycle()
        val access = rememberNotificationAccess()
        val now = rememberNow()
        val gestures by LaunchpadSettings.gestures.collectAsStateWithLifecycle()
        LaunchedEffect(items) { LaunchpadSettings.pruneRead(items.map { it.key }.toSet()) }

        LaunchpadContent(
            items = items, rules = rules, prefs = prefs, readKeys = read, hasAccess = access, now = now,
            gestures = gestures,
            endInset = endInset,
            onClose = onDismiss,
            onOpenItem = { n ->
                LaunchpadSettings.markRead(n.key)
                val sent = n.contentIntent?.let { pi -> runCatching { pi.send() }.isSuccess } ?: false
                if (!sent && n.packageName.isNotBlank()) onOpenApp(n.packageName)
                onDismiss()
            },
            onOpenApp = { pkg -> onOpenApp(pkg); onDismiss() },
            onDismissItem = { NotificationProvider.dismiss(it.key) },
            onClearAll = { NotificationProvider.clearAll() },
            onAddRule = { LaunchpadSettings.addRule(it) },
            onSetDefaultMode = { LaunchpadSettings.setDefaultMode(it) },
            onRequestAccess = {
                runCatching {
                    ctx.startActivity(SphereNotificationListener.notificationAccessIntent().addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            },
        )
    }
    }
}

/**
 * Stateless Launchpad body (header + grouped list / empty / access states). All data comes in
 * as plain parameters, so the whole thing can be driven from tests or previews.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LaunchpadContent(
    items: List<HubNotification>,
    rules: List<Rule>,
    prefs: LaunchpadPrefs,
    readKeys: Set<String>,
    hasAccess: Boolean,
    now: Long,
    onClose: () -> Unit,
    onOpenItem: (HubNotification) -> Unit,
    onOpenApp: (String) -> Unit,
    onDismissItem: (HubNotification) -> Unit,
    onClearAll: () -> Unit,
    onAddRule: (Rule) -> Unit,
    onSetDefaultMode: (GroupMode) -> Unit,
    onRequestAccess: () -> Unit,
    modifier: Modifier = Modifier,
    gestures: Map<com.bos.sphere.core.data.LaunchpadGesture, com.bos.sphere.core.data.LaunchpadAction> = com.bos.sphere.core.data.LaunchpadGestures.defaults,
    endInset: androidx.compose.ui.unit.Dp = 0.dp,
) {
    val ctx = LocalContext.current
    val zone = remember { ZoneId.systemDefault() }
    val is24 = remember(ctx) { DateFormat.is24HourFormat(ctx) }
    var filter by remember { mutableStateOf<String?>(null) }
    var collapsed by remember { mutableStateOf(setOf<String>()) }
    var menuFor by remember { mutableStateOf<HubNotification?>(null) }
    var unreadOnly by remember { mutableStateOf(false) }
    val shown = remember(items, readKeys, unreadOnly) { if (unreadOnly) items.filter { it.key !in readKeys } else items }
    val toast = remember { mutableStateOf<String?>(null) }
    LaunchedEffect(toast.value) { if (toast.value != null) { delay(1400); toast.value = null } }

    fun perform(action: com.bos.sphere.core.data.LaunchpadAction, n: HubNotification?, sections: List<Section>) {
        val a = com.bos.sphere.core.data.LaunchpadAction.entries
        val pkg = n?.packageName.orEmpty()
        when (action) {
            com.bos.sphere.core.data.LaunchpadAction.None -> Unit
            com.bos.sphere.core.data.LaunchpadAction.ShowUnread -> { unreadOnly = true; toast.value = L10n.tr("Unread only") }
            com.bos.sphere.core.data.LaunchpadAction.ShowAll -> { unreadOnly = false; toast.value = L10n.tr("Showing everything") }
            com.bos.sphere.core.data.LaunchpadAction.ToggleUnread -> { unreadOnly = !unreadOnly; toast.value = L10n.tr(if (unreadOnly) "Unread only" else "Showing everything") }
            com.bos.sphere.core.data.LaunchpadAction.NextGrouping -> {
                val next = GroupMode.entries[(prefs.defaultMode.ordinal + 1) % GroupMode.entries.size]
                onSetDefaultMode(next); toast.value = L10n.tr("Grouped: %s", L10n.tr(groupModeLabel(next)))
            }
            com.bos.sphere.core.data.LaunchpadAction.CollapseAll ->
                collapsed = if (collapsed.size >= sections.size) emptySet() else sections.map { it.key }.toSet()
            com.bos.sphere.core.data.LaunchpadAction.ClearAll -> onClearAll()
            com.bos.sphere.core.data.LaunchpadAction.Dismiss -> n?.takeIf { !it.isOngoing }?.let(onDismissItem)
            com.bos.sphere.core.data.LaunchpadAction.MarkRead -> n?.let { LaunchpadSettings.markRead(it.key); toast.value = L10n.tr("Marked as read") }
            com.bos.sphere.core.data.LaunchpadAction.OpenApp -> if (pkg.isNotBlank()) onOpenApp(pkg)
            com.bos.sphere.core.data.LaunchpadAction.MoreOptions -> menuFor = n
            com.bos.sphere.core.data.LaunchpadAction.MuteApp -> if (pkg.isNotBlank()) { onAddRule(Rule.create(RuleAction.Silent, packageName = pkg)); toast.value = L10n.tr("Muted") }
            com.bos.sphere.core.data.LaunchpadAction.HideApp -> if (pkg.isNotBlank()) { onAddRule(Rule.create(RuleAction.Hide, packageName = pkg)); toast.value = L10n.tr("Hidden") }
            com.bos.sphere.core.data.LaunchpadAction.PinSender -> n?.let { LaunchpadLogic.senderOf(it).takeIf { s -> s.isNotBlank() }?.let { s -> onAddRule(Rule.create(RuleAction.Pin, pkg.ifBlank { null }, s)); toast.value = L10n.tr("Pinned") } }
        }
        @Suppress("UNUSED_VARIABLE") val unused = a
    }

    // Counts for the filter menu reflect what the user can actually see.
    val visible = remember(items, rules, now) { LaunchpadLogic.visible(items, rules, now) }
    val counts = remember(visible) { LaunchpadLogic.countsBySource(visible) }
    val activeFilter = filter?.takeIf { f -> counts.any { it.packageName == f } }
    val sections = remember(shown, rules, prefs, now, activeFilter) {
        LaunchpadLogic.apply(
            LaunchpadLogic.filter(shown, activeFilter), rules, prefs, now, zone,
            labelFor = { d: LocalDate -> LaunchpadLogic.defaultDayLabel(d) },
            todayLabel = L10n.tr("Today"), yesterdayLabel = L10n.tr("Yesterday"),
            pinnedLabel = L10n.tr("Pinned"), flatLabel = L10n.tr("All"),
        )
    }

    Column(
        modifier
            .fillMaxSize()
            .background(LocalLp.current.bg)
            .pointerInput(Unit) { detectTapGestures { } }
            .pointerInput(Unit) {
                var total = 0f
                detectHorizontalDragGestures(
                    onDragStart = { total = 0f },
                    onDragEnd = { if (total < -80f) onClose() },
                ) { _, dx -> total += dx }
            }
            .lpPinch { zoomIn ->
                perform(
                    gestures[if (zoomIn) com.bos.sphere.core.data.LaunchpadGesture.PinchOut else com.bos.sphere.core.data.LaunchpadGesture.PinchIn]
                        ?: com.bos.sphere.core.data.LaunchpadAction.None,
                    null, sections,
                )
            }
            .padding(end = endInset),
    ) {
        LaunchpadHeader(
            counts = counts, filter = activeFilter, mode = prefs.defaultMode,
            onFilter = { filter = it }, onMode = onSetDefaultMode, onClearAll = onClearAll,
            canClear = items.isNotEmpty(), onClose = onClose,
            onDoubleTap = { perform(gestures[com.bos.sphere.core.data.LaunchpadGesture.DoubleTapHeader] ?: com.bos.sphere.core.data.LaunchpadAction.None, null, sections) },
        )
        when {
            !hasAccess -> AccessCard(onRequestAccess)
            sections.isEmpty() -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                Text(L10n.tr(if (unreadOnly) "No unread notifications" else "All caught up"), color = LocalLp.current.dim, fontSize = 18.sp, fontWeight = FontWeight.Light)
            }
            else -> LazyColumn(Modifier.fillMaxSize().navigationBarsPadding()) {
                sections.forEach { sec ->
                    val isCollapsed = sec.key in collapsed
                    item(key = "h:${sec.key}") {
                        SectionHeader(sec, isCollapsed) {
                            collapsed = if (isCollapsed) collapsed - sec.key else collapsed + sec.key
                        }
                    }
                    if (!isCollapsed) {
                        items(sec.items, key = { "n:${it.key}" }) { n ->
                            NotificationRow(
                                n = n, unread = n.key !in readKeys, now = now, is24 = is24, zone = zone,
                                important = LaunchpadLogic.isImportant(n, rules),
                                pinned = sec.pinned,
                                onClick = { onOpenItem(n) },
                                onLongClick = { perform(gestures[com.bos.sphere.core.data.LaunchpadGesture.LongPressItem] ?: com.bos.sphere.core.data.LaunchpadAction.MoreOptions, n, sections) },
                                rightAction = gestures[com.bos.sphere.core.data.LaunchpadGesture.SwipeItemRight] ?: com.bos.sphere.core.data.LaunchpadAction.Dismiss,
                                leftAction = gestures[com.bos.sphere.core.data.LaunchpadGesture.SwipeItemLeft] ?: com.bos.sphere.core.data.LaunchpadAction.Dismiss,
                                onSwipe = { act -> perform(act, n, sections) },
                                modifier = Modifier.animateItem(),
                            )
                        }
                    }
                }
            }
        }
    }

    toast.value?.let { t ->
        Box(Modifier.fillMaxSize().navigationBarsPadding().padding(bottom = 24.dp), Alignment.BottomCenter) {
            Text(
                t, color = Color.White, fontSize = 13.sp,
                modifier = Modifier.background(Color(0xE6202228), RoundedCornerShape(50)).padding(horizontal = 16.dp, vertical = 9.dp),
            )
        }
    }
    menuFor?.let { n ->
        RowMenuSheet(
            n = n,
            onDismissSheet = { menuFor = null },
            onAddRule = { onAddRule(it); menuFor = null },
            onOpenApp = { onOpenApp(n.packageName); menuFor = null },
            onDismissItem = { onDismissItem(n); menuFor = null },
        )
    }
}

@Composable
private fun LaunchpadHeader(
    counts: List<com.bos.sphere.core.data.SourceCount>,
    filter: String?,
    mode: GroupMode,
    onFilter: (String?) -> Unit,
    onMode: (GroupMode) -> Unit,
    onClearAll: () -> Unit,
    canClear: Boolean,
    onClose: () -> Unit,
    onDoubleTap: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box(
        Modifier.fillMaxWidth().background(Brush.horizontalGradient(listOf(BlueDark, BlueLight)))
            .pointerInput(Unit) { detectTapGestures(onDoubleTap = { onDoubleTap() }) },
    ) {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().height(64.dp).padding(start = 20.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                L10n.tr("LAUNCHPAD"), color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Light,
                letterSpacing = 1.sp, modifier = Modifier.weight(1f),
            )
            if (filter != null) {
                Text(
                    counts.firstOrNull { it.packageName == filter }?.appLabel.orEmpty(),
                    color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(end = 4.dp),
                )
            }
            Box {
                Box(
                    Modifier.size(48.dp).clip(CircleShape).clickable { open = true },
                    Alignment.Center,
                ) { Chevron(Color.White, down = true) }
                DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                    MenuLabel(L10n.tr("Group by"))
                    GroupMode.entries.forEach { m ->
                        DropdownMenuItem(
                            text = { Text((if (m == mode) "✓  " else "     ") + groupModeLabel(m)) },
                            onClick = { onMode(m); open = false },
                        )
                    }
                    MenuLabel(L10n.tr("Show"))
                    DropdownMenuItem(
                        text = {
                            Text((if (filter == null) "✓  " else "     ") + L10n.tr("All") + "  (${counts.sumOf { it.count }})")
                        },
                        onClick = { onFilter(null); open = false },
                    )
                    counts.forEach { c ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    (if (filter == c.packageName) "✓  " else "     ") + c.appLabel + "  (${c.count})",
                                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                                )
                            },
                            onClick = { onFilter(c.packageName); open = false },
                        )
                    }
                    if (canClear) {
                        MenuLabel("")
                        DropdownMenuItem(
                            text = { Text(L10n.tr("Clear all")) },
                            onClick = { onClearAll(); open = false },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MenuLabel(text: String) {
    if (text.isNotEmpty()) {
        Text(
            text.uppercase(), color = LocalLp.current.dim, fontSize = 11.sp, fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 2.dp),
        )
    }
}

@Composable
private fun Chevron(color: Color, down: Boolean, size: Int = 18) {
    Canvas(Modifier.size(size.dp)) {
        val w = this.size.width
        val h = this.size.height
        val y1 = if (down) h * 0.32f else h * 0.68f
        val y2 = if (down) h * 0.68f else h * 0.32f
        val sw = 2.2.dp.toPx()
        drawLine(color, Offset(w * 0.1f, y1), Offset(w * 0.5f, y2), sw, StrokeCap.Round)
        drawLine(color, Offset(w * 0.5f, y2), Offset(w * 0.9f, y1), sw, StrokeCap.Round)
    }
}

@Composable
private fun SectionHeader(sec: Section, collapsed: Boolean, onToggle: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onToggle)) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val lead = sec.items.firstOrNull()
            if (lead != null && sec.key != LaunchpadLogic.FLAT_KEY && !sec.pinned && sec.key.startsWith("app:")) {
                AppIcon(lead.packageName, sec.title, 20, Modifier.padding(end = 8.dp))
            }
            Text(
                sec.title, color = LocalLp.current.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
            )
            if (sec.subtitle.isNotEmpty()) {
                Text(
                    "  ${sec.subtitle}", color = LocalLp.current.dim, fontSize = 12.sp, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
                )
            }
            Spacer(Modifier.weight(1f))
            Text("${sec.items.size}", color = LocalLp.current.dim, fontSize = 12.sp, modifier = Modifier.padding(end = 8.dp))
            Chevron(LocalLp.current.dim, down = !collapsed, size = 14)
        }
        Box(Modifier.fillMaxWidth().height(2.dp).background(BlueLight))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NotificationRow(
    n: HubNotification,
    unread: Boolean,
    now: Long,
    is24: Boolean,
    zone: ZoneId,
    important: Boolean,
    pinned: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    rightAction: com.bos.sphere.core.data.LaunchpadAction,
    leftAction: com.bos.sphere.core.data.LaunchpadAction,
    onSwipe: (com.bos.sphere.core.data.LaunchpadAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    var dx by remember { mutableFloatStateOf(0f) }
    var base by remember { mutableFloatStateOf(0f) } // 0, or the palette's width while it is held open
    var widthPx by remember { mutableFloatStateOf(1f) }
    val weight = if (unread) FontWeight.Bold else FontWeight.Normal
    val density = androidx.compose.ui.platform.LocalDensity.current
    val palette = listOf(
        com.bos.sphere.core.data.LaunchpadAction.MarkRead to Color(0xFF0B7BB8),
        com.bos.sphere.core.data.LaunchpadAction.OpenApp to Color(0xFF0A6296),
        com.bos.sphere.core.data.LaunchpadAction.MuteApp to Color(0xFF3B4A5A),
        com.bos.sphere.core.data.LaunchpadAction.PinSender to Color(0xFF2F6F4F),
        com.bos.sphere.core.data.LaunchpadAction.MoreOptions to Color(0xFF55486F),
    )
    val cellPx = minOf(with(density) { 72.dp.toPx() }, widthPx * 0.9f / palette.size)
    val paletteW = cellPx * palette.size
    val offsetPx = (base + dx).coerceIn(-widthPx, paletteW * 1.1f)
    Column(modifier) {
      Box(Modifier.fillMaxWidth()) {
        if (offsetPx > 0f) {
            // Slide right: a whole palette of actions, held open until one is chosen or the row is tapped.
            Row(Modifier.matchParentSize()) {
                palette.forEach { (act, color) ->
                    val label = when (act) {
                        com.bos.sphere.core.data.LaunchpadAction.MarkRead -> "Read"
                        com.bos.sphere.core.data.LaunchpadAction.OpenApp -> "Open"
                        com.bos.sphere.core.data.LaunchpadAction.MuteApp -> "Mute"
                        com.bos.sphere.core.data.LaunchpadAction.PinSender -> "Pin"
                        else -> "More"
                    }
                    Box(
                        Modifier.width(with(density) { cellPx.toDp() }).fillMaxHeight().background(color)
                            .clickable { base = 0f; dx = 0f; onSwipe(act) },
                        contentAlignment = Alignment.Center,
                    ) { Text(L10n.tr(label), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1) }
                }
            }
        } else if (offsetPx < 0f && leftAction != com.bos.sphere.core.data.LaunchpadAction.None) {
            // Slide left: the one action, uncovered behind the row.
            Box(
                Modifier.matchParentSize().background(Color(0xFFB3261E)).padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterEnd,
            ) { Text(L10n.tr(leftAction.label), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium) }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .offset { IntOffset(offsetPx.roundToInt(), 0) }
                .background(LocalLp.current.bg)
                .pointerInput(n.key, leftAction) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            val total = base + dx
                            dx = 0f
                            if (total < -widthPx * 0.3f) { base = 0f; onSwipe(leftAction) }
                            else if (total > paletteW * 0.4f) base = paletteW
                            else base = 0f
                        },
                        onDragCancel = { dx = 0f },
                    ) { _, delta -> dx += delta }
                }
                .combinedClickable(onClick = { if (base > 0f) base = 0f else onClick() }, onLongClick = onLongClick)
                .onSizeChanged { widthPx = it.width.toFloat().coerceAtLeast(1f) }
                .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.width(3.dp).height(36.dp).background(
                if (pinned || important) BlueLight else Color.Transparent, RoundedCornerShape(2.dp),
            ))
            Spacer(Modifier.width(10.dp))
            Box {
                AppIcon(n.packageName, n.appLabel.ifBlank { n.title }, 40)
                if (unread) {
                    Box(
                        Modifier.size(10.dp).align(Alignment.TopStart).offset((-3).dp, (-3).dp)
                            .background(BlueLight, CircleShape),
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    n.title, color = LocalLp.current.ink, fontSize = 16.sp, fontWeight = weight,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                if (n.summary.isNotEmpty()) {
                    Text(
                        n.summary, color = if (unread) LocalLp.current.ink2 else LocalLp.current.dim, fontSize = 13.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            Text(
                formatStamp(n.timestamp, now, is24, zone), color = LocalLp.current.dim, fontSize = 12.sp,
                fontWeight = if (unread) FontWeight.Medium else FontWeight.Normal,
            )
        }
      }
        Box(Modifier.fillMaxWidth().height(0.5.dp).background(LocalLp.current.line))
    }
}

@Composable
private fun AccessCard(onRequestAccess: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(32.dp), Alignment.Center) {
        Column(
            Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(LocalLp.current.surface)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                L10n.tr("Allow notification access to see everything here"),
                color = LocalLp.current.ink, fontSize = 16.sp, textAlign = TextAlign.Center,
            )
            Box(
                Modifier
                    .clip(RoundedCornerShape(24.dp))
                    .background(Brush.horizontalGradient(listOf(BlueDark, BlueLight)))
                    .clickable(onClick = onRequestAccess)
                    .padding(horizontal = 22.dp, vertical = 10.dp),
            ) { Text(L10n.tr("Allow access"), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RowMenuSheet(
    n: HubNotification,
    onDismissSheet: () -> Unit,
    onAddRule: (Rule) -> Unit,
    onOpenApp: () -> Unit,
    onDismissItem: () -> Unit,
) {
    val app = n.appLabel.ifBlank { n.packageName }
    val sender = LaunchpadLogic.senderOf(n).takeIf { it.isNotBlank() }
    val pkg = n.packageName
    val entries = buildList<Pair<String, () -> Unit>> {
        add(L10n.tr("Open app") to onOpenApp)
        if (!n.isOngoing) add(L10n.tr("Dismiss") to onDismissItem)
        if (pkg.isNotBlank()) {
            add(L10n.tr("Hide this app") to { onAddRule(Rule.create(RuleAction.Hide, packageName = pkg)) })
            add(L10n.tr("Mute this app (no badges)") to { onAddRule(Rule.create(RuleAction.Silent, packageName = pkg)) })
        }
        if (sender != null) {
            add(L10n.tr("Hide %s", sender) to { onAddRule(Rule.create(RuleAction.Hide, pkg.ifBlank { null }, sender)) })
            add(L10n.tr("Pin %s", sender) to { onAddRule(Rule.create(RuleAction.Pin, pkg.ifBlank { null }, sender)) })
        }
        val conv = n.conversation?.takeIf { it.isNotBlank() }
        if (conv != null) {
            add(L10n.tr("Pin this conversation") to { onAddRule(Rule.create(RuleAction.Pin, pkg.ifBlank { null }, conversation = conv)) })
        }
        if (pkg.isNotBlank()) {
            add(L10n.tr("Group by app for %s", app) to { onAddRule(Rule.create(RuleAction.GroupApp, pkg)) })
            add(L10n.tr("Auto-clear after 1 h for %s", app) to { onAddRule(Rule.create(RuleAction.AutoClear, pkg, minutes = 60)) })
            add(L10n.tr("Auto-clear after 1 day for %s", app) to { onAddRule(Rule.create(RuleAction.AutoClear, pkg, minutes = 1440)) })
        }
    }
    ModalBottomSheet(onDismissRequest = onDismissSheet, containerColor = LocalLp.current.surface) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 12.dp)) {
            Row(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                AppIcon(pkg, app, 32)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(n.title, color = LocalLp.current.ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(app, color = LocalLp.current.dim, fontSize = 12.sp)
                }
            }
            entries.forEach { (label, act) ->
                Text(
                    label, color = LocalLp.current.ink2, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth().clickable { act() }.padding(horizontal = 20.dp, vertical = 13.dp),
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Rail
// ---------------------------------------------------------------------------------------------

/**
 * The BB10-style peek rail: a narrow strip pinned to the left edge showing per-app unread
 * counts (rules applied: hidden / muted apps do not appear). Tap or swipe right to open.
 * Renders nothing when there is nothing unread or notification access is missing.
 */
@Composable
fun LaunchpadRail(onOpen: () -> Unit, modifier: Modifier = Modifier) {
    rememberLaunchpadInit()
    val access = rememberNotificationAccess()
    val items by NotificationProvider.notifications.collectAsStateWithLifecycle(emptyList())
    val rules by LaunchpadSettings.rules.collectAsStateWithLifecycle()
    val read by LaunchpadSettings.readKeys.collectAsStateWithLifecycle()
    val now = rememberNow()
    val counts = remember(items, rules, read, now) { LaunchpadLogic.unreadCounts(items, rules, read, now) }
    if (!access || counts.isEmpty()) return
    val total = counts.sumOf { it.count }

    Column(
        modifier
            .width(44.dp)
            .clip(RoundedCornerShape(topEnd = 22.dp, bottomEnd = 22.dp))
            .background(Color(0xB30B0D14))
            .semantics(mergeDescendants = true) { contentDescription = L10n.tr("%d unread. Launchpad", total) }
            .clickable(onClick = onOpen, onClickLabel = L10n.tr("Open"))
            .pointerInput(Unit) {
                var total = 0f
                detectHorizontalDragGestures(
                    onDragStart = { total = 0f },
                    onDragEnd = { if (total > 24f) onOpen() },
                ) { _, dx -> total += dx }
            }
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        counts.take(6).forEach { c ->
            Box(Modifier.size(38.dp), Alignment.Center) {
                AppIcon(c.packageName, c.appLabel, 28)
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .defaultMinSize(minWidth = 16.dp, minHeight = 16.dp)
                        .background(BlueLight, CircleShape)
                        .padding(horizontal = 4.dp),
                    Alignment.Center,
                ) {
                    Text(
                        if (c.count > 99) "99+" else c.count.toString(), color = Color.White,
                        fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1,
                    )
                }
            }
        }
    }
}

/**
 * Two-finger pinch on the list: reports [zoomIn] true for fingers apart, false for together, once per
 * gesture, and only after the spread has changed by more than ~18%. One-finger input is never
 * touched, so scrolling and swipes are unaffected.
 */
private fun Modifier.lpPinch(onPinch: (zoomIn: Boolean) -> Unit): Modifier = this.pointerInput(Unit) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false, pass = androidx.compose.ui.input.pointer.PointerEventPass.Initial)
        var start = 0f
        var fired = false
        while (true) {
            val ev = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
            val down = ev.changes.filter { it.pressed }
            if (down.isEmpty()) break
            if (down.size >= 2) {
                val d = (down[0].position - down[1].position).getDistance()
                if (start == 0f) start = d
                else if (!fired && start > 1f) {
                    val r = d / start
                    if (r > 1.18f || r < 0.82f) { fired = true; onPinch(r > 1f); down.forEach { it.consume() } }
                }
                if (fired) down.forEach { it.consume() }
            }
        }
    }
}
