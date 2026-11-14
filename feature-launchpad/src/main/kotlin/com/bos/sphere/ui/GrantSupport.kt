package com.bos.sphere.ui

import android.Manifest
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Process
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.bos.sphere.core.data.SphereNotificationListener
import com.bos.sphere.core.design.GrantKind

/** CFG-07: reading the current state of each grant and the public route to turn it on. */
object GrantSupport {
    private const val FILE = "grant_history"

    fun isGranted(ctx: Context, kind: GrantKind): Boolean = when (kind) {
        GrantKind.Notifications -> SphereNotificationListener.isEnabled(ctx)
        GrantKind.UsageAccess -> runCatching {
            val ops = ctx.getSystemService(AppOpsManager::class.java)
            ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName) == AppOpsManager.MODE_ALLOWED
        }.getOrDefault(false)
        GrantKind.Contacts -> ctx.checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
        GrantKind.Microphone -> ctx.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        GrantKind.Folders -> ctx.contentResolver.persistedUriPermissions.any { it.isReadPermission }
    }

    /** The settings page for kinds that are special accesses; null for runtime permissions and the folder picker. */
    fun settingsIntent(ctx: Context, kind: GrantKind): Intent? = when (kind) {
        GrantKind.Notifications -> SphereNotificationListener.notificationAccessIntent()
        GrantKind.UsageAccess -> Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
        GrantKind.Contacts, GrantKind.Microphone ->
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", ctx.packageName, null))
        GrantKind.Folders -> null
    }?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun wasAsked(ctx: Context, kind: GrantKind): Boolean =
        ctx.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean(kind.name, false)

    fun markAsked(ctx: Context, kind: GrantKind) {
        ctx.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putBoolean(kind.name, true).apply()
    }

    /** Opens the settings page for [kind] and records that the user was asked. */
    fun request(ctx: Context, kind: GrantKind) {
        markAsked(ctx, kind)
        settingsIntent(ctx, kind)?.let { runCatching { ctx.startActivity(it) } }
    }
}

/** True while [kind] is granted; re-checked each time the app resumes (the user comes back from Settings). */
@Composable
fun rememberGranted(kind: GrantKind): Boolean {
    val ctx = LocalContext.current
    var granted by remember(kind) { mutableStateOf(GrantSupport.isGranted(ctx, kind)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { granted = GrantSupport.isGranted(ctx, kind) }
    return granted
}

@Composable
fun rememberGrantAsked(kind: GrantKind): Boolean {
    val ctx = LocalContext.current
    var asked by remember(kind) { mutableStateOf(GrantSupport.wasAsked(ctx, kind)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { asked = GrantSupport.wasAsked(ctx, kind) }
    return asked
}

/**
 * CFG-07: shows the one-screen explanation before a feature asks for its grant. A caller does
 * `prompter.ask(GrantKind.Contacts) { launchSystemPrompt() }`; the system prompt only runs after the user
 * taps the explanation's button. Place [Host] once near the root.
 */
@androidx.compose.runtime.Stable
class GrantPrompter {
    private var pending by mutableStateOf<Pair<GrantKind, () -> Unit>?>(null)

    fun ask(kind: GrantKind, proceed: () -> Unit) { pending = kind to proceed }

    @Composable
    fun Host() {
        val ctx = LocalContext.current
        val p = pending ?: return
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { pending = null },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        ) {
            androidx.compose.foundation.layout.Box(Modifier.fillMaxSize().background(com.bos.sphere.core.design.HyleColors.Background)) {
                com.bos.sphere.core.design.GrantScreen(
                    kind = p.first, askedBefore = GrantSupport.wasAsked(ctx, p.first),
                    onEnable = { GrantSupport.markAsked(ctx, p.first); pending = null; p.second() },
                    onNotNow = { pending = null },
                )
            }
        }
    }
}

@Composable
fun rememberGrantPrompter(): GrantPrompter = remember { GrantPrompter() }

/**
 * STR-05: the media strip's grant prompt. Without notification access no media session can be read, so the strip
 * is replaced by a chip that opens the explanation, rather than showing nothing.
 */
@Composable
fun MediaStripGrantPrompt(visible: Boolean, modifier: Modifier = Modifier) {
    val granted = rememberGranted(GrantKind.Notifications)
    if (granted || !visible) return
    val ctx = LocalContext.current
    val prompter = rememberGrantPrompter()
    com.bos.sphere.core.design.GrantChip(GrantKind.Notifications, onClick = {
        prompter.ask(GrantKind.Notifications) { GrantSupport.request(ctx, GrantKind.Notifications) }
    }, modifier = modifier)
    prompter.Host()
}
