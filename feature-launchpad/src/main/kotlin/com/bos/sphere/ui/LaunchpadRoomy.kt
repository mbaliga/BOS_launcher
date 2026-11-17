package com.bos.sphere.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bos.sphere.core.data.HandOff
import com.bos.sphere.core.data.HubNotification
import com.bos.sphere.core.data.PendingIntentLauncher
import com.bos.sphere.core.data.Section
import com.bos.sphere.core.design.L10n
import kotlinx.coroutines.launch

/** STR-02: the measures of the roomy, fully expanded notification list, and which conversations fold behind "+N more". */
internal object RoomyList {
    const val ROW_MIN_DP = 72
    const val H_PAD_DP = 16
    const val V_PAD_DP = 12
    const val ICON_DP = 40
    const val ROW_GAP_DP = 8
    /** Extra air above a new conversation or section, drawn with a hairline. */
    const val GROUP_GAP_DP = 16

    /** Only a conversation-like section (by conversation or sender) holds messages of one thread; day, app, flat and pinned lists never fold. */
    fun foldable(key: String, pinned: Boolean) =
        !pinned && (key.startsWith("conv:") || key.startsWith("appconv:") || key.startsWith("sender:"))

    /** The rows shown: everything, except that a foldable conversation shows only its latest message until the user expands it. */
    fun rows(key: String, items: List<HubNotification>, expanded: Boolean, pinned: Boolean): List<HubNotification> =
        if (expanded || !foldable(key, pinned) || items.size <= 1) items else items.take(1)

    /** How many rows sit behind "+N more" (0 when nothing folds or the conversation is expanded: then only "Show fewer" is offered). */
    fun hidden(key: String, items: List<HubNotification>, pinned: Boolean): Int =
        if (foldable(key, pinned) && items.size > 1) items.size - 1 else 0
}

/** STR-02: the hand-off of a tapped notification, kept free of Compose so it can be tested. */
internal object LaunchpadOpen {
    /**
     * Marks [n] read, sends its content intent (else starts the app through [launchApp]), cancels it when it asked to be
     * auto-cancelled, and calls [close] only when something was actually handed off. On failure nothing closes.
     */
    fun open(
        n: HubNotification, context: Context?, launchApp: (String) -> Boolean,
        markRead: (String) -> Unit, cancel: (String) -> Unit, close: () -> Unit,
    ): HandOff {
        markRead(n.key)
        val result = PendingIntentLauncher.open(n.contentIntent, context) { n.packageName.isNotBlank() && launchApp(n.packageName) }
        if (result.ok) {
            if (result == HandOff.Sent && n.autoCancel && !n.isOngoing) cancel(n.key)
            close()
        }
        return result
    }
}

@Composable
internal fun MoreLine(hidden: Int, expanded: Boolean, id: String, onClick: () -> Unit) {
    Text(
        if (expanded) L10n.tr("Show fewer") else L10n.tr("+%d more", hidden),
        color = BlueLight, fontSize = 13.sp, fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(start = RoomyList.H_PAD_DP.dp + 3.dp + 10.dp + RoomyList.ICON_DP.dp + 12.dp)
            .heightIn(min = 48.dp).clip(RoundedCornerShape(24.dp)).clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onClick) // NFR-13: 48 dp target, announced as a button
            .padding(horizontal = 12.dp, vertical = 14.dp).testTag("lp.more.$id"),
    )
}

/**
 * STR-02: the compact title strip at the top. Tapping a title scrolls the long list to that section; the section in view is
 * marked. It is a jump list, not a tab bar: every section is always in the list.
 */
@Composable
internal fun SectionStrip(sections: List<Section>, listState: LazyListState, headIndex: Map<String, Int>, motion: com.bos.sphere.core.quality.MotionPrefs) {
    if (sections.size < 2) return
    val scope = rememberCoroutineScope()
    val current by remember(headIndex) {
        derivedStateOf {
            val first = listState.firstVisibleItemIndex
            sections.lastOrNull { (headIndex[it.key] ?: -1).let { i -> i in 0..first } }?.key ?: sections.first().key
        }
    }
    LazyRow(
        Modifier.fillMaxWidth().testTag("lp.strip"),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = RoomyList.H_PAD_DP.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        items(sections, key = { it.key }) { s ->
            val on = s.key == current
            // The chip is drawn 40 dp high; its touch target is 48 dp (NFR-13).
            Box(
                Modifier.heightIn(min = 48.dp)
                    .semantics { selected = on; role = androidx.compose.ui.semantics.Role.Tab; contentDescription = L10n.tr("Jump to %s", s.title) }
                    .clickable {
                        val i = headIndex[s.key] ?: -1
                        if (i >= 0) scope.launch { if (motion.reduced) listState.scrollToItem(i) else listState.animateScrollToItem(i) }
                    }
                    .testTag("lp.strip.${s.key}"),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    s.title, color = if (on) LocalLp.current.bg else LocalLp.current.ink2, fontSize = 13.sp,
                    fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.heightIn(min = 40.dp, max = 40.dp).padding(vertical = 4.dp).clip(RoundedCornerShape(16.dp))
                        .background(if (on) LocalLp.current.ink else LocalLp.current.surface)
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                )
            }
        }
    }
}
