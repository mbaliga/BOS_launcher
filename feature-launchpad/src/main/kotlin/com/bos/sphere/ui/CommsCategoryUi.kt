package com.bos.sphere.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bos.sphere.core.data.CommsCategory
import com.bos.sphere.core.data.HubNotification
import com.bos.sphere.core.design.L10n

/**
 * Wave 53 (commsCategories, STR-16 and STR-17): the words, glyphs and small composables of the Messages / Notifications / Alerts views and of the
 * pinned block. The classification itself is [com.bos.sphere.core.data.NotificationClass] (core-data).
 */
internal fun categoryLabel(c: CommsCategory): String = L10n.tr(when (c) {
    CommsCategory.Messages -> "Messages"
    CommsCategory.Notifications -> "Notifications"
    CommsCategory.Alerts -> "Alerts"
})

/** The one-line definition shown under the category in the menu (the long form is in docs/commsCategories.md and the Settings hint). */
internal fun categoryCaption(c: CommsCategory): String = L10n.tr(when (c) {
    CommsCategory.Messages -> "Chats, SMS, email and calls to you"
    CommsCategory.Notifications -> "Promos, news and app updates"
    CommsCategory.Alerts -> "Codes, alarms, reminders, warnings"
})

/** The chip that sends a row to [c]: "Make this an Alert" and its two siblings. */
internal fun categoryChipLabel(c: CommsCategory): String = L10n.tr(when (c) {
    CommsCategory.Messages -> "Make this a Message"
    CommsCategory.Notifications -> "Make this a Notification"
    CommsCategory.Alerts -> "Make this an Alert"
})

internal fun categoryToast(c: CommsCategory): String = L10n.tr(when (c) {
    CommsCategory.Messages -> "Treated as Messages from now on"
    CommsCategory.Notifications -> "Treated as Notifications from now on"
    CommsCategory.Alerts -> "Treated as Alerts from now on"
})

/** A 24-unit line glyph: a speech bubble, a bell, and a bell with an exclamation mark. */
@Composable
internal fun CategoryGlyph(c: CommsCategory, color: Color, glyphDp: Float = CommsStyle.SHOW_ICON_DP) {
    Canvas(Modifier.size(glyphDp.dp)) {
        val k = size.minDimension / 24f
        val st = Stroke(2f * k, cap = StrokeCap.Round, join = StrokeJoin.Round)
        fun p(x: Float, y: Float) = Offset(x * k, y * k)
        when (c) {
            CommsCategory.Messages -> {
                val bubble = Path().apply {
                    addRoundRect(androidx.compose.ui.geometry.RoundRect(3f * k, 4f * k, 21f * k, 17f * k, CornerRadius(3f * k)))
                    moveTo(8f * k, 17f * k); lineTo(8f * k, 21f * k); lineTo(12.5f * k, 17f * k)
                }
                drawPath(bubble, color, style = st)
            }
            CommsCategory.Notifications, CommsCategory.Alerts -> {
                val bell = Path().apply {
                    moveTo(5.5f * k, 17f * k); lineTo(5.5f * k, 11f * k)
                    cubicTo(5.5f * k, 7.4f * k, 8.4f * k, 4.5f * k, 12f * k, 4.5f * k)
                    cubicTo(15.6f * k, 4.5f * k, 18.5f * k, 7.4f * k, 18.5f * k, 11f * k)
                    lineTo(18.5f * k, 17f * k); lineTo(20.5f * k, 18.5f * k); lineTo(3.5f * k, 18.5f * k); close()
                }
                drawPath(bell, color, style = st)
                drawLine(color, p(10f, 21.5f), p(14f, 21.5f), 2f * k, StrokeCap.Round)
                if (c == CommsCategory.Alerts) {
                    drawLine(color, p(12f, 8f), p(12f, 12.2f), 2f * k, StrokeCap.Round)
                    drawCircle(color, 1.1f * k, p(12f, 15f))
                }
            }
        }
    }
}

/** A pushpin, drawn in code like the other Comms glyphs. */
@Composable
internal fun PinGlyph(color: Color, glyphDp: Float = 14f, modifier: Modifier = Modifier) {
    Canvas(modifier.size(glyphDp.dp)) {
        val k = size.minDimension / 24f
        val st = Stroke(2f * k, cap = StrokeCap.Round, join = StrokeJoin.Round)
        val head = Path().apply {
            moveTo(9f * k, 3f * k); lineTo(15f * k, 3f * k); lineTo(14.2f * k, 9f * k)
            lineTo(18f * k, 13f * k); lineTo(6f * k, 13f * k); lineTo(9.8f * k, 9f * k); close()
        }
        drawPath(head, color, style = st)
        drawLine(color, Offset(12f * k, 13f * k), Offset(12f * k, 21f * k), 2f * k, StrokeCap.Round)
    }
}

/**
 * A category row of the menu's Show list: its glyph, its name with the one-line definition below, and the unread count as a small badge (the accent
 * fill when there is something unread, a dim zero otherwise). A radio like the app rows: selecting it filters the list.
 */
@Composable
internal fun CategoryShowRow(c: CommsCategory, selected: Boolean, unread: Int, onClick: () -> Unit) {
    val ink = LocalLp.current
    val tag = "lp.menu.cat.${c.id}"
    Row(
        Modifier.fillMaxWidth().heightIn(min = (CommsStyle.SHOW_ROW_DP + 14f).dp)
            .semantics(mergeDescendants = true) {
                this.selected = selected; role = Role.RadioButton
                contentDescription = categoryLabel(c) + ", " + categoryCaption(c) + ", " + L10n.tr("%d unread", unread)
            }
            .clickable(onClick = onClick).testTag(tag).padding(start = CommsStyle.H_PAD_DP.dp, end = CommsStyle.PANEL_END_PAD_DP.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(CommsStyle.SHOW_ICON_DP.dp).testTag("$tag.icon"), Alignment.Center) { CategoryGlyph(c, if (selected) ink.ink else ink.glyphDim) }
        Spacer(Modifier.width(CommsStyle.SHOW_ICON_GAP_DP.dp))
        Column(Modifier.weight(1f)) {
            Text(categoryLabel(c), color = ink.ink, fontSize = CommsStyle.SHOW_SP.sp, fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.testTag("$tag.label"))
            Text(categoryCaption(c), color = ink.dim, fontSize = CommsStyle.NOTE_SP.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.testTag("$tag.caption"))
        }
        Spacer(Modifier.width(8.dp))
        Text(
            unread.toString(), color = if (unread > 0) Color.White else ink.count, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1,
            modifier = Modifier.then(if (unread > 0) Modifier.clip(RoundedCornerShape(50)).background(BlueDark).padding(horizontal = 8.dp, vertical = 2.dp) else Modifier).testTag("$tag.count"),
        )
    }
}

/** How a row in the pinned block got there: the system keeps it ([Ongoing]) or the owner's Pin rule does ([Rule]). */
internal enum class PinKind { Ongoing, Rule }

/** The pin in the left margin of a pinned row, where an unread row has its dot (a pinned row is never unread). */
@Composable
internal fun PinMargin(tag: String, modifier: Modifier = Modifier) {
    Box(modifier.offset(x = (CommsStyle.UNREAD_DOT_CENTRE_X_DP - 6f).dp).testTag(tag)) { PinGlyph(LocalLp.current.dim, 12f) }
}

/**
 * The pinned block's compact form, used when more than [com.bos.sphere.core.data.NotificationClass.PINNED_COMPACT_AFTER] notifications are pinned:
 * one row with the apps' icons, "N pinned", and Show all (Show fewer when open).
 */
@Composable
internal fun PinnedCompactRow(items: List<HubNotification>, expanded: Boolean, onToggle: () -> Unit) {
    val ink = LocalLp.current
    val action = L10n.tr(if (expanded) "Show fewer" else "Show all")
    Row(
        Modifier.fillMaxWidth().heightIn(min = commsTarget()).clickable(role = Role.Button, onClick = onToggle)
            .semantics(mergeDescendants = true) { contentDescription = L10n.tr("%d pinned", items.size) + ", " + action }
            .padding(start = CommsStyle.H_PAD_DP.dp, end = CommsStyle.END_PAD_DP.dp, top = 6.dp, bottom = 6.dp).testTag("lp.pinned.compact"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy((-4).dp)) {
            items.distinctBy { it.packageName }.take(5).forEach { n -> AppIcon(n.packageName, n.appLabel.ifBlank { n.title }, 24) }
        }
        Spacer(Modifier.width(12.dp))
        Text(L10n.tr("%d pinned", items.size), color = ink.ink2, fontSize = CommsStyle.BODY_SP.sp, maxLines = 1, modifier = Modifier.weight(1f))
        Text(action, color = BlueLight, fontSize = CommsStyle.CHIP_SP.sp, fontWeight = FontWeight.Medium, maxLines = 1, modifier = Modifier.testTag("lp.pinned.toggle"))
    }
}
