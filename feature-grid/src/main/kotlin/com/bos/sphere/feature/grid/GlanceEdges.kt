package com.bos.sphere.feature.grid

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.absolutePadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.bos.sphere.core.data.HomeChromeRules
import com.bos.sphere.core.design.L10n
import androidx.compose.runtime.snapshotFlow
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

/** How the quiet edge handle of the Glance home fades (HOM-23, wave 17): the alpha while a touch is recent and when it has been idle. */
object GlanceHandleFade {
    const val AWAKE_ALPHA = 0.30f
    const val IDLE_ALPHA = 0.06f
    const val IDLE_AFTER_MS = 4000L
    fun alpha(awake: Boolean) = if (awake) AWAKE_ALPHA else IDLE_ALPHA
    /** Wave 23 sweep: the bar takes the Glance ink of the theme; a white bar vanished on the light theme's light grey Glance home. */
    fun tint(dark: Boolean): Color = GlanceInk.clock(dark)
}

/** Wave 23 second review: the plain (not quiet) edge bars of the other landings were white too, and as faint on the light theme's light home. */
object HomeHandleBar {
    const val ALPHA = 0.28f
    fun color(dark: Boolean): Color = GlanceInk.clock(dark).copy(alpha = ALPHA)
}

/**
 * HOM-23, STR-01, STR-14 (wave 17): the slim, quiet edge handle of the Glance home: a short bar at low alpha that fades to almost
 * nothing after [GlanceHandleFade.IDLE_AFTER_MS] and returns when [wake] changes (any touch on the screen) or the handle is touched.
 * The tap target is 48 dp wide by 160 dp tall at the physical edge, a labelled button, focusable. [alignment] is a physical edge
 * (use a BiasAbsoluteAlignment), [rightEdge] tells which side the bar hugs.
 */
@Composable
fun GlanceEdgeHandle(
    label: String, tag: String, rightEdge: Boolean, wake: () -> Int, reduced: Boolean, tint: Color,
    onOpen: () -> Unit, modifier: Modifier = Modifier,
) {
    var awake by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) { snapshotFlow(wake).collectLatest { awake = true; delay(GlanceHandleFade.IDLE_AFTER_MS); awake = false } }
    val a by animateFloatAsState(GlanceHandleFade.alpha(awake), tween(if (reduced) 0 else 600), label = "glanceHandle")
    Box(
        modifier.width(HomeChromeRules.HANDLE_WIDTH_DP.dp).height(HomeChromeRules.HANDLE_HEIGHT_DP.dp).testTag(tag)
            .semantics { contentDescription = label }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClickLabel = label, role = Role.Button) { awake = true; onOpen() },
        // absolute, not Start/End: the edge is a physical one and must not flip with the layout direction
        contentAlignment = androidx.compose.ui.BiasAbsoluteAlignment(if (rightEdge) 1f else -1f, 0f),
    ) {
        Box(
            Modifier.absolutePadding(left = if (rightEdge) 0.dp else 2.dp, right = if (rightEdge) 2.dp else 0.dp).width(3.dp).height(56.dp)
                .graphicsLayer { alpha = a }.background(tint, RoundedCornerShape(2.dp)),
        )
    }
}

/** Observes every touch (initial pass, nothing consumed) and calls [onTouch]: the quiet Glance handles return on any touch. */
fun Modifier.glanceWakeOnTouch(onTouch: () -> Unit): Modifier = this.pointerInput(Unit) {
    awaitEachGesture { awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial); onTouch() }
}
