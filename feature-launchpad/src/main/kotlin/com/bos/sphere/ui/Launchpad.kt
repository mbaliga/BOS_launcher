package com.bos.sphere.ui

import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.graphics.graphicsLayer
import android.content.Context
import android.content.Intent
import android.text.format.DateFormat
import com.bos.sphere.core.design.predictiveBackLayer
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.collectAsState
import kotlinx.coroutines.flow.MutableStateFlow
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.toArgb
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
import androidx.compose.foundation.focusable
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import com.bos.sphere.core.data.AppPolicy
import com.bos.sphere.core.data.DeliveryLogic
import com.bos.sphere.core.data.FocusTarget
import com.bos.sphere.core.data.HubAction
import com.bos.sphere.core.data.LaunchpadFocus
import com.bos.sphere.core.data.LaunchpadView
import com.bos.sphere.core.data.LaunchpadViewBuilder
import com.bos.sphere.core.data.ReplyLogic
import com.bos.sphere.core.data.RowCommand
import com.bos.sphere.core.data.RowKeys
import com.bos.sphere.core.data.SnoozeLogic
import com.bos.sphere.core.data.SystemSnapshot
import com.bos.sphere.core.data.SystemTileKind
import com.bos.sphere.core.design.GrantColors
import com.bos.sphere.core.design.GrantGate
import com.bos.sphere.core.design.GrantKind
import androidx.compose.ui.input.InputMode as ComposeInputMode
import com.bos.sphere.core.input.InputMode
import com.bos.sphere.core.input.InputModeTracker
import com.bos.sphere.core.data.CommsScale
import com.bos.sphere.core.data.CommsScaleSettings
import com.bos.sphere.core.data.GroupMode
import com.bos.sphere.core.data.ContactDirectory
import com.bos.sphere.core.data.GroupPriority
import com.bos.sphere.core.data.GroupResolver
import com.bos.sphere.core.data.GroupsConfig
import com.bos.sphere.core.data.GroupsSettings
import com.bos.sphere.core.data.NoContacts
import com.bos.sphere.core.data.RuleDrafts
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
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.paneTitle
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
internal class LpColors(
    val bg: Color, val surface: Color, val ink: Color, val ink2: Color, val dim: Color, val line: Color,
    /** Wave 28: unselected tabs, the menu grid's unselected glyphs, the menu counts and the grid's hairlines; the light look uses [dim] and [line]. */
    val tabDim: Color = dim, val glyphDim: Color = dim, val count: Color = dim, val gridLine: Color = line,
)
/** CFG-10: Launchpad's two looks come from the shared design tokens (dark and light variants), not a private palette. */
internal fun lpColorsFor(variant: com.bos.sphere.core.design.ThemeVariant): LpColors =
    if (variant == com.bos.sphere.core.design.ThemeVariant.Dark)
        // Wave 27: the dark Comms look is the owner's mock-up palette (near-black navy page, #1A1A1A panel); the light look stays on the shared light tokens.
        LpColors(CommsStyle.DarkBg, CommsStyle.DarkPanel, CommsStyle.DarkInk, CommsStyle.DarkInk2, CommsStyle.DarkDim, CommsStyle.DarkLine, CommsStyle.DarkTabDim, CommsStyle.DarkGlyphDim, CommsStyle.DarkCount, CommsStyle.DarkGridLine)
    else com.bos.sphere.core.design.HyleColors.tokens(variant).let { LpColors(it.background, it.surface, it.inkBright, it.ink, it.inkDim, it.hairline) }
internal val LpDark = lpColorsFor(com.bos.sphere.core.design.ThemeVariant.Dark)
internal val LpLight = lpColorsFor(com.bos.sphere.core.design.ThemeVariant.Light)
internal val LocalLp = androidx.compose.runtime.compositionLocalOf { LpDark }

/** The shared accent, and the same accent darkened until white text on it reads (contrast 4.5 or more). */
internal val BlueLight: Color get() = com.bos.sphere.core.design.HyleColors.Violet
internal val BlueDark: Color get() = lpAccentFill(com.bos.sphere.core.design.HyleColors.Violet)

internal fun lpAccentFill(accent: Color): Color {
    var c = accent
    var n = 0
    while (n < 20 && com.bos.sphere.core.design.WallpaperPalette.contrast(c.toArgb(), 0xFFFFFFFF.toInt()) < 4.5) {
        c = androidx.compose.ui.graphics.lerp(c, Color.Black, 0.12f); n++
    }
    return c
}

// ---------------------------------------------------------------------------------------------
// Shared state helpers
// ---------------------------------------------------------------------------------------------

@Composable
private fun rememberLaunchpadInit() {
    val ctx = LocalContext.current
    remember(ctx) { LaunchpadSettings.init(ctx); GroupsSettings.init(ctx); DigestWidget.start(ctx); true }
}

/** True while the user has granted notification access; re-checked every time the app resumes. */
@Composable
internal fun rememberNotificationAccess(): Boolean {
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

internal val iconCache = ConcurrentHashMap<String, ImageBitmap>()

@Composable
private fun rememberAppIcon(pkg: String): ImageBitmap? {
    val ctx = LocalContext.current
    val icon by produceState(iconCache[pkg], pkg) {
        if (pkg.isBlank()) return@produceState
        value = iconCache[pkg] ?: withContext(Dispatchers.IO) {
            runCatching { ctx.packageManager.getApplicationIcon(pkg).toBitmap(168, 168).asImageBitmap() }.getOrNull()
        }?.also { iconCache[pkg] = it }
    }
    return icon
}

@Composable
internal fun AppIcon(pkg: String, label: String, size: Int, modifier: Modifier = Modifier) = AppIcon(pkg, label, size.toFloat(), modifier)

@Composable
internal fun AppIcon(pkg: String, label: String, size: Float, modifier: Modifier = Modifier, alpha: Float = 1f) {
    val icon = rememberAppIcon(pkg)
    // Wave 30: a read row's icon is dimmer and a little greyer (alpha < 1): the whole icon, its outline and the letter badge.
    val modifier = if (alpha < 1f) modifier.alpha(alpha) else modifier
    val shape = RoundedCornerShape((size * CommsStyle.ICON_RADIUS_FRACTION).dp)
    // A hairline outline keeps a black icon visible on the dark page and a white one on the light page (the mock-up's music icon has one).
    val outline = Modifier.border(0.75.dp, LocalLp.current.dim.copy(alpha = 0.45f), shape)
    if (icon != null) {
        Image(icon, null, modifier.size(size.dp).clip(shape).then(outline), colorFilter = if (alpha < 1f) ReadIconFilter else null)
    } else {
        // No icon to load: a coloured letter badge (the colour is the app's stable hue), never a grey tile.
        Box(modifier.size(size.dp).clip(shape).background(HubStyle.stripeFor(pkg)), Alignment.Center) {
            Text(
                label.take(1).uppercase(), color = Color.White,
                fontSize = (size * 0.42f).sp, fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

internal fun formatStamp(ts: Long, now: Long, is24: Boolean, zone: ZoneId): String {
    val d = Instant.ofEpochMilli(ts).atZone(zone)
    val same = d.toLocalDate() == Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    val pattern = if (is24) "HH:mm" else "h:mm a"
    val time = DateTimeFormatter.ofPattern(pattern, Locale.getDefault()).format(d)
    return if (same) time else DateTimeFormatter.ofPattern("d MMM", Locale.getDefault()).format(d)
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
    /**
     * STR-01: the live reveal fraction (0..1) of the home card that sits in front of this panel. Read only while drawing, so a drag
     * never recomposes the panel. Used with [underlay] for the content fade-in and the reduced-motion cross-fade.
     */
    cardFraction: () -> Float = { 1f },
    /**
     * STR-02: starts an app's launcher activity and says whether it started. Used when a notification's own PendingIntent cannot be
     * sent; unlike [onOpenApp] it must not close anything, so a failure leaves Launchpad open with a message. Null: [onOpenApp] is trusted.
     */
    launchApp: ((packageName: String) -> Boolean)? = null,
    /**
     * Wave 25 (STR-01): the state of the menu side panel. The host passes its own so the home card can slide out of view with it
     * (read [LaunchpadMenuState.progress] and [LaunchpadMenuMotion]); a panel used alone keeps one itself.
     */
    menu: LaunchpadMenuState = rememberLaunchpadMenu(),
    /** Wave 27: a tap on the header clock; the host opens the Sound and Vibration popup. Nothing else is wired to it here. */
    onOpenSound: () -> Unit = {},
    /** Wave 25 (STR-02): a slot in the list's top bar for the history, files and timeline view switcher. */
    viewSwitcher: @Composable () -> Unit = {},
    /** Wave 25 (hubData): renders the Files and Timeline views of the hub; null leaves the hub with the history views only. */
    panelBody: (@Composable (HubView) -> Unit)? = null,
    /** Wave 29 (commsRead): the launcher's strength-aware haptics (the host's rememberLauncherHaptics()); the read-filter pinch ticks through it. Null: no haptic. */
    haptics: androidx.compose.ui.hapticfeedback.HapticFeedback? = null,
) {
    rememberLaunchpadInit()
    val dark = when (theme) {
        com.bos.sphere.core.data.LaunchpadTheme.Dark -> true
        com.bos.sphere.core.data.LaunchpadTheme.Light -> false
        com.bos.sphere.core.data.LaunchpadTheme.System -> androidx.compose.foundation.isSystemInDarkTheme()
    }
    // NFR-14: with animations off the slide becomes a short cross-fade (no travel).
    val motion = com.bos.sphere.core.design.rememberMotionPrefs()
    // NFR-19: the handler lives outside the animated content, which stays composed during the exit; it is off from the
    // first frame of the exit and follows the back gesture while open.
    val backProgress = com.bos.sphere.core.design.rememberPredictiveBackProgress(visible, onDismiss)
    // Back closes the menu panel first (registered after the predictive handler, so it takes the press while open); a second Back leaves Launchpad.
    androidx.activity.compose.BackHandler(enabled = visible && menu.open) { menu.open = false }
    val rtl = androidx.compose.ui.platform.LocalLayoutDirection.current == androidx.compose.ui.unit.LayoutDirection.Rtl
    androidx.compose.runtime.CompositionLocalProvider(LocalLp provides if (dark) LpDark else LpLight) {
    androidx.compose.foundation.layout.BoxWithConstraints(modifier.predictiveBackLayer(backProgress)) {
    // The home card leaves only a thin sliver at the far edge (STR-01); the panel is full width and its content is inset from the sliver.
    val geo = LaunchpadCardGeometry(maxWidth.value, maxHeight.value, CardEdge.forLaunchpad(rtl), 1f, motion.reduced)
    // The panel is full width and static; only its content is inset from the sliver side (at least a 48 dp return zone).
    // Wave 26: the list ends exactly at the card's near edge (the visible sliver), so rows, bands and dividers run edge to edge; text keeps its own padding.
    val cardInset = if (underlay) geo.sliverPx.dp else 0.dp
    AnimatedVisibility(
        visible = visible,
        modifier = Modifier.fillMaxSize()
            .then(if (underlay) Modifier.graphicsLayer { alpha = geo.pose(cardFraction()).backdropAlpha } else Modifier)
            .then(if (underlay) Modifier.launchpadBackdrop(LocalLp.current.bg, LocalLp.current.surface) else Modifier)
            // The content fades in over the last part of the card's motion; the backdrop is already there.
            .then(if (underlay) Modifier.graphicsLayer { alpha = geo.pose(cardFraction()).contentAlpha } else Modifier),
        enter = if (underlay) androidx.compose.animation.EnterTransition.None
        else if (motion.reduced) fadeIn(androidx.compose.animation.core.tween(motion.crossFadeMs()))
        else slideInHorizontally { -it } + fadeIn(),
        exit = if (underlay) androidx.compose.animation.ExitTransition.None
        else if (motion.reduced) fadeOut(androidx.compose.animation.core.tween(motion.crossFadeMs()))
        else slideOutHorizontally { -it } + fadeOut(),
    ) {
        RestoreFocusOnExit()
        val ctx = LocalContext.current
        val items by NotificationProvider.notifications.collectAsStateWithLifecycle(remember { NotificationProvider.snapshot })
        val rules by LaunchpadSettings.rules.collectAsStateWithLifecycle()
        val prefs by LaunchpadSettings.prefs.collectAsStateWithLifecycle()
        val read by LaunchpadSettings.readKeys.collectAsStateWithLifecycle()
        val policies by LaunchpadSettings.policies.collectAsStateWithLifecycle()
        val snoozed by LaunchpadSettings.snoozed.collectAsStateWithLifecycle()
        val access = rememberNotificationAccess()
        val asked = rememberGrantAsked(GrantKind.Notifications)
        val now = rememberNow()
        val gestures by LaunchpadSettings.gestures.collectAsStateWithLifecycle()
        val groups by GroupsSettings.config.collectAsStateWithLifecycle()
        val contacts = rememberContactDirectory()
        val sys = rememberSystemSnapshot()
        val dnd = rememberDndOn() // wave 24: Priority groups break through Do Not Disturb in Liftoff's own UI
        LaunchedEffect(items) {
            // An empty feed can be the listener reconnecting; neither read marks nor snoozes are forgotten because of it.
            LaunchpadSettings.pruneToFeed(items.map { it.key }.toSet(), System.currentTimeMillis())
        }

        // The hub is on when the host supplies the Files and Timeline views (the app does); a bare panel keeps the live grouped list.
        val makeHub = { HubState(HubPrefs.of(ctx), com.bos.sphere.core.data.NotificationHistory.init(ctx), true, liveAll = true) }
        val hub = if (panelBody == null) null else androidx.compose.runtime.saveable.rememberSaveable(saver = hubStateSaver(makeHub)) { makeHub() } // wave 33: the tab and filter survive process death
        if (hub != null) LaunchedEffect(hub) { LaunchpadHubNav.requests.collect { v -> if (v != null) { hub.select(v); LaunchpadHubNav.take() } } }
        LaunchpadContent(
            hub = hub, hubPanelBody = panelBody ?: {}, haptics = haptics, viewSwitcher = { if (hub != null) HubSwitcher(hub); viewSwitcher() },
            items = items, rules = rules, prefs = prefs, readKeys = read, hasAccess = access, now = now,
            gestures = gestures,
            endInset = cardInset,
            policies = policies, snoozed = snoozed, askedBefore = asked,
            groups = groups, contacts = contacts,
            dndOn = dnd,
            systemState = sys,
            onOpenSystemPanel = { openSystemPanel(ctx, it) },
            onSnooze = { n, until -> LaunchpadSettings.snooze(n.key, until) },
            onRunAction = { a -> ReplyLogic.run(a) },
            onSendReply = { a, text -> ReplyLogic.send(ctx, a, text) },
            runAction = { a -> ReplyLogic.run(a) },
            sendReply = { a, text -> ReplyLogic.send(ctx, a, text) },
            onClose = onDismiss,
            menu = menu, onOpenSound = onOpenSound,
            onOpenItem = { n -> openThrough(ctx, n, launchApp, onOpenApp) },
            openItem = { n -> openThrough(ctx, n, launchApp, onOpenApp).ok },
            openApp = { pkg ->
                launchApp?.invoke(pkg) ?: run { onOpenApp(pkg); true } // W31: Comms stays open under the app, so Back returns to it
            },
            onOpenApp = { pkg -> onOpenApp(pkg) }, // W31: stays open
            onDismissItem = { NotificationProvider.dismiss(it.key) },
            onClearAll = { NotificationProvider.clearAll() },
            onAddRule = { LaunchpadSettings.addRule(it) },
            onSetDefaultMode = { LaunchpadSettings.setDefaultMode(it) },
            onRequestAccess = { GrantSupport.request(ctx, GrantKind.Notifications) },
        )
    }
    }
    }
}

/** STR-02: the shared hand-off for a tapped notification: mark read, send its intent (or start the app), cancel if asked, then close. */
private fun openThrough(
    ctx: Context, n: HubNotification, launchApp: ((String) -> Boolean)?, onOpenApp: (String) -> Unit,
) = LaunchpadOpen.open(
    n, ctx, launchApp ?: { pkg -> onOpenApp(pkg); true },
    markRead = { LaunchpadSettings.markRead(it) }, cancel = { NotificationProvider.dismiss(it) }, close = {}, // W31: opening a notification no longer closes Comms: Back from the app returns to it
)

private sealed interface Entry {
    val key: String
    data class Head(val sec: Section, val collapsed: Boolean, val first: Boolean = false) : Entry { override val key get() = "h:${sec.key}" }
    data class Item(val n: HubNotification, val sec: Section, val pinned: Boolean = sec.pinned) : Entry { override val key get() = "n:${n.key}" }
    /** STR-13: the by-app / by-conversation sub-heading inside a group section. */
    data class Sub(val title: String, val id: String) : Entry { override val key get() = "s:$id" }
    /** STR-02: "+N more" (or "Show fewer") under a conversation's latest message; expanding is the user's choice. */
    data class More(val id: String, val hidden: Int, val expanded: Boolean) : Entry { override val key get() = "m:$id" }
    data class Held(val count: Int, val at: Long?) : Entry { override val key get() = "held" }
}

/**
 * Stateless Launchpad body (header + system row + grouped list / empty / grant states). All data comes in
 * as plain parameters, so the whole thing can be driven from tests or previews.
 */
@OptIn(ExperimentalFoundationApi::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
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
    policies: List<AppPolicy> = emptyList(),
    snoozed: Map<String, Long> = emptyMap(),
    askedBefore: Boolean = false,
    /** STR-13: the user's groups; with none, the column is exactly as before. */
    groups: GroupsConfig = GroupsConfig(),
    contacts: ContactDirectory = NoContacts,
    systemState: SystemSnapshot? = null,
    onOpenSystemPanel: (SystemTileKind) -> Unit = {},
    onSnooze: (HubNotification, Long) -> Unit = { _, _ -> },
    onRunAction: (HubAction) -> Unit = {},
    onSendReply: (HubAction, String) -> Unit = { _, _ -> },
    /** STR-02: a hand-off that reports success; when set it replaces [onOpenItem], and a false result keeps the column and says so. */
    openItem: ((HubNotification) -> Boolean)? = null,
    /** STR-02: like [onRunAction] and [onSendReply] but reporting whether the intent was sent; a failure is shown, never swallowed. */
    runAction: ((HubAction) -> Boolean)? = null,
    sendReply: ((HubAction, String) -> Boolean)? = null,
    openApp: ((String) -> Boolean)? = null,
    zone: ZoneId = remember { ZoneId.systemDefault() },
    /** Wave 24: system Do Not Disturb is on (read only); with a Priority group the column says Priority still breaks through. */
    dndOn: Boolean = false,
    /** Wave 25 (STR-01): the menu side panel's state; see [LaunchpadPanel]. */
    menu: LaunchpadMenuState = rememberLaunchpadMenu(),
    /** Wave 27: a tap on the header clock (the Sound and Vibration popup); see [LaunchpadPanel]. */
    onOpenSound: () -> Unit = {},
    /** Wave 25 (STR-02): the slot for the view switcher (history, files, timeline), in the top bar next to the title. */
    viewSwitcher: @Composable () -> Unit = {},
    hub: HubState? = null,
    hubPanelBody: @Composable (HubView) -> Unit = {},
    /** Wave 27: the Sound and Vibration sheet; the header's sound hook calls [SoundSheetState.show], the system row's sound button does too. */
    soundSheet: SoundSheetState = rememberSoundSheetState(),
    /** Wave 34: the Connectivity sheet; the signal and Wi-Fi glyphs of the header open it (it replaces the system Internet panel). */
    connectivitySheet: ConnectivitySheetState = rememberConnectivitySheetState(),
    /** Wave 28: the Comms size in percent (75 to 125); by default the persisted setting, which the Comms size stepper and the pinch change. */
    commsScalePercent: Int = rememberCommsScalePercent(),
    /** Wave 29 (commsRead): the tick performed on each read-filter step (null: none). */
    haptics: androidx.compose.ui.hapticfeedback.HapticFeedback? = null,
) {
    val ctx = LocalContext.current
    val rtlLayout = androidx.compose.ui.platform.LocalLayoutDirection.current == androidx.compose.ui.unit.LayoutDirection.Rtl
    val is24 = remember(ctx) { DateFormat.is24HourFormat(ctx) }
    var filter by remember { mutableStateOf<String?>(null) }
    // STR-02: nothing is folded by default. A section folds only when the user asks (arrow left, pinch in); a conversation shows
    // its latest message and "+N more", which the user expands in place.
    var collapsed by remember { mutableStateOf(setOf<String>()) }
    var expandedConvs by remember { mutableStateOf(setOf<String>()) }
    var actionsOpenFor by remember { mutableStateOf(setOf<String>()) } // wave 25: rows whose action chips are open (long press)
    var menuFor by remember { mutableStateOf<HubNotification?>(null) }
    var menuExpand by remember { mutableStateOf<RowCommand?>(null) }
    var replyFor by remember { mutableStateOf<String?>(null) }
    // Wave 29: the read filter (Unread - All - Read) of the All view: the hub's when there is one, else the panel's own.
    var localReadFilter by remember { mutableStateOf(ReadFilter.All) }
    val readFilter = hub?.readFilter ?: localReadFilter
    fun setReadFilter(f: ReadFilter) { if (hub != null) hub.chooseReadFilter(f) else localReadFilter = f }
    // Wave 29: ALL is the live list plus the persistent history (one row per key, newest first), so a read notification stays.
    val historyFlow = remember(hub?.store) { hub?.store?.entries ?: MutableStateFlow(emptyList<com.bos.sphere.core.data.HistoryEntry>()) }
    val history by historyFlow.collectAsState()
    // Wave 33: a large merge runs off the main thread (see [rememberCommsMerged]); the previous result stays on screen meanwhile.
    val merged = rememberCommsMerged(items, history, readKeys, enabled = hub != null && hub.liveAll)
    val effRead = merged.readKeys
    val historyOnly = merged.historyOnly
    var groupFor by remember { mutableStateOf<HubNotification?>(null) }
    var ruleFrom by remember { mutableStateOf<HubNotification?>(null) }
    var groupsOpen by remember { mutableStateOf(false) } // wave 24: the Groups manager inside Launchpad
    val shown = remember(merged, readFilter) { CommsRead.filter(merged.items, effRead, readFilter) }
    val toast = remember { mutableStateOf<String?>(null) }
    LaunchedEffect(toast.value) { if (toast.value != null) { delay(1400); toast.value = null } }
    val tracker = remember { InputModeTracker() }
    val inputManager = androidx.compose.ui.platform.LocalInputModeManager.current
    val inputMode by tracker.mode.collectAsStateWithLifecycle()

    fun perform(action: com.bos.sphere.core.data.LaunchpadAction, n: HubNotification?, sections: List<Section>) {
        val pkg = n?.packageName.orEmpty()
        when (action) {
            com.bos.sphere.core.data.LaunchpadAction.None -> Unit
            com.bos.sphere.core.data.LaunchpadAction.ShowUnread -> { setReadFilter(ReadFilter.Unread); toast.value = L10n.tr("Unread only") }
            com.bos.sphere.core.data.LaunchpadAction.ShowAll -> { setReadFilter(ReadFilter.All); toast.value = L10n.tr("Showing everything") }
            com.bos.sphere.core.data.LaunchpadAction.ToggleUnread -> { val on = readFilter != ReadFilter.Unread; setReadFilter(if (on) ReadFilter.Unread else ReadFilter.All); toast.value = L10n.tr(if (on) "Unread only" else "Showing everything") }
            com.bos.sphere.core.data.LaunchpadAction.NextGrouping -> {
                val next = GroupMode.entries[(prefs.defaultMode.ordinal + 1) % GroupMode.entries.size]
                onSetDefaultMode(next); toast.value = L10n.tr("Grouped: %s", groupModeText(next))
            }
            com.bos.sphere.core.data.LaunchpadAction.CollapseAll ->
                collapsed = if (collapsed.size >= sections.size) emptySet() else sections.map { it.key }.toSet()
            com.bos.sphere.core.data.LaunchpadAction.ClearAll -> onClearAll()
            com.bos.sphere.core.data.LaunchpadAction.Dismiss -> n?.takeIf { !it.isOngoing }?.let { // W32: in a persistent store, dismiss = mark as read (it leaves the places of importance: the Unread view and the unread styling), and it clears the shade; the row stays as history
                LaunchpadSettings.markRead(it.key); onDismissItem(it); toast.value = L10n.tr("Marked as read")
            }
            com.bos.sphere.core.data.LaunchpadAction.MarkRead -> n?.let { LaunchpadSettings.markRead(it.key); toast.value = L10n.tr("Marked as read") }
            com.bos.sphere.core.data.LaunchpadAction.OpenApp -> if (pkg.isNotBlank()) {
                if (openApp == null) onOpenApp(pkg) else if (!openApp(pkg)) toast.value = L10n.tr("Couldn't open %s", n?.appLabel?.ifBlank { pkg } ?: pkg)
            }
            com.bos.sphere.core.data.LaunchpadAction.MoreOptions -> { menuFor = n; menuExpand = null }
            com.bos.sphere.core.data.LaunchpadAction.MuteApp -> if (pkg.isNotBlank()) { onAddRule(Rule.create(RuleAction.Silent, packageName = pkg)); toast.value = L10n.tr("Muted") }
            com.bos.sphere.core.data.LaunchpadAction.HideApp -> if (pkg.isNotBlank()) { onAddRule(Rule.create(RuleAction.Hide, packageName = pkg)); toast.value = L10n.tr("Hidden") }
            com.bos.sphere.core.data.LaunchpadAction.PinSender -> n?.let { LaunchpadLogic.senderOf(it).takeIf { s -> s.isNotBlank() }?.let { s -> onAddRule(Rule.create(RuleAction.Pin, pkg.ifBlank { null }, s)); toast.value = L10n.tr("Pinned") } }
        }
    }

    // Counts for the filter menu reflect what the user can actually see.
    val visible = remember(merged, rules, now) { LaunchpadLogic.visible(merged.items, rules, now) }
    val counts = remember(visible, effRead, readFilter) { CommsRead.counts(visible, effRead, readFilter) }
    val activeFilter = filter?.takeIf { f -> counts.any { it.packageName == f } }
    val digestLabel = L10n.tr("Digest")
    val todayLabel = L10n.tr("Today"); val yesterdayLabel = L10n.tr("Yesterday"); val pinnedLabel = L10n.tr("Pinned"); val flatLabel = L10n.tr("All")
    val fallbackTitle = L10n.tr("Everything else")
    val resolver = remember(groups, contacts, zone) { if (groups.groups.isEmpty()) null else GroupResolver(groups, contacts, zone) }
    fun buildView(r: GroupResolver?) = LaunchpadViewBuilder.build(
        LaunchpadLogic.filter(shown, activeFilter), rules, prefs, policies, snoozed, now, zone,
        digestLabel = digestLabel, todayLabel = todayLabel, yesterdayLabel = yesterdayLabel, pinnedLabel = pinnedLabel, flatLabel = flatLabel,
        grouping = r,
    )
    val plainView = remember(shown, rules, prefs, policies, snoozed, now, activeFilter, zone, resolver) { if (resolver == null) buildView(null) else null }
    // STR-13: with groups the matching runs off the main thread; until the first result the column shows nothing rather than the wrong sections.
    val groupedView by produceState<LaunchpadView?>(null, shown, rules, prefs, policies, snoozed, now, activeFilter, zone, resolver) {
        value = if (resolver == null) null else withContext(Dispatchers.Default) { buildView(resolver) }
    }
    val loadingGroups = resolver != null && groupedView == null
    val view = plainView ?: groupedView ?: LaunchpadView(emptyList(), emptyList(), null, emptySet())
    val sections = remember(view, fallbackTitle) { if (view.grouped.isEmpty()) view.sections else GroupColumn.sections(view.grouped, fallbackTitle) }
    val entries = remember(view, sections, collapsed, expandedConvs) {
        buildList<Entry> {
            if (view.held.isNotEmpty()) add(Entry.Held(view.held.size, view.nextRelease))
            // One list, every section and every notification in it (STR-02); a conversation folds only behind its own "+N more".
            fun addRows(sec: Section, id: String, items: List<HubNotification>, pinned: Boolean) {
                val open = id in expandedConvs
                val shownRows = RoomyList.rows(id, items, open, pinned)
                shownRows.forEach { add(Entry.Item(it, sec, pinned)) }
                val hidden = RoomyList.hidden(id, items, pinned)
                if (hidden > 0) add(Entry.More(id, hidden, open))
            }
            sections.forEachIndexed { i, sec ->
                // Wave 26: a one-item group has no header (it would repeat the row below); with no header it cannot be collapsed either.
                val headed = GroupColumn.showsHeader(sec.key, sec.items.size)
                val isCollapsed = headed && sec.key in collapsed
                if (headed) add(Entry.Head(sec, isCollapsed, first = i == 0 && view.held.isEmpty()))
                if (!isCollapsed) {
                    val gs = view.grouped.firstOrNull { it.key == sec.key }
                    if (gs == null) addRows(sec, sec.key, sec.items, sec.pinned)
                    else gs.sections.forEach { inner ->
                        if (GroupColumn.showSubHeads(gs) && GroupColumn.showsHeader(inner.key, inner.items.size)) add(Entry.Sub(inner.title, inner.key))
                        addRows(sec, inner.key, inner.items, inner.pinned)
                    }
                }
            }
        }
    }

    // ---- keyboard focus over groups and rows (STR-08) ----
    val order = remember(entries) {
        entries.mapNotNull { e ->
            when (e) {
                is Entry.Head -> FocusTarget.Group(e.sec.key)
                is Entry.Item -> FocusTarget.Row(e.n.key, e.sec.key)
                else -> null
            }
        }
    }
    var focus by remember { mutableStateOf<FocusTarget?>(null) }
    var pending by remember { mutableStateOf<FocusTarget?>(null) }
    val requesters = remember { HashMap<String, FocusRequester>() }
    fun req(t: FocusTarget) = requesters.getOrPut((if (t is FocusTarget.Group) "g:" else "r:") + t.key) { FocusRequester() }
    val rootReq = remember { FocusRequester() }
    val listState = rememberLazyListState()
    var lastOrder by remember { mutableStateOf(order) }
    LaunchedEffect(order) {
        val f = focus
        if (f != null && order.none { it.key == f.key && it::class == f::class }) {
            val next = LaunchpadFocus.afterRemoval(lastOrder, f, order)
            focus = next; pending = next
            if (next == null) runCatching { rootReq.requestFocus() }
        }
        lastOrder = order
    }
    LaunchedEffect(pending) {
        val t = pending ?: return@LaunchedEffect
        val idx = entries.indexOfFirst { e -> (e is Entry.Head && t is FocusTarget.Group && e.sec.key == t.key) || (e is Entry.Item && t is FocusTarget.Row && e.n.key == t.key) }
        if (idx >= 0) {
            val visibleNow = listState.layoutInfo.visibleItemsInfo.any { it.index == idx }
            if (!visibleNow) listState.scrollToItem(idx)
            withFrameNanos { }
            runCatching { req(t).requestFocus() }
        }
        pending = null
    }
    fun move(delta: Int) { LaunchpadFocus.move(order, focus, delta)?.let { focus = it; pending = it } }
    /** Folds or unfolds a section: only ever the user's choice (arrow keys, pinch in, tapping a folded title). */
    fun toggleGroup(key: String, open: Boolean?) {
        val isCollapsed = key in collapsed
        collapsed = if (open == null) (if (isCollapsed) collapsed - key else collapsed + key) else if (open) collapsed - key else collapsed + key
    }
    /** STR-02: the shared way to open a notification; a failed hand-off keeps Launchpad open and says so. */
    fun open(n: HubNotification) {
        val h = historyOnly[n.key]
        if (h != null) { // wave 29: a row whose notification left the shade has no intent: tapping reads it and opens its app
            hub?.store?.setRead(listOf(h.id), true)
            if (n.packageName.isNotBlank()) { if (openApp == null) onOpenApp(n.packageName) else if (!openApp(n.packageName)) toast.value = L10n.tr("Couldn't open %s", n.appLabel.ifBlank { n.packageName }) }
            return
        }
        if (openItem == null) onOpenItem(n)
        else if (!openItem(n)) toast.value = L10n.tr("Couldn't open %s", n.appLabel.ifBlank { n.title })
    }
    fun listKey(e: androidx.compose.ui.input.key.KeyEvent): Boolean {
        if (e.type != KeyEventType.KeyDown) return false
        return when (val r = RowKeys.map(e.key.nativeKeyCode, e.isShiftPressed)) {
            RowKeys.Result.Close -> { if (menu.open) menu.open = false else onClose(); true }
            is RowKeys.Result.Move -> { move(r.delta); true }
            RowKeys.Result.First -> { order.firstOrNull()?.let { focus = it; pending = it }; true }
            RowKeys.Result.Last -> { order.lastOrNull()?.let { focus = it; pending = it }; true }
            RowKeys.Result.Collapse -> when (val f = focus) {
                is FocusTarget.Row -> { LaunchpadFocus.owningGroup(order, f)?.let { focus = it; pending = it }; true }
                is FocusTarget.Group -> { toggleGroup(f.key, false); true }
                null -> false
            }
            RowKeys.Result.Expand -> (focus as? FocusTarget.Group)?.let { toggleGroup(it.key, true); true } ?: false
            else -> false
        }
    }
    LaunchedEffect(Unit) { withFrameNanos { }; runCatching { rootReq.requestFocus() } }
    // The handler is read through updated state: with groups the first composition has no sections yet (they are matched off the
    // main thread), and a handler captured then would see an empty focus order for good.
    // (A holder, not state: two references to a local function compare equal whatever they captured, so state would never update.)
    val listKeyNow = remember { arrayOf<(androidx.compose.ui.input.key.KeyEvent) -> Boolean>({ false }) }
    androidx.compose.runtime.SideEffect { listKeyNow[0] = ::listKey }
    val keyHandler = remember { { ev: androidx.compose.ui.input.key.KeyEvent -> listKeyNow[0](ev) } }

    fun command(cmd: RowCommand, n: HubNotification) {
        val h = historyOnly[n.key]
        if (h != null && (cmd == RowCommand.MarkRead || cmd == RowCommand.Dismiss)) { // wave 29: history-only rows mark in, and leave, the history
            hub?.store?.setRead(listOf(h.id), true); toast.value = L10n.tr("Marked as read") // W32: dismiss and mark-read are the same on a history row
            return
        }
        when (cmd) {
            RowCommand.Open -> open(n)
            RowCommand.Dismiss -> perform(com.bos.sphere.core.data.LaunchpadAction.Dismiss, n, sections)
            RowCommand.MarkRead -> perform(com.bos.sphere.core.data.LaunchpadAction.MarkRead, n, sections)
            RowCommand.Snooze -> { menuFor = n; menuExpand = RowCommand.Snooze }
            RowCommand.MuteApp -> perform(com.bos.sphere.core.data.LaunchpadAction.MuteApp, n, sections)
            RowCommand.HideApp -> perform(com.bos.sphere.core.data.LaunchpadAction.HideApp, n, sections)
            RowCommand.PinSender -> perform(com.bos.sphere.core.data.LaunchpadAction.PinSender, n, sections)
            RowCommand.MakeRule -> { menuFor = n; menuExpand = RowCommand.MakeRule }
            RowCommand.MoveToGroup -> groupFor = n
            RowCommand.GroupRule -> ruleFrom = n
            RowCommand.Reply -> replyFor = if (replyFor == n.key) null else n.key
        }
    }

    // Wave 25: "mark what is visible as read" for the hub: the live list marks its unread rows, a history view uses the store (with undo).
    // Only what is on screen: not what a filter, the app filter or a rule hides (marking everything under "Read only" would read what the user never saw).
    val visibleNow = androidx.compose.runtime.rememberUpdatedState(CommsRead.filter(LaunchpadLogic.filter(visible, activeFilter), effRead, readFilter) to effRead)
    val liveNow = visibleNow
    val hubMarkRead: () -> Unit = {
        if (hub != null) {
            if (hub.listIsLive) {
                val unread = liveNow.value.first.filter { it.key !in liveNow.value.second }
                unread.forEach { LaunchpadSettings.markRead(it.key) }
                if (unread.isNotEmpty()) toast.value = L10n.tr("Marked %d as read", unread.size)
            } else hub.markVisibleRead(System.currentTimeMillis(), zone)
        }
    }

    // Wave 29: the read filter's steps: the pinch and Ctrl+U move one stop, the TalkBack actions and the label's tap choose one.
    fun tick() { haptics?.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove) }
    fun stepFilter(d: PinchDir) {
        val changed = if (hub != null) hub.pinch(d) else localReadFilter.toward(d).let { n -> (n != localReadFilter).also { if (it) localReadFilter = n } }
        if (changed) tick()
    }
    fun chooseFilter(f: ReadFilter) {
        if (hub != null && hub.view != HubView.All && HubView.All in hub.views) hub.select(HubView.All) // from Unread, Read, Files or Timeline too: the filter is the All view's
        if (readFilter != f) tick()
        setReadFilter(f)
    }
    val filterActions = listOf(
        CustomAccessibilityAction(L10n.tr("Show unread only")) { chooseFilter(ReadFilter.Unread); true },
        CustomAccessibilityAction(L10n.tr("Show read only")) { chooseFilter(ReadFilter.Read); true },
        CustomAccessibilityAction(L10n.tr("Show all")) { chooseFilter(ReadFilter.All); true },
    )

    // Wave 27: on a history view (Unread, Read) the grid groups that history; on the live list it sets the default grouping of the live list.
    val historyGrouping = hub != null && !hub.listIsLive && hub.view.history
    val menuActions = MenuActions(
        mode = if (historyGrouping && hub != null) hub.grouping.toMode() else prefs.defaultMode, filter = activeFilter, counts = counts,
        onMode = { m -> if (historyGrouping && hub != null) hub.chooseGrouping(HubGrouping.of(m)) else onSetDefaultMode(m); menu.open = false },
        onFilter = { f ->
            filter = f
            // The app filter follows into the history views, so Show means the same on every tab.
            hub?.let { h -> HubView.entries.filter { it.history }.forEach { v -> h.setFilters(h.filters(v).copy(app = f), v) } }
            menu.open = false
        },
        scalePercent = commsScalePercent,
    )
    // Wave 25 (STR-01): the notifications layer shifts away by the panel's width while the menu is open.
    Box(Modifier.fillMaxSize().graphicsLayer {
        translationX = LaunchpadMenuMotion.listShiftPx((LaunchpadMenuMotion.panelWidthDp(size.width / density) + CommsStyle.PANEL_GAP_DP) * density, menu.progress, menu.reduced, if (rtlLayout) -1f else 1f)
    }) {
    Column(
        modifier
            .fillMaxSize()
            // Beside the home card the host draws the backdrop (it must not fade with the content); alone, the content draws its own.
            .then(if (endInset > 0.dp) Modifier else Modifier.background(LocalLp.current.bg))
            .testTag("lp.root")
            .semantics {
                paneTitle = L10n.tr("Comms")
                // Wave 29: the pinch's equivalents for TalkBack and switch access.
                customActions = filterActions
            } // wave 23 (NFR-13): TalkBack announces the panel when it opens
            .onPreviewKeyEvent { tracker.onKey(); inputManager.requestInputMode(ComposeInputMode.Keyboard); false }
            .hubKeys( // outside focusable(): the keys work whether the root or a row has focus
                onView = { d -> hub?.step(d)?.let(hub::select) },
                onScale = { bigger -> CommsScaleSettings.set(CommsScale.step(CommsScaleSettings.percent.value, if (bigger) +1 else -1)) },
                onMarkRead = { hubMarkRead() },
                onFilter = { d -> stepFilter(d) },
            )
            .onKeyEvent(keyHandler)
            .focusRequester(rootReq)
            .focusable()
            .pointerInput(Unit) { detectTapGestures { } }
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) { val ev = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial); if (ev.changes.any { it.pressed }) tracker.onTouch() }
                }
            }
            .pointerInput(Unit) {
                var total = 0f
                detectHorizontalDragGestures(
                    onDragStart = { total = 0f },
                    onDragEnd = { if (total < -80f) { if (menu.open) menu.open = false else onClose() } },
                ) { _, dx -> total += dx }
            }
            // Wave 29: a pinch steps the read filter Unread - All - Read (it no longer sets the Comms size); a two-finger swipe belongs to the hub.
            .hubGestures(
                onPinch = { d -> stepFilter(d) },
                onSwipe = if (hub == null) null else { s -> hub.swipe(s) { hubMarkRead() } },
            )
            .padding(end = endInset),
    ) {
      CommsScaleHost(commsScalePercent) {
      Column(Modifier.fillMaxSize()) {
        LaunchpadHeader(
            filterLabel = activeFilter?.let { f -> counts.firstOrNull { it.packageName == f }?.appLabel },
            onMenu = { menu.open = true }, onOpenSound = { onOpenSound(); soundSheet.show() },
            snapshot = systemState, now = now, is24 = is24, zone = zone, onOpenPanel = { k -> if (k == SystemTileKind.Connectivity) connectivitySheet.show() else onOpenSystemPanel(k) },
            onDoubleTap = { perform(gestures[com.bos.sphere.core.data.LaunchpadGesture.DoubleTapHeader] ?: com.bos.sphere.core.data.LaunchpadAction.None, null, sections) },
        )
        viewSwitcher()
        Box(Modifier.padding(start = CommsStyle.H_PAD_DP.dp, end = CommsStyle.END_PAD_DP.dp).fillMaxWidth().height(CommsStyle.HAIR_DP.dp).background(LocalLp.current.line).testTag("lp.tabsLine"))
        if (hasAccess && com.bos.sphere.core.data.GroupDnd.breaksThrough(groups, dndOn)) Text(
            L10n.tr("Do Not Disturb is on. Only Priority groups light the edge indicator."), color = LocalLp.current.dim, fontSize = CommsStyle.NOTE_SP.sp,
            modifier = Modifier.fillMaxWidth().padding(start = CommsStyle.H_PAD_DP.dp, end = CommsStyle.END_PAD_DP.dp, top = 8.dp, bottom = 6.dp).testTag("lp.dnd"),
        )
        if (readFilter != ReadFilter.All && !(hub != null && !hub.listIsLive)) Text(
            L10n.tr(if (readFilter == ReadFilter.Unread) "Unread only" else "Read only"), color = LocalLp.current.dim, fontSize = CommsStyle.NOTE_SP.sp,
            modifier = Modifier.fillMaxWidth().heightIn(min = commsTarget()).clickable { chooseFilter(ReadFilter.All) }
                .semantics { role = Role.Button; contentDescription = L10n.tr("Tap to show all") + ": " + L10n.tr(if (readFilter == ReadFilter.Unread) "Unread only" else "Read only") }
                .wrapContentHeight(Alignment.CenterVertically).padding(start = CommsStyle.H_PAD_DP.dp, end = CommsStyle.END_PAD_DP.dp).testTag("lp.readFilter"),
        )
        if (hub != null && !hub.listIsLive) HubBody(
            hub, now, zone, is24, liveKeys = items.mapTo(HashSet()) { it.key }, modifier = Modifier.weight(1f), hasAccess = hasAccess, onRequestAccess = onRequestAccess, panelBody = hubPanelBody,
            onOpen = { e ->
                val live = items.firstOrNull { it.key == e.key }
                if (live != null) open(live) else if (openApp == null) onOpenApp(e.packageName) else if (!openApp(e.packageName)) toast.value = L10n.tr("Couldn't open %s", e.appLabel.ifBlank { e.packageName })
            },
            onMore = { e -> items.firstOrNull { it.key == e.key }?.let { menuFor = it; menuExpand = null } },
        ) else GrantGate(
            kind = GrantKind.Notifications, granted = hasAccess, askedBefore = askedBefore, onEnable = onRequestAccess,
            colors = GrantColors(LocalLp.current.bg, LocalLp.current.surface, LocalLp.current.ink, LocalLp.current.dim, BlueLight, Color.White),
        ) {
            if (entries.isEmpty()) {
                if (!loadingGroups) Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Text(L10n.tr(when (readFilter) { ReadFilter.Unread -> "No unread notifications"; ReadFilter.Read -> "Nothing read yet"; ReadFilter.All -> "All caught up" }), color = LocalLp.current.dim, fontSize = CommsStyle.EMPTY_SP.sp, fontWeight = FontWeight.Light)
                }
            } else Column(Modifier.fillMaxSize()) {
              LazyColumn(
                Modifier.fillMaxSize().navigationBarsPadding().testTag("lp.list"), state = listState,
                verticalArrangement = Arrangement.spacedBy(RoomyList.ROW_GAP_DP.dp),
              ) {
                items(entries, key = { it.key }) { e ->
                    when (e) {
                        is Entry.Held -> HeldDigestLine(e.count, e.at?.let { formatStamp(it, now, is24, zone) } ?: "")
                        is Entry.Head -> {
                            val sec = e.sec
                            val extra = if (sec.key == LaunchpadView.DIGEST_KEY && view.nextRelease != null) L10n.tr("next at %s", formatStamp(view.nextRelease!!, now, is24, zone)) else ""
                            val target = FocusTarget.Group(sec.key)
                            val grp = view.grouped.firstOrNull { it.key == sec.key }?.group
                            SectionHeader(
                                sec, e.collapsed, unreadAny = (e.collapsed || sec.key == LaunchpadView.DIGEST_KEY) && sec.items.any { it.key !in effRead }, title = HubStyle.dayHeader(sec.key, sec.title), first = e.first, onToggle = { if (e.collapsed) toggleGroup(sec.key, true) },
                                accent = grp?.let { g -> g.colorArgb?.let { Color(it) } ?: if (g.priority == GroupPriority.Priority) BlueLight else null },
                                badge = grp?.takeIf { it.priority != GroupPriority.Normal }?.let { GroupTexts.priorityLabel(it.priority) }.orEmpty(),
                                showRing = inputMode == InputMode.Keyboard, requester = req(target), onFocused = { focus = target },
                                onKey = { ev ->
                                    if (ev.type == KeyEventType.KeyDown && RowKeys.map(ev.key.nativeKeyCode) == RowKeys.Result.Activate && e.collapsed) { toggleGroup(sec.key, true); true } else false
                                },
                                extra = extra, modifier = Modifier.animateItem(),
                            )
                        }
                        is Entry.Sub -> Column(Modifier.fillMaxWidth().padding(top = RoomyList.GROUP_GAP_DP.dp)) {
                            Box(Modifier.padding(start = CommsStyle.H_PAD_DP.dp, end = CommsStyle.END_PAD_DP.dp).fillMaxWidth().height(CommsStyle.HAIR_DP.dp).background(LocalLp.current.line))
                            Text(
                                e.title, color = LocalLp.current.dim, fontSize = CommsStyle.HEADING_SP.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.fillMaxWidth().padding(start = CommsStyle.H_PAD_DP.dp, end = CommsStyle.END_PAD_DP.dp, top = 10.dp).testTag("lp.sub.${e.id}"),
                            )
                        }
                        is Entry.More -> MoreLine(e.hidden, e.expanded, e.id) {
                            expandedConvs = if (e.expanded) expandedConvs - e.id else expandedConvs + e.id
                        }
                        is Entry.Item -> {
                            val n = e.n
                            val target = FocusTarget.Row(n.key, e.sec.key)
                            val globalLeft = gestures[com.bos.sphere.core.data.LaunchpadGesture.SwipeItemLeft] ?: com.bos.sphere.core.data.LaunchpadAction.Dismiss
                            val leftAction = DeliveryLogic.primaryFor(n.packageName, policies, globalLeft)
                            NotificationRow(
                                n = n, unread = n.key !in effRead, now = now, is24 = is24, zone = zone,
                                important = LaunchpadLogic.isImportant(n, rules),
                                pinned = e.pinned,
                                silent = n.key in view.silentKeys,
                                leftAction = leftAction,
                                handlers = RowHandlers(
                                    onOpen = { open(n) },
                                    onPrimary = { perform(leftAction, n, sections) },
                                    onCommand = { command(it, n) },
                                    onMenu = { menuFor = n; menuExpand = null },
                                    onToggleActions = { actionsOpenFor = if (n.key in actionsOpenFor) actionsOpenFor - n.key else actionsOpenFor + n.key },
                                    onToggleRead = { historyOnly[n.key]?.let { h -> hub?.store?.setRead(listOf(h.id), !h.read) } },
                                    onDeleteFromHistory = { hub?.store?.delete(history.filter { it.key == n.key }.map { it.id }) },
                                    onRunAction = { a ->
                                        if (runAction == null) onRunAction(a) else if (!runAction(a)) toast.value = L10n.tr("Couldn't open %s", a.label.ifBlank { n.appLabel })
                                    },
                                    onSendReply = { a, t ->
                                        if (sendReply == null) { onSendReply(a, t); replyFor = null; toast.value = L10n.tr("Reply sent") }
                                        else if (sendReply(a, t)) { replyFor = null; toast.value = L10n.tr("Reply sent") }
                                        else toast.value = L10n.tr("Couldn't send your reply")
                                    },
                                ),
                                showRing = inputMode == InputMode.Keyboard, requester = req(target), onFocused = { focus = target },
                                onPointer = tracker::onPointer, onKeyOther = keyHandler,
                                replyOpen = replyFor == n.key,
                                actionsOpen = n.key in actionsOpenFor,
                                historyOnly = n.key in historyOnly,
                                modifier = Modifier.animateItem(),
                            )
                        }
                    }
                }
              }
            }
        }
      }
      }
    }

    }
    // The menu panel and its scrim sit above the notifications layer, on the left.
    Box(Modifier.fillMaxSize()) { LaunchpadMenuLayer(menu, rtlLayout, menuActions) { menu.open = false; runCatching { rootReq.requestFocus() } } }

    toast.value?.let { t ->
        Box(Modifier.fillMaxSize().navigationBarsPadding().padding(bottom = 24.dp), Alignment.BottomCenter) {
            Text(
                t, color = Color.White, fontSize = 13.sp,
                modifier = Modifier.background(Color(0xE6202228), RoundedCornerShape(50)).padding(horizontal = 16.dp, vertical = 9.dp),
            )
        }
    }
    // The manager is a screen of buttons, so it stays clear of the card's 48 dp return zone (the list behind runs to the card's edge).
    LaunchedEffect(Unit) { LaunchpadHubNav.groups.collect { if (it) { LaunchpadHubNav.takeGroups(); menu.open = false; groupsOpen = true } } } // wave 29: Ground Control's "Groups and rules" opens the manager here
    LaunchpadGroupsOverlay(groupsOpen, items, endInset = if (endInset > 0.dp) maxOf(endInset, 48.dp) + 8.dp else 0.dp) { groupsOpen = false; runCatching { rootReq.requestFocus() } }
    if (soundSheet.open) SoundVibrationSheet(onDismiss = { soundSheet.open = false; runCatching { rootReq.requestFocus() } }, modifier = Modifier.padding(end = if (endInset > 0.dp) maxOf(endInset, 48.dp) + 8.dp else 0.dp))
    if (connectivitySheet.open) ConnectivitySheet(onDismiss = { connectivitySheet.open = false; runCatching { rootReq.requestFocus() } }, modifier = Modifier.padding(end = if (endInset > 0.dp) maxOf(endInset, 48.dp) + 8.dp else 0.dp))
    groupFor?.let { n ->
        GroupAssignSheet(
            n, groups, contacts, items,
            onMakeRule = { groupFor = null; ruleFrom = n },
            onDone = { m -> groupFor = null; m?.let { toast.value = it } },
        )
    }
    ruleFrom?.let { n ->
        GroupRuleSheet(n, groups, contacts, items, onDone = { m -> ruleFrom = null; m?.let { toast.value = it } })
    }
    menuFor?.let { n ->
        RowMenuSheet(
            n = n, unread = n.key !in effRead, historyOnly = n.key in historyOnly, canReply = ReplyLogic.pick(n.actions) != null, now = now, zone = zone,
            expand = menuExpand,
            onDismissSheet = { menuFor = null },
            onAddRule = { onAddRule(it); menuFor = null },
            onCommand = { c -> menuFor = null; command(c, n) },
            onSnooze = { p -> onSnooze(n, SnoozeLogic.until(p, now, zone)); menuFor = null; toast.value = L10n.tr("Snoozed") },
        )
    }
}

/**
 * Wave 27: the Comms header of the owner's mock-up (wave 28: re-measured, see CommsStyle). First row: the three-line menu glyph and the title (17 sp, the app's name filter while one is on).
 * Second row: the clock (a tap opens the Sound and Vibration popup through [onOpenSound]) and the four status glyphs. The tab row follows in the
 * view-switcher slot. There is no "Home" button: the home card's visible edge, Back and a swipe close the screen.
 */
@Composable
private fun LaunchpadHeader(
    filterLabel: String?,
    onMenu: () -> Unit,
    onOpenSound: () -> Unit,
    snapshot: SystemSnapshot?,
    now: Long,
    is24: Boolean,
    zone: ZoneId,
    onOpenPanel: (SystemTileKind) -> Unit,
    onDoubleTap: () -> Unit,
) {
    val ink = LocalLp.current.ink
    // The menu target is 48 dp of real screen at least (invisible padding around the glyph); the glyph keeps its left edge 32 dp from the screen edge.
    val box = maxOf(CommsStyle.HEADER_ROW_DP.dp, commsTarget())
    // Wave 30: the glyph is as high as the capitals of the title (follows the font scale), and its middle bar sits on their middle.
    val capDp = with(androidx.compose.ui.platform.LocalDensity.current) { (CommsStyle.CAP_HEIGHT_EM * CommsStyle.TITLE_SP).sp.toDp() }
    val glyphW = capDp * CommsStyle.MENU_GLYPH_ASPECT
    Column(
        Modifier.fillMaxWidth()
            .pointerInput(Unit) { detectTapGestures(onDoubleTap = { onDoubleTap() }) },
    ) {
        CommsHeaderPair(
            Modifier.fillMaxWidth().statusBarsPadding().padding(top = CommsStyle.HEADER_TOP_DP.dp, end = CommsStyle.END_PAD_DP.dp).height(CommsStyle.HEADER_ROW_DP.dp),
            box = box, glyphWidth = glyphW,
            menu = {
                Box(
                    Modifier.requiredSize(box).clip(CircleShape)
                        .semantics { contentDescription = L10n.tr("Comms menu") }
                        .clickable(onClickLabel = L10n.tr("Comms menu"), role = androidx.compose.ui.semantics.Role.Button, onClick = onMenu).testTag("lp.menu"),
                    Alignment.Center,
                ) { HamburgerIcon(ink, glyphW, capDp) }
            },
            title = {
                Text(
                    filterLabel ?: L10n.tr("Comms"), color = ink, fontSize = CommsStyle.TITLE_SP.sp, fontWeight = FontWeight.Normal, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.testTag("lp.title"),
                )
            },
        )
        CommsClockRow(snapshot, now, is24, zone, onOpenPanel, onOpenSound)
    }
}

/**
 * Wave 30 (commsPolish): the header's first row. The menu button (a [box] square with the glyph in its middle) and the title share one vertical centre:
 * the title is centred in the row and the button is placed so that its middle (the glyph's middle bar) is on the middle of the title's capitals
 * (baseline minus half the cap height), the same whatever the font's line metrics are. The glyph's left edge is [CommsStyle.H_PAD_DP] from the start edge,
 * the title starts at [CommsStyle.TITLE_X_DP]; both mirror in a right-to-left layout.
 */
@Composable
private fun CommsHeaderPair(modifier: Modifier, box: androidx.compose.ui.unit.Dp, glyphWidth: androidx.compose.ui.unit.Dp, menu: @Composable () -> Unit, title: @Composable () -> Unit) {
    androidx.compose.ui.layout.Layout({ menu(); title() }, modifier) { measurables, constraints ->
        val boxPx = box.roundToPx()
        val menuP = measurables[0].measure(androidx.compose.ui.unit.Constraints.fixed(boxPx, boxPx))
        val titleX = CommsStyle.TITLE_X_DP.dp.roundToPx()
        val titleP = measurables[1].measure(androidx.compose.ui.unit.Constraints(maxWidth = (constraints.maxWidth - titleX).coerceAtLeast(0)))
        val h = if (constraints.hasBoundedHeight) constraints.maxHeight else maxOf(titleP.height, boxPx)
        val dy = CommsStyle.HEADER_PAIR_DY_DP.dp.roundToPx()
        val titleY = (h - titleP.height) / 2 + dy
        val baseline = titleP[androidx.compose.ui.layout.FirstBaseline]
        val cap = CommsStyle.CAP_HEIGHT_EM * CommsStyle.TITLE_SP.sp.toPx()
        val capCentre = titleY + (if (baseline == androidx.compose.ui.layout.AlignmentLine.Unspecified) titleP.height / 2f else baseline - cap / 2f)
        val menuX = (CommsStyle.H_PAD_DP.dp.roundToPx() - (boxPx - glyphWidth.roundToPx()) / 2f).roundToInt()
        layout(constraints.maxWidth, h) {
            menuP.placeRelative(menuX, (capCentre - boxPx / 2f).roundToInt())
            titleP.placeRelative(titleX, titleY)
        }
    }
}

/** The three-line menu icon: [width] by [height] (the title's cap height), three bars of [CommsStyle.MENU_GLYPH_STROKE_DP], the outer two on the glyph's top and bottom edge. */
@Composable
internal fun HamburgerIcon(color: Color, width: androidx.compose.ui.unit.Dp, height: androidx.compose.ui.unit.Dp) {
    Canvas(Modifier.size(width, height)) {
        val stroke = CommsStyle.MENU_GLYPH_STROKE_DP.dp.toPx()
        for (i in 0..2) {
            val y = stroke / 2f + (size.height - stroke) * i / 2f
            drawLine(color, androidx.compose.ui.geometry.Offset(0f, y), androidx.compose.ui.geometry.Offset(size.width, y), strokeWidth = stroke, cap = androidx.compose.ui.graphics.StrokeCap.Butt)
        }
    }
}

@Composable
internal fun Chevron(color: Color, down: Boolean, size: Int = 18) {
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

// ---------------------------------------------------------------------------------------------
// Rail
// ---------------------------------------------------------------------------------------------

/**
 * The BB10-style peek rail: a narrow strip pinned to the left edge showing per-app unread
 * counts (rules applied: hidden / muted apps do not appear). Tap or swipe right to open.
 * Renders nothing when there is nothing unread or notification access is missing.
 */
@Composable
fun LaunchpadRail(
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
    /** Where the unread notifications come from; the live stream by default. A test passes its own (INP-07). */
    source: kotlinx.coroutines.flow.Flow<List<HubNotification>> = NotificationProvider.notifications,
    /** Overrides the notification-access check; null reads the real grant. */
    accessOverride: Boolean? = null,
) {
    rememberLaunchpadInit()
    val grant = rememberNotificationAccess()
    val access = accessOverride ?: grant
    val items by source.collectAsStateWithLifecycle(emptyList())
    val rules by LaunchpadSettings.rules.collectAsStateWithLifecycle()
    val read by LaunchpadSettings.readKeys.collectAsStateWithLifecycle()
    val now = rememberNow()
    val counts = remember(items, rules, read, now) { LaunchpadLogic.unreadCounts(items, rules, read, now) }
    if (!access || counts.isEmpty()) return
    val total = counts.sumOf { it.count }

    Column(
        modifier
            .width(44.dp)
            .clip(androidx.compose.foundation.shape.AbsoluteRoundedCornerShape(topRight = 22.dp, bottomRight = 22.dp))
            .background(Color(0xB30B0D14))
            .semantics(mergeDescendants = true) { contentDescription = L10n.tr("%d unread. Comms", total) }
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
