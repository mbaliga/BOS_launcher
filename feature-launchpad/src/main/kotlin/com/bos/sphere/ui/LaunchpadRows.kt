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

private val FocusRing = Color(0xFF7CC8FF)

/** Everything a row does, as callbacks, so the same handler serves swipe, buttons, menu, keys and tests. */
internal class RowHandlers(
    val onOpen: () -> Unit,
    val onPrimary: () -> Unit,
    val onCommand: (RowCommand) -> Unit,
    val onMenu: () -> Unit,
    val onRunAction: (HubAction) -> Unit,
    val onSendReply: (HubAction, String) -> Unit,
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
    onToggle: () -> Unit,
    showRing: Boolean,
    requester: FocusRequester,
    onFocused: () -> Unit,
    onKey: (androidx.compose.ui.input.key.KeyEvent) -> Boolean,
    extra: String = "",
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    Column(
        modifier.fillMaxWidth()
            .testTag("lp.group.${sec.key}")
            .onKeyEvent(onKey)
            .focusRequester(requester)
            .onFocusChanged { focused = it.isFocused; if (it.isFocused) onFocused() }
            .then(if (showRing && focused) Modifier.border(2.dp, FocusRing, RoundedCornerShape(6.dp)) else Modifier)
            .clickable(onClick = onToggle),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val lead = sec.items.firstOrNull()
            if (lead != null && sec.key != LaunchpadLogic.FLAT_KEY && !sec.pinned && (sec.key.startsWith("app:") || sec.key.startsWith("appconv:"))) {
                AppIcon(lead.packageName, sec.subtitle.ifBlank { sec.title }, 20, Modifier.padding(end = 8.dp))
            }
            Text(
                sec.title, color = LocalLp.current.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
            )
            val sub = listOf(sec.subtitle, extra).filter { it.isNotEmpty() }.joinToString(" · ")
            if (sub.isNotEmpty()) {
                Text(
                    "  $sub", color = LocalLp.current.dim, fontSize = 12.sp, maxLines = 1,
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

/** A line under a group saying how many items wait for the next digest (STR-04). */
@Composable
internal fun HeldDigestLine(count: Int, timeText: String) {
    Text(
        L10n.tr("%d held for the digest at %s", count, timeText),
        color = LocalLp.current.dim, fontSize = 12.sp,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
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
) {
    var dx by remember { mutableFloatStateOf(0f) }
    var base by remember { mutableFloatStateOf(0f) } // 0, or the palette's width while it is held open
    var widthPx by remember { mutableFloatStateOf(1f) }
    var focused by remember { mutableStateOf(false) }
    val hoverSource = remember { MutableInteractionSource() }
    val hovered by hoverSource.collectIsHoveredAsState()
    LaunchedEffect(hovered) { if (hovered) onPointer() }
    val weight = if (unread) FontWeight.Bold else FontWeight.Normal
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
    val offsetPx = (base + dx).coerceIn(-widthPx, paletteW * 1.1f)
    val canSwipeLeft = leftAction != LaunchpadAction.None
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
            Row(Modifier.matchParentSize()) {
                cells.forEach { cmd ->
                    Box(
                        Modifier.width(with(density) { cellPx.toDp() }).fillMaxHeight().background(colors[cmd] ?: Color(0xFF3B4A5A))
                            .clickable { base = 0f; dx = 0f; handlers.onCommand(cmd) },
                        contentAlignment = Alignment.Center,
                    ) { Text(L10n.tr(paletteLabel(cmd)), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1) }
                }
            }
        } else if (offsetPx < 0f && canSwipeLeft) {
            // Slide left: the one primary action, uncovered behind the row.
            Box(
                Modifier.matchParentSize().background(Color(0xFFB3261E)).padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterEnd,
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
                .onSecondaryClick { handlers.onMenu() }
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
                .combinedClickable(onClick = { if (base > 0f) base = 0f else handlers.onOpen() }, onLongClick = handlers.onMenu)
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
                if (unread && !silent) {
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
                        n.summary, color = if (unread && !silent) LocalLp.current.ink2 else LocalLp.current.dim, fontSize = 13.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            if (showButtons) {
                // Buttons for both gestures, shown on hover and on focus (STR-03 equivalents).
                if (canSwipeLeft) RowButton(primaryLabel, handlers.onPrimary)
                RowButton(L10n.tr("Actions"), handlers.onMenu)
            } else {
                Text(
                    formatStamp(n.timestamp, now, is24, zone), color = LocalLp.current.dim, fontSize = 12.sp,
                    fontWeight = if (unread) FontWeight.Medium else FontWeight.Normal,
                )
            }
        }
      }
        if (plain.isNotEmpty() || reply != null) {
            Row(
                Modifier.fillMaxWidth().padding(start = 81.dp, end = 16.dp, bottom = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically,
            ) {
                if (reply != null) ActionChip(reply.label.ifBlank { L10n.tr("Reply") }, true) { handlers.onCommand(RowCommand.Reply) }
                plain.forEach { a -> ActionChip(a.label, false) { handlers.onRunAction(a) } }
            }
        }
        if (replyOpen && reply != null) {
            ReplyRow(reply, hint = reply.reply?.hint, onSend = { text -> handlers.onSendReply(reply, text) })
        }
        Box(Modifier.fillMaxWidth().height(0.5.dp).background(LocalLp.current.line))
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
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 7.dp),
    )
}

@Composable
private fun ActionChip(label: String, accent: Boolean, onClick: () -> Unit) {
    Text(
        label, color = if (accent) Color.White else LocalLp.current.ink2, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
        modifier = Modifier.heightIn(min = 32.dp).clip(RoundedCornerShape(16.dp))
            .background(if (accent) BlueDark else LocalLp.current.surface)
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 7.dp),
    )
}

/** Inline reply: a text field and Send, sent through the notification's own RemoteInput (STR-02). */
@Composable
internal fun ReplyRow(action: HubAction, hint: String?, onSend: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    Row(
        Modifier.fillMaxWidth().padding(start = 81.dp, end = 16.dp, bottom = 8.dp),
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
                .clickable(enabled = ReplyLogic.canSend(text)) { onSend(text); text = "" }
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
        Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = LocalLp.current.ink2, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
        if (expandable) { Spacer(Modifier.width(8.dp)); Chevron(LocalLp.current.dim, down = !expanded, size = 14) }
    }
}
