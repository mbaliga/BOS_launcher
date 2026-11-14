package com.bos.sphere.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.bos.sphere.core.data.AppPolicy
import com.bos.sphere.core.data.Connectivity
import com.bos.sphere.core.data.DeliveryMode
import com.bos.sphere.core.data.GroupMode
import com.bos.sphere.core.data.HubNotification
import com.bos.sphere.core.data.LaunchpadPrefs
import com.bos.sphere.core.data.SoundState
import com.bos.sphere.core.data.SystemSnapshot
import org.junit.Rule
import java.io.File
import java.time.LocalDate
import java.time.ZoneOffset

/** Shared fixtures and capture for the Launchpad screenshot tests (STR-07). */
abstract class LaunchpadShots {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    protected val outDir = File(System.getProperty("user.dir"), "build/screenshots").apply { mkdirs() }

    protected val zone = ZoneOffset.UTC
    private val day = LocalDate.of(2024, 3, 10)
    protected fun at(h: Int, m: Int = 0) = day.atTime(h, m).toInstant(ZoneOffset.UTC).toEpochMilli()
    protected val now = at(12)

    private fun n(key: String, pkg: String, app: String, title: String, text: String, ts: Long, conv: String? = null, sender: String? = null) =
        HubNotification(id = key, key = key, source = "x", title = title, summary = text, timestamp = ts, packageName = pkg, appLabel = app, conversation = conv, sender = sender, isMessaging = conv != null)

    protected val grouped = listOf(
        n("m1", "demo.chat", "Chat", "Mum", "Are you coming on Sunday?", at(11, 40), conv = "Mum", sender = "Mum"),
        n("m2", "demo.chat", "Chat", "Mum", "Bring the dessert", at(11, 45), conv = "Mum", sender = "Mum"),
        n("m3", "demo.chat", "Chat", "Team", "Build is green", at(10, 5), conv = "Team", sender = "Ana"),
        n("c1", "demo.cal", "Calendar", "Standup in 15 minutes", "Room 4", at(11, 0)),
        n("e1", "demo.mail", "Mail", "Invoice 2291", "Payment received", at(9, 30)),
    )

    protected val digestItems = grouped + listOf(
        n("d1", "demo.news", "News", "Morning briefing", "Seven stories", at(7, 30)),
        n("d2", "demo.news", "News", "Breaking", "A new story", at(10, 15)),
    )
    protected val digestPolicies = listOf(AppPolicy("demo.news", DeliveryMode.Digest, listOf(8 * 60, 18 * 60)))

    protected val system = SystemSnapshot(now, 82, true, Connectivity.Wifi, SoundState.Vibrate)

    @Composable
    protected fun Themed(dark: Boolean, content: @Composable () -> Unit) {
        CompositionLocalProvider(LocalLp provides if (dark) LpDark else LpLight) { content() }
    }

    @Composable
    protected fun Body(
        items: List<HubNotification>, hasAccess: Boolean = true, asked: Boolean = false,
        policies: List<AppPolicy> = emptyList(), mode: GroupMode = GroupMode.AppConversation,
    ) {
        LaunchpadContent(
            items = items, rules = emptyList(), prefs = LaunchpadPrefs(mode), readKeys = setOf("c1"), hasAccess = hasAccess, now = now,
            onClose = {}, onOpenItem = {}, onOpenApp = {}, onDismissItem = {}, onClearAll = {}, onAddRule = {},
            onSetDefaultMode = {}, onRequestAccess = {}, policies = policies, askedBefore = asked, systemState = system, zone = zone,
        )
    }

    /** Renders the current content and writes `build/screenshots/<name>.png`; returns mean luminance 0..255. */
    protected fun shoot(name: String): Double {
        rule.mainClock.advanceTimeBy(800)
        rule.waitForIdle()
        val view = rule.activity.findViewById<android.view.View>(android.R.id.content)
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread { view.draw(Canvas(bmp)) }
        File(outDir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        var sum = 0.0
        val step = 7
        var count = 0
        for (y in 0 until bmp.height step step) for (x in 0 until bmp.width step step) {
            val p = bmp.getPixel(x, y)
            sum += 0.299 * ((p shr 16) and 255) + 0.587 * ((p shr 8) and 255) + 0.114 * (p and 255); count++
        }
        return sum / count
    }
}
