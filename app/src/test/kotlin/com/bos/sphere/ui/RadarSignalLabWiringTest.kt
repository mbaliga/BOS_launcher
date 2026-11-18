package com.bos.sphere.ui

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/** PAL-24, CFG-17: Ground Control has the Radar background pane, and the Lab (gated by LabGate) can force every signal state. */
class RadarSignalLabWiringTest {
    private fun text(path: String): String {
        val dirs = listOf(File("."), File(".."), File("../.."))
        return dirs.map { File(it, path) }.first { it.exists() }.readText()
    }

    @Test fun PAL_24_labListsOneRowPerForcedStateAndWritesThePlainPref() {
        val lab = text("app/src/main/kotlin/com/bos/sphere/ui/Lab.kt")
        assertTrue(lab.contains("radarSignalLabEntries(app)"))
        assertTrue(lab.contains("SignalForce.entries.map"))
        assertTrue(lab.contains("radarSignalForce = f"))
        val data = text("core-data/src/main/kotlin/com/bos/sphere/core/data/HomeModel.kt")
        for (state in listOf("Live", "No signal", "Weak", "Fair", "Good", "Excellent")) assertTrue(state, data.contains("\"$state\""))
    }

    /** Wave 15: the Radar-only pane became the one Background pane (HOM-16), which Radar shares with the home. */
    @Test fun PAL_24_theBackgroundPaneReplacesTheRadarOnlyPaneAndHoldsTheLabSignalControls() {
        val cats = text("app/src/main/kotlin/com/bos/sphere/ui/SettingsCategories.kt")
        assertTrue(cats.contains("BackgroundPane(p, WallpaperSurface.Glance, upd)") && cats.contains("BackgroundPane(p, WallpaperSurface.Pages, upd)"))
        assertTrue(!cats.contains("RadarBackgroundPane"))
        assertTrue(text("feature-grid/src/main/kotlin/com/bos/sphere/feature/grid/BackgroundPane.kt").contains("Preview signal"))
    }

    @Test fun PAL_24_noNewPermissionIsDeclaredForTheSignalSource() {
        val manifest = text("app/src/main/AndroidManifest.xml")
        assertTrue(manifest.contains("android.permission.ACCESS_NETWORK_STATE"))
        assertTrue(!manifest.contains("READ_PHONE_STATE"))
        val src = text("core-signals/src/main/kotlin/com/bos/sphere/core/signals/RadarSignal.kt")
        assertTrue(!src.contains("TelephonyCallback") && !src.contains("TelephonyManager"))
    }
}
