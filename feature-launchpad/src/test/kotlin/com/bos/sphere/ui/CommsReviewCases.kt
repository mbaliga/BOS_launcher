package com.bos.sphere.ui

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.test.core.app.ApplicationProvider
import com.bos.sphere.core.data.CommsScaleSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Wave 28 adversarial review: findings the implementation did not report, each with a test that failed before the fix. */
@OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h909dp-352dpi", sdk = [35])
class CommsReviewTest : CommsLayoutCases("portrait") {
    private fun b(tag: String) = rule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

    @Test fun REV_01_atFontScale2AndSize125TheRowTextStaysInsideItsRowAndTimeDoesNotCoverTheTitle() {
        RuntimeEnvironment.setFontScale(2f)
        render(scale = 125)
        for (k in listOf("k0", "k1")) {
            val row = b("lp.row.$k"); val title = b("lp.title.$k"); val time = b("lp.time.$k")
            assertTrue("$k title ${title.top}..${title.bottom} inside row ${row.top}..${row.bottom}", title.top >= row.top - 1f && title.bottom <= row.bottom + 1f)
            assertTrue("$k time ${time.top}..${time.bottom} inside row", time.top >= row.top - 1f && time.bottom <= row.bottom + 1f)
            assertTrue("$k time (${time.left}) does not overlap the title (${title.right})", time.left >= title.right - 1f || time.top >= title.bottom - 1f)
        }
        shoot("comms-portrait-font2-scale125")
    }

    @Test fun REV_02_inRtlTheLeftKeyMakesTheSizeBiggerBecauseThePlusKeyIsOnTheLeft() {
        render(rtl = true); openMenu()
        rule.onNodeWithTag("lp.menu.list").performScrollToNode(hasTestTag("lp.menu.scale")); rule.waitForIdle()
        rule.onNodeWithTag("lp.menu.scale").requestFocus()
        rule.onNodeWithTag("lp.menu.scale").performKeyInput { pressKey(Key.DirectionLeft) }; rule.waitForIdle()
        assertEquals(105, CommsScaleSettings.percent.value)
    }

    @Test fun REV_03_aSizeChosenBeforeTheFileIsOpenedIsKeptAndWritten() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        ctx.getSharedPreferences(CommsScaleSettings.FILE, 0).edit().clear().commit()
        CommsScaleSettings.resetForTest()
        CommsScaleSettings.set(115)
        CommsScaleSettings.init(ctx)
        assertEquals(115, CommsScaleSettings.percent.value)
        assertEquals(115, ctx.getSharedPreferences(CommsScaleSettings.FILE, 0).getInt(CommsScaleSettings.KEY, -1))
        ctx.getSharedPreferences(CommsScaleSettings.FILE, 0).edit().clear().commit()
    }

    @Test fun REV_04_everyCommsStyleConstantAndEveryScaleHelperIsUsed() {
        val dir = File("src/main/kotlin/com/bos/sphere/ui")
        val all = dir.listFiles { f -> f.extension == "kt" }!!.joinToString("\n") { it.readText() }
        val style = File(dir, "CommsStyle.kt").readText()
        val names = Regex("(?:const val|val) ([A-Z][A-Z0-9_]+)").findAll(style.substringAfter("internal object CommsStyle").substringBefore("/** Dp at scale")).map { it.groupValues[1] }.toList()
        val dead = names.filter { n -> Regex("\\b$n\\b").findAll(all).count() < 2 }
        assertTrue("unused CommsStyle constants: $dead", dead.isEmpty())
        assertTrue("CommsStyle.scaled is dead", !Regex("CommsStyle\\.scaled|fun scaled\\(").containsMatchIn(all))
    }
}
