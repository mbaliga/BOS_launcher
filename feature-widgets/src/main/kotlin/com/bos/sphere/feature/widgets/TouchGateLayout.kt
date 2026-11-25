package com.bos.sphere.feature.widgets

import android.annotation.SuppressLint
import android.content.Context
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout

/**
 * Wraps a hosted widget view and decides whether it may receive touches. While [passTouches] is
 * false the gate intercepts the stream and declines it, so nothing reaches the widget and the
 * Compose gesture handler underneath sees the touch untouched (an unfocused equator tile behaves
 * like an app tile). Accessibility actions are not gated.
 */
internal class TouchGateLayout(context: Context) : FrameLayout(context) {

    var passTouches: Boolean = false

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean = !passTouches

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean = false

    /** Moves [child] into this gate, detaching it from any previous parent first. */
    fun host(child: View) {
        if (child.parent === this) return
        (child.parent as? ViewGroup)?.removeView(child)
        removeAllViews()
        addView(child, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }
}
