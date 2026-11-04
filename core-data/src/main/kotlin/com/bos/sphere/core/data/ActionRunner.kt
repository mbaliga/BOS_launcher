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
) {
    private val ctx = context.applicationContext
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
        toast("Allow Do Not Disturb access for Sphere to change silent mode")
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
