package com.bos.sphere.feature.grid

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Process
import androidx.test.core.app.ApplicationProvider
import com.bos.sphere.core.data.AppEntry
import com.bos.sphere.core.data.AppUninstall
import com.bos.sphere.core.data.UninstallStarter
import com.bos.sphere.core.input.MenuAction
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/** W25 (owner: "Uninstall doesn't work"): one helper behind every Uninstall entry, a recorded intent seam, system apps without the entry. */
@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [35])
class UninstallW25Test {
    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private val sent = mutableListOf<Intent>()
    private val saved = AppUninstall.starter
    private fun app(pkg: String, system: Boolean = false) = AppEntry(pkg, ComponentName(pkg, "$pkg.Main"), pkg, Process.myUserHandle(), isSystem = system) { null }

    @Before fun record() { AppUninstall.starter = UninstallStarter { _, i -> sent += i } }
    @After fun restore() { AppUninstall.starter = saved }

    @Test fun UNI_01_theIntentIsActionDeleteOnThePackageUriForThatUser() {
        val a = app("demo.a")
        assertTrue(AppUninstall.start(ctx, a))
        val i = sent.single()
        assertEquals(Intent.ACTION_DELETE, i.action)
        assertEquals("package:demo.a", i.data.toString())
        assertEquals(a.user, i.getParcelableExtra<android.os.UserHandle>(Intent.EXTRA_USER))
    }

    @Test fun UNI_02_radarsActionListOffersUninstallForUserAppsAndNotForSystemApps() {
        val user = ResultMenus.forKind(ResultKind.App, canUninstall = true).map { it.action }
        val system = ResultMenus.forKind(ResultKind.App, canUninstall = false).map { it.action }
        assertTrue(MenuAction.Uninstall in user)
        assertFalse(MenuAction.Uninstall in system)
        assertTrue(MenuAction.AppInfo in system)
    }

    @Test fun UNI_03_theDrawerAndHomeMenuModelHidesUninstallForSystemApps() {
        val on = AppMenuModel.rows(AppMenuModel.context(false, null, false, false, canUninstall = true)).map { it.shared }
        val off = AppMenuModel.rows(AppMenuModel.context(false, null, false, false, canUninstall = false)).map { it.shared }
        assertTrue(MenuAction.Uninstall in on)
        assertFalse(MenuAction.Uninstall in off)
        assertTrue(MenuAction.AppInfo in off)
    }

    @Test fun UNI_04_aSystemAppNeverStartsTheSystemScreen() {
        assertFalse(AppUninstall.start(ctx, app("demo.sys", system = true)))
        assertTrue(sent.isEmpty())
    }

    @Test fun UNI_05_anUpdatedSystemAppCanBeRemovedButAPlainOneCannot() {
        assertFalse(AppUninstall.isRemovable(ApplicationInfo.FLAG_SYSTEM))
        assertTrue(AppUninstall.isRemovable(ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP))
        assertTrue(AppUninstall.isRemovable(0))
    }

    @Test fun UNI_06_radarsHandlerAndTheRepositoryBothGoThroughTheHelperAndTheManifestHoldsThePermission() {
        val root = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "settings.gradle.kts").exists() || File(it, "settings.gradle").exists() }
        val manifest = File(root, "app/src/main/AndroidManifest.xml").readText()
        assertTrue(manifest.contains("""<uses-permission android:name="android.permission.REQUEST_DELETE_PACKAGES" />"""))
        assertTrue(File(root, "feature-grid/src/main/kotlin/com/bos/sphere/feature/grid/SearchOverlay.kt").readText().contains("AppUninstall.start(context, tile.app)"))
        assertTrue(File(root, "core-data/src/main/kotlin/com/bos/sphere/core/data/AppRepository.kt").readText().contains("AppUninstall.start(appContext, entry)"))
        assertEquals(Intent.ACTION_DELETE, com.bos.sphere.feature.grid.appUninstallIntent(app("demo.a")).action)
    }
}
