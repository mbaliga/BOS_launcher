package com.bos.sphere

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.role.RoleManager
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.os.PowerManager
import android.widget.Toast
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import com.bos.sphere.core.data.ActionRunner
import com.bos.sphere.core.data.CornerActionId
import com.bos.sphere.core.data.CornerItem
import com.bos.sphere.core.data.Torch
import com.bos.sphere.core.data.opensUi
import com.bos.sphere.core.data.LauncherStores
import com.bos.sphere.core.data.MotionGestureSensors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * Keeps motion gestures (shake / flick twice / flip twice / face down) working while home is
 * not on screen (paused while the screen is off). Opt-in from settings; runs as a foreground service with a quiet notification.
 * Ringer / torch / DND / app actions run here directly; launcher-internal actions (search, all
 * apps, settings, notifications) bring the home activity forward with [MainActivity.EXTRA_ACTION].
 */
class MotionGestureService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var sensors: MotionGestureSensors
    private lateinit var runner: ActionRunner

    override fun onCreate() {
        super.onCreate()
        val stores = LauncherStores.get(this)
        runner = ActionRunner(
            this,
            onInternal = { id -> openHome(id) },
            onAppLaunched = { stores.history.record(it) },
        )
        Torch.init(this)
        sensors = MotionGestureSensors(this) { gesture ->
            val item = stores.shortcuts.gestures.value[gesture] ?: return@MotionGestureSensors
            val opensUi = item is CornerItem.App || (item is CornerItem.Action && item.id.opensUi())
            // Android only lets a background service open UI for the default home app.
            if (opensUi && !isDefaultHome()) {
                Toast.makeText(this, "Set Liftoff as your home app to open things with motion gestures", Toast.LENGTH_SHORT).show()
            } else {
                runner.run(item)
            }
        }
        stores.prefs.prefs.onEach { sensors.sensitivity = it.motionSensitivity }.launchIn(scope)
        // Don't listen (or act) with the screen off: saves battery and avoids pocket triggers.
        registerReceiver(
            screenReceiver,
            IntentFilter().apply { addAction(Intent.ACTION_SCREEN_OFF); addAction(Intent.ACTION_SCREEN_ON) },
            Context.RECEIVER_NOT_EXPORTED,
        )
    }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> sensors.stop()
                Intent.ACTION_SCREEN_ON -> sensors.start()
            }
        }
    }

    private fun isDefaultHome(): Boolean =
        getSystemService(RoleManager::class.java)?.isRoleHeld(RoleManager.ROLE_HOME) == true

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        if (getSystemService(PowerManager::class.java)?.isInteractive != false) sensors.start()
        return START_STICKY
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(screenReceiver) }
        sensors.stop()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun openHome(id: CornerActionId) {
        val i = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(MainActivity.EXTRA_ACTION, id.name)
        runCatching { startActivity(i) }
    }

    private fun notification(): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Motion gestures", NotificationManager.IMPORTANCE_MIN).apply {
                    description = "Shown while Liftoff listens for motion gestures in the background"
                    setShowBadge(false)
                },
            )
        }
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).putExtra(MainActivity.EXTRA_ACTION, CornerActionId.LauncherSettings.name),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_mono)
            .setContentTitle("Motion gestures on")
            .setContentText("Shake, flick or flip to run your shortcuts")
            .setContentIntent(open)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val CHANNEL = "motion"
        private const val NOTIFICATION_ID = 7

        fun start(context: Context) {
            runCatching { context.startForegroundService(Intent(context, MotionGestureService::class.java)) }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, MotionGestureService::class.java))
        }
    }
}

