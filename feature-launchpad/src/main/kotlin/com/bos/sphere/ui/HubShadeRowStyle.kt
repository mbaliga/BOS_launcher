package com.bos.sphere.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Wave 33 (commsDebt): the read/unread look of a Comms list row ([CommsRowTone], wave 30) for the app module's pull-down notification shade (`HubPanel`),
 * so both read the same: an unread row has a SemiBold full-ink headline, a secondary second line, an accent-ink source/time line and a solid accent dot; a
 * read row has the quieter normal-weight headline, dimmer lines and no dot. The shade is a dark panel, so the dark Comms palette is used.
 */
class HubShadeRowStyle internal constructor(
    val unread: Boolean, val headline: Color, val headlineWeight: FontWeight, val secondLine: Color, val meta: Color,
) {
    /** TalkBack's state text key (the English source string; the caller passes it through `L10n.tr`). */
    val stateKey: String get() = if (unread) "Unread" else "Read"
}

/** The style of a shade row in the given read state; it cross-fades when [unread] flips (instant with reduced motion). */
@Composable
fun rememberHubShadeRowStyle(unread: Boolean): HubShadeRowStyle {
    val t = rememberRowTone(unread, lp = LpDark)
    return HubShadeRowStyle(t.unread, t.title, t.titleWeight, t.sub, t.time)
}

/** The unread dot of a shade row (same size and accent ink as the Comms list's); place it in the row's start margin. */
@Composable
fun HubShadeUnreadDot(tag: String, modifier: Modifier = Modifier) {
    Box(modifier.testTag(tag).size(CommsStyle.UNREAD_DOT_DP.dp).background(unreadDotColor(LpDark), CircleShape))
}

/** The width of the margin a shade row reserves for the dot, so read and unread rows have the same layout. */
const val HUB_SHADE_DOT_MARGIN_DP = 16
