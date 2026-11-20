package com.bos.sphere.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.bos.sphere.core.data.HistoryEntry
import com.bos.sphere.core.data.LaunchEvent
import com.bos.sphere.core.data.TimelineInputs
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Wave 41: screenshots of the richer Timeline with a large mixture of every kind (read by eye; see docs/status/w41-timelineRich.md). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w400dp-h800dp-xhdpi", sdk = [35])
class TimelineRichW41ShotsTest : TimelineRichW41Base() {
    private fun mixture(): TimelineInputs {
        val base = everyKind()
        val burst = (1..4).map { note("wa$it", "demo.wa", "WhatsApp", "Ann", "message number $it", now - (3 + it) * min, category = "msg", read = it > 1, sender = "Ann") }
        val opens = base.opens + listOf(LaunchEvent("demo.maps/.Main#0", now - 27 * min, 11), LaunchEvent("demo.maps/.Main#0", now - 26 * min + 30_000, 11), LaunchEvent("demo.chat/.Main#0", now - 4 * 60 * min, 8))
        return TimelineInputs(opens, base.notifications + burst, base.stored, emptyList(), base.labelOfKey, base.labelOfPackage + ("demo.wa" to "WhatsApp"), base.sessions)
    }

    private fun shot(name: String, dark: Boolean, expand: Boolean = false, fontScale: Float = 1f) {
        host(mixture(), dark = dark, nextAlarm = NextAlarm(now + 19 * 60 * min), header = false, fontScale = fontScale)
        if (expand) rule.onNodeWithTag("timeline.chevron.0", useUnmergedTree = true).performClick()
        val lum = shoot("w41-timeline-$name")
        assertTrue("$name drew something ($lum)", lum > 1.0)
    }

    @Test fun HOM_18_w41_shotDark() = shot("dark", true)
    @Test fun HOM_18_w41_shotLight() = shot("light", false)
    @Test fun HOM_18_w41_shotDarkBurstExpanded() = shot("dark-expanded", true, expand = true)
    @Config(qualifiers = "w400dp-h1900dp-xhdpi", sdk = [35])
    @Test fun HOM_18_w41_shotDarkTallWithEveryKind() = shot("dark-tall", true)
    @Test fun HOM_18_w41_shotLightFont2() = shot("light-font2", false, fontScale = 2f)
}
