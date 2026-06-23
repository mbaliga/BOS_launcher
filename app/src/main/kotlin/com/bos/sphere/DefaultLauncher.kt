package com.bos.sphere

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings

/**
 * Helpers for the "set as default launcher" flow (build brief §3, onboarding step 1 — the only
 * mandatory permission). Prefers the modern [RoleManager.ROLE_HOME] request and falls back to
 * the system home-settings screen on devices/SKUs where the role request isn't honoured.
 */
object DefaultLauncher {

    /** True if this app is currently the resolved home (default) launcher. */
    fun isDefault(context: Context): Boolean {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val resolved = context.packageManager.resolveActivity(
            intent,
            PackageManager.MATCH_DEFAULT_ONLY,
        )
        return resolved?.activityInfo?.packageName == context.packageName
    }

    /**
     * Returns an intent that asks the user to make this app the home launcher, or null if the
     * role is unavailable / already held (caller can then fall back to [homeSettingsIntent]).
     */
    fun requestRoleIntent(context: Context): Intent? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        val roleManager = context.getSystemService(RoleManager::class.java) ?: return null
        if (!roleManager.isRoleAvailable(RoleManager.ROLE_HOME)) return null
        if (roleManager.isRoleHeld(RoleManager.ROLE_HOME)) return null
        return roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME)
    }

    /** Fallback: open the system "Home app" settings screen. */
    fun homeSettingsIntent(): Intent =
        Intent(Settings.ACTION_HOME_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
