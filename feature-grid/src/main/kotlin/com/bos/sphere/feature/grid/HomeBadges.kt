package com.bos.sphere.feature.grid

import android.content.Context
import android.os.Process
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bos.sphere.core.catalog.AppMark
import com.bos.sphere.core.catalog.AppPresentation
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
    val prefs by remember(context) { com.bos.sphere.core.data.LauncherStores.get(context).prefs.prefs }.collectAsState()
    Box(modifier) {
        // HOM-10 / HOM-06: a suspended, archived or paused-profile app stays on home, dimmed and marked.
        val quiet by com.bos.sphere.core.catalog.WorkProfileState.quietProfileIds.collectAsState()
        val look = AppPresentation.of(app.suspended, app.archived, app.user.hashCode() in quiet)
        AppIcon(app, size, Modifier.size(size).then(if (look.mark != AppMark.None) Modifier.alpha(look.alpha) else Modifier))
        if (look.mark != AppMark.None) AppMarkGlyph(look.mark, Modifier.align(Alignment.BottomStart).offset(x = (-2).dp, y = 2.dp))
        // HOM-06: where the icon lost the system's profile badge (override, pack, themed), draw it here.
        val kinds by com.bos.sphere.core.catalog.WorkProfileState.profileKinds.collectAsState()
        val lost = IconCache.systemBadgeLost(LocalIconSources.current.cacheKey(app))
        val own = com.bos.sphere.core.catalog.ProfileBadgeRule.decide(kinds[app.user.hashCode()] ?: com.bos.sphere.core.catalog.ProfileKind.Personal, lost)
        if (own != com.bos.sphere.core.catalog.ProfileBadgeKind.None) ProfileBadgeGlyph(own, Modifier.align(Alignment.BottomEnd).offset(x = 2.dp, y = 2.dp))
        if (prefs.badgesOn) {
            // HOM-09: the notification feed is per profile, so a work app is badged by its own profile's notifications.
            val map by BadgeFeed.badges.collectAsState()
            map[Badges.keyFor(app.packageName, app.user.hashCode())]?.let { b ->
                BadgeDot(b.label(prefs.badgeCount), Modifier.align(Alignment.TopEnd).offset(x = 3.dp, y = (-3).dp))
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
    val list = remember(all, app.packageName) { Badges.previewFor(all, app.packageName, app.user.hashCode(), 4) }
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

/** HOM-06: the profile badge drawn by the launcher: a briefcase for work, a padlock for private space, drawn as shapes. */
@Composable
fun ProfileBadgeGlyph(kind: com.bos.sphere.core.catalog.ProfileBadgeKind, modifier: Modifier = Modifier) {
    val desc = if (kind == com.bos.sphere.core.catalog.ProfileBadgeKind.Private) L10n.tr("Private space") else L10n.tr("Work profile")
    androidx.compose.foundation.Canvas(
        modifier.size(16.dp).background(Color(0xFF1F6FEB), CircleShape).semantics { contentDescription = desc },
    ) {
        val w = size.width; val h = size.height
        val c = Color.White
        if (kind == com.bos.sphere.core.catalog.ProfileBadgeKind.Private) {
            drawRoundRect(c, androidx.compose.ui.geometry.Offset(w * 0.28f, h * 0.48f), androidx.compose.ui.geometry.Size(w * 0.44f, h * 0.32f), androidx.compose.ui.geometry.CornerRadius(w * 0.05f))
            drawArc(c, 180f, 180f, false, androidx.compose.ui.geometry.Offset(w * 0.34f, h * 0.22f), androidx.compose.ui.geometry.Size(w * 0.32f, h * 0.5f), style = androidx.compose.ui.graphics.drawscope.Stroke(w * 0.07f))
        } else {
            drawRoundRect(c, androidx.compose.ui.geometry.Offset(w * 0.24f, h * 0.38f), androidx.compose.ui.geometry.Size(w * 0.52f, h * 0.36f), androidx.compose.ui.geometry.CornerRadius(w * 0.06f))
            drawRoundRect(c, androidx.compose.ui.geometry.Offset(w * 0.38f, h * 0.24f), androidx.compose.ui.geometry.Size(w * 0.24f, h * 0.2f), androidx.compose.ui.geometry.CornerRadius(w * 0.04f), style = androidx.compose.ui.graphics.drawscope.Stroke(w * 0.07f))
        }
    }
}

/** HOM-10 / HOM-06: the small mark on a dimmed icon (pause bars for paused or suspended, a down arrow for archived). */
@Composable
fun AppMarkGlyph(mark: AppMark, modifier: Modifier = Modifier) {
    val (glyph, desc) = when (mark) {
        AppMark.Archived -> "\u2193" to L10n.tr("Archived")
        AppMark.ProfilePaused -> "\u23F8" to L10n.tr("Paused")
        AppMark.Suspended -> "\u23F8" to L10n.tr("Suspended")
        AppMark.None -> return
    }
    Box(
        modifier.size(16.dp).background(Color(0xE6101426), CircleShape)
            .semantics { contentDescription = desc },
        contentAlignment = Alignment.Center,
    ) { Text(glyph, color = Color.White, fontSize = 10.sp) }
}

/**
 * HOM-06: the work profile pause chip on the home surface. Shown only while a work profile exists;
 * "Resume work apps" while paused, "Pause work apps" otherwise.
 */
@Composable
fun WorkPauseChip(paused: Boolean?, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val label = com.bos.sphere.core.catalog.WorkProfileState.chipLabel(paused) ?: return
    Text(
        L10n.tr(label), color = Color.White, fontSize = 12.sp,
        modifier = modifier.background(if (paused == true) Color(0xE6B3541E) else Color(0xCC101426), androidx.compose.foundation.shape.RoundedCornerShape(50))
            .clickable(onClick = onToggle).padding(horizontal = 14.dp, vertical = 8.dp)
            .semantics { role = Role.Button },
    )
}
