package com.bos.sphere.core.capabilities

import android.app.AppOpsManager
import android.app.role.RoleManager
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.display.DisplayManager
import android.hardware.input.InputManager
import android.os.Build
import android.os.Process
import android.provider.Settings
import android.speech.SpeechRecognizer
import android.view.InputDevice

/** Reads the device once into [CapabilityInputs]; the only source features may use for gating. */
class CapabilityRegistry(private val context: Context) {

    fun snapshot(windowWidthDp: Int = context.resources.configuration.screenWidthDp): CapabilityInputs {
        val app = context.applicationContext
        return CapabilityInputs(
            apiLevel = Build.VERSION.SDK_INT,
            windowWidthDp = windowWidthDp,
            hasKeyboard = hasSource(InputDevice.SOURCE_KEYBOARD) { it.keyboardType == InputDevice.KEYBOARD_TYPE_ALPHABETIC },
            hasPointer = hasSource(InputDevice.SOURCE_MOUSE) { true } || hasSource(InputDevice.SOURCE_TOUCHPAD) { true },
            hasController = hasSource(InputDevice.SOURCE_GAMEPAD) { true } || hasSource(InputDevice.SOURCE_JOYSTICK) { true },
            displays = displays(),
            desktopWindowing = desktopWindowing(),
            isHomeRole = isHomeRole(),
            notificationAccess = Settings.Secure.getString(app.contentResolver, "enabled_notification_listeners")?.contains(app.packageName) == true,
            usageAccess = usageAccess(),
            contactsGranted = app.checkSelfPermission(android.Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED,
            folderGranted = app.checkSelfPermission(android.Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED,
            speechOnDevice = runCatching { if (Build.VERSION.SDK_INT >= 31) SpeechRecognizer.isOnDeviceRecognitionAvailable(app) else null }.getOrNull(),
            pinShortcutsSupported = runCatching {
                app.getSystemService(android.content.pm.LauncherApps::class.java)?.hasShortcutHostPermission() == true
            }.getOrDefault(false),
        )
    }

    private fun hasSource(mask: Int, extra: (InputDevice) -> Boolean): Boolean = runCatching {
        val ids = InputDevice.getDeviceIds()
        ids.any { id -> InputDevice.getDevice(id)?.let { d -> d.supportsSource(mask) && !d.isVirtual && extra(d) } == true }
    }.getOrDefault(false)

    private fun displays(): List<DisplayInfo> = runCatching {
        val dm = context.getSystemService(DisplayManager::class.java)
        dm.displays.map { d ->
            val m = android.util.DisplayMetrics().also { @Suppress("DEPRECATION") d.getRealMetrics(it) }
            DisplayInfo(
                id = d.displayId, name = d.name, widthPx = m.widthPixels, heightPx = m.heightPixels, densityDpi = m.densityDpi,
                isDefault = d.displayId == android.view.Display.DEFAULT_DISPLAY,
                // Presentation / private displays cannot host ordinary tasks; the Lab spike SPK-01 confirms the rest.
                canHostTasks = if (d.displayId == android.view.Display.DEFAULT_DISPLAY) true else d.flags and android.view.Display.FLAG_PRESENTATION == 0 && d.flags and android.view.Display.FLAG_PRIVATE == 0,
            )
        }
    }.getOrDefault(emptyList())

    private fun desktopWindowing(): Boolean? = runCatching {
        if (Build.VERSION.SDK_INT < 34) null
        else context.packageManager.hasSystemFeature(PackageManager.FEATURE_FREEFORM_WINDOW_MANAGEMENT)
    }.getOrNull()

    private fun isHomeRole(): Boolean = runCatching {
        context.getSystemService(RoleManager::class.java).isRoleHeld(RoleManager.ROLE_HOME)
    }.getOrDefault(false)

    private fun usageAccess(): Boolean = runCatching {
        val ops = context.getSystemService(AppOpsManager::class.java)
        @Suppress("DEPRECATION")
        ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName) == AppOpsManager.MODE_ALLOWED
    }.getOrDefault(false)

    @Suppress("unused") private val keep = InputManager::class
}
