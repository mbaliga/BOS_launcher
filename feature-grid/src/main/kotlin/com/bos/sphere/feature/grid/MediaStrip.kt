package com.bos.sphere.feature.grid

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.collectAsState
import com.bos.sphere.core.data.MediaInfo
import com.bos.sphere.core.data.MediaNow
import com.bos.sphere.core.design.HyleColors

/**
 * A compact glass pill for the home screen: the current track on the left (tap to open the player)
 * and previous / play-pause / next on the right. Show it only while a media session is active.
 */
@Composable
fun MediaStrip(
    info: MediaInfo,
    onPrev: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth().heightIn(min = 52.dp),
        shape = RoundedCornerShape(28.dp),
        color = HyleColors.SurfaceTranslucent,
        border = BorderStroke(1.dp, HyleColors.Hairline),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 4.dp, end = 8.dp)) {
            Column(
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .clickable(onClickLabel = "Open player", onClick = onOpen)
                    .padding(horizontal = 12.dp),
            ) {
                Text(info.title, color = HyleColors.InkBright, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (info.artist.isNotBlank()) {
                    Text(info.artist, color = HyleColors.InkDim, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            MediaButton(MediaGlyphs.Previous, "Previous track", onPrev, enabled = info.canPrev)
            MediaButton(
                if (info.playing) MediaGlyphs.Pause else MediaGlyphs.Play,
                if (info.playing) "Pause" else "Play",
                onPlayPause, emphasised = true,
            )
            MediaButton(MediaGlyphs.Next, "Next track", onNext, enabled = info.canNext)
        }
    }
}

@Composable
private fun MediaButton(glyph: ImageVector, label: String, onClick: () -> Unit, enabled: Boolean = true, emphasised: Boolean = false) {
    val shape = CircleShape
    androidx.compose.foundation.layout.Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(40.dp)
            .clip(shape)
            .then(if (emphasised) Modifier.androidxBackground(HyleColors.Violet) else Modifier)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label }
            .alpha(if (enabled) 1f else 0.35f),
    ) {
        Icon(glyph, null, tint = if (emphasised) HyleColors.OnAccent else HyleColors.Ink, modifier = Modifier.size(if (emphasised) 20.dp else 18.dp))
    }
}

private fun Modifier.androidxBackground(color: androidx.compose.ui.graphics.Color): Modifier =
    this.then(Modifier.background(color))

/** The current media, or null while nothing is playing / paused (or when [enabled] is false). Starts and stops [MediaNow] with the composition. */
@Composable
fun rememberMediaInfo(enabled: Boolean): State<MediaInfo?> {
    val context = LocalContext.current
    DisposableEffect(enabled) {
        if (enabled) MediaNow.start(context)
        onDispose { if (enabled) MediaNow.stop() }
    }
    val flow = remember { MediaNow.info }
    val state = flow.collectAsState()
    return if (enabled) state else remember { androidx.compose.runtime.mutableStateOf<MediaInfo?>(null) }
}
