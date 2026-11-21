package com.bos.sphere.feature.desktop

import android.os.SystemClock
import android.view.MotionEvent
import android.view.View

/**
 * Taps and long-presses at the cursor by handing touch events to the launcher's own view hierarchy (EXT-10). The events never
 * leave this window: no accessibility service, no injection into other apps. This is what lets the phone's trackpad click the
 * dock, menus and every other control of the desktop surface, not only the items the surface registers itself.
 */
object SyntheticTouch {
    const val LONG_PRESS_MS = 600L

    /** Down then up at ([x], [y]) in [view]'s own coordinates. Returns true when the view tree consumed the press. */
    fun tap(view: View, x: Float, y: Float, now: () -> Long = SystemClock::uptimeMillis): Boolean {
        val t = now()
        val down = event(t, t, MotionEvent.ACTION_DOWN, x, y)
        val handled = view.dispatchTouchEvent(down)
        val up = event(t, t + 40, MotionEvent.ACTION_UP, x, y)
        view.dispatchTouchEvent(up)
        down.recycle(); up.recycle()
        return handled
    }

    /** Press, hold for [LONG_PRESS_MS] on the view's handler, then release; the long press is what opens an item's menu. */
    fun longPress(view: View, x: Float, y: Float, now: () -> Long = SystemClock::uptimeMillis) {
        val t = now()
        val down = event(t, t, MotionEvent.ACTION_DOWN, x, y)
        view.dispatchTouchEvent(down)
        view.postDelayed({
            val up = event(t, t + LONG_PRESS_MS + 20, MotionEvent.ACTION_UP, x, y)
            view.dispatchTouchEvent(up); up.recycle(); down.recycle()
        }, LONG_PRESS_MS + 20)
    }

    private fun event(downTime: Long, eventTime: Long, action: Int, x: Float, y: Float): MotionEvent =
        MotionEvent.obtain(downTime, eventTime, action, x, y, 0).also { it.source = android.view.InputDevice.SOURCE_TOUCHSCREEN }
}
