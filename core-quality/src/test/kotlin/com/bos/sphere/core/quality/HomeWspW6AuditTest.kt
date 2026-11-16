package com.bos.sphere.core.quality

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Wave 6 audit of home, workspaces and the dock: structural guards for the rules the audit found open. */
class HomeWspW6AuditTest {
    private fun text(path: String) = Repo.file(path).readText()

    @Test fun WSP_01_appsAreStartedOnlyThroughTheLaunchLayerExceptTheNamedFallbacks() {
        val allowed = setOf(
            "LaunchExecutor.kt",   // the one place that starts apps
            "AppRepository.kt",    // safe mode, which deliberately avoids the launch layer
            "ActionRunner.kt",     // the fallback when no launch hook is installed (the app always installs one)
        )
        val callers = Repo.mainSources().filter { it.readText().contains(".startMainActivity(") }.map { it.name }.toSet()
        assertEquals("startMainActivity outside the launch layer: $callers", emptySet<String>(), callers - allowed)
        val screen = text("app/src/main/kotlin/com/bos/sphere/HomeScreen.kt")
        assertTrue("the Launchpad's fallback goes through the launcher", !screen.contains("getLaunchIntentForPackage(pkg)"))
        assertTrue(screen.contains("runner.launchHook ="))
    }

    @Test fun WSP_12_theApplicationFreeSwitchPathStaysSafeAndTheHooksFallBackToDetached() {
        val repo = text("feature-dock/src/main/kotlin/com/bos/sphere/feature/dock/FileStore.kt")
        assertTrue(repo.contains("var hooks: WorkspaceHooks = DETACHED"))
        val layer = text("app/src/main/kotlin/com/bos/sphere/ui/DockLayer.kt")
        assertTrue(layer.contains("WorkspaceRepository.hooks = WorkspaceRepository.DETACHED"))
        val manager = text("feature-dock/src/main/kotlin/com/bos/sphere/feature/dock/Workspaces.kt")
        assertTrue(manager.contains("captureDue"))
    }

    @Test fun HOM_12_editModeKeepsRemovalsUndoableThroughTheHostHooks() {
        val behaviour = text("app/src/main/kotlin/com/bos/sphere/HomeBehaviour.kt")
        assertTrue(behaviour.contains("hooks?.begin(") && behaviour.contains("hooks?.end(") && behaviour.contains("hooks?.revived("))
        assertTrue(text("app/src/main/kotlin/com/bos/sphere/HomeScreen.kt").contains("homeEdit.hooks = homeItems"))
        val host = text("app/src/main/kotlin/com/bos/sphere/HomeItemsHost.kt")
        assertTrue("a widget id is not deleted while edit mode can still undo its removal", host.contains("if (keep != null)"))
    }

    @Test fun HOM_10_aPackageCallbackCannotTakeTheProcessDown() {
        val repo = text("core-data/src/main/kotlin/com/bos/sphere/core/data/AppRepository.kt")
        assertTrue(repo.contains("runCatching { loadAll() }"))
    }

    @Test fun WSP_11_theNotificationTextsOfScheduledLaunchesAndTheSwitcherAreTranslated() {
        val auto = text("feature-dock/src/main/kotlin/com/bos/sphere/feature/dock/Automation.kt")
        assertTrue(auto.contains("L10n.tr(\"Tap Open to start it\")") && auto.contains("L10n.tr(\"Scheduled launches\")"))
        val launcher = text("feature-dock/src/main/kotlin/com/bos/sphere/feature/dock/AppLauncher.kt")
        assertTrue(launcher.contains("L10n.tr(\"Group switcher\")") && launcher.contains("areNotificationsEnabled()"))
    }
}
