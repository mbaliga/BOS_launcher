package com.bos.sphere.feature.homewidgets

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.os.BatteryManager
import android.os.Process
import android.os.UserManager
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import com.bos.sphere.core.data.AppEntry
import com.bos.sphere.core.data.HiddenAppsStore
import com.bos.sphere.core.data.NotificationProvider
import com.bos.sphere.core.data.SphereNotificationListener
import com.bos.sphere.feature.homewidgets.model.AppFact
import com.bos.sphere.feature.homewidgets.model.BatteryModel
import com.bos.sphere.feature.homewidgets.model.BatteryReading
import com.bos.sphere.feature.homewidgets.model.MediaFacts
import com.bos.sphere.feature.homewidgets.model.NotificationFact
import com.bos.sphere.feature.homewidgets.model.NowPlayingModel
import com.bos.sphere.feature.homewidgets.model.PadRef
import kotlin.math.roundToInt

/**
 * Reads the data the widgets draw, straight from Sphere's own sources each time (nothing is cached
 * on disk): the battery broadcast, media sessions and active notifications (through Sphere's
 * notification listener), the hidden-apps set and `LauncherApps`.
 */
internal object Sources {

    fun listenerComponent(c: Context) = ComponentName(c, SphereNotificationListener::class.java)

    /** Whether the user granted Sphere notification access (the listener may still be connecting). */
    fun notificationAccess(c: Context): Boolean = runCatching {
        c.getSystemService(NotificationManager::class.java).isNotificationListenerAccessGranted(listenerComponent(c))
    }.getOrDefault(false)

    // --- Battery ---

    /** The current battery reading from the sticky `ACTION_BATTERY_CHANGED`, or null when Android reports none. */
    fun battery(c: Context, intent: Intent? = null): BatteryReading? {
        val i = intent ?: ContextCompat.registerReceiver(
            c.applicationContext, null, IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED,
        ) ?: return null
        val bm = c.getSystemService(BatteryManager::class.java)
        return BatteryModel.reading(
            level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1),
            scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, -1),
            status = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1),
            plugged = i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0),
            temperatureTenths = if (i.hasExtra(BatteryManager.EXTRA_TEMPERATURE)) i.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) else null,
            systemLow = if (i.hasExtra(BatteryManager.EXTRA_BATTERY_LOW)) i.getBooleanExtra(BatteryManager.EXTRA_BATTERY_LOW, false) else null,
            chargeRemainingMs = runCatching { bm?.computeChargeTimeRemaining() ?: -1L }.getOrDefault(-1L),
            at = System.currentTimeMillis(),
        )
    }

    // --- Media ---

    /** Active media sessions (platform order), or null without notification access or when they can't be read. */
    fun mediaSessions(c: Context): List<MediaFacts>? {
        if (!notificationAccess(c)) return null
        val controllers = activeControllers(c) ?: return null
        val labels = Labels(c)
        return controllers.map { mc ->
            val state = mc.playbackState
            val playback = NowPlayingModel.playback(state?.state ?: NowPlayingModel.STATE_NONE)
            val md = mc.metadata
            MediaFacts(
                packageName = mc.packageName,
                appLabel = labels[mc.packageName],
                playback = playback,
                title = NowPlayingModel.clean(md?.getText(MediaMetadata.METADATA_KEY_DISPLAY_TITLE) ?: md?.getText(MediaMetadata.METADATA_KEY_TITLE)),
                artist = NowPlayingModel.clean(
                    md?.getText(MediaMetadata.METADATA_KEY_ARTIST)
                        ?: md?.getText(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE)
                        ?: md?.getText(MediaMetadata.METADATA_KEY_ALBUM_ARTIST),
                ),
                controls = NowPlayingModel.controls(state?.actions ?: 0L, playback),
            )
        }
    }

    fun activeControllers(c: Context): List<MediaController>? = runCatching {
        c.getSystemService(MediaSessionManager::class.java).getActiveSessions(listenerComponent(c))
    }.getOrNull()

    // --- Notifications ---

    /** Every active notification as package + profile + flags (no content), or null while the listener isn't connected. */
    fun notifications(): List<NotificationFact>? =
        NotificationProvider.active.value?.map { NotificationFact(it.packageName, it.isGroupSummary, it.isOngoing, it.userSerial) }

    fun hiddenKeys(c: Context): Set<String> = HiddenAppsStore(c).hidden.value

    // --- Launchable apps ---

    class App(val fact: AppFact, val info: LauncherActivityInfo)

    /** Every launcher entry in every profile, keyed as pads store them, with Sphere's hidden flag. */
    fun launchableApps(c: Context): Map<PadRef, App> {
        val la = c.getSystemService(LauncherApps::class.java) ?: return emptyMap()
        val um = c.getSystemService(UserManager::class.java) ?: return emptyMap()
        val hidden = hiddenKeys(c)
        val out = LinkedHashMap<PadRef, App>()
        for (user in runCatching { um.userProfiles }.getOrDefault(emptyList())) {
            val serial = um.getSerialNumberForUser(user)
            for (info in runCatching { la.getActivityList(null, user) }.getOrDefault(emptyList())) {
                val ref = PadRef(info.componentName.flattenToString(), serial)
                // The hidden-apps store keys entries as AppEntry does; build one to get the same key.
                val key = AppEntry(info.applicationInfo.packageName, info.componentName, "", user) { null }.key
                out[ref] = App(AppFact(ref, info.label?.toString().orEmpty(), key in hidden), info)
            }
        }
        return out
    }

    /** An app's icon, badged for its profile, as a bitmap small enough for a widget update. */
    fun icon(c: Context, info: LauncherActivityInfo): Bitmap? = runCatching {
        val px = (48 * c.resources.displayMetrics.density).roundToInt().coerceIn(48, 144)
        info.getBadgedIcon(0)?.toBitmap(px, px, Bitmap.Config.ARGB_8888)
    }.getOrNull()

    /** Sphere's own profile's serial, the default for a package with no profile of its own. */
    fun myUserSerial(c: Context): Long =
        runCatching { c.getSystemService(UserManager::class.java)?.getSerialNumberForUser(Process.myUserHandle()) }.getOrNull() ?: -1L

    /** App labels by package (and profile), looked up once per draw. */
    class Labels(private val c: Context) {
        private val cache = HashMap<String, String>()
        private val mine by lazy { myUserSerial(c) }

        /** In Sphere's own profile. */
        operator fun get(pkg: String): String = cache.getOrPut(pkg) {
            runCatching {
                val pm = c.packageManager
                pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
            }.getOrDefault(pkg)
        }

        /**
         * In the profile with [userSerial] (a work-profile notification reaches the personal
         * listener too). Another profile's label is read through `LauncherApps`, which can see it,
         * and badged the way the system names that profile's apps ("Work Mail").
         */
        fun get(pkg: String, userSerial: Long): String {
            if (userSerial < 0 || userSerial == mine) return get(pkg)
            return cache.getOrPut("$pkg#$userSerial") {
                runCatching {
                    val user = c.getSystemService(UserManager::class.java)!!.getUserForSerialNumber(userSerial)!!
                    val la = c.getSystemService(LauncherApps::class.java)!!
                    val pm = c.packageManager
                    val label = la.getActivityList(pkg, user).firstOrNull()?.label
                        ?: la.getApplicationInfo(pkg, 0, user).loadLabel(pm)
                    pm.getUserBadgedLabel(label, user).toString()
                }.getOrDefault(pkg)
            }
        }
    }
}
