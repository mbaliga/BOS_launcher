package com.bos.sphere.core.quality

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * CFG-07: every place that opens the system route for notification access, usage access or the microphone, or launches the
 * contacts permission, must sit inside the explanation hook (GrantHooks.ask / grantPrompter.ask) in the same few lines.
 * The Access pane and the grant screen itself are the explanation, so they are the only allowed exceptions.
 */
class GrantCallSitesTest {
    private val exempt = setOf("AccessPane.kt", "GrantSupport.kt", "GrantGate.kt", "Recents.kt", "NotificationManager.kt", "SettingsIndex.kt")
    private val triggers = listOf("ACTION_NOTIFICATION_LISTENER_SETTINGS", "ACTION_USAGE_ACCESS_SETTINGS", "launch(Manifest.permission.RECORD_AUDIO)", "launch(android.Manifest.permission.RECORD_AUDIO)", "contactsPermission.launch(")

    @Test fun CFG_07_everyGrantRouteIsReachedThroughTheExplanationHook() {
        val bad = ArrayList<String>()
        for (f in Repo.mainSources()) {
            if (f.name in exempt) continue
            val lines = f.readLines()
            lines.forEachIndexed { i, l ->
                if (triggers.any { l.contains(it) } && !l.trimStart().startsWith("//") && !l.trimStart().startsWith("*")) {
                    val window = lines.subList(maxOf(0, i - 4), i + 1).joinToString("\n")
                    if (!window.contains("GrantHooks.ask") && !window.contains("grantPrompter.ask") && !window.contains("Prompter.ask") && !window.contains("AccessPane")) bad += "${f.name}:${i + 1}"
                }
            }
        }
        assertTrue("grant route outside the explanation hook: $bad", bad.isEmpty())
    }
}

/** NFR-18: device-bound values are kept out of platform restore and the guard that removes the rest is installed at start. */
class PlatformRestoreWiringTest {
    /** Wave 15: no background picture is written any more, so the no-backup picture check became "none is written". */
    @Test fun NFR_18_theGuardRunsAtStartAndNoBackgroundPictureIsWrittenAnywhere() {
        val main = Repo.file("app/src/main/kotlin/com/bos/sphere/MainActivity.kt").readText()
        assertTrue(main.contains("guardPlatformRestore()"))
        val home = Repo.file("app/src/main/kotlin/com/bos/sphere/HomeScreen.kt").readText()
        assertTrue(!home.contains("background_"))
    }
}
