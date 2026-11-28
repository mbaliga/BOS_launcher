package com.bos.sphere.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bos.sphere.core.data.HubNotification
import com.bos.sphere.core.data.GroupsSettings
import com.bos.sphere.core.data.LaunchpadSettings
import com.bos.sphere.core.design.L10n

/**
 * Wave 57 (previewAll), CFG-53: the side panel's Comms column for the Settings Preview. It is the real [LaunchpadContent] with the user's theme,
 * size, categories and pinned block, fed with made-up notifications from [CommsPreviewSamples], never the user's own: a screenshot of Settings
 * must not carry a private message. Nothing in it is wired: no tap opens, marks or dismisses anything, and it takes no focus (it runs in the
 * Preview's sandbox, see LocalPreviewSandbox).
 */
@Composable
fun CommsPreview(
    theme: com.bos.sphere.core.data.LaunchpadTheme,
    modifier: Modifier = Modifier,
    /** Wave 58 (CFG-73): the Widgets section switch and its place; null reads the live Comms options (the Preview's case). */
    widgets: Pair<Boolean, WidgetsSlot>? = null,
) {
    val ctx = LocalContext.current
    remember(ctx) { LaunchpadSettings.init(ctx); GroupsSettings.init(ctx); true }
    val dark = when (theme) {
        com.bos.sphere.core.data.LaunchpadTheme.Dark -> true
        com.bos.sphere.core.data.LaunchpadTheme.Light -> false
        com.bos.sphere.core.data.LaunchpadTheme.System -> androidx.compose.foundation.isSystemInDarkTheme()
    }
    val prefs by LaunchpadSettings.prefs.collectAsState()
    val rules by LaunchpadSettings.rules.collectAsState()
    val liveOn by CommsOptions.widgetsSection.collectAsState()
    val liveSlot by CommsOptions.widgetsSlot.collectAsState()
    val (widgetsOn, slot) = widgets ?: (liveOn to liveSlot)
    val now = remember { System.currentTimeMillis() }
    val items = remember(now) { CommsPreviewSamples.items(now) }
    CompositionLocalProvider(LocalLp provides if (dark) LpDark else LpLight) {
        androidx.compose.foundation.layout.Column(modifier.launchpadBackdrop(LocalLp.current.bg, LocalLp.current.surface)) {
            // Wave 58 (CFG-73): the panel's Widgets section, as placeholder cards (no real widget is hosted in the Preview's sandbox), at the
            // place the section has in the panel's tab row: above the Comms column when it is the first section, below it otherwise.
            val first = widgetsOn && PanelSections.order(true, slot).first() == HubView.Widgets
            if (first) PanelWidgetsPreview(slot)
            androidx.compose.foundation.layout.Box(Modifier.weight(1f)) {
                LaunchpadContent(
                    items = items, rules = rules, prefs = prefs, readKeys = setOf(items.last().key), hasAccess = true, now = now,
                    onClose = {}, onOpenItem = {}, onOpenApp = {}, onDismissItem = {}, onClearAll = {}, onAddRule = {}, onSetDefaultMode = {}, onRequestAccess = {},
                    openItem = { true }, runAction = { true }, sendReply = { _, _ -> true }, openApp = { true },
                )
            }
            if (widgetsOn && !first) PanelWidgetsPreview(slot)
        }
    }
}

/** The tag of the Preview's Widgets block (tests). */
const val PREVIEW_WIDGETS_TAG = "preview.panelWidgets"

/**
 * The Widgets section as the Preview shows it: its header with the section's place in the tab row, and two sample cards at the section's
 * card sizes (full width, the smallest and a taller height) with a dashed outline, read as one line by a screen reader.
 */
@Composable
internal fun PanelWidgetsPreview(slot: WidgetsSlot) {
    val lp = LocalLp.current
    val order = PanelSections.order(true, slot).joinToString(" · ") { L10n.tr(it.label) }
    androidx.compose.foundation.layout.Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp).testTag(PREVIEW_WIDGETS_TAG)
            .clearAndSetSemantics { contentDescription = L10n.tr("Widgets section, sample cards: %s", order) },
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
    ) {
        androidx.compose.material3.Text(L10n.tr("Widgets").uppercase(), color = lp.ink2, fontSize = 12.sp, letterSpacing = 1.2.sp)
        androidx.compose.material3.Text(order, color = lp.dim, fontSize = 11.sp)
        listOf(PanelWidgetSizing.MIN_DP, PanelWidgetSizing.MIN_DP * 1.4f).forEachIndexed { i, h ->
            val shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp)
            androidx.compose.foundation.layout.Box(
                Modifier.fillMaxWidth().height(h.dp).clip(shape).background(lp.surface)
                    .drawBehind {
                        drawRoundRect(lp.line, cornerRadius = androidx.compose.ui.geometry.CornerRadius(18.dp.toPx()),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(1.5.dp.toPx(), pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 6.dp.toPx()))))
                    },
                contentAlignment = androidx.compose.ui.Alignment.Center,
            ) { androidx.compose.material3.Text(L10n.tr("Sample widget %d", i + 1), color = lp.dim, fontSize = 13.sp) }
        }
    }
}

/** The made-up rows of the Comms preview: a message thread, a calendar reminder, an ongoing (pinned) player and a read alert. */
object CommsPreviewSamples {
    const val KEY_PREFIX = "preview.sample:"

    fun items(now: Long): List<HubNotification> {
        val m = 60_000L
        return listOf(
            HubNotification(
                id = "1", key = KEY_PREFIX + "msg", source = L10n.tr("Messages"), title = L10n.tr("Sample contact"), summary = L10n.tr("Sample message"),
                timestamp = now - 2 * m, packageName = "preview.sample.messages", appLabel = L10n.tr("Messages"), category = android.app.Notification.CATEGORY_MESSAGE,
                sender = L10n.tr("Sample contact"), conversation = L10n.tr("Sample contact"),
            ),
            HubNotification(
                id = "2", key = KEY_PREFIX + "cal", source = L10n.tr("Calendar"), title = L10n.tr("Sample event"), summary = L10n.tr("In 15 minutes"),
                timestamp = now - 9 * m, packageName = "preview.sample.calendar", appLabel = L10n.tr("Calendar"), category = android.app.Notification.CATEGORY_EVENT,
            ),
            HubNotification(
                id = "3", key = KEY_PREFIX + "play", source = L10n.tr("Music"), title = L10n.tr("Sample song"), summary = L10n.tr("Playing"),
                timestamp = now - 20 * m, packageName = "preview.sample.music", appLabel = L10n.tr("Music"), isOngoing = true, category = android.app.Notification.CATEGORY_TRANSPORT,
            ),
            HubNotification(
                id = "4", key = KEY_PREFIX + "alert", source = L10n.tr("System"), title = L10n.tr("Sample alert"), summary = L10n.tr("Sample message"),
                timestamp = now - 45 * m, packageName = "preview.sample.system", appLabel = L10n.tr("System"), unread = false,
            ),
        )
    }
}
