package com.bos.sphere.ui

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import com.bos.sphere.core.data.AppPolicy
import com.bos.sphere.core.data.DeliveryMode
import com.bos.sphere.core.data.HubNotification
import com.bos.sphere.core.data.LaunchpadPrefs
import com.bos.sphere.core.data.LaunchpadViewBuilder
import com.bos.sphere.feature.launchpad.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DigestWidgetTest {
    private val app = ApplicationProvider.getApplicationContext<android.app.Application>()
    private val now = 1_710_000_000_000L
    private val zone = ZoneOffset.UTC

    private fun n(key: String, pkg: String, label: String, title: String, agoMin: Long) =
        HubNotification(id = key, key = key, source = label, title = title, summary = "", timestamp = now - agoMin * 60_000, packageName = pkg, appLabel = label)

    private fun view(items: List<HubNotification>, vararg policies: AppPolicy) =
        LaunchpadViewBuilder.build(items, emptyList(), LaunchpadPrefs(), policies.toList(), emptyMap(), now, zone)

    @Test fun STR_11_releasedDigestItemsBecomeRowsNewestFirstCappedAtFour() {
        val items = (1..6).map { n("k$it", "demo.mail", "Mail", "Mail $it", 60L + it) }
        val p = AppPolicy("demo.mail", DeliveryMode.Digest, listOf(15 * 60))   // released at 15:00; now is 16:00, the items are older
        val m = DigestWidgetLogic.model(view(items, p), now, zone)
        assertEquals(DigestWidgetLogic.MAX_ROWS, m.rows.size)
        assertEquals("Mail · Mail 1", m.rows.first())
        assertEquals("6 in the digest", m.summary)
    }

    @Test fun STR_11_heldItemsAreCountedWithTheirReleaseTimeNotShown() {
        val items = listOf(n("a", "demo.chat", "Chat", "Hi", 5))
        val p = AppPolicy("demo.chat", DeliveryMode.Digest, listOf(23 * 60 + 59))
        val v = view(items, p)
        val m = DigestWidgetLogic.model(v, now, zone)
        assertTrue(m.rows.isEmpty())
        assertTrue(m.summary, m.summary.startsWith("1 held for the digest at "))
    }

    @Test fun STR_11_emptyDigestSaysSo() {
        val m = DigestWidgetLogic.model(view(emptyList()), now, zone)
        assertEquals(DigestWidgetLogic.EMPTY, m.summary)
        assertTrue(m.rows.isEmpty())
    }

    @Test fun STR_11_remoteViewsCarryTheModelAndHideUnusedRows() {
        val model = DigestWidgetModel("Digest", "2 in the digest", listOf("Mail · One", "Chat · Two"))
        val rv = DigestWidget.views(app, model)
        val root = rv.apply(app, FrameLayout(app))
        assertEquals("Digest", root.findViewById<TextView>(R.id.digest_title).text.toString())
        assertEquals("2 in the digest", root.findViewById<TextView>(R.id.digest_summary).text.toString())
        assertEquals("Mail · One", root.findViewById<TextView>(R.id.digest_row0).text.toString())
        assertEquals(android.view.View.VISIBLE, root.findViewById<TextView>(R.id.digest_row1).visibility)
        assertEquals(android.view.View.GONE, root.findViewById<TextView>(R.id.digest_row2).visibility)
    }

    @Test fun STR_11_theProviderIsDeclaredForTheHomeScreenAndPaintsPlacedWidgets() {
        val comp = ComponentName(app, DigestWidgetProvider::class.java)
        val info = app.packageManager.getReceiverInfo(comp, android.content.pm.PackageManager.GET_META_DATA)
        assertTrue(info.metaData.containsKey("android.appwidget.provider"))
        val sh = shadowOf(AppWidgetManager.getInstance(app))
        val id = sh.createWidget(DigestWidgetProvider::class.java, R.layout.widget_digest)
        DigestWidgetProvider().onUpdate(app, AppWidgetManager.getInstance(app), intArrayOf(id))
        val v = sh.getViewFor(id)
        assertTrue(v.findViewById<TextView>(R.id.digest_title).text.isNotEmpty())
    }
}
