package com.bos.sphere.ui

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
        Box(modifier.size(size.dp).clip(shape).background(HyleColors.Surface), Alignment.Center) {
            Text(
                label.take(1).uppercase(), color = HyleColors.InkDim,
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
) {
    rememberLaunchpadInit()
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = slideInHorizontally { -it } + fadeIn(),
        exit = slideOutHorizontally { -it } + fadeOut(),
    ) {
        BackHandler(onBack = onDismiss)
        val ctx = LocalContext.current
        val items by NotificationProvider.notifications.collectAsStateWithLifecycle(emptyList())
        val rules by LaunchpadSettings.rules.collectAsStateWithLifecycle()
        val prefs by LaunchpadSettings.prefs.collectAsStateWithLifecycle()
        val read by LaunchpadSettings.readKeys.collectAsStateWithLifecycle()
        val access = rememberNotificationAccess()
        val now = rememberNow()
        LaunchedEffect(items) { LaunchpadSettings.pruneRead(items.map { it.key }.toSet()) }

        LaunchpadContent(
            items = items, rules = rules, prefs = prefs, readKeys = read, hasAccess = access, now = now,
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
) {
    val ctx = LocalContext.current
    val zone = remember { ZoneId.systemDefault() }
    val is24 = remember(ctx) { DateFormat.is24HourFormat(ctx) }
    var filter by remember { mutableStateOf<String?>(null) }
    var collapsed by remember { mutableStateOf(setOf<String>()) }
    var menuFor by remember { mutableStateOf<HubNotification?>(null) }

    // Counts for the filter menu reflect what the user can actually see.
    val visible = remember(items, rules, now) { LaunchpadLogic.visible(items, rules, now) }
    val counts = remember(visible) { LaunchpadLogic.countsBySource(visible) }
    val activeFilter = filter?.takeIf { f -> counts.any { it.packageName == f } }
    val sections = remember(items, rules, prefs, now, activeFilter) {
        LaunchpadLogic.apply(
            LaunchpadLogic.filter(items, activeFilter), rules, prefs, now, zone,
            labelFor = { d: LocalDate -> LaunchpadLogic.defaultDayLabel(d) },
            todayLabel = L10n.tr("Today"), yesterdayLabel = L10n.tr("Yesterday"),
            pinnedLabel = L10n.tr("Pinned"), flatLabel = L10n.tr("All"),
        )
    }

    Column(
        modifier
            .fillMaxSize()
            .background(HyleColors.Background.copy(alpha = 0.95f))
            .pointerInput(Unit) { detectTapGestures { } }
            .pointerInput(Unit) {
                var total = 0f
                detectHorizontalDragGestures(
                    onDragStart = { total = 0f },
                    onDragEnd = { if (total < -80f) onClose() },
                ) { _, dx -> total += dx }
            },
    ) {
        LaunchpadHeader(
            counts = counts, filter = activeFilter, mode = prefs.defaultMode,
            onFilter = { filter = it }, onMode = onSetDefaultMode, onClearAll = onClearAll,
            canClear = items.isNotEmpty(), onClose = onClose,
        )
        when {
            !hasAccess -> AccessCard(onRequestAccess)
            sections.isEmpty() -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                Text(L10n.tr("All caught up"), color = HyleColors.InkDim, fontSize = 18.sp, fontWeight = FontWeight.Light)
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
                                onLongClick = { menuFor = n },
                                onSwipeDismiss = { onDismissItem(n) },
                                onSwipeClose = onClose,
                                modifier = Modifier.animateItem(),
                            )
                        }
                    }
                }
            }
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
) {
    var open by remember { mutableStateOf(false) }
    Box(
        Modifier.fillMaxWidth().background(Brush.horizontalGradient(listOf(BlueDark, BlueLight))),
    ) {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().height(64.dp).padding(start = 20.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                L10n.tr("LAUNCHPAD"), color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Light,
                letterSpacing = 1.sp, modifier = Modifier.weight(1f).clickable(onClick = onClose),
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
            text.uppercase(), color = HyleColors.InkDim, fontSize = 11.sp, fontWeight = FontWeight.Medium,
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
            if (sec.key != LaunchpadLogic.FLAT_KEY && !sec.pinned && sec.key.startsWith("app:")) {
                AppIcon(sec.items.first().packageName, sec.title, 20, Modifier.padding(end = 8.dp))
            }
            Text(
                sec.title, color = HyleColors.InkBright, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
            )
            if (sec.subtitle.isNotEmpty()) {
                Text(
                    "  ${sec.subtitle}", color = HyleColors.InkDim, fontSize = 12.sp, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
                )
            }
            Spacer(Modifier.weight(1f))
            Text("${sec.items.size}", color = HyleColors.InkDim, fontSize = 12.sp, modifier = Modifier.padding(end = 8.dp))
            Chevron(HyleColors.InkDim, down = !collapsed, size = 14)
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
    onSwipeDismiss: () -> Unit,
    onSwipeClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var dx by remember { mutableFloatStateOf(0f) }
    var widthPx by remember { mutableFloatStateOf(1f) }
    val weight = if (unread) FontWeight.Bold else FontWeight.Normal
    Column(modifier) {
        Row(
            Modifier
                .fillMaxWidth()
                .offset { IntOffset(dx.roundToInt(), 0) }
                .alpha((1f - (kotlin.math.abs(dx) / widthPx)).coerceIn(0.2f, 1f))
                .pointerInput(n.key) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            val d = dx
                            dx = 0f
                            if (d > widthPx * 0.35f && !n.isOngoing) onSwipeDismiss()
                            else if (d < -widthPx * 0.25f) onSwipeClose()
                        },
                        onDragCancel = { dx = 0f },
                    ) { _, delta -> dx += delta }
                }
                .combinedClickable(onClick = onClick, onLongClick = onLongClick)
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
                    n.title, color = HyleColors.InkBright, fontSize = 16.sp, fontWeight = weight,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                if (n.summary.isNotEmpty()) {
                    Text(
                        n.summary, color = if (unread) HyleColors.Ink else HyleColors.InkDim, fontSize = 13.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            Text(
                formatStamp(n.timestamp, now, is24, zone), color = HyleColors.InkDim, fontSize = 12.sp,
                fontWeight = if (unread) FontWeight.Medium else FontWeight.Normal,
            )
        }
        Box(Modifier.fillMaxWidth().height(0.5.dp).background(HyleColors.Hairline))
    }
}

@Composable
private fun AccessCard(onRequestAccess: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(32.dp), Alignment.Center) {
        Column(
            Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(HyleColors.SurfaceTranslucent)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                L10n.tr("Allow notification access to see everything here"),
                color = HyleColors.InkBright, fontSize = 16.sp, textAlign = TextAlign.Center,
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
    ModalBottomSheet(onDismissRequest = onDismissSheet, containerColor = HyleColors.Surface) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 12.dp)) {
            Row(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                AppIcon(pkg, app, 32)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(n.title, color = HyleColors.InkBright, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(app, color = HyleColors.InkDim, fontSize = 12.sp)
                }
            }
            entries.forEach { (label, act) ->
                Text(
                    label, color = HyleColors.Ink, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
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

    Column(
        modifier
            .width(44.dp)
            .clip(RoundedCornerShape(topEnd = 22.dp, bottomEnd = 22.dp))
            .background(Color(0xB30B0D14))
            .clickable(onClick = onOpen)
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
