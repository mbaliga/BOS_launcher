package com.bos.sphere.core.quality

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CompatibilityAuditTest {
    @Test fun NFR_19_predictiveBackIsEnabledInTheManifest() {
        assertTrue(Repo.file("app/src/main/AndroidManifest.xml").readText().contains("android:enableOnBackInvokedCallback=\"true\""))
    }

    @Test fun NFR_19_edgeToEdgeIsEnabledBeforeSetContent() {
        val a = Repo.file("app/src/main/kotlin/com/bos/sphere/MainActivity.kt").readText()
        assertTrue(a.indexOf("enableEdgeToEdge()") in 0 until a.indexOf("setContent {"))
    }

    @Test fun NFR_19_noLegacyOnBackPressedOrKeyCodeBackInterception() {
        val offenders = Repo.mainSources().filter { f -> f.readText().let { Regex("""override\s+fun\s+onBackPressed""").containsMatchIn(it) || it.contains("KEYCODE_BACK") || Regex("""Key\.Back\b""").containsMatchIn(it) } }
        assertTrue("legacy back handling in $offenders", offenders.isEmpty())
    }

    /**
     * Every BackHandler is either gated on its surface (`enabled = ...` or an enclosing `if (...)`) or is the body of a
     * composable that only exists while its surface is open. The second kind is an audited allow-list with exact counts, so
     * a new bare handler fails here until someone audits it.
     */
    @Test fun NFR_19_backHandlersAreEnabledOnlyWhileTheirSurfaceIsOpen() {
        val modalOnly = mapOf("Corners.kt" to 1, "SettingsSpace.kt" to 1, "Launchpad.kt" to 1)
        val bare = HashMap<String, Int>()
        Repo.mainSources().forEach { f ->
            f.readLines().filter { it.contains("BackHandler") && !it.contains("import") }.forEach { l ->
                val gated = l.contains("enabled =") || Regex("""\bif \(""").containsMatchIn(l)
                if (!gated) bare.merge(f.name, 1, Int::plus)
            }
        }
        // HomeScreen.kt: LeftWidgetPage wraps its handler in `if (visible)` on the preceding line.
        val homeLeftPage = Repo.file("app/src/main/kotlin/com/bos/sphere/HomeScreen.kt").readLines()
        val i = homeLeftPage.indexOfFirst { it.contains("BackHandler(onBack = onClose)") }
        if (i > 0 && homeLeftPage[i - 1].contains("if (visible)")) bare.remove("HomeScreen.kt")
        assertEquals(modalOnly, bare)
    }

    @Test fun NFR_19_everyEdgeToEdgeActivityDrawsBehindTheBars() {
        // The home host and the desktop-mode host both opt in; neither sets a fixed bar colour or fits system windows.
        listOf("MainActivity.kt", "DesktopActivity.kt").forEach { n ->
            val t = Repo.file("app/src/main/kotlin/com/bos/sphere/$n").readText()
            assertTrue("$n enableEdgeToEdge", t.contains("enableEdgeToEdge()") || t.contains("setDecorFitsSystemWindows(false)"))
        }
        val all = Repo.mainSources().filter { it.readText().contains("setDecorFitsSystemWindows(true)") }
        assertTrue("fits-system-windows opt-out in $all", all.isEmpty())
    }

    /**
     * NFR-20: the launcher must not claim system gestures. The only exclusion today is the Orbit carousel region, whose
     * horizontal drags would otherwise lose to the back gesture. Android caps an exclusion at 200 dp of height per edge,
     * so it cannot disable back navigation, but it is listed here so adding another one is a conscious decision.
     */
    @Test fun NFR_20_noSystemGestureExclusionAnywhere() {
        // The Orbit carousel exclusion was removed (it kept 200 dp of an edge away from the back gesture); none is allowed now.
        val allowed = emptyMap<String, Int>()
        val found = Repo.mainSources().associate { it.name to it.readLines().count { l -> l.contains("systemGestureExclusion") && !l.contains("import") } }.filterValues { it > 0 }
        assertEquals(allowed, found)
    }

    @Test fun NFR_20_noWindowLevelGestureInterception() {
        val offenders = Repo.mainSources().filter { f ->
            f.readText().let { it.contains("setSystemGestureExclusionRects") || it.contains("SYSTEM_UI_FLAG_IMMERSIVE") || it.contains("TYPE_APPLICATION_OVERLAY") }
        }
        assertTrue("window-level gesture control in $offenders", offenders.isEmpty())
    }

    /** Hiding the status bar uses the platform's own swipe-to-reveal behaviour, so the system keeps the gesture. */
    @Test fun NFR_20_barBehaviourIsTheSystemsTransientSwipe() {
        val users = Repo.mainSources().filter { it.readText().contains("systemBarsBehavior") }
        assertEquals(listOf("HomeScreen.kt"), users.map { it.name })
        val lines = users.single().readLines().filter { it.contains("systemBarsBehavior") }
        assertTrue(lines.all { it.contains("BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE") })
    }

    @Test fun NFR_20_documentedInTheOemNotes() {
        assertTrue(Repo.file("docs/oem-notes.md").readText().contains("gesture"))
    }
}
