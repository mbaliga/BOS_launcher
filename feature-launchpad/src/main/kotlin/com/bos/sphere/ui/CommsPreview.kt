package com.bos.sphere.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
fun CommsPreview(theme: com.bos.sphere.core.data.LaunchpadTheme, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    remember(ctx) { LaunchpadSettings.init(ctx); GroupsSettings.init(ctx); true }
    val dark = when (theme) {
        com.bos.sphere.core.data.LaunchpadTheme.Dark -> true
        com.bos.sphere.core.data.LaunchpadTheme.Light -> false
        com.bos.sphere.core.data.LaunchpadTheme.System -> androidx.compose.foundation.isSystemInDarkTheme()
    }
    val prefs by LaunchpadSettings.prefs.collectAsState()
    val rules by LaunchpadSettings.rules.collectAsState()
    val now = remember { System.currentTimeMillis() }
    val items = remember(now) { CommsPreviewSamples.items(now) }
    CompositionLocalProvider(LocalLp provides if (dark) LpDark else LpLight) {
        androidx.compose.foundation.layout.Box(modifier.launchpadBackdrop(LocalLp.current.bg, LocalLp.current.surface)) {
            LaunchpadContent(
                items = items, rules = rules, prefs = prefs, readKeys = setOf(items.last().key), hasAccess = true, now = now,
                onClose = {}, onOpenItem = {}, onOpenApp = {}, onDismissItem = {}, onClearAll = {}, onAddRule = {}, onSetDefaultMode = {}, onRequestAccess = {},
                openItem = { true }, runAction = { true }, sendReply = { _, _ -> true }, openApp = { true },
            )
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
