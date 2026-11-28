package com.bos.sphere.feature.grid

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Wave 27 (soundPopup): every haptic the launcher performs goes through the one strength-aware helper. */
class LauncherHapticsRoutingTest {
    private fun sources(): List<File> {
        val dir = File(System.getProperty("user.dir"), "src/main/kotlin")
        return dir.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
    }

    @Test fun SND_15_noFileObtainsPlatformHapticsDirectlyExceptTheHelper() {
        val offenders = sources().filter { it.name != "LauncherHaptics.kt" && it.readText().contains("LocalHapticFeedback") }.map { it.name }
        assertEquals("these files bypass rememberLauncherHaptics(): $offenders", emptyList<String>(), offenders)
    }

    @Test fun SND_16_everyFileThatPerformsHapticFeedbackGetsItFromTheHelper() {
        val users = sources().filter { it.name != "LauncherHaptics.kt" && it.readText().contains(".performHapticFeedback(") }
        assertTrue("there are haptic call sites", users.size >= 5)
        val bad = users.filter { !it.readText().contains("rememberLauncherHaptics()") }.map { it.name }
        assertEquals(emptyList<String>(), bad)
    }

    private class Rec : androidx.compose.ui.hapticfeedback.HapticFeedback {
        val seen = mutableListOf<androidx.compose.ui.hapticfeedback.HapticFeedbackType>()
        override fun performHapticFeedback(hapticFeedbackType: androidx.compose.ui.hapticfeedback.HapticFeedbackType) { seen += hapticFeedbackType }
    }

    @Test fun SND_17_theStrengthChangesWhatTheHelperPerformsAtOnce() {
        val T = androidx.compose.ui.hapticfeedback.HapticFeedbackType
        val rec = Rec(); val h = StrengthHaptics(rec)
        com.bos.sphere.core.data.LauncherHapticSettings.resetForTest()
        try {
            h.performHapticFeedback(T.TextHandleMove); h.performHapticFeedback(T.LongPress)
            assertEquals(listOf(T.TextHandleMove, T.LongPress), rec.seen) // Medium: unchanged
            rec.seen.clear()
            com.bos.sphere.core.data.LauncherHapticSettings.set(com.bos.sphere.core.data.HapticStrength.Off)
            h.performHapticFeedback(T.TextHandleMove); h.performHapticFeedback(T.LongPress)
            assertTrue(rec.seen.isEmpty())
            com.bos.sphere.core.data.LauncherHapticSettings.set(com.bos.sphere.core.data.HapticStrength.Light)
            h.performHapticFeedback(T.TextHandleMove); h.performHapticFeedback(T.LongPress)
            assertEquals(listOf(T.TextHandleMove), rec.seen)
            rec.seen.clear()
            com.bos.sphere.core.data.LauncherHapticSettings.set(com.bos.sphere.core.data.HapticStrength.Strong)
            h.performHapticFeedback(T.TextHandleMove)
            assertEquals(listOf(T.LongPress), rec.seen)
        } finally { com.bos.sphere.core.data.LauncherHapticSettings.resetForTest() }
    }
}
