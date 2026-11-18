package com.bos.sphere.core.quality

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Wave 23 (NFR-13): source checks that complement the rendered sweep in the app module (`A11ySweepTourTest`). The sweep reads the semantics tree, so a
 * control that only listens to raw pointer events (no click action) never shows up in it; this scan finds those by their text.
 */
class A11yPolishW23AuditTest {
    private fun rel(f: java.io.File) = f.relativeTo(Repo.root).path.replace('\\', '/')

    /** An icon or box that carries a name and reacts to a tap only through pointerInput has nothing for TalkBack to activate, unless it also declares a click action or a role. A long-press-only control (onLongClick) and a pad or slider that states its value (stateDescription or setProgress: the colour picker) are not taps. */
    @Test fun NFR_13_noNamedControlListensToTapsOnlyThroughPointerInputWithoutAClickActionOrRole() {
        val offenders = ArrayList<String>()
        for (f in Repo.mainSources().filter { it.extension == "kt" }) {
            val lines = f.readLines()
            for ((i, l) in lines.withIndex()) {
                if (!l.contains("detectTapGestures")) continue
                val window = lines.subList(maxOf(0, i - 6), minOf(lines.size, i + 3)).joinToString("\n")
                val named = Regex("""contentDescription\s*=\s*(\"|L10n\.tr)""").containsMatchIn(window)
                val actionable = window.contains("onClick(") || window.contains("onClick {") || window.contains("onLongClick") || window.contains("stateDescription") || window.contains("setProgress") || window.contains("role =") || window.contains(".clickable") || window.contains("combinedClickable")
                if (named && !actionable) offenders += "${rel(f)}:${i + 1}"
            }
        }
        assertEquals("named controls with a pointer-only tap (give them clickable(role = ...) or an onClick semantic): $offenders", emptyList<String>(), offenders)
    }

    @Test fun NFR_13_theRadarMicHasAStateDescriptionAndAPoliteLiveRegion() {
        val src = Repo.file("feature-grid/src/main/kotlin/com/bos/sphere/feature/grid/VoiceEntry.kt").readText()
        assertTrue(src.contains("stateDescription = L10n.tr(VoiceStates.describe("))
        assertTrue("polite, never assertive", src.contains("liveRegion = LiveRegionMode.Polite") && !src.contains("LiveRegionMode.Assertive"))
    }

    @Test fun NFR_13_theLaunchpadAndTheRecentsPanelAnnounceThemselvesWithAPaneTitle() {
        assertTrue(Repo.file("feature-launchpad/src/main/kotlin/com/bos/sphere/ui/Launchpad.kt").readText().contains("paneTitle = L10n.tr(\"Launchpad\")"))
        assertTrue(Repo.file("feature-grid/src/main/kotlin/com/bos/sphere/feature/grid/RecentsPanel.kt").readText().contains("paneTitle = L10n.tr(\"Recent apps\")"))
    }
}
