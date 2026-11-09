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
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bos.sphere.core.data.HubNotification
import com.bos.sphere.core.data.NotificationProvider
import com.bos.sphere.core.data.SphereNotificationListener
import com.bos.sphere.core.design.HyleColors
import com.bos.sphere.core.design.L10n
import kotlinx.coroutines.launch

/**
 * The pull-down context Hub: the quick notification shade used when no left page (Launchpad) is
 * on. Driven by [fraction] (0 hidden .. 1 fully down); slides in from the top as a glass panel.
 * Shows the newest system notifications, or how to grant notification access when it is missing.
 */
@Composable
fun HubPanel(fraction: Float, modifier: Modifier = Modifier, onRequestAccess: () -> Unit = {}) {
    if (fraction <= 0.001f) return
    val context = LocalContext.current
    val notifications by NotificationProvider.notifications.collectAsStateWithLifecycle(initialValue = emptyList())
    val hasAccess = SphereNotificationListener.isEnabled(context)
    val scope = rememberCoroutineScope()

    var heightPx by remember { mutableStateOf(0) }
    var dismissedKeys by remember { mutableStateOf(setOf<String>()) }
    val live = notifications.filter { it.key !in dismissedKeys }
    val visibleNotes = live.take(3)
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
                    L10n.tr("Notifications").uppercase(),
                    color = HyleColors.InkDim,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            when {
                !hasAccess -> {
                    Text(
                        L10n.tr("Allow notification access so your notifications can show here."),
                        color = HyleColors.Ink, fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp),
                    )
                    Row(Modifier.padding(top = 8.dp)) { HubActionButton(L10n.tr("Notification access"), onClick = onRequestAccess) }
                }
                live.isEmpty() -> Text(L10n.tr("Nothing new."), color = HyleColors.InkDim, fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp))
                else -> {
                    visibleNotes.forEach { note ->
                        HubNotificationRow(
                            note,
                            onAction = { act -> scope.launch { runCatching { act() } } },
                            onDismiss = { dismissedKeys += note.key },
                        )
                    }
                    if (live.size > visibleNotes.size) {
                        Text(
                            L10n.tr("%d more", live.size - visibleNotes.size),
                            color = HyleColors.InkDim,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HubNotificationRow(notif: HubNotification, onAction: (suspend () -> Unit) -> Unit, onDismiss: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(notif.source, color = HyleColors.InkDim, fontSize = 11.sp, fontWeight = FontWeight.Medium)
        Text(notif.title, color = HyleColors.InkBright, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (notif.summary.isNotEmpty()) {
            Text(notif.summary, color = HyleColors.Ink, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            notif.actions.take(2).forEach { action ->
                HubActionButton(action.label) { onAction(action.action) }
            }
            HubActionButton(L10n.tr("Dismiss"), isGhost = true) { onDismiss() }
        }
    }
}

@Composable
private fun HubActionButton(label: String, isGhost: Boolean = false, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .defaultMinSize(minHeight = 40.dp)
            .clickable(onClick = onClick, role = Role.Button)
            .background(
                if (isGhost) HyleColors.Surface.copy(alpha = 0.4f) else HyleColors.Violet.copy(alpha = 0.18f),
                RoundedCornerShape(14.dp),
            )
            .border(
                1.dp,
                if (isGhost) HyleColors.Hairline else HyleColors.Violet.copy(alpha = 0.35f),
                RoundedCornerShape(14.dp),
            )
            .padding(horizontal = 11.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = if (isGhost) HyleColors.InkDim else HyleColors.Ink, fontSize = 12.sp)
    }
}
