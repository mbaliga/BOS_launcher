package com.bos.sphere.feature.grid

import android.content.Context
import android.os.Process
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bos.sphere.core.catalog.Badge
import com.bos.sphere.core.catalog.BadgeSettings
import com.bos.sphere.core.catalog.Badges
import com.bos.sphere.core.data.AppEntry
import com.bos.sphere.core.data.HubNotification
import com.bos.sphere.core.data.NotificationProvider
import com.bos.sphere.core.design.HyleColors
import com.bos.sphere.core.design.L10n
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** HOM-09: one process-wide badge map derived from the notification feed (empty without notification access). */
internal object BadgeFeed {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val badges: StateFlow<Map<String, Badge>> = NotificationProvider.notifications
        .map { Badges.compute(Badges.forHub(it)) }
        .stateIn(scope, SharingStarted.Eagerly, emptyMap())
    val notifications: StateFlow<List<HubNotification>> = NotificationProvider.notifications
        .stateIn(scope, SharingStarted.Eagerly, emptyList())
}

/** The dot (with the count when [label] is not empty). */
@Composable
fun BadgeDot(label: String, modifier: Modifier = Modifier, size: Dp = 12.dp) {
    val showText = label.isNotEmpty()
    Box(
        modifier
            .defaultMinSize(minWidth = if (showText) 16.dp else size, minHeight = if (showText) 16.dp else size)
            .background(Color(0xFFE5484D), CircleShape)
            .padding(horizontal = if (showText) 4.dp else 0.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (showText) Text(label, color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

/** An [AppIcon] with a notification badge on its top-right corner when the app has unread notifications. */
@Composable
fun BadgedAppIcon(app: AppEntry, size: Dp, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val settings = remember(context) { BadgeSettings(context) }
    Box(modifier) {
        AppIcon(app, size, Modifier.size(size))
        if (settings.enabled && app.user == Process.myUserHandle()) {
            val map by BadgeFeed.badges.collectAsState()
            map[Badges.keyFor(app.packageName, 0)]?.let { b ->
                BadgeDot(b.label(settings.showCount), Modifier.align(Alignment.TopEnd).offset(x = 3.dp, y = (-3).dp))
            }
        }
    }
}

/**
 * Notification preview for the long-press / right-click menu of an app: newest few notifications
 * (title and text), or nothing when there are none or notification access is not granted.
 */
@Composable
fun NotificationPreview(app: AppEntry, modifier: Modifier = Modifier) {
    val all by BadgeFeed.notifications.collectAsState()
    val list = remember(all, app.packageName) { if (app.user == Process.myUserHandle()) Badges.preview(all, app.packageName, 4) else emptyList() }
    if (list.isEmpty()) return
    Column(modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 6.dp)) {
        Text(L10n.tr("Notifications"), color = HyleColors.InkDim, fontSize = 11.sp)
        Spacer(Modifier.height(4.dp))
        for (n in list) {
            Text(n.title.ifEmpty { n.appLabel }, color = HyleColors.InkBright, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (n.summary.isNotEmpty()) Text(n.summary, color = HyleColors.Ink, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(6.dp))
        }
    }
}
