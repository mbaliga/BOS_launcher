package com.bos.sphere.core.data

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.os.UserManager
import android.provider.AlarmClock
import android.provider.MediaStore
import android.provider.Settings
import android.widget.Toast
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The intents behind app-backed actions, shared by the runner and the icon resolver. */
object ActionApps {
    fun intentFor(id: CornerActionId): Intent? = when (id) {
        CornerActionId.Music -> category(Intent.CATEGORY_APP_MUSIC)
        CornerActionId.Camera -> Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
        CornerActionId.Phone -> Intent(Intent.ACTION_DIAL)
        CornerActionId.Messages -> category(Intent.CATEGORY_APP_MESSAGING)
        CornerActionId.Browser -> category(Intent.CATEGORY_APP_BROWSER)
        CornerActionId.Email -> category(Intent.CATEGORY_APP_EMAIL)
        CornerActionId.Maps -> category(Intent.CATEGORY_APP_MAPS)
        CornerActionId.Calendar -> category(Intent.CATEGORY_APP_CALENDAR)
        CornerActionId.Gallery -> category(Intent.CATEGORY_APP_GALLERY)
        CornerActionId.Contacts -> category(Intent.CATEGORY_APP_CONTACTS)
        CornerActionId.Calculator -> category(Intent.CATEGORY_APP_CALCULATOR)
        CornerActionId.Clock -> Intent(AlarmClock.ACTION_SHOW_ALARMS)
        CornerActionId.Assistant -> Intent(Intent.ACTION_ASSIST)
        CornerActionId.SystemSettings -> Intent(Settings.ACTION_SETTINGS)
        else -> null
    }

    private fun category(c: String) = Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, c)

    /** True when the system would show its own "Complete action using" chooser (several handlers, none preferred). */
    fun needsChoice(context: Context, id: CornerActionId): Boolean {
        val intent = intentFor(id) ?: return false
        val pm = context.packageManager
        val best = pm.resolveActivity(intent, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo
        if (best != null && best.packageName != "android") return false
        return pm.queryIntentActivities(intent, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY).size > 1
    }

    /** Actions where the user may prefer to pick one specific app (the system would ask, or pick for them). */
    val appCapable: Set<CornerActionId> = setOf(
        CornerActionId.Music, CornerActionId.Camera, CornerActionId.Phone, CornerActionId.Messages, CornerActionId.Browser,
        CornerActionId.Email, CornerActionId.Maps, CornerActionId.Calendar, CornerActionId.Gallery, CornerActionId.Contacts,
        CornerActionId.Calculator, CornerActionId.Clock,
    )

    /** The app that would handle [id] right now, or null when the system would show a chooser / nothing handles it. */
    fun resolve(context: Context, id: CornerActionId): ComponentName? {
        ActionPrefs.get(context, id)?.let { key ->
            val at = key.lastIndexOf('#')
            if (at > 0) ComponentName.unflattenFromString(key.substring(0, at))?.let { return it }
        }
        val intent = intentFor(id) ?: return null
        val pm = context.packageManager
        val best = pm.resolveActivity(intent, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo
        // The chooser lives in the "android" package; fall back to the first real handler.
        val info = if (best != null && best.packageName != "android") best
        else pm.queryIntentActivities(intent, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY).firstOrNull()?.activityInfo
        return info?.let { ComponentName(it.packageName, it.name) }
    }
}

/** The app the user picked for an app-backed action ([AppEntry.key]); null = let the system decide. */
object ActionPrefs {
    private fun sp(c: Context) = c.applicationContext.getSharedPreferences("action_apps", Context.MODE_PRIVATE)
    fun get(c: Context, id: CornerActionId): String? = sp(c).getString(id.name, null)
    fun set(c: Context, id: CornerActionId, key: String?) {
        sp(c).edit().apply { if (key == null) remove(id.name) else putString(id.name, key) }.apply()
    }
}

/** Which installed apps are good candidates for an app-backed action, so the picker can lead with them. */
object ActionCandidates {
    private val knownMusic = setOf(
        "com.apple.android.music", "com.spotify.music", "org.videolan.vlc", "com.google.android.apps.youtube.music",
        "com.amazon.mp3", "com.aspiro.tidal", "deezer.android.app", "com.soundcloud.android", "com.maxmpz.audioplayer",
        "code.name.monkey.retromusic", "com.android.music", "org.lineageos.eleven", "com.google.android.music",
    )

    fun packages(context: Context, id: CornerActionId): Set<String> {
        val pm = context.packageManager
        val out = HashSet<String>()
        fun add(i: Intent) = pm.queryIntentActivities(i, android.content.pm.PackageManager.MATCH_ALL).forEach { out += it.activityInfo.packageName }
        ActionApps.intentFor(id)?.let(::add)
        if (id == CornerActionId.Music) {
            out += knownMusic.filter { runCatching { pm.getPackageInfo(it, 0) }.isSuccess }
            add(Intent(Intent.ACTION_VIEW).setDataAndType(android.net.Uri.parse("content://media/external/audio/media/1"), "audio/*"))
            // Anything that exposes a media browser or takes media buttons is a player.
            pm.queryIntentServices(Intent("android.media.browse.MediaBrowserService"), android.content.pm.PackageManager.MATCH_ALL)
                .forEach { out += it.serviceInfo.packageName }
            pm.queryBroadcastReceivers(Intent(Intent.ACTION_MEDIA_BUTTON), android.content.pm.PackageManager.MATCH_ALL)
                .forEach { out += it.activityInfo.packageName }
        }
        return out
    }
}

/** True if running [id] needs to start an activity (as opposed to toggling a system service). */
fun CornerActionId.opensUi(): Boolean = when (this) {
    CornerActionId.Flashlight, CornerActionId.RingerCycle, CornerActionId.RingerNormal,
    CornerActionId.RingerVibrate, CornerActionId.RingerSilent, CornerActionId.DoNotDisturb -> false
    else -> true
}

/**
 * Executes corner / palette / gesture items. External actions resolve to system intents or
 * system services (ringer, torch, DND); [CornerActionId.internal] ones are handed to
 * [onInternal] (the home UI opens its drawer / settings / hub; the background service forwards
 * them to the activity). Usable from an Activity or a Service context.
 */
class ActionRunner(
    context: Context,
    private val onInternal: (CornerActionId) -> Unit,
    private val onAppLaunched: (String) -> Unit = {},
    /** Asked when an app-backed action has no chosen app and the system would show its own chooser. Null = let the system do it. */
    private val onPickApp: ((CornerActionId) -> Unit)? = null,
) {
    private val ctx = context.applicationContext

    /** Routes app launches through the launch layer (WSP-01); returns whether the app started. Null keeps the direct path. */
    var launchHook: ((String) -> Boolean)? = null
    private val launcherApps = ctx.getSystemService(LauncherApps::class.java)
    private val userManager = ctx.getSystemService(UserManager::class.java)
    private val audio = ctx.getSystemService(AudioManager::class.java)
    private val notifications = ctx.getSystemService(NotificationManager::class.java)

    fun run(item: CornerItem) {
        when (item) {
            is CornerItem.Action -> run(item.id)
            is CornerItem.App -> launchKey(item.key)
        }
    }

    fun run(id: CornerActionId) {
        if (id.internal) {
            onInternal(id)
            return
        }
        if (id in ActionApps.appCapable) {
            ActionPrefs.get(ctx, id)?.let { if (launchKey(it)) return }
            if (onPickApp != null && ActionApps.needsChoice(ctx, id)) { onPickApp.invoke(id); return }
        }
        when (id) {
            CornerActionId.Music -> category(Intent.CATEGORY_APP_MUSIC, id)
            CornerActionId.Camera -> start(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA), id)
            CornerActionId.Phone -> start(Intent(Intent.ACTION_DIAL), id)
            CornerActionId.Messages -> category(Intent.CATEGORY_APP_MESSAGING, id)
            CornerActionId.Browser -> category(Intent.CATEGORY_APP_BROWSER, id)
            CornerActionId.Email -> category(Intent.CATEGORY_APP_EMAIL, id)
            CornerActionId.Maps -> category(Intent.CATEGORY_APP_MAPS, id)
            CornerActionId.Calendar -> category(Intent.CATEGORY_APP_CALENDAR, id)
            CornerActionId.Gallery -> category(Intent.CATEGORY_APP_GALLERY, id)
            CornerActionId.Contacts -> category(Intent.CATEGORY_APP_CONTACTS, id)
            CornerActionId.Calculator -> category(Intent.CATEGORY_APP_CALCULATOR, id)
            CornerActionId.Clock -> start(Intent(AlarmClock.ACTION_SHOW_ALARMS), id)
            CornerActionId.Assistant ->
                if (!start(Intent(Intent.ACTION_VOICE_COMMAND), null)) start(Intent(Intent.ACTION_ASSIST), id)
            CornerActionId.SystemSettings -> start(Intent(Settings.ACTION_SETTINGS), id)
            CornerActionId.Flashlight -> Torch.toggle(ctx)?.let { toast(if (it) "Flashlight on" else "Flashlight off") }
                ?: toast("No flashlight on this device")
            CornerActionId.RingerNormal -> setRinger(AudioManager.RINGER_MODE_NORMAL)
            CornerActionId.RingerVibrate -> setRinger(AudioManager.RINGER_MODE_VIBRATE)
            CornerActionId.RingerSilent -> setRinger(AudioManager.RINGER_MODE_SILENT)
            CornerActionId.RingerCycle -> setRinger(
                when (audio.ringerMode) {
                    AudioManager.RINGER_MODE_NORMAL -> AudioManager.RINGER_MODE_VIBRATE
                    AudioManager.RINGER_MODE_VIBRATE -> AudioManager.RINGER_MODE_SILENT
                    else -> AudioManager.RINGER_MODE_NORMAL
                },
            )
            CornerActionId.DoNotDisturb -> toggleDnd()
            else -> Unit
        }
    }

    /** Launches the app behind an [AppEntry.key] (`component#userHash`). */
    fun launchKey(key: String): Boolean {
        launchHook?.let { hook -> return hook(key).also { if (it) onAppLaunched(key) else toast("App not available") } }
        val hashAt = key.lastIndexOf('#')
        if (hashAt <= 0) return false
        val component = ComponentName.unflattenFromString(key.substring(0, hashAt)) ?: return false
        val userHash = key.substring(hashAt + 1).toIntOrNull() ?: return false
        val user = userManager.userProfiles.firstOrNull { it.hashCode() == userHash } ?: return false
        return runCatching {
            launcherApps.startMainActivity(component, user, null, null)
            onAppLaunched(key)
        }.isSuccess.also { if (!it) toast("App not available") }
    }

    private fun category(category: String, id: CornerActionId) {
        start(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, category), id)
    }

    private fun start(intent: Intent, failureFor: CornerActionId?): Boolean {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val ok = runCatching { ctx.startActivity(intent) }.isSuccess
        if (!ok && failureFor != null) toast("No app for ${failureFor.label}")
        return ok
    }

    private fun setRinger(mode: Int) {
        // Leaving / entering silent touches Do-Not-Disturb, which needs policy access.
        val touchesDnd = mode == AudioManager.RINGER_MODE_SILENT || audio.ringerMode == AudioManager.RINGER_MODE_SILENT
        if (touchesDnd && !notifications.isNotificationPolicyAccessGranted) {
            requestPolicyAccess()
            return
        }
        val ok = runCatching { audio.ringerMode = mode }.isSuccess
        if (!ok) {
            requestPolicyAccess()
            return
        }
        toast(
            when (mode) {
                AudioManager.RINGER_MODE_SILENT -> "Silent"
                AudioManager.RINGER_MODE_VIBRATE -> "Vibrate"
                else -> "Ring"
            },
        )
    }

    private fun toggleDnd() {
        if (!notifications.isNotificationPolicyAccessGranted) {
            requestPolicyAccess()
            return
        }
        val on = notifications.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL
        notifications.setInterruptionFilter(
            if (on) NotificationManager.INTERRUPTION_FILTER_ALL else NotificationManager.INTERRUPTION_FILTER_PRIORITY,
        )
        toast(if (on) "Do not disturb off" else "Do not disturb on")
    }

    private fun requestPolicyAccess() {
        toast("Allow Do Not Disturb access for Liftoff to change silent mode")
        start(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS), null)
    }

    private fun toast(text: String) {
        val show = { Toast.makeText(ctx, text, Toast.LENGTH_SHORT).show() }
        if (Looper.myLooper() == Looper.getMainLooper()) show() else Handler(Looper.getMainLooper()).post(show)
    }
}

/** Flashlight state + toggle (no CAMERA permission needed for the torch). */
object Torch {
    private val _on = MutableStateFlow(false)
    val on: StateFlow<Boolean> = _on.asStateFlow()

    @Volatile private var cameraId: String? = null
    @Volatile private var registered = false

    /** Registers the torch callback early so [on] reflects the real state before the first toggle. */
    fun init(context: Context) { ensure(context) }

    private fun ensure(context: Context): CameraManager? {
        val cm = context.applicationContext.getSystemService(CameraManager::class.java) ?: return null
        if (cameraId == null) {
            cameraId = runCatching {
                cm.cameraIdList.firstOrNull { id ->
                    val c = cm.getCameraCharacteristics(id)
                    c.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true &&
                        c.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
                } ?: cm.cameraIdList.firstOrNull { id ->
                    cm.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                }
            }.getOrNull()
        }
        if (!registered && cameraId != null) {
            registered = true
            cm.registerTorchCallback(
                object : CameraManager.TorchCallback() {
                    override fun onTorchModeChanged(id: String, enabled: Boolean) {
                        if (id == cameraId) _on.value = enabled
                    }
                },
                Handler(Looper.getMainLooper()),
            )
        }
        return cm
    }

    /** Toggles the torch; returns the new state, or null if there is no usable flash. */
    fun toggle(context: Context): Boolean? {
        val cm = ensure(context) ?: return null
        val id = cameraId ?: return null
        val next = !_on.value
        return if (runCatching { cm.setTorchMode(id, next) }.isSuccess) {
            _on.value = next
            next
        } else {
            null
        }
    }
}
