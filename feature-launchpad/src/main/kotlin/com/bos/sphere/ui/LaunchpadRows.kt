package com.bos.sphere.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.paddingFromBaseline
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bos.sphere.core.data.HubAction
import com.bos.sphere.core.data.HubNotification
import com.bos.sphere.core.data.LaunchpadAction
import com.bos.sphere.core.data.LaunchpadLogic
import com.bos.sphere.core.data.LaunchpadRowMenus
import com.bos.sphere.core.data.ReplyLogic
import com.bos.sphere.core.data.Rule
import com.bos.sphere.core.data.RuleAction
import com.bos.sphere.core.data.RowCommand
import com.bos.sphere.core.data.RowMenuContext
import com.bos.sphere.core.data.Section
import com.bos.sphere.core.data.SnoozeLogic
import com.bos.sphere.core.design.L10n
import java.time.ZoneId
import kotlin.math.roundToInt

internal val FocusRing = Color(0xFF7CC8FF)

/** Everything a row does, as callbacks, so the same handler serves swipe, buttons, menu, keys and tests. */
internal class RowHandlers(
    val onOpen: () -> Unit,
    val onPrimary: () -> Unit,
    val onCommand: (RowCommand) -> Unit,
    val onMenu: () -> Unit,
    val onRunAction: (HubAction) -> Unit,
    val onSendReply: (HubAction, String) -> Unit,
    /** Wave 25: a long press (or the secondary button) opens the row's action chips in place; the full menu is behind the chips' "More". */
    val onToggleActions: () -> Unit = onMenu,
)

internal fun menuContextFor(n: HubNotification, replyAvailable: Boolean, unread: Boolean) = RowMenuContext(
    hasContentIntent = n.contentIntent != null,
    canDismiss = !n.isOngoing,
    unread = unread,
    hasSender = LaunchpadLogic.senderOf(n).isNotBlank() && n.packageName.isNotBlank(),
    hasPackage = n.packageName.isNotBlank(),
    canReply = replyAvailable,
)

@Composable
internal fun SectionHeader(
    sec: Section,
    collapsed: Boolean,
    first: Boolean,
    onToggle: () -> Unit,
    showRing: Boolean,
    requester: FocusRequester,
    onFocused: () -> Unit,
    onKey: (androidx.compose.ui.input.key.KeyEvent) -> Boolean,
    extra: String = "",
    modifier: Modifier = Modifier,
    /** STR-13: a group section's colour dot and priority badge. */
    accent: Color? = null,
    badge: String = "",
    /** Wave 25: the text shown; a day section passes its date ("Thu 20 Aug 2015"). */
    title: String = sec.title,
) {
    var focused by remember { mutableStateOf(false) }
    // Wave 25 (STR-02): the hub's date band: a small quiet title (a day reads "Thu 20 Aug 2015"), left-aligned with the rows' badges, a thin divider below.
    Column(
        modifier.fillMaxWidth()
            .testTag("lp.group.${sec.key}")
            .semantics { role = androidx.compose.ui.semantics.Role.Tab }
            .onKeyEvent(onKey)
            .focusRequester(requester)
            .onFocusChanged { focused = it.isFocused; if (it.isFocused) onFocused() }
            .then(if (showRing && focused) Modifier.border(2.dp, FocusRing, RoundedCornerShape(10.dp)) else Modifier)
            .clickable(onClick = onToggle),
    ) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 32.dp).padding(start = CommsStyle.H_PAD_DP.dp, end = CommsStyle.END_PAD_DP.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (accent != null) Box(Modifier.padding(end = 8.dp).size(10.dp).background(accent, CircleShape))
            val lead = sec.items.firstOrNull()
            if (lead != null && sec.key != LaunchpadLogic.FLAT_KEY && !sec.pinned && (sec.key.startsWith("app:") || sec.key.startsWith("appconv:"))) {
                AppIcon(lead.packageName, sec.subtitle.ifBlank { sec.title }, 20, Modifier.padding(end = 8.dp))
            }
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Text(
                title, color = LocalLp.current.dim, fontSize = CommsStyle.HEADING_SP.sp, fontWeight = FontWeight.Medium,
                maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
            )
            val sub = listOf(sec.subtitle, extra).filter { it.isNotEmpty() }.joinToString(" · ")
            if (sub.isNotEmpty()) {
                Text(
                    "  $sub", color = LocalLp.current.dim, fontSize = CommsStyle.NOTE_SP.sp, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
                )
            }
            if (badge.isNotEmpty()) Text("  $badge", color = LocalLp.current.dim, fontSize = 11.sp, fontWeight = FontWeight.Medium, maxLines = 1)
            }
            Text("${sec.items.size}", color = LocalLp.current.dim, fontSize = CommsStyle.NOTE_SP.sp)
            if (collapsed) { Spacer(Modifier.width(6.dp)); Chevron(LocalLp.current.dim, down = false, size = 14) }
        }
        CommsRowLine()
    }
}

/** A line under a group saying how many items wait for the next digest (STR-04). */
@Composable
internal fun HeldDigestLine(count: Int, timeText: String) {
    Text(
        L10n.tr("%d held for the digest at %s", count, timeText),
        color = LocalLp.current.dim, fontSize = CommsStyle.NOTE_SP.sp,
        modifier = Modifier.fillMaxWidth().padding(start = CommsStyle.H_PAD_DP.dp, end = CommsStyle.END_PAD_DP.dp, top = 8.dp, bottom = 8.dp),
    )
}

/** Secondary-button press (right click, or the pointer's equivalent): the same menu as long-press (INP-02, INP-03). */
internal fun Modifier.onSecondaryClick(onClick: () -> Unit): Modifier = this.pointerInput(Unit) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        if (currentEvent.buttons.isSecondaryPressed) {
            down.consume()
            onClick()
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun NotificationRow(
    n: HubNotification,
    unread: Boolean,
    now: Long,
    is24: Boolean,
    zone: ZoneId,
    important: Boolean,
    pinned: Boolean,
    silent: Boolean,
    leftAction: LaunchpadAction,
    handlers: RowHandlers,
    showRing: Boolean,
    requester: FocusRequester,
    onFocused: () -> Unit,
    onPointer: () -> Unit,
    onKeyOther: (androidx.compose.ui.input.key.KeyEvent) -> Boolean,
    replyOpen: Boolean,
    modifier: Modifier = Modifier,
    /** Wave 25: the action chips (Reply and the notification's own actions) show only when the row is expanded. */
    actionsOpen: Boolean = false,
) {
    var dx by remember { mutableFloatStateOf(0f) }
    var base by remember { mutableFloatStateOf(0f) } // 0, or the palette's width while it is held open
    var widthPx by remember { mutableFloatStateOf(1f) }
    var focused by remember { mutableStateOf(false) }
    val hoverSource = remember { MutableInteractionSource() }
    val hovered by hoverSource.collectIsHoveredAsState()
    LaunchedEffect(hovered) { if (hovered) onPointer() }
    val density = LocalDensity.current
    val reply = remember(n.actions) { ReplyLogic.pick(n.actions) }
    val plain = remember(n.actions) { ReplyLogic.plain(n.actions).take(3) }
    val ctx = menuContextFor(n, reply != null, unread)
    val cells = LaunchpadRowMenus.paletteCells(ctx) + RowCommand.MakeRule
    val colors = mapOf(
        RowCommand.Snooze to Color(0xFF7A5C00), RowCommand.MarkRead to Color(0xFF0B7BB8), RowCommand.MuteApp to Color(0xFF3B4A5A),
        RowCommand.Open to Color(0xFF0A6296), RowCommand.MakeRule to Color(0xFF55486F),
    )
    val cellPx = minOf(with(density) { 72.dp.toPx() }, widthPx * 0.9f / cells.size)
    val paletteW = cellPx * cells.size
    val canSwipeLeft = leftAction != LaunchpadAction.None
    // With no primary action there is nothing behind a left slide, so the row does not move that way.
    val offsetPx = (base + dx).coerceIn(if (canSwipeLeft) -widthPx else 0f, paletteW * 1.1f)
    val showButtons = hovered || focused
    val primaryLabel = L10n.tr(leftAction.label)
    val a11yActions = buildList {
        add(CustomAccessibilityAction(primaryLabel) { handlers.onPrimary(); true })
        add(CustomAccessibilityAction(L10n.tr("Actions")) { handlers.onMenu(); true })
    }

    Column(modifier.testTag("lp.row.${n.key}")) {
      Box(Modifier.fillMaxWidth()) {
        if (offsetPx > 0f) {
            // Slide right: the action palette, held open until one is chosen or the row is tapped.
            // The row slides physically to the right, so the palette is laid out from the physical left in every language.
            androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Ltr) {
            Row(Modifier.matchParentSize()) {
                cells.forEach { cmd ->
                    Box(
                        Modifier.width(with(density) { cellPx.toDp() }).fillMaxHeight().background(colors[cmd] ?: Color(0xFF3B4A5A))
                            .clickable { base = 0f; dx = 0f; handlers.onCommand(cmd) },
                        contentAlignment = Alignment.Center,
                    ) { Text(L10n.tr(paletteLabel(cmd)), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1) }
                }
            }
            }
        } else if (offsetPx < 0f && canSwipeLeft) {
            // Slide left: the one primary action, uncovered behind the row.
            Box(
                Modifier.matchParentSize().background(Color(0xFFB3261E)).padding(horizontal = 20.dp),
                contentAlignment = androidx.compose.ui.BiasAbsoluteAlignment(1f, 0f), // the physical right: where a left slide uncovers
            ) { Text(primaryLabel, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium) }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .offset { IntOffset(offsetPx.roundToInt(), 0) }
                .background(LocalLp.current.bg)
                .testTag("lp.focus.${n.key}")
                .semantics { customActions = a11yActions }
                .pointerInput(n.key, leftAction) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            val total = base + dx
                            dx = 0f
                            if (total < -widthPx * 0.3f && canSwipeLeft) { base = 0f; handlers.onPrimary() }
                            else if (total > paletteW * 0.4f) base = paletteW
                            else base = 0f
                        },
                        onDragCancel = { dx = 0f },
                    ) { _, delta -> dx += delta }
                }
                .onSecondaryClick { handlers.onToggleActions() }
                .hoverable(hoverSource)
                .onKeyEvent { e ->
                    if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
                    when (com.bos.sphere.core.data.RowKeys.map(e.key.nativeKeyCode, e.isShiftPressed)) {
                        com.bos.sphere.core.data.RowKeys.Result.Primary -> { if (canSwipeLeft) handlers.onPrimary(); canSwipeLeft }
                        com.bos.sphere.core.data.RowKeys.Result.Palette -> { handlers.onMenu(); true }
                        com.bos.sphere.core.data.RowKeys.Result.Activate -> { handlers.onOpen(); true }
                        else -> onKeyOther(e)
                    }
                }
                .focusRequester(requester)
                .onFocusChanged { focused = it.isFocused; if (it.isFocused) onFocused() }
                .then(if (showRing && focused) Modifier.border(2.dp, FocusRing, RoundedCornerShape(6.dp)) else Modifier)
                .combinedClickable(onClick = { if (base > 0f) base = 0f else handlers.onOpen() }, onLongClick = handlers.onToggleActions)
                .onSizeChanged { widthPx = it.width.toFloat().coerceAtLeast(1f) }
                .heightIn(min = RoomyList.ROW_MIN_DP.dp)
                .height(androidx.compose.foundation.layout.IntrinsicSize.Min),
        ) {
            // Wave 27 (the owner's Comms mock-up), wave 28 (measured): no stripe. A 38 dp rounded app icon, the headline (a message's text; else the title) in 17 sp,
            // a grey second line (the sender; else the text) with the time right-aligned on it.
            val messaging = n.isMessaging && n.summary.isNotEmpty()
            val headline = if (messaging) n.summary else n.title.ifBlank { n.summary }
            val second = if (messaging) n.title else if (n.title.isNotBlank()) n.summary.ifBlank { n.appLabel } else n.appLabel
            CommsRowContent(
                pkg = n.packageName, appLabel = n.appLabel.ifBlank { n.title }, iconTag = "lp.badge.${n.key}",
                headline = headline, headlineColor = if (unread && !silent) LocalLp.current.ink else LocalLp.current.ink2, titleTag = "lp.title.${n.key}",
                second = second, previewTag = "lp.preview.${n.key}", stamp = formatStamp(n.timestamp, now, is24, zone), timeTag = "lp.time.${n.key}",
                dot = pinned || important, modifier = Modifier.weight(1f).fillMaxHeight(),
            ) {
                if (showButtons) {
                    // Buttons for both gestures, shown on hover and on focus (STR-03 equivalents).
                    Spacer(Modifier.width(10.dp))
                    if (canSwipeLeft) RowButton(primaryLabel, handlers.onPrimary)
                    RowButton(L10n.tr("Actions"), handlers.onMenu)
                }
            }
        }
      }
        // Wave 25 (STR-02): no chips on a resting row; a long press opens them in place, with "Actions" for the full menu.
        if (actionsOpen && (plain.isNotEmpty() || reply != null)) {
            Row(
                Modifier.fillMaxWidth().testTag("lp.chips.${n.key}").padding(start = HubStyle.TEXT_START_DP.dp, end = CommsStyle.END_PAD_DP.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically,
            ) {
                if (reply != null) ActionChip(reply.label.ifBlank { L10n.tr("Reply") }, true) { handlers.onCommand(RowCommand.Reply) }
                plain.forEach { a -> ActionChip(a.label, false) { handlers.onRunAction(a) } }
                ActionChip(L10n.tr("Actions"), false) { handlers.onMenu() }
            }
        } else if (actionsOpen) {
            Row(Modifier.fillMaxWidth().testTag("lp.chips.${n.key}").padding(start = HubStyle.TEXT_START_DP.dp, end = CommsStyle.END_PAD_DP.dp, bottom = 8.dp)) {
                ActionChip(L10n.tr("Actions"), false) { handlers.onMenu() }
            }
        }
        if (replyOpen && reply != null) {
            ReplyRow(reply, hint = reply.reply?.hint, onSend = { text -> handlers.onSendReply(reply, text) })
        }
        CommsRowLine(Modifier.testTag("lp.rowLine.${n.key}"))
    }
}

private fun paletteLabel(c: RowCommand) = when (c) {
    RowCommand.Snooze -> "Snooze"
    RowCommand.MarkRead -> "Read"
    RowCommand.MuteApp -> "Mute"
    RowCommand.Open -> "Open"
    RowCommand.MakeRule -> "Rule"
    else -> c.label
}

@Composable
private fun RowButton(label: String, onClick: () -> Unit) {
    Text(
        label, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1,
        modifier = Modifier.padding(start = 6.dp).heightIn(min = 32.dp).clip(RoundedCornerShape(16.dp)).background(BlueDark)
            .clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onClick).padding(horizontal = 12.dp, vertical = 7.dp),
    )
}

@Composable
private fun ActionChip(label: String, accent: Boolean, onClick: () -> Unit) {
    Text(
        label, color = if (accent) Color.White else LocalLp.current.ink2, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
        modifier = Modifier.heightIn(min = 32.dp).clip(RoundedCornerShape(16.dp))
            .background(if (accent) BlueDark else LocalLp.current.surface)
            .clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onClick).padding(horizontal = 12.dp, vertical = 7.dp),
    )
}

/** Inline reply: a text field and Send, sent through the notification's own RemoteInput (STR-02). */
@Composable
internal fun ReplyRow(action: HubAction, hint: String?, onSend: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    Row(
        Modifier.fillMaxWidth().padding(start = HubStyle.TEXT_START_DP.dp, end = CommsStyle.END_PAD_DP.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.weight(1f).clip(RoundedCornerShape(18.dp)).background(LocalLp.current.surface).padding(horizontal = 14.dp, vertical = 9.dp)) {
            if (text.isEmpty()) Text(hint?.takeIf { it.isNotBlank() } ?: L10n.tr("Write a reply"), color = LocalLp.current.dim, fontSize = 14.sp)
            BasicTextField(
                value = text, onValueChange = { text = it }, singleLine = true,
                textStyle = TextStyle(color = LocalLp.current.ink, fontSize = 14.sp),
                cursorBrush = SolidColor(BlueLight),
                modifier = Modifier.fillMaxWidth().focusRequester(focus).onKeyEvent { e ->
                    if (e.type == KeyEventType.KeyDown && (e.key.nativeKeyCode == com.bos.sphere.core.data.RowKeys.ENTER) && ReplyLogic.canSend(text)) { onSend(text); text = ""; true } else false
                },
            )
        }
        Text(
            L10n.tr("Send"), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium,
            modifier = Modifier.heightIn(min = 36.dp).clip(RoundedCornerShape(18.dp))
                .background(if (ReplyLogic.canSend(text)) BlueDark else LocalLp.current.dim)
                .clickable(enabled = ReplyLogic.canSend(text), role = androidx.compose.ui.semantics.Role.Button) { onSend(text); text = "" }
                .padding(horizontal = 16.dp, vertical = 9.dp),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RowMenuSheet(
    n: HubNotification,
    unread: Boolean,
    canReply: Boolean,
    now: Long,
    zone: ZoneId,
    expand: RowCommand? = null,
    onDismissSheet: () -> Unit,
    onAddRule: (Rule) -> Unit,
    onCommand: (RowCommand) -> Unit,
    onSnooze: (SnoozeLogic.Preset) -> Unit,
) {
    val app = n.appLabel.ifBlank { n.packageName }
    val sender = LaunchpadLogic.senderOf(n).takeIf { it.isNotBlank() }
    val pkg = n.packageName
    var open by remember { mutableStateOf(expand) }
    val entries = LaunchpadRowMenus.onLongPress(menuContextFor(n, canReply, unread))
    val rules = buildList<Pair<String, () -> Unit>> {
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
            entries.forEach { cmd ->
                SheetLine(L10n.tr(cmd.label), expandable = cmd == RowCommand.Snooze || cmd == RowCommand.MakeRule, expanded = open == cmd) {
                    when (cmd) {
                        RowCommand.Snooze, RowCommand.MakeRule -> open = if (open == cmd) null else cmd
                        else -> onCommand(cmd)
                    }
                }
                if (cmd == RowCommand.Snooze && open == cmd) {
                    SnoozeLogic.Preset.entries.forEach { p -> SheetLine("      " + L10n.tr(p.label), false, false) { onSnooze(p) } }
                }
                if (cmd == RowCommand.MakeRule && open == cmd) {
                    rules.forEach { (label, act) -> SheetLine("      $label", false, false) { act() } }
                }
            }
        }
    }
}

@Composable
private fun SheetLine(label: String, expandable: Boolean, expanded: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onClick).padding(horizontal = 20.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = LocalLp.current.ink2, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
        if (expandable) { Spacer(Modifier.width(8.dp)); Chevron(LocalLp.current.dim, down = !expanded, size = 14) }
    }
}


/** The inset hairline under a Comms row: from the left margin to the right margin, one dp. */
@Composable
internal fun CommsRowLine(modifier: Modifier = Modifier) =
    Box(Modifier.padding(start = CommsStyle.H_PAD_DP.dp, end = CommsStyle.END_PAD_DP.dp).then(modifier).fillMaxWidth().height(CommsStyle.HAIR_DP.dp).background(LocalLp.current.line))

/**
 * One Comms row's content, shared by the live list and the history views, at the mock-up's measures: the icon 16 dp below the row's top, the headline's
 * baseline 33.2 dp and the second line's 52.7 dp below it, the time's 50.9 dp, text 16 dp after the icon. With [expanded] (an opened history row) the
 * headline may take several lines and the second line follows it.
 */
@Composable
internal fun CommsRowContent(
    pkg: String, appLabel: String, iconTag: String,
    headline: String, headlineColor: Color, titleTag: String,
    second: String, previewTag: String, stamp: String, timeTag: String,
    modifier: Modifier = Modifier, dot: Boolean = false, expanded: Boolean = false,
    trailing: @Composable () -> Unit = {},
) {
    val lp = LocalLp.current
    Row(modifier.padding(start = CommsStyle.H_PAD_DP.dp, end = CommsStyle.END_PAD_DP.dp)) {
        AppIcon(pkg, appLabel, CommsStyle.ICON_DP, Modifier.padding(top = CommsStyle.ICON_TOP_DP.dp).testTag(iconTag))
        Spacer(Modifier.width(CommsStyle.TEXT_GAP_DP.dp))
        val titleText: @Composable (Modifier) -> Unit = { m ->
            Text(
                headline, color = headlineColor, fontSize = CommsStyle.TITLE_ROW_SP.sp, fontWeight = FontWeight.Normal,
                maxLines = if (expanded) 12 else 1, overflow = TextOverflow.Ellipsis, modifier = m.testTag(titleTag),
            )
        }
        val subLine: @Composable (Boolean) -> Unit = { baselineAligned ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = if (baselineAligned) Alignment.Top else Alignment.Bottom) {
                Text(
                    second, color = lp.dim, fontSize = CommsStyle.SUB_SP.sp, fontWeight = FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).then(if (baselineAligned) Modifier.paddingFromBaseline(top = CommsStyle.SUB_BASELINE_DP.dp) else Modifier).testTag(previewTag),
                )
                Spacer(Modifier.width(8.dp))
                if (dot) Box(Modifier.padding(end = 6.dp, top = (CommsStyle.TIME_BASELINE_DP - 8f).dp).size(6.dp).background(BlueLight, CircleShape))
                Text(
                    stamp, color = lp.dim, fontSize = CommsStyle.TIME_SP.sp, maxLines = 1,
                    modifier = (if (baselineAligned) Modifier.paddingFromBaseline(top = CommsStyle.TIME_BASELINE_DP.dp) else Modifier).testTag(timeTag),
                )
            }
        }
        if (expanded) Column(Modifier.weight(1f).heightIn(min = (CommsStyle.ROW_DP - CommsStyle.HAIR_DP).dp)) {
            titleText(Modifier.paddingFromBaseline(top = CommsStyle.TITLE_BASELINE_DP.dp).padding(end = CommsStyle.TITLE_END_DP.dp))
            Box(Modifier.padding(top = 2.dp, bottom = 10.dp)) { subLine(false) }
        } else Box(Modifier.weight(1f).heightIn(min = (CommsStyle.ROW_DP - CommsStyle.HAIR_DP).dp)) {
            titleText(Modifier.paddingFromBaseline(top = CommsStyle.TITLE_BASELINE_DP.dp).padding(end = CommsStyle.TITLE_END_DP.dp))
            subLine(true)
        }
        trailing()
    }
}
