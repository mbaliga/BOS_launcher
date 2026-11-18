package com.bos.sphere

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherActivityInfo
import android.os.Process
import android.os.UserHandle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.bos.sphere.core.data.CrashLoopGuard
import com.bos.sphere.core.data.LauncherStores
import com.bos.sphere.feature.grid.LauncherInput
import org.robolectric.Shadows
import org.robolectric.shadows.ShadowLauncherApps

/**
 * The whole-app smoke harness (INP-01, INP-05, NFR-11, NFR-19, HOM-01, PAL-01).
 *
 * What runs for real: [MainActivity] (its onCreate, window, key dispatch, Back dispatcher, new-intent path), the composed
 * [HomeScreen] with every overlay, the stores, the input hub, the dock, the open-surface store and the crash-loop guard.
 *
 * What is faked, because there is no system behind Robolectric:
 *  - the launcher-apps service: [seedApps] fills Robolectric's `ShadowLauncherApps` with activities (the app list is the real
 *    `LauncherAppsAppRepository` reading them);
 *  - Health Connect, the widget host, the notification listener and the usage-stats service have no provider here; the home
 *    reads them through code that already tolerates their absence (the "healthconnect" service-not-found line in the log).
 *
 * One process, many tests: Robolectric keeps the app's static singletons alive across tests of a class, so [resetProcess] clears
 * them the way a fresh process would. A "restart" inside one test is [restartProcess]: the preference files stay, every
 * singleton is rebuilt, and the crash-loop guard sees a start that survived its window (so it is not taken for a crash loop).
 */
internal object AppSmokeHarness {
    val app: Application get() = ApplicationProvider.getApplicationContext()

    /** Clears the process-wide singletons the launcher keeps in statics (what a new process starts without). */
    fun resetProcess() {
        runCatching { com.bos.sphere.core.data.GroupsSettings.restore(com.bos.sphere.core.data.GroupsConfig()) } // wave 22: the in-memory groups of an earlier test
        for ((cls, field) in listOf(
            "com.bos.sphere.core.data.LauncherStores" to "instance",
            "com.bos.sphere.core.data.OpenSurfaceStore" to "shared",
            "com.bos.sphere.core.data.StateService" to "instance",
            "com.bos.sphere.core.data.PuckActionsStore" to "shared",
            "com.bos.sphere.core.catalog.HomeExtrasStore" to "instance",
            "com.bos.sphere.feature.desktop.DesktopController" to "instance",
            "com.bos.sphere.feature.dock.WorkspaceRepository" to "instance",
            "com.bos.sphere.feature.grid.LauncherWidgetHost" to "shared",
            "com.bos.sphere.OverrideStoreHolder" to "store",
            "com.bos.sphere.core.data.GroupsSettings" to "sp", // wave 22: the groups file of the previous test's application
        )) runCatching {
            Class.forName(cls).getDeclaredField(field).apply { isAccessible = true }.set(null, null)
        }
        runCatching { // the input hub is one object for the process: drop focus nodes, layers and the type hook a finished activity left
            LauncherInput.focus.nodes().map { it.id }.forEach(LauncherInput.focus::unregister)
            LauncherInput.typeHook = null
            LauncherInput.overlayOpen = false
        }
        HomeOpenRequests.folder.value = null
        CrashLoopGuard.exitProbe = { null } // a fresh process: no exit record unless a test says one
        runCatching { // CrashLoopGuard.safeMode is a process value: a new process decides it again in onStart
            Class.forName("com.bos.sphere.core.data.CrashLoopGuard").getDeclaredField("safeMode").apply { isAccessible = true }.setBoolean(CrashLoopGuard, false)
        }
    }

    /**
     * Ends the activity the way the OS ends a process: a real finish clears the guard's "starting" marker (a clean exit, CFG-09), a kill does not,
     * so the marker is put back after the scenario closes.
     */
    fun closeLikeAKill(sc: androidx.test.core.app.ActivityScenario<*>) {
        val sp = app.getSharedPreferences("crash_loop", android.content.Context.MODE_PRIVATE)
        val saved = sp.all
        sc.close()
        sp.edit().clear().apply { for ((k, v) in saved) when (v) { is Long -> putLong(k, v); is Boolean -> putBoolean(k, v); is Int -> putInt(k, v) } }.commit()
    }

    /** Wave 19: what the OS exit history would say about the previous process (null: no record, as for a kill nobody recorded). */
    fun previousProcessExit(reason: Int?) {
        CrashLoopGuard.exitProbe = { if (reason == null) null else com.bos.sphere.core.data.CrashLoopMath.Exit(reason, System.currentTimeMillis() + 60_000) }
    }

    /** A new process over the same preference files: the first one lived past the crash-loop window. */
    fun restartProcess() {
        CrashLoopGuard.onStable(app)
        resetProcess()
    }

    fun packageOf(label: String) = "app.${label.lowercase().replace(" ", "")}"

    /** The home key of a seeded app (what the layout, the dock and folders store). */
    fun appKey(label: String): String = "${ComponentName(packageOf(label), packageOf(label) + ".Main").flattenToShortString()}#${Process.myUserHandle().hashCode()}"

    /** Fills the launcher-apps service with one launchable activity per label (package `app.<label lower>`). */
    fun seedApps(vararg labels: String) {
        val shadow = Shadows.shadowOf(app.getSystemService(Context.LAUNCHER_APPS_SERVICE) as android.content.pm.LauncherApps)
        val user: UserHandle = Process.myUserHandle()
        for (label in labels) {
            val pkg = packageOf(label)
            val ai = ApplicationInfo().apply { packageName = pkg; this.name = label; sourceDir = "/data/app/$pkg.apk"; nonLocalizedLabel = label }
            val info = ActivityInfo().apply {
                packageName = pkg; name = "$pkg.Main"; applicationInfo = ai; nonLocalizedLabel = label; exported = true
            }
            // The platform constructors are hidden; a test may reach them by reflection (the app code never does).
            val states = Class.forName("android.content.pm.IncrementalStatesInfo").getDeclaredConstructor().newInstance()
            val internalCls = Class.forName("android.content.pm.LauncherActivityInfoInternal")
            val internal = internalCls.getDeclaredConstructor(ActivityInfo::class.java, states.javaClass, UserHandle::class.java).newInstance(info, states, user)
            val ctor = LauncherActivityInfo::class.java.getDeclaredConstructor(Context::class.java, internalCls)
            ctor.isAccessible = true
            shadow.addActivity(user, ctor.newInstance(app, internal))
        }
    }

    /**
     * Wave 26: what the system does after an uninstall is confirmed: the package leaves the launcher-apps inventory and the `onPackageRemoved`
     * callback fires (the fake callback of the brief: Robolectric's shadow posts it to the callback the repository registered).
     */
    fun removeApp(label: String) {
        val pkg = packageOf(label)
        val shadow = Shadows.shadowOf(app.getSystemService(Context.LAUNCHER_APPS_SERVICE) as android.content.pm.LauncherApps)
        val f = Class.forName("org.robolectric.shadows.ShadowLauncherApps").getDeclaredField("activityList").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST") val list = f.get(null) as com.google.common.collect.Multimap<UserHandle, LauncherActivityInfo>
        list.values().removeAll { it.applicationInfo.packageName == pkg }
        shadow.notifyPackageRemoved(pkg)
    }

    /** Past onboarding, with the given preference edits; the stores are the ones the activity will read. */
    fun configure(edit: (com.bos.sphere.core.data.LauncherPrefs) -> com.bos.sphere.core.data.LauncherPrefs = { it }) {
        // Wave 26: Glance is on by default; the existing smoke tests exercise the pages (the Selected apps home of the old landing), so they start
        // with Glance off, where home shows the first page. Glance has its own tests, which use configureFresh().
        LauncherStores.get(app).prefs.update { edit(it.copy(onboardingDone = true, glanceEnabled = false)) }
    }

    /** HOM-23: past onboarding and nothing else touched: the default landing (Glance) as a fresh install shows it. */
    fun configureFresh(edit: (com.bos.sphere.core.data.LauncherPrefs) -> com.bos.sphere.core.data.LauncherPrefs = { it }) {
        LauncherStores.get(app).prefs.update { edit(it.copy(onboardingDone = true)) }
    }

    fun launch(): ActivityScenario<MainActivity> = ActivityScenario.launch(MainActivity::class.java)

    /** A key sent the way the system sends one: through the activity's dispatch (the launcher input hub sees it first). */
    fun press(scenario: ActivityScenario<*>, keyCode: Int, ctrl: Boolean = false, shift: Boolean = false, unicode: Int = 0): Boolean {
        var handled = false
        scenario.onActivity { a ->
            val meta = (if (ctrl) KeyEvent.META_CTRL_ON else 0) or (if (shift) KeyEvent.META_SHIFT_ON else 0)
            val t = android.os.SystemClock.uptimeMillis()
            handled = a.dispatchKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_DOWN, keyCode, 0, meta))
            a.dispatchKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_UP, keyCode, 0, meta))
        }
        return handled
    }

    /** The Back gesture: the activity's dispatcher, which is what both gesture and 3-button navigation reach on API 33+. */
    fun back(scenario: ActivityScenario<*>) { scenario.onActivity { (it as ComponentActivity).onBackPressedDispatcher.onBackPressed() } }

    fun hasEnabledBackCallback(scenario: ActivityScenario<*>): Boolean {
        var v = false
        scenario.onActivity { v = (it as ComponentActivity).onBackPressedDispatcher.hasEnabledCallbacks() }
        return v
    }

    @Suppress("unused") fun idle(rule: ComposeTestRule) { rule.waitForIdle() }
}
