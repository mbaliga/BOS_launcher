package com.bos.sphere.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bos.sphere.core.data.LaunchpadSettings
import com.bos.sphere.core.data.NotificationProvider
import com.bos.sphere.core.data.WaitingLogic
import com.bos.sphere.core.design.L10n
import kotlinx.coroutines.delay
import java.time.ZoneId

/**
 * STR-09: a slim indicator on the left edge of the home card. It shows how many items wait, and widens for a few
 * seconds when a new one arrives (only if the app's rules let it count). It never takes focus: it is not focusable,
 * and a tap opens Launchpad.
 */
@Composable
fun LaunchpadWaitingIndicator(onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    remember(ctx) { LaunchpadSettings.init(ctx); true }
    val items by NotificationProvider.notifications.collectAsStateWithLifecycle(emptyList())
    val rules by LaunchpadSettings.rules.collectAsStateWithLifecycle()
    val policies by LaunchpadSettings.policies.collectAsStateWithLifecycle()
    val snoozed by LaunchpadSettings.snoozed.collectAsStateWithLifecycle()
    val read by LaunchpadSettings.readKeys.collectAsStateWithLifecycle()
    val access = rememberNotificationAccess()
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(30_000); now = System.currentTimeMillis() } }
    val waiting = remember(items, rules, policies, snoozed, read, now) {
        WaitingLogic.waiting(items, rules, policies, read, snoozed, now, ZoneId.systemDefault())
    }
    var seen by remember { mutableStateOf<Set<String>?>(null) }
    var headline by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(waiting) {
        val before = seen
        seen = waiting.map { it.key }.toSet()
        if (before != null) {
            WaitingLogic.fresh(before, waiting).maxByOrNull { it.timestamp }?.let { n ->
                headline = n.title
                delay(WaitingLogic.EXPAND_MS)
                headline = null
            }
        }
    }
    WaitingIndicatorView(if (access) waiting.size else 0, headline, onOpen, modifier)
}

/** The drawn indicator, separate from the data so it can be shown in tests. */
@Composable
fun WaitingIndicatorView(count: Int, headline: String?, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    if (count <= 0) return
    val desc = L10n.tr("%d waiting. Launchpad", count)
    Row(
        modifier
            .focusProperties { canFocus = false }
            .semantics { contentDescription = desc; onClick(label = L10n.tr("Open")) { onOpen(); true } }
            .pointerInput(Unit) { detectTapGestures { onOpen() } }
            .animateContentSize()
            .heightIn(min = 36.dp)
            .background(Color(0xE60A5C95), RoundedCornerShape(topEnd = 10.dp, bottomEnd = 10.dp))
            .padding(start = 4.dp, end = if (headline != null) 10.dp else 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(if (count > 99) "99+" else "$count", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        if (headline != null) {
            Text(headline, color = Color.White, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 6.dp).width(140.dp))
        }
    }
}
