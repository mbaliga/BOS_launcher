package com.bos.sphere.ui

import com.bos.sphere.BuildConfig
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** CFG-17: the Lab category's gate. Debug always lists it; a release build lists it unless LAB_VISIBLE is false. */
class LabGateTest {
    @Test fun CFG_17_debugAlwaysShowsTheLabWhateverTheSetting() {
        assertTrue(LabGate.visible(debug = true, labVisible = true))
        assertTrue(LabGate.visible(debug = true, labVisible = false))
    }

    @Test fun CFG_17_releaseFollowsTheBuildSetting() {
        assertTrue(LabGate.visible(debug = false, labVisible = true))
        assertFalse(LabGate.visible(debug = false, labVisible = false))
    }

    @Test fun CFG_17_thisDebugBuildHasTheFieldOn() {
        assertTrue(BuildConfig.DEBUG)
        assertTrue(BuildConfig.LAB_VISIBLE)
        assertTrue(LabGate.visible())
    }

    private fun text(path: String): String {
        val dirs = listOf(File("."), File(".."), File("../.."))
        return dirs.map { File(it, path) }.first { it.exists() }.readText()
    }

    @Test fun CFG_17_gradleDefaultIsVisibleWhileAlphaAndThePropertyHidesIt() {
        val g = text("app/build.gradle.kts")
        assertTrue("property name", g.contains("liftoff.lab"))
        // default true: only an explicit "false" hides it
        assertTrue(g.contains("""?.lowercase() != "false""""))
        assertTrue("debug is pinned on", Regex("""debug \{[^}]*buildConfigField\("boolean", "LAB_VISIBLE", "true"\)""").containsMatchIn(g))
        assertTrue("release reads the property", g.contains("""buildConfigField("boolean", "LAB_VISIBLE", labVisibleRelease.toString())"""))
    }

    @Test fun CFG_17_theLabCategoryIsListedThroughTheGate() {
        val s = text("app/src/main/kotlin/com/bos/sphere/ui/SettingsCategories.kt")
        assertTrue(s.contains("m.lab.isNotEmpty() && LabGate.visible()"))
    }

    @Test fun CFG_17_decisionNoteNamesTheOwnerStep() {
        val d = text("docs/decisions/CFG-17.md")
        assertTrue(d.contains("-Pliftoff.lab=false"))
        assertTrue(d.contains("owner"))
    }
}
