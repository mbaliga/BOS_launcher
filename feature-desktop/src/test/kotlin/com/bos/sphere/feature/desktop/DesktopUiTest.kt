package com.bos.sphere.feature.desktop

import android.app.Activity
import android.content.Context
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.Toast
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/** Parts of the desktop stream that need an Android runtime. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DesktopUiTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test fun EXT_10_cursorTapAndLongPressReachViewsOfTheLaunchersOwnWindowOnly() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val root = FrameLayout(activity)
        val seen = mutableListOf<Triple<Int, Float, Float>>()
        root.setOnTouchListener { _, e -> seen += Triple(e.actionMasked, e.x, e.y); true }
        activity.setContentView(root)
        assertTrue(SyntheticTouch.tap(root, 120f, 340f))
        assertEquals(listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP), seen.map { it.first })
        assertTrue(seen.all { it.second == 120f && it.third == 340f })
        seen.clear()
        SyntheticTouch.longPress(root, 10f, 20f)
        assertEquals(listOf(MotionEvent.ACTION_DOWN), seen.map { it.first })        // held down
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(SyntheticTouch.LONG_PRESS_MS + 100))
        assertEquals(listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP), seen.map { it.first })  // released after the hold
    }

    @Test fun EXT_10_aViewThatIgnoresTouchReportsNotHandledSoNothingElseIsDriven() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val plain = View(activity)
        assertFalse(SyntheticTouch.tap(plain, 1f, 1f))
    }

    @Test fun EXT_02_theOpenTrampolineFinishesAndSaysSoWhenNoDisplayCanHostTheDesktop() {
        val a = Robolectric.buildActivity(DesktopOpenActivity::class.java).setup().get()
        assertTrue(a.isFinishing)
        assertEquals("No external display is ready for the desktop", lastToast())
    }

    @Test fun EXT_02_theOpenIntentNamesTheTrampolineAndTheSurfaceIsLaunchedWithADisplayId() {
        val i = DesktopController.get(context).openIntent()
        assertEquals(DesktopController.OPEN_CLASS, i.component!!.className)
        val info = context.packageManager.getActivityInfo(i.component!!, 0)
        assertFalse(info.exported)
    }
}

internal fun lastToast(): String? = org.robolectric.shadows.ShadowToast.getTextOfLatestToast()
