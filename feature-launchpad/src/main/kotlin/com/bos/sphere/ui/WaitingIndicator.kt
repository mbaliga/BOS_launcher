package com.bos.sphere.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bos.sphere.core.data.HubNotification
import com.bos.sphere.core.data.LaunchpadSettings
import com.bos.sphere.core.data.NotificationProvider
import com.bos.sphere.core.data.WaitingLogic
import com.bos.sphere.core.design.L10n
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.time.ZoneId

/**
 * STR-09: a slim indicator on the left edge of the home card. It shows how many items wait, and widens for a few
 * seconds when a new one arrives (only if the app's rules let it count). It never takes focus: it is not focusable,
 * and a tap opens Launchpad.
 */
@Composable
fun LaunchpadWaitingIndicator(onOpen: () -> Unit, modifier: Modifier = Modifier, quiet: Boolean = false) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    remember(ctx) { LaunchpadSettings.init(ctx); true }
    val items by NotificationProvider.notifications.collectAsStateWithLifecycle(remember { NotificationProvider.snapshot })
    val rules by LaunchpadSettings.rules.collectAsStateWithLifecycle()
    val policies by LaunchpadSettings.policies.collectAsStateWithLifecycle()
    val snoozed by LaunchpadSettings.snoozed.collectAsStateWithLifecycle()
    val read by LaunchpadSettings.readKeys.collectAsStateWithLifecycle()
    val access = rememberNotificationAccess()
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(30_000); now = System.currentTimeMillis() } }
    // STR-13: Priority groups count even when their app is on a digest, and may tint the indicator; matching runs off the main thread.
    remember(ctx) { com.bos.sphere.core.data.GroupsSettings.init(ctx); true }
    val groups by com.bos.sphere.core.data.GroupsSettings.config.collectAsStateWithLifecycle()
    val contacts = rememberContactDirectory()
    val dnd = rememberDndOn() // wave 24: while Do Not Disturb is on only Priority groups count (Liftoff's own indicator)
    val plain = remember(items, rules, policies, snoozed, read, now, groups) {
        if (groups.groups.isEmpty()) WaitingLogic.waiting(items, rules, policies, read, snoozed, now, ZoneId.systemDefault()) else null
    }
    val grouped by androidx.compose.runtime.produceState<Pair<List<HubNotification>, com.bos.sphere.core.data.PriorityWaiting>?>(null, items, rules, policies, snoozed, read, now, groups, contacts, dnd) {
        value = if (groups.groups.isEmpty()) null else withContext(kotlinx.coroutines.Dispatchers.Default) {
            val r = com.bos.sphere.core.data.GroupResolver(groups, contacts)
            val w = com.bos.sphere.core.data.GroupWaiting.waiting(items, rules, policies, read, snoozed, now, ZoneId.systemDefault(), r, dndOn = dnd)
            w to com.bos.sphere.core.data.GroupWaiting.priority(w, r)
        }
    }
    val waiting = plain ?: grouped?.first ?: emptyList()
    val priority = if (plain != null) null else grouped?.second
    // Until the grouped list is computed there is nothing to compare against: its arrival must not look like a new item.
    val headline = rememberWaitingHeadline(waiting, ready = plain != null || grouped != null)
    WaitingIndicatorView(
        if (access) waiting.size else 0, headline, onOpen, modifier,
        priorityCount = if (access) priority?.count ?: 0 else 0,
        accent = priority?.takeIf { it.count > 0 }?.let { p -> p.accentArgb?.let { Color(it) } ?: BlueDark },
        quiet = quiet,
    )
}

/**
 * The title of the newest item that just started to wait, for [expandMs]. The timer is keyed on the headline itself, so a
 * later change of the list that brings no new item (a row dismissed while the indicator is wide) cannot cancel it and
 * leave the indicator widened for good.
 */
@Composable
internal fun rememberWaitingHeadline(waiting: List<HubNotification>, expandMs: Long = WaitingLogic.EXPAND_MS, ready: Boolean = true): String? {
    var seen by remember { mutableStateOf<Set<String>?>(null) }
    var shown by remember { mutableStateOf<Pair<Int, String>?>(null) }
    var counter by remember { mutableStateOf(0) }
    LaunchedEffect(waiting, ready) {
        if (!ready) return@LaunchedEffect
        val before = seen
        seen = waiting.map { it.key }.toSet()
        if (before != null) WaitingLogic.fresh(before, waiting).maxByOrNull { it.timestamp }?.let { n -> counter += 1; shown = counter to n.title }
    }
    LaunchedEffect(shown) { if (shown != null) { delay(expandMs); shown = null } }
    return shown?.second
}

/** The drawn indicator, separate from the data so it can be shown in tests. */
@Composable
fun WaitingIndicatorView(
    count: Int, headline: String?, onOpen: () -> Unit, modifier: Modifier = Modifier,
    /** STR-13: how many of the waiting items sit in Priority groups, and the colour to tint the indicator with (null: the usual blue). */
    priorityCount: Int = 0, accent: Color? = null,
    /** Wave 17 (HOM-23): the Glance form: a small dot (a 48 dp target around it) in the group accent, no count and no headline. */
    quiet: Boolean = false,
) {
    if (count <= 0) return
    val desc = if (priorityCount > 0) L10n.tr("%d waiting, %d priority. Comms", count, priorityCount) else L10n.tr("%d waiting. Comms", count)
    if (quiet) {
        val dot = if (priorityCount > 0 && accent != null) lpAccentFill(accent) else Color(0xFF3FA7E8)
        androidx.compose.foundation.layout.Box(
            modifier.focusProperties { canFocus = false }
                .testTag("glance.waiting")
                .semantics { contentDescription = desc; onClick(label = L10n.tr("Open")) { onOpen(); true } }
                .pointerInput(Unit) { detectTapGestures { onOpen() } }
                .width(48.dp).heightIn(min = 48.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            androidx.compose.foundation.layout.Box(
                Modifier.padding(start = 3.dp).width(if (priorityCount > 0) 8.dp else 6.dp).height(if (priorityCount > 0) 8.dp else 6.dp)
                    .background(dot.copy(alpha = 0.9f), androidx.compose.foundation.shape.CircleShape),
            )
        }
        return
    }
    // The indicator hugs the physical left edge, where Launchpad opens from, so it is laid out left to right in every language.
    androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Ltr) {
    Row(
        modifier
            .focusProperties { canFocus = false }
            .semantics { contentDescription = desc; onClick(label = L10n.tr("Open")) { onOpen(); true } }
            .pointerInput(Unit) { detectTapGestures { onOpen() } }
            .animateContentSize()
            .heightIn(min = 36.dp)
            .background(if (priorityCount > 0 && accent != null) lpAccentFill(accent).copy(alpha = 0.95f) else Color(0xE60A5C95), RoundedCornerShape(topEnd = 10.dp, bottomEnd = 10.dp))
            .padding(start = 4.dp, end = if (headline != null) 10.dp else 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text((if (priorityCount > 0) "★ " else "") + (if (count > 99) "99+" else "$count"), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        if (headline != null) {
            Text(headline, color = Color.White, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 6.dp).width(140.dp))
        }
    }
    }
}
