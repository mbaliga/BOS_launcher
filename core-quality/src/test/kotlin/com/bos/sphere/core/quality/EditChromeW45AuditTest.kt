package com.bos.sphere.core.quality

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Wave 45 (editChrome, HOM-12): structural guards for the single-page edit mode's wiring in the home screen, the state classification and the strings. */
class EditChromeW45AuditTest {
    private fun text(path: String) = Repo.file(path).readText()
    private val home get() = text("app/src/main/kotlin/com/bos/sphere/HomeScreen.kt")

    @Test fun HOM_12_theLeftTrayAndBothHandleBlocksAreOffWhileEditing() {
        val h = home
        assertTrue(h.contains("!leftOpen && !rightOpen && !homeEdit.active && HomeChromeRules.shows(landing, ChromeElement.LeftHandle"))
        assertTrue(h.contains("!rightOpen && !leftOpen && !homeEdit.active && HomeChromeRules.shows(landing, ChromeElement.RightHandle"))
    }

    @Test fun HOM_12_everyEdgeSwipeIsOffWhileEditing() {
        val swipes = home.lines().filter { it.contains(".launchpadEdgeDrag(") || it.contains(".rightEdgeSwipe(") }
        assertEquals(3, swipes.size)
        swipes.forEach { assertTrue("edge swipe still live while editing: $it", it.contains("!homeEdit.active")) }
    }

    @Test fun HOM_12_theDockStaysDimmedAndTheFixedChromeIsDimmed() {
        val h = home
        assertTrue(h.contains("EditChromeGeometry.DOCK_DIM"))
        assertTrue(h.contains("EditChromeGeometry.FIXED_DIM"))
        assertTrue("the shield over the hotspots and the music line", h.contains("EditFixedChromeShield("))
    }

    @Test fun HOM_12_theOldFloatingPillsAndTheToastLikeHintAreGone() {
        assertFalse("W47: the four-pill bar is deleted, the page editor uses the one edit bar", Repo.file("feature-grid/src/main/kotlin/com/bos/sphere/feature/grid/HomeEditBar.kt").exists())
        assertFalse(text("feature-grid/src/main/kotlin/com/bos/sphere/feature/grid/HomeEditChrome.kt").contains("Editing home: drag items to rearrange"))
        assertFalse("no toast for the hint", text("feature-grid/src/main/kotlin/com/bos/sphere/feature/grid/HomeEditChrome.kt").contains("Toast"))
        assertTrue("the single-page chrome replaces the pill bar outside the page editor", home.contains("HomeEditChrome("))
    }

    @Test fun HOM_12_theHintCounterIsATolerantDeviceLocalPrefFile() {
        val h = home
        assertTrue(h.contains("TolerantPrefs.of(context.applicationContext, com.bos.sphere.core.data.EditChromeStore.FILE)"))
        assertTrue(text("core-data/src/main/kotlin/com/bos/sphere/core/data/StateSchema.kt").contains("EditChromeStore.FILE"))
        assertTrue(text("app/src/main/res/xml/backup_rules.xml").contains("edit_chrome.xml"))
        assertEquals(2, Regex("edit_chrome\\.xml").findAll(text("app/src/main/res/xml/data_extraction_rules.xml")).count())
    }

    @Test fun HOM_12_noInfiniteAnimationInTheEditChrome() {
        listOf("HomeEditChrome.kt", "EditChromeGeometry.kt", "EditGridDotsLayer.kt").forEach { n ->
            val t = text("feature-grid/src/main/kotlin/com/bos/sphere/feature/grid/$n")
            assertFalse("$n loops", t.contains("rememberInfiniteTransition") || t.contains("infiniteRepeatable") || t.contains("RepeatMode"))
        }
    }

    @Test fun HOM_12_backIsGatedOnTheChromeBeingVisible() {
        val t = text("feature-grid/src/main/kotlin/com/bos/sphere/feature/grid/HomeEditChrome.kt")
        assertTrue(t.contains("BackHandler(enabled = visible)"))
    }
}
