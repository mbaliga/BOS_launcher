package com.bos.sphere.ui

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bos.sphere.core.data.SystemSnapshot
import com.bos.sphere.core.data.SystemStateLogic
import com.bos.sphere.core.data.SystemTileKind
import com.bos.sphere.core.design.L10n
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** STR-10: time, battery, connectivity and sound at the top of Launchpad. Tiles show state; tapping opens the system panel. */
@Composable
fun SystemStateRow(snapshot: SystemSnapshot, is24: Boolean, onOpenPanel: (SystemTileKind) -> Unit, modifier: Modifier = Modifier) {
    val tiles = SystemStateLogic.tiles(snapshot)
    val zone = remember { ZoneId.systemDefault() }
    Row(
        modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        tiles.forEach { t ->
            val value = if (t.kind == SystemTileKind.Time) {
                DateTimeFormatter.ofPattern(if (is24) "HH:mm" else "h:mm a", Locale.getDefault()).format(Instant.ofEpochMilli(snapshot.now).atZone(zone))
            } else L10n.tr(t.value)
            val detail = L10n.tr(t.detail.ifEmpty { t.kind.label })
            Tile(value.ifEmpty { "-" }, detail, onClick = { onOpenPanel(t.kind) }, description = "${L10n.tr(t.kind.label)}: $value")
        }
    }
}

@Composable
private fun RowScope.Tile(value: String, detail: String, onClick: () -> Unit, description: String) {
    Column(
        Modifier.weight(1f).heightIn(min = 52.dp).clip(RoundedCornerShape(14.dp)).background(LocalLp.current.surface)
            .semantics(mergeDescendants = true) { contentDescription = description; role = Role.Button }
            .clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
    ) {
        Text(value, color = LocalLp.current.ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        Text(detail, color = LocalLp.current.dim, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
    }
}

/** The live snapshot, refreshed every 20 seconds while composed. */
@Composable
fun rememberSystemSnapshot(): SystemSnapshot {
    val ctx = LocalContext.current
    val s by produceState(SystemStateLogic.read(ctx)) {
        while (true) { delay(20_000); value = SystemStateLogic.read(ctx) }
    }
    return s
}

/** Starts the public system panel for [kind]; tiles whose panel nothing handles simply do nothing. */
fun openSystemPanel(ctx: android.content.Context, kind: SystemTileKind) {
    SystemStateLogic.intentFor(ctx, kind)?.let { runCatching { ctx.startActivity(it) } }
}
