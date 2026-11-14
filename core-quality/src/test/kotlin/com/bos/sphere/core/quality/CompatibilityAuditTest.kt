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

    @Test fun NFR_19_backHandlersAreEnabledOnlyWhileTheirSurfaceIsOpen() {
        // Audit list: every BackHandler is conditional on an open surface, or is the body of a modal screen that only exists while open.
        val conditional = Regex("""BackHandler\s*(\(\s*enabled\s*=|\{|\(\s*onBack)""")
        Repo.mainSources().forEach { f ->
            f.readLines().withIndex().filter { (_, l) -> l.contains("BackHandler") && !l.contains("import") }.forEach { (i, l) ->
                assertTrue("${f.name}:${i + 1} $l", conditional.containsMatchIn(l))
            }
        }
    }

    /**
     * NFR-20: the launcher must not claim system gestures. The only exclusion today is the Orbit carousel region, whose
     * horizontal drags would otherwise lose to the back gesture. Android caps an exclusion at 200 dp of height per edge,
     * so it cannot disable back navigation, but it is listed here so adding another one is a conscious decision.
     */
    @Test fun NFR_20_systemGestureExclusionUsageIsAllowlisted() {
        val allowed = mapOf("SearchLayouts.kt" to 1)
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
