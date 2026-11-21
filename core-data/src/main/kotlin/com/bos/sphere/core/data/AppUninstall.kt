package com.bos.sphere.core.data

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.net.Uri

/** Test seam: the one call that really starts the system uninstall screen. */
fun interface UninstallStarter {
    fun start(context: Context, intent: Intent)
}

/**
 * W25: the one helper behind every Uninstall entry (Radar action list, nested rows, preview, drawer and home app menu).
 *
 * The system confirmation dialog is shown by the package installer for ACTION_DELETE. From Android 9 it ignores the
 * request unless the caller holds REQUEST_DELETE_PACKAGES, and startActivity still returns normally, which is why the
 * entry used to look dead. The intent names the app's own profile (EXTRA_USER) so a work-profile copy asks about itself.
 */
object AppUninstall {
    @Volatile var starter: UninstallStarter = UninstallStarter { c, i -> c.startActivity(i) }

    /** A system app that was never updated cannot be removed: it is not offered Uninstall, App info covers disabling. */
    fun isRemovable(flags: Int): Boolean =
        (flags and ApplicationInfo.FLAG_SYSTEM) == 0 || (flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0

    fun intent(app: AppEntry): Intent =
        Intent(Intent.ACTION_DELETE, Uri.fromParts("package", app.packageName, null))
            .putExtra(Intent.EXTRA_USER, app.user)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Starts the system confirmation. False when the app cannot be removed or the system refused. */
    fun start(context: Context, app: AppEntry): Boolean {
        if (app.isSystem) return false
        return runCatching { starter.start(context, intent(app)) }.isSuccess
    }
}
