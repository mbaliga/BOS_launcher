package com.bos.sphere.ui

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/** Wave 20 audit wiring checks (source level, like RadarSignalLabWiringTest): the pieces exist and are connected to a screen. */
class GlanceAuditW20WiringTest {
    private fun text(path: String): String {
        val dirs = listOf(File("."), File(".."), File("../.."))
        return dirs.map { File(it, path) }.first { it.exists() }.readText()
    }

    @Test fun HOM_23_theDockRailSettingReachesTheDockHostThroughTheLayer() {
        val home = text("app/src/main/kotlin/com/bos/sphere/HomeScreen.kt")
        assertTrue(home.contains("alwaysShown = HomeChromeRules.forcesDock(landing, chromeSettings)"))
        assertTrue(text("app/src/main/kotlin/com/bos/sphere/ui/DockLayer.kt").contains("alwaysShown = alwaysShown"))
        assertTrue(text("feature-dock/src/main/kotlin/com/bos/sphere/feature/dock/DockHost.kt").contains("!forceEnabled && !alwaysShown && !state.dock.isEnabled(tier)"))
    }

    @Test fun PAL_24_theApplicationClosesTheLabSignalGateInBuildsWithoutTheLabAndResetsTheStoredForce() {
        val app = text("app/src/main/kotlin/com/bos/sphere/LiftoffApplication.kt")
        assertTrue(app.contains("SignalForceGate.labAvailable = com.bos.sphere.ui.LabGate.visible()"))
        assertTrue(app.contains("radarSignalForce = com.bos.sphere.core.data.SignalForce.Live"))
        assertTrue(text("feature-grid/src/main/kotlin/com/bos/sphere/feature/grid/SignalBackground.kt").contains("SignalForceGate.effective(requestedForce)"))
    }

    @Test fun HOM_23_theWeatherLayerClockFollowsTheOneAnimationRule() {
        assertTrue(text("feature-grid/src/main/kotlin/com/bos/sphere/feature/grid/GlanceWeatherLayer.kt").contains("GlanceWeather.animates("))
    }
}
