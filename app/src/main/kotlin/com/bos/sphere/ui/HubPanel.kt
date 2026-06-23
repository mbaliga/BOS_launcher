package com.bos.sphere.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bos.sphere.core.design.HyleColors

private data class HubNote(val source: String, val summary: String, val chips: List<String>)

private val DemoNotes = listOf(
    HubNote("Messages · Mom", "“Are you coming for dinner tonight?”", listOf("On my way", "Running late", "Dismiss")),
    HubNote("Calendar", "Standup starts in 15 minutes.", listOf("Join", "Snooze")),
    HubNote("App Store", "3 updates ready to install.", listOf("Update all", "Later")),
)

/**
 * The pull-down context Hub. Driven by [fraction] (0 hidden .. 1 fully down) from the surface
 * gesture; slides in from the top as a glass panel. Content is the "Needs you" demo set for now —
 * a real NotificationListenerService feed lands at M3.
 */
@Composable
fun HubPanel(fraction: Float, modifier: Modifier = Modifier) {
    if (fraction <= 0.001f) return
    var heightPx by remember { mutableStateOf(0) }
    var notes by remember { mutableStateOf(DemoNotes) }
    val contentAlpha = ((fraction - 0.12f) / 0.6f).coerceIn(0f, 1f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .onSizeChanged { heightPx = it.height }
            .graphicsLayer { translationY = (fraction - 1f) * heightPx }
            .padding(horizontal = 16.dp)
            .background(
                HyleColors.SurfaceTranslucent,
                RoundedCornerShape(bottomStart = 26.dp, bottomEnd = 26.dp),
            )
            .border(
                1.dp,
                HyleColors.Hairline,
                RoundedCornerShape(bottomStart = 26.dp, bottomEnd = 26.dp),
            ),
    ) {
        Column(
            modifier = Modifier
                .safeContentPadding()
                .padding(20.dp)
                .graphicsLayer { alpha = contentAlpha },
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                Box(modifier = Modifier.size(8.dp).background(HyleColors.Violet, CircleShape))
                Text(
                    "NEEDS YOU",
                    color = HyleColors.InkDim,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            notes.forEach { note ->
                HubNoteRow(note) { notes = notes - note }
            }
            Text(
                "9 more notifications · Show all",
                color = HyleColors.InkDim,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun HubNoteRow(note: HubNote, onDismiss: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(note.source, color = HyleColors.InkDim, fontSize = 11.sp, fontWeight = FontWeight.Medium)
        Text(note.summary, color = HyleColors.InkBright, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            note.chips.forEach { chip ->
                val ghost = chip == "Dismiss" || chip == "Later" || chip == "Snooze"
                Box(
                    modifier = Modifier
                        .clickable { if (ghost) onDismiss() }
                        .background(
                            if (ghost) HyleColors.Surface.copy(alpha = 0.4f) else HyleColors.Violet.copy(alpha = 0.18f),
                            RoundedCornerShape(14.dp),
                        )
                        .border(
                            1.dp,
                            if (ghost) HyleColors.Hairline else HyleColors.Violet.copy(alpha = 0.35f),
                            RoundedCornerShape(14.dp),
                        )
                        .padding(horizontal = 11.dp, vertical = 5.dp),
                ) {
                    Text(chip, color = if (ghost) HyleColors.InkDim else HyleColors.Ink, fontSize = 12.sp)
                }
            }
        }
    }
}
