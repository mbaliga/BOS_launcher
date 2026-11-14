package com.bos.sphere.feature.grid

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.os.Bundle
import android.util.SizeF
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.bos.sphere.core.data.Corner
import com.bos.sphere.core.design.HyleColors
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

// ---------------------------------------------------------------------------------------------
// Host
// ---------------------------------------------------------------------------------------------

/**
 * Thin wrapper over the launcher's single [AppWidgetHost]. Keep exactly one per process/activity
 * (see [rememberLauncherWidgetHost]): two hosts with the same [HOST_ID] steal each other's
 * update callbacks.
 */
class LauncherWidgetHost(context: Context) {
    private val appContext = context.applicationContext

    val appWidgetManager: AppWidgetManager = AppWidgetManager.getInstance(appContext)
    val host: AppWidgetHost = AppWidgetHost(appContext, HOST_ID)

    private var listening = false

    /** Start receiving RemoteViews updates. Safe to call repeatedly. */
    fun startListening() {
        if (listening) return
        // Can throw (e.g. TransactionTooLargeException wrapped, or a dead system service).
        listening = runCatching { host.startListening() }.isSuccess
    }

    fun stopListening() {
        if (!listening) return
        runCatching { host.stopListening() }
        listening = false
    }

    /** New unbound widget id; bind it before use, delete it if binding is abandoned. */
    fun allocateAppWidgetId(): Int = host.allocateAppWidgetId()

    fun deleteWidget(appWidgetId: Int) {
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return
        runCatching { host.deleteAppWidgetId(appWidgetId) }
    }

    /** The bound provider, or null if [appWidgetId] is invalid, unbound, or its app is gone. */
    fun providerInfo(appWidgetId: Int): AppWidgetProviderInfo? =
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) null
        else runCatching { appWidgetManager.getAppWidgetInfo(appWidgetId) }.getOrNull()

    companion object {
        /** Stable host id ("SH"). Changing it orphans every previously bound widget. */
        const val HOST_ID = 0x5348
    }
}

/**
 * A remembered [LauncherWidgetHost] that listens while the current lifecycle is at least
 * STARTED (start on ON_START, stop on ON_STOP / disposal).
 */
@Composable
fun rememberLauncherWidgetHost(): LauncherWidgetHost {
    val context = LocalContext.current
    val host = remember { LauncherWidgetHost(context.applicationContext) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner, host) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> host.startListening()
                Lifecycle.Event.ON_STOP -> host.stopListening()
                else -> Unit
            }
        }
        owner.lifecycle.addObserver(observer) // replays ON_START if already started
        onDispose {
            owner.lifecycle.removeObserver(observer)
            host.stopListening()
        }
    }
    return host
}

// ---------------------------------------------------------------------------------------------
// Live widget view
// ---------------------------------------------------------------------------------------------

/**
 * The live widget: an [AppWidgetHostView] from [LauncherWidgetHost.host] filling the incoming
 * constraints, reporting that size (in dp) to the provider via `updateAppWidgetSize`.
 */
@Composable
fun HostedWidget(host: LauncherWidgetHost, appWidgetId: Int, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier) {
        val wDp = maxWidth.value
        val hDp = maxHeight.value
        val info = remember(host, appWidgetId) { host.providerInfo(appWidgetId) }
        androidx.compose.runtime.key(appWidgetId) {
            val lastSize = remember { floatArrayOf(-1f, -1f) }
            AndroidView(
                factory = { ctx ->
                    host.host.createView(ctx, appWidgetId, info).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        )
                    }
                },
                update = { view: AppWidgetHostView ->
                    if (wDp.isFinite() && hDp.isFinite() && wDp > 0f && hDp > 0f &&
                        (lastSize[0] != wDp || lastSize[1] != hDp)
                    ) {
                        lastSize[0] = wDp
                        lastSize[1] = hDp
                        runCatching { view.updateAppWidgetSize(Bundle(), listOf(SizeF(wDp, hDp))) }
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Peek geometry (pure)
// ---------------------------------------------------------------------------------------------

internal data class PeekSize(val width: Float, val height: Float)
internal data class PeekPoint(val x: Float, val y: Float)
internal data class PeekInsets(
    val left: Float = 0f,
    val top: Float = 0f,
    val right: Float = 0f,
    val bottom: Float = 0f,
)

/**
 * Pure px math for [PeekWidget]. The card is always laid out at its expanded size; collapsing
 * only moves it so that its *inner* corner (opposite the anchor) pokes out past the safe-area
 * edge by [visibleExtent], with the rest tucked beyond the screen corner.
 */
internal object PeekGeometry {
    /** Fraction of each axis left on screen while peeking (0.5 × 0.5 ≈ a quarter of the card). */
    const val VisibleFraction = 0.5f
    const val PeekScale = 0.9f
    const val MaxScreenFraction = 0.85f

    fun isLeft(anchor: Corner) = anchor == Corner.TopLeft || anchor == Corner.BottomLeft
    fun isTop(anchor: Corner) = anchor == Corner.TopLeft || anchor == Corner.TopRight

    /**
     * Expanded card size: the provider's min size, at least [floor], at most
     * [MaxScreenFraction] of the container and never beyond the safe area minus [margin]s.
     */
    fun expandedSize(
        minWidth: Float,
        minHeight: Float,
        container: PeekSize,
        insets: PeekInsets,
        margin: Float,
        floor: PeekSize,
    ): PeekSize {
        val maxW = min(
            container.width * MaxScreenFraction,
            container.width - insets.left - insets.right - 2 * margin,
        ).coerceAtLeast(1f)
        val maxH = min(
            container.height * MaxScreenFraction,
            container.height - insets.top - insets.bottom - 2 * margin,
        ).coerceAtLeast(1f)
        return PeekSize(
            max(minWidth, floor.width).coerceAtMost(maxW),
            max(minHeight, floor.height).coerceAtMost(maxH),
        )
    }

    /** How much of [card] stays on screen while peeking, capped by [maxVisible]. */
    fun visibleExtent(card: PeekSize, maxVisible: PeekSize): PeekSize = PeekSize(
        min(card.width * VisibleFraction, maxVisible.width),
        min(card.height * VisibleFraction, maxVisible.height),
    )

    /** Top-left of the card while peeking at [anchor]. */
    fun peekOrigin(
        anchor: Corner,
        container: PeekSize,
        card: PeekSize,
        visible: PeekSize,
        insets: PeekInsets,
    ): PeekPoint = PeekPoint(
        x = if (isLeft(anchor)) insets.left + visible.width - card.width
        else container.width - insets.right - visible.width,
        y = if (isTop(anchor)) insets.top + visible.height - card.height
        else container.height - insets.bottom - visible.height,
    )

    /** Top-left of the card when fully out, [margin] in from the safe-area corner. */
    fun expandedOrigin(
        anchor: Corner,
        container: PeekSize,
        card: PeekSize,
        insets: PeekInsets,
        margin: Float,
    ): PeekPoint = PeekPoint(
        x = if (isLeft(anchor)) insets.left + margin
        else container.width - insets.right - margin - card.width,
        y = if (isTop(anchor)) insets.top + margin
        else container.height - insets.bottom - margin - card.height,
    )

    /** Pivot (fractions of the card) at the anchor corner itself. */
    fun anchorPivot(anchor: Corner) = PeekPoint(if (isLeft(anchor)) 0f else 1f, if (isTop(anchor)) 0f else 1f)

    /** Pivot at the corner opposite the anchor — the visible corner of a peek. */
    fun innerPivot(anchor: Corner) = PeekPoint(if (isLeft(anchor)) 1f else 0f, if (isTop(anchor)) 1f else 0f)

    fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t
    fun lerp(a: PeekPoint, b: PeekPoint, t: Float) = PeekPoint(lerp(a.x, b.x, t), lerp(a.y, b.y, t))
}

// ---------------------------------------------------------------------------------------------
// Peek widget
// ---------------------------------------------------------------------------------------------

private val PeekCorner = 22.dp
private val PeekMargin = 12.dp
private val MinCardWidth = 160.dp
private val MinCardHeight = 120.dp
private val MaxVisibleWidth = 132.dp
private val MaxVisibleHeight = 96.dp

/**
 * A widget living in a screen corner. Collapsed, a glass card peeks out of [anchor] (about a
 * quarter visible, slightly scaled down, with [label]); touches go to the peek, not the widget.
 * Tapping springs it out to full size at that corner, fully interactive; tapping the scrim or
 * pressing back retracts it. An invalid/unbound id shows an "unavailable" peek that calls
 * [onRemove] when tapped.
 *
 * [modifier] should fill the screen: the card positions itself (respecting
 * `WindowInsets.safeDrawing`) and areas outside it do not intercept touches while collapsed.
 */
@Composable
fun PeekWidget(
    host: LauncherWidgetHost,
    appWidgetId: Int,
    label: String,
    anchor: Corner,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val info = remember(host, appWidgetId) { host.providerInfo(appWidgetId) }
    val available = info != null
    val isExpanded = expanded && available

    val progress = remember { Animatable(if (isExpanded) 1f else 0f) }
    LaunchedEffect(isExpanded) {
        progress.animateTo(
            targetValue = if (isExpanded) 1f else 0f,
            animationSpec = if (isExpanded) {
                spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessMediumLow) // overshoot out
            } else {
                spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)
            },
        )
    }
    val onChange by rememberUpdatedState(onExpandedChange)
    val onRemoveNow by rememberUpdatedState(onRemove)
    BackHandler(enabled = isExpanded) { onChange(false) }

    val density = LocalDensity.current
    val dir = LocalLayoutDirection.current
    val safe = WindowInsets.safeDrawing

    BoxWithConstraints(modifier, contentAlignment = AbsoluteAlignment.TopLeft) {
        val container = PeekSize(constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat())
        val insets = PeekInsets(
            left = safe.getLeft(density, dir).toFloat(),
            top = safe.getTop(density).toFloat(),
            right = safe.getRight(density, dir).toFloat(),
            bottom = safe.getBottom(density).toFloat(),
        )
        val marginPx = with(density) { PeekMargin.toPx() }
        val card = with(density) {
            PeekGeometry.expandedSize(
                minWidth = (info?.minWidth ?: 0).toFloat(), // provider min sizes are px
                minHeight = (info?.minHeight ?: 0).toFloat(),
                container = container,
                insets = insets,
                margin = marginPx,
                floor = PeekSize(MinCardWidth.toPx(), MinCardHeight.toPx()),
            )
        }
        val visible = with(density) {
            PeekGeometry.visibleExtent(card, PeekSize(MaxVisibleWidth.toPx(), MaxVisibleHeight.toPx()))
        }
        val peekAt = PeekGeometry.peekOrigin(anchor, container, card, visible, insets)
        val outAt = PeekGeometry.expandedOrigin(anchor, container, card, insets, marginPx)
        val inner = PeekGeometry.innerPivot(anchor)
        val outer = PeekGeometry.anchorPivot(anchor)

        // Scrim: only composed while (partly) out, so a collapsed peek never blocks the home.
        if (isExpanded || progress.value > 0.001f) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = progress.value.coerceIn(0f, 1f) }
                    .background(Color.Black.copy(alpha = 0.5f))
                    .pointerInput(Unit) { detectTapGestures { onChange(false) } },
            )
        }

        val shape = RoundedCornerShape(PeekCorner)
        val cardWdp = with(density) { card.width.toDp() }
        val cardHdp = with(density) { card.height.toDp() }
        Box(
            Modifier
                .absoluteOffset {
                    val p = PeekGeometry.lerp(peekAt, outAt, progress.value)
                    IntOffset(p.x.roundToInt(), p.y.roundToInt())
                }
                .size(cardWdp, cardHdp)
                .graphicsLayer {
                    val t = progress.value
                    val s = PeekGeometry.lerp(PeekGeometry.PeekScale, 1f, t)
                    scaleX = s
                    scaleY = s
                    val pivot = PeekGeometry.lerp(inner, outer, t.coerceIn(0f, 1f))
                    transformOrigin = TransformOrigin(pivot.x, pivot.y)
                }
                .shadow(elevation = 18.dp, shape = shape, ambientColor = Color.Black, spotColor = Color.Black)
                .background(HyleColors.SurfaceTranslucent, shape)
                .border(
                    width = 1.dp,
                    brush = Brush.linearGradient(listOf(HyleColors.InsetHighlight, HyleColors.Hairline)),
                    shape = shape,
                )
                .androidClip(shape),
        ) {
            if (available) {
                HostedWidget(host, appWidgetId, Modifier.fillMaxSize().padding(8.dp))
            } else {
                UnavailableBody(anchor, Modifier.fillMaxSize())
            }

            // Collapsed: veil + label, and an interceptor so the widget never sees peek touches.
            if (!isExpanded) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = (1f - progress.value * 2f).coerceIn(0f, 1f) }
                        .drawBehind { drawRect(innerVeil(anchor, size)) }
                        .pointerInput(available) {
                            detectTapGestures {
                                if (available) onChange(true) else onRemoveNow()
                            }
                        },
                ) {
                    if (available) {
                        PeekLabel(
                            text = label,
                            modifier = Modifier
                                .align(innerAlignment(anchor))
                                .padding(10.dp)
                                .size(
                                    width = (with(density) { visible.width.toDp() } - 20.dp).coerceAtLeast(24.dp),
                                    height = 18.dp,
                                ),
                            alignEnd = PeekGeometry.isLeft(anchor),
                        )
                    }
                }
            }
        }
    }
}

/** Clip children (incl. the AndroidView) to the card shape. */
private fun Modifier.androidClip(shape: RoundedCornerShape): Modifier =
    this.then(Modifier.graphicsLayer { this.shape = shape; clip = true })

/** Alignment of the inner (visible) corner of a card peeking from [anchor]. */
private fun innerAlignment(anchor: Corner): Alignment = when (anchor) {
    Corner.TopLeft -> AbsoluteAlignment.BottomRight
    Corner.TopRight -> AbsoluteAlignment.BottomLeft
    Corner.BottomRight -> AbsoluteAlignment.TopLeft
    Corner.BottomLeft -> AbsoluteAlignment.TopRight
}

/** A soft glass veil that darkens toward the visible corner so the label reads. */
private fun innerVeil(anchor: Corner, size: Size): Brush {
    val p = PeekGeometry.innerPivot(anchor)
    val a = PeekGeometry.anchorPivot(anchor)
    return Brush.linearGradient(
        colorStops = arrayOf(
            0f to Color.Transparent,
            0.55f to HyleColors.Background.copy(alpha = 0.15f),
            1f to HyleColors.Background.copy(alpha = 0.72f),
        ),
        start = Offset(a.x * size.width, a.y * size.height),
        end = Offset(p.x * size.width, p.y * size.height),
    )
}

@Composable
private fun PeekLabel(text: String, modifier: Modifier, alignEnd: Boolean) {
    Row(
        modifier,
        horizontalArrangement = if (alignEnd) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(6.dp).background(HyleColors.Violet, CircleShape))
        Text(
            text = text,
            color = HyleColors.InkBright,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 6.dp),
        )
    }
}

@Composable
private fun UnavailableBody(anchor: Corner, modifier: Modifier) {
    Box(modifier.padding(12.dp)) {
        Column(
            Modifier.align(innerAlignment(anchor)),
            horizontalAlignment = if (PeekGeometry.isLeft(anchor)) Alignment.End else Alignment.Start,
        ) {
            Text("Widget unavailable", color = HyleColors.Ink, fontSize = 11.sp, fontWeight = FontWeight.Medium)
            Text("Tap to remove", color = HyleColors.InkDim, fontSize = 10.sp)
        }
    }
}
